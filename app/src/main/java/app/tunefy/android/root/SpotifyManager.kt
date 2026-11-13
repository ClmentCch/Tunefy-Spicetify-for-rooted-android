package app.tunefy.android.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.util.FileHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class SpotifyManager(
    private val context: Context,
    private val rootManager: RootManager
) {

    @Suppress("DEPRECATION")
    fun status(): SpotifyStatus {
        val pm = context.packageManager
        val info = runCatching {
            pm.getPackageInfo(
                Constants.SPOTIFY_PACKAGE,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
            )
        }.getOrNull() ?: return SpotifyStatus(installed = false)

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        val signatures = info.signingInfo?.apkContentsSigners
            ?: info.signingInfo?.signingCertificateHistory
        val signature = signatures?.firstOrNull()?.let { FileHasher.sha256Bytes(it.toByteArray()) }
        val dataDir = File("/data/data/${Constants.SPOTIFY_PACKAGE}")
        return SpotifyStatus(
            installed = true,
            versionName = info.versionName,
            versionCode = versionCode,
            signatureSha256 = signature,
            dataDirReadable = dataDir.canRead(),
            splits = info.applicationInfo?.splitSourceDirs?.map { File(it).name }.orEmpty(),
            permissions = info.requestedPermissions?.toList().orEmpty()
        )
    }

    /** Rough size of Spotify's private data, only readable with root. */
    suspend fun dataDirectorySize(): Long? = withContext(Dispatchers.IO) {
        val result = rootManager.runAsRoot(
            "du -sb /data/data/${Constants.SPOTIFY_PACKAGE} 2>/dev/null | cut -f1",
            15_000
        )
        result.stdout.trim().toLongOrNull()
    }

    /**
     * Creates a gzipped tar of Spotify's data, streamed directly into TuneFy's
     * private storage. TuneFy only reads: nothing is ever written back into
     * Spotify's data directory.
     */
    suspend fun backup(destination: File): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            val process = rootManager.streamFromRoot(
                "tar -czf - -C /data/data ${Constants.SPOTIFY_PACKAGE}"
            )
            destination.outputStream().use { output ->
                process.inputStream.copyTo(output)
            }
            val errorText = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exit = process.waitFor()
            if (exit != 0) {
                destination.delete()
                throw IOException(errorText.ifBlank { "tar a échoué (code $exit)" })
            }
            if (!destination.exists() || destination.length() == 0L) {
                throw IOException("La sauvegarde est vide. Spotify est-il installé ?")
            }
            destination.length()
        }
    }

    fun openSpotifySettings(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(Constants.SPOTIFY_PACKAGE)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
