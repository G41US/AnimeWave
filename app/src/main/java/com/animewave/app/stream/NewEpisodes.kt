package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Twice a day, checks the shows in My List and notifies when a new episode is out. */
class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store.get(applicationContext)
        if (!store.alerts) return Result.success()
        store.myList.filter { it.kind == Kind.TV }.forEach { show ->
            try {
                val (ep, date) = Tmdb.lastAired(show.id) ?: return@forEach
                val seen = store.lastSeen(show.key)
                if (seen != null && seen != ep) notify(show, ep, date)
                store.setLastSeen(show.key, ep)
            } catch (_: Exception) { }
        }
        return Result.success()
    }

    private fun notify(show: Title, ep: String, @Suppress("UNUSED_PARAMETER") date: String) {
        val ctx = applicationContext
        I18n.init(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val (s, e) = ep.split(":")
        val open = PendingIntent.getActivity(
            ctx, show.id, Intent(ctx, MainActivity::class.java).putExtra("open_kind", show.kind.name).putExtra("open_id", show.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_play_fill)
            .setContentTitle(tf("New episode of {1}", show.name))
            .setContentText(tf("Season {1}, Episode {2} is out now", s, e))
            .setContentIntent(open).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(ctx).notify(show.id, n)
    }

    companion object {
        const val CHANNEL = "new_episodes"

        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(NotificationChannel(CHANNEL, t("New episodes"), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val req = PeriodicWorkRequestBuilder<NewEpisodesWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("new_episodes", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
