package com.animewave.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

fun parseUpdate(json: String) = JSONObject(json).let {
    UpdateInfo(it.getLong("versionCode"), it.getString("versionName"), it.optString("notes"))
}

/**
 * Updates come from the repo's "latest" release, published by CI. Android only installs them
 * over the current app if they're signed with the same key, so a tampered APK can't replace it.
 */
object Updates {
    private const val BASE = "https://github.com/G41US/AnimeWave/releases/download/latest/"

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        if (responseCode !in 200..299) throw IOException("HTTP $responseCode")
    }

    suspend fun latest(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = open(BASE + "version.json")
        try { parseUpdate(conn.inputStream.bufferedReader().use { it.readText() }) } finally { conn.disconnect() }
    }

    fun installedCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun installedName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    suspend fun download(context: Context, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(dir, "AnimeWave.apk.part")
        val apk = File(dir, "AnimeWave.apk")
        val conn = open(BASE + "AnimeWave.apk")
        try {
            val total = conn.contentLengthLong
            var done = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != shown) { shown = pct; onProgress(pct / 100f) }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Download was cut off. Try again.")
            apk.delete()
            if (!part.renameTo(apk)) throw IOException("Couldn't save the update.")
            apk
        } finally {
            conn.disconnect()
            part.delete()
        }
    }

    /** Returns false when the user first has to allow AnimeWave to install apps (Android 8+). */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return false
            } catch (e: ActivityNotFoundException) {
                // Some TV boxes lack this screen; the installer will ask for permission itself.
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
