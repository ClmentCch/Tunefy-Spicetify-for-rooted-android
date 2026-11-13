package app.tunefy.android.patch

import android.content.Context
import app.tunefy.android.util.FileHasher
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

/**
 * The patch bundle is shipped in assets, never downloaded and never executed
 * from a writable location before its SHA-256 has been checked. It is copied
 * once to app-private storage because the patcher needs a real file path.
 */
object PatchBundleStore {

    const val ASSET = "patches/patches-1.2.0-tunefy.mpp"
    const val EXPECTED_SHA256 = "bdc38eadd482228c5c520765f7027d2c68d1d903e8d76c4536d225d2b3470977"
    const val VERSION = "1.2.0"

    private val lock = Any()

    fun ensure(context: Context): File = synchronized(lock) {
        val directory = File(context.filesDir, "patches").apply { mkdirs() }
        val target = File(directory, "patches-$VERSION-tunefy.mpp")
        if (target.exists() && FileHasher.sha256(target) == EXPECTED_SHA256) {
            validate(target)
            return target
        }
        if (target.exists()) target.delete()
        val temporary = File(directory, "${target.name}.tmp")
        try {
            context.assets.open(ASSET).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = FileHasher.sha256(temporary)
            if (!actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
                throw PatchRunException(
                    "Le bundle de patches intégré ne correspond pas à son empreinte SHA-256. Réinstallez TuneFy.",
                    "expected=$EXPECTED_SHA256 actual=$actual"
                )
            }
            validate(temporary)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target
        } catch (error: IOException) {
            temporary.delete()
            throw PatchRunException(
                "Impossible d'extraire le bundle de patches intégré.",
                error.stackTraceToString(),
                error
            )
        }
    }

    private fun validate(file: File) {
        try {
            JarFile(file).use { jar ->
                val dex = jar.getJarEntry("classes.dex")
                    ?: throw PatchRunException(
                        "Bundle de patches invalide : classes.dex absent.",
                        "Missing classes.dex in ${file.name}"
                    )
                if (dex.size <= 0) {
                    throw PatchRunException(
                        "Bundle de patches invalide : classes.dex vide.",
                        "Empty classes.dex in ${file.name}"
                    )
                }
            }
        } catch (error: IOException) {
            throw PatchRunException(
                "Bundle de patches illisible.",
                error.stackTraceToString(),
                error
            )
        }
    }
}
