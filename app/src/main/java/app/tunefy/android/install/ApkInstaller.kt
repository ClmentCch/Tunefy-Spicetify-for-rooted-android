package app.tunefy.android.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.HistoryEntry
import app.tunefy.android.data.model.InstalledItem
import app.tunefy.android.data.repo.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class InstallResult(
    val sessionId: Int,
    val packageName: String?,
    val itemName: String?,
    val success: Boolean,
    val message: String?
)

/**
 * Installs APKs through the official Android PackageInstaller session API.
 * The user always sees Android's own confirmation dialog: TuneFy never writes
 * into another application's private storage and never bypasses signature
 * checks.
 */
class ApkInstaller(
    private val context: Context,
    private val libraryRepository: LibraryRepository
) {

    private data class PendingInstall(
        val itemId: String?,
        val itemName: String,
        val version: String,
        val archiveSha256: String?,
        val signatureSha256: String?,
        val packageName: String?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<Int, PendingInstall>()

    private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
    val results: SharedFlow<InstallResult> = _results.asSharedFlow()

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun startSession(
        apks: List<File>,
        packageName: String?,
        item: CatalogItem?,
        archiveSha256: String?,
        signatureSha256: String?
    ): Result<Int> = runCatching {
        require(apks.isNotEmpty()) { "Aucun APK à installer" }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            if (!packageName.isNullOrBlank()) setAppPackageName(packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apks.forEach { apk ->
                val output = session.openWrite(apk.name, 0L, apk.length())
                try {
                    FileInputStream(apk).use { input -> input.copyTo(output) }
                    output.flush()
                    session.fsync(output)
                } finally {
                    output.close()
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_RESULT
                putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(InstallResultReceiver.EXTRA_ITEM_ID, item?.id)
                putExtra(InstallResultReceiver.EXTRA_ITEM_NAME, item?.name ?: apks.first().name)
                putExtra(InstallResultReceiver.EXTRA_ITEM_VERSION, item?.version)
                putExtra(InstallResultReceiver.EXTRA_SHA256, archiveSha256)
                putExtra(InstallResultReceiver.EXTRA_SIGNATURE, signatureSha256)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                flags
            )
            pending[sessionId] = PendingInstall(
                itemId = item?.id,
                itemName = item?.name ?: apks.first().name,
                version = item?.version.orEmpty(),
                archiveSha256 = archiveSha256,
                signatureSha256 = signatureSha256,
                packageName = packageName
            )
            session.commit(pendingIntent.intentSender)
        }
        sessionId
    }

    fun abandonSession(sessionId: Int) {
        pending.remove(sessionId)
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
    }

    fun handleInstallResult(
        intent: Intent,
        sessionId: Int,
        status: Int,
        message: String?,
        installedPackage: String?
    ) {
        val info = pending.remove(sessionId)
        val success = status == PackageInstaller.STATUS_SUCCESS
        val itemName = info?.itemName
            ?: intent.getStringExtra(InstallResultReceiver.EXTRA_ITEM_NAME)
            ?: installedPackage
        if (success && info != null) {
            scope.launch {
                val resolvedVersion = ApkInspector.installedVersionName(
                    context,
                    installedPackage ?: info.packageName.orEmpty()
                )?.first ?: info.version
                libraryRepository.markInstalled(
                    InstalledItem(
                        itemId = info.itemId ?: installedPackage ?: UUID.randomUUID().toString(),
                        name = info.itemName,
                        version = resolvedVersion,
                        packageId = installedPackage ?: info.packageName.orEmpty(),
                        kind = "apk",
                        sha256 = info.archiveSha256,
                        signatureSha256 = info.signatureSha256
                    )
                )
                libraryRepository.recordHistory(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        itemId = info.itemId.orEmpty(),
                        itemName = info.itemName,
                        version = resolvedVersion,
                        action = "Installation",
                        success = true,
                        detail = installedPackage ?: "",
                        sha256 = info.archiveSha256
                    )
                )
            }
        } else if (!success) {
            scope.launch {
                libraryRepository.recordHistory(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        itemId = info?.itemId.orEmpty(),
                        itemName = itemName.orEmpty(),
                        version = info?.version.orEmpty(),
                        action = "Installation",
                        success = false,
                        detail = message ?: "Code ${status}"
                    )
                )
            }
        }
        scope.launch {
            _results.emit(
                InstallResult(
                    sessionId = sessionId,
                    packageName = installedPackage,
                    itemName = itemName,
                    success = success,
                    message = message
                )
            )
        }
    }

    fun requestSystemUninstall(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openExternal(url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isPackageInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    fun openMorpheSource(): Boolean = openExternal(Constants.MORPHE_ADD_SOURCE_URL)
}
