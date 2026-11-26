package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** What a download is, kept inside the download request itself. */
data class DownloadInfo(val title: Title, val season: Int, val episode: Int, val headers: Map<String, String>, val subtitle: String?) {
    val label get() = if (title.kind == Kind.TV) "${title.name} · S$season E$episode" else title.name

    fun toBytes(): ByteArray = JSONObject()
        .put("id", title.id).put("kind", title.kind.name).put("name", title.name)
        .put("poster", title.poster ?: "").put("backdrop", title.backdrop ?: "").put("year", title.year ?: "")
        .put("s", season).put("e", episode).put("sub", subtitle ?: "")
        .put("headers", JSONObject(headers as Map<*, *>))
        .toString().toByteArray()

    companion object {
        fun from(bytes: ByteArray): DownloadInfo? = try {
            val o = JSONObject(String(bytes))
            val h = o.optJSONObject("headers")
            DownloadInfo(
                Title(o.getInt("id"), Kind.valueOf(o.getString("kind")), o.getString("name"), "",
                    o.optString("poster").ifBlank { null }, o.optString("backdrop").ifBlank { null }, o.optString("year").ifBlank { null }, 0.0),
                o.optInt("s", 1), o.optInt("e", 1),
                h?.keys()?.asSequence()?.associateWith { h.getString(it) } ?: emptyMap(),
                o.optString("sub").ifBlank { null },
            )
        } catch (_: Exception) { null }
    }
}

@OptIn(UnstableApi::class)
object Downloads {
    private const val CHANNEL = "downloads"
    @Volatile private var manager: DownloadManager? = null
    private lateinit var cache: SimpleCache
    private val hostHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val io = Executors.newSingleThreadExecutor()

    @Synchronized
    fun manager(context: Context): DownloadManager {
        manager?.let { return it }
        val app = context.applicationContext
        val db = StandaloneDatabaseProvider(app)
        cache = SimpleCache(File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads"), NoOpCacheEvictor(), db)
        val m = DownloadManager(app, db, cache, httpFactory(), io).apply { maxParallelDownloads = 2 }
        // Each stream needs the headers its source expects; remember them per host.
        m.downloadIndex.getDownloads().use { c ->
            while (c.moveToNext()) remember(c.download.request)
        }
        manager = m
        return m
    }

    private fun remember(r: DownloadRequest) {
        val info = DownloadInfo.from(r.data) ?: return
        r.uri.host?.let { hostHeaders[it] = info.headers }
    }

    private fun httpFactory(): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
    ) { spec ->
        val h = spec.uri.host?.let { hostHeaders[it] } ?: hostHeaders.values.firstOrNull()
        if (h == null) spec else spec.withAdditionalHeaders(h)
    }

    /** Offline player data: everything comes from the download cache. */
    fun offlineFactory(context: Context): DataSource.Factory {
        manager(context)
        return CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(httpFactory()).setCacheWriteDataSinkFactory(null)
    }

    fun all(context: Context): List<Download> {
        val m = manager(context)
        val out = mutableListOf<Download>()
        m.downloadIndex.getDownloads().use { c -> while (c.moveToNext()) out.add(c.download) }
        return out.sortedByDescending { it.startTimeMs }
    }

    fun find(context: Context, id: String): Download? = manager(context).downloadIndex.getDownload(id)

    fun idFor(t: Title, s: Int, e: Int) = if (t.kind == Kind.TV) "${t.key}:$s:$e" else t.key

    fun remove(context: Context, id: String) {
        DownloadService.sendRemoveDownload(context, AnimeDownloadService::class.java, id, false)
        File(context.filesDir, "subs/${id.replace(':', '_')}.vtt").delete()
    }

    /**
     * Starts downloading [url] (HLS or MP4) at up to 720p, with an English subtitle when one is known.
     * Calls back with an error message, or null once the download is queued.
     */
    fun start(context: Context, info: DownloadInfo, url: String, hls: Boolean, subtitleUrl: String?, done: (String?) -> Unit) {
        val app = context.applicationContext
        manager(app)
        val id = idFor(info.title, info.season, info.episode)
        url.toUri().host?.let { hostHeaders[it] = info.headers }
        io.execute {
            val sub = subtitleUrl?.let { saveSubtitle(app, id, it, info.headers) }
            val data = info.copy(subtitle = sub).toBytes()
            val item = MediaItem.Builder().setUri(url).setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null).build()
            android.os.Handler(app.mainLooper).post {
                if (!hls) {
                    DownloadService.sendAddDownload(app, AnimeDownloadService::class.java,
                        DownloadRequest.Builder(id, url.toUri()).setData(data).build(), false)
                    done(null); return@post
                }
                val params = DefaultTrackSelector.Parameters.Builder(app).setMaxVideoSize(1280, 720).build()
                val helper = DownloadHelper.forMediaItem(item, params, DefaultRenderersFactory(app), httpFactory(), false)
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(h: DownloadHelper, tracksInfoAvailable: Boolean) {
                        val request = h.getDownloadRequest(id, data)
                        h.release()
                        DownloadService.sendAddDownload(app, AnimeDownloadService::class.java, request, false)
                        done(null)
                    }
                    override fun onPrepareError(h: DownloadHelper, e: IOException) {
                        h.release(); done("Couldn't start the download. Try another source.")
                    }
                })
            }
        }
    }

    private fun saveSubtitle(context: Context, id: String, url: String, headers: Map<String, String>): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val vtt = if (text.trimStart().startsWith("WEBVTT")) text else SubtitleSync.srtToVtt(text)
        File(context.filesDir, "subs").mkdirs()
        File(context.filesDir, "subs/${id.replace(':', '_')}.vtt").apply { writeText(vtt) }.absolutePath
    } catch (_: Exception) { null }

    fun notificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    fun helper(context: Context) = DownloadNotificationHelper(context, CHANNEL)
}

@OptIn(UnstableApi::class)
class AnimeDownloadService : DownloadService(7, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, "downloads", R.string.downloads, 0) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        Downloads.helper(this).buildProgressNotification(this, R.drawable.ic_download, null, null, downloads, notMetRequirements)
}
