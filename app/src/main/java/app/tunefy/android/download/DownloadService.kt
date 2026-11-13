package app.tunefy.android.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.tunefy.android.TunefyApplication
import app.tunefy.android.core.Constants
import app.tunefy.android.core.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps downloads alive and shows live progress.
 * It owns no download logic: DownloadManager does, the service only surfaces it.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as TunefyApplication).container.downloadManager
        ServiceCompat.startForeground(
            this,
            Constants.NOTIFICATION_DOWNLOAD_ID,
            Notifications.downloadProgress(this, manager.tasks.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        when (intent?.action) {
            Constants.ACTION_CANCEL -> intent.getStringExtra(Constants.EXTRA_TASK_ID)?.let(manager::cancel)
            Constants.ACTION_CANCEL_ALL -> manager.cancelAll()
        }

        if (!monitoring) {
            monitoring = true
            scope.launch {
                manager.tasks.collect { tasks ->
                    val active = tasks.filter { it.status.isActive }
                    if (active.isEmpty()) {
                        stopSelf()
                        return@collect
                    }
                    Notifications.notifyDownloadProgress(this@DownloadService, tasks)
                    runCatching {
                        NotificationManagerCompat.from(this@DownloadService).notify(
                            Constants.NOTIFICATION_DOWNLOAD_ID,
                            Notifications.downloadProgress(this@DownloadService, tasks)
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun ensureRunning(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(Constants.ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
