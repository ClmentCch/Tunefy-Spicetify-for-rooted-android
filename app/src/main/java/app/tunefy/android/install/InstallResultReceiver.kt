package app.tunefy.android.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.tunefy.android.TunefyApplication
import app.tunefy.android.core.Notifications

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirmation != null) {
                runCatching { context.startActivity(confirmation) }
            }
            return
        }

        val application = context.applicationContext as? TunefyApplication ?: return
        application.container.apkInstaller.handleInstallResult(
            intent = intent,
            sessionId = sessionId,
            status = status,
            message = message,
            installedPackage = packageName
        )
        Notifications.showInstallResult(
            context = context,
            success = status == PackageInstaller.STATUS_SUCCESS,
            title = if (status == PackageInstaller.STATUS_SUCCESS) "Installation terminée" else "Installation refusée",
            message = itemName ?: packageName ?: "TuneFy"
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "app.tunefy.android.INSTALL_RESULT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_NAME = "item_name"
        const val EXTRA_ITEM_VERSION = "item_version"
        const val EXTRA_SHA256 = "sha256"
        const val EXTRA_SIGNATURE = "signature"
    }
}
