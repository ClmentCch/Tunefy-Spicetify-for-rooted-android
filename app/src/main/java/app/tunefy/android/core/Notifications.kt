package app.tunefy.android.core

import android.Manifest
import android.app.Notification
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
import app.tunefy.android.MainActivity
import app.tunefy.android.R
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.download.DownloadService
import app.tunefy.android.util.Formatters

object Notifications {

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val downloads = NotificationChannel(
            Constants.CHANNEL_DOWNLOADS,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.download_channel_description)
            setShowBadge(false)
        }
        val installs = NotificationChannel(
            Constants.CHANNEL_INSTALLS,
            context.getString(R.string.install_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.install_channel_description)
        }
        manager.createNotificationChannels(listOf(downloads, installs))
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun downloadProgress(context: Context, tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.status.isActive }
        val single = active.singleOrNull()
        val totalBytes = active.sumOf { it.totalBytes }
        val doneBytes = active.sumOf { it.downloadedBytes }
        val percent = if (totalBytes > 0) (doneBytes * 100 / totalBytes).toInt() else 0
        val speed = active.sumOf { it.speedBytesPerSecond }
        val title = when {
            single != null -> single.itemName
            active.size > 1 -> "${active.size} téléchargements"
            else -> "TuneFy"
        }
        val detail = when {
            single != null -> "${Formatters.bytes(doneBytes)} / ${Formatters.bytes(totalBytes)} · ${Formatters.speed(speed)}"
            active.size > 1 -> "${Formatters.bytes(doneBytes)} · ${Formatters.speed(speed)}"
            else -> "Préparation…"
        }
        val cancelAll = PendingIntent.getService(
            context,
            101,
            Intent(context, DownloadService::class.java).setAction(Constants.ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setSubText(if (single != null) "TuneFy · téléchargement" else "TuneFy")
            .setProgress(100, percent, totalBytes <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .addAction(0, "Tout annuler", cancelAll)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyDownloadProgress(context: Context, tasks: List<DownloadTask>) {
        if (!canNotify(context)) return
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Constants.NOTIFICATION_DOWNLOAD_ID, downloadProgress(context, tasks))
        }
    }

    fun showDownloadFinished(context: Context, title: String, message: String, success: Boolean) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(title.hashCode() and 0xFFFF, notification)
        }
    }

    fun showInstallResult(context: Context, success: Boolean, title: String, message: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_INSTALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Constants.NOTIFICATION_INSTALL_ID, notification)
        }
    }
}
