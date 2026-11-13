package app.tunefy.android.root

import android.content.Context
import app.tunefy.android.data.model.RootFlavor
import app.tunefy.android.data.model.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Read-only root detection for Magisk, KernelSU and APatch.
 * Root is optional: every feature of TuneFy works without it.
 */
class RootManager(private val context: Context) {

    private val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    private val managerPackages = mapOf(
        "com.topjohnwu.magisk" to RootFlavor.MAGISK,
        "io.github.vvb2060.magisk" to RootFlavor.MAGISK,
        "me.weishu.kernelsu" to RootFlavor.KERNELSU,
        "me.bmax.apatch" to RootFlavor.APATCH
    )

    fun hasRootBinary(): Boolean = suPaths.any { File(it).exists() }

    suspend fun probe(): RootStatus = withContext(Dispatchers.IO) {
        val binary = suPaths.firstOrNull { File(it).exists() }
        val idResult = runCatching { exec(arrayOf("su", "-c", "id"), 8_000) }.getOrNull()
        val rooted = idResult?.stdout?.contains("uid=0") == true
        if (!rooted) {
            return@withContext RootStatus(
                rooted = false,
                flavor = RootFlavor.NONE,
                suPath = binary,
                error = when {
                    idResult == null -> "Impossible d'exécuter su (délai ou refus)."
                    idResult.stderr.isNotBlank() -> idResult.stderr
                    binary == null -> "Aucun binaire su détecté."
                    else -> "Accès root refusé ou non accordé à TuneFy."
                }
            )
        }
        val versionResult = runCatching { exec(arrayOf("su", "-v"), 4_000) }.getOrNull()
        val versionLine = versionResult?.stdout?.lineSequence()?.firstOrNull()?.trim()
        val flavor = detectFlavor(versionLine)
        RootStatus(
            rooted = true,
            flavor = flavor,
            suPath = binary,
            version = versionLine
        )
    }

    private fun detectFlavor(versionLine: String?): RootFlavor {
        if (File("/data/adb/magisk").exists() ||
            managerPackages.filterValues { it == RootFlavor.MAGISK }.keys.any { isInstalled(it) } ||
            versionLine?.contains("MAGISK", ignoreCase = true) == true
        ) return RootFlavor.MAGISK
        if (File("/data/adb/ksu").exists() ||
            isInstalled("me.weishu.kernelsu") ||
            versionLine?.contains("KernelSU", ignoreCase = true) == true
        ) return RootFlavor.KERNELSU
        if (File("/data/adb/ap").exists() ||
            isInstalled("me.bmax.apatch") ||
            versionLine?.contains("APatch", ignoreCase = true) == true
        ) return RootFlavor.APATCH
        return RootFlavor.OTHER
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** User-visible list of detected managers, used by the root screen. */
    fun detectedManagers(): List<RootFlavor> = managerPackages.keys
        .filter { isInstalled(it) }
        .mapNotNull { managerPackages[it] }
        .distinct()

    suspend fun runAsRoot(command: String, timeoutMs: Long = 20_000): ShellResult =
        withContext(Dispatchers.IO) { exec(arrayOf("su", "-c", command), timeoutMs) }

    /**
     * Install an already verified APK through the platform package manager.
     * Used only when the user explicitly chooses the root install path.
     */
    suspend fun installApkAsRoot(apkPaths: List<String>): ShellResult = withContext(Dispatchers.IO) {
        require(apkPaths.isNotEmpty())
        val quoted = apkPaths.joinToString(" ") { "'${it.replace("'", "")}'" }
        val command = if (apkPaths.size == 1) {
            "pm install -r -d $quoted"
        } else {
            "pm install-multiple -r -d $quoted"
        }
        exec(arrayOf("su", "-c", command), 180_000)
    }

    fun streamFromRoot(command: String): Process =
        ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()

    private fun exec(command: Array<String>, timeoutMs: Long): ShellResult {
        val process = ProcessBuilder(*command).start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
            }
        }.apply { isDaemon = true; start() }
        val errThread = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) }
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            process.destroyForcibly()
            outThread.join(400)
            errThread.join(400)
            return ShellResult(-1, stdout.toString().trim(), "Délai dépassé")
        }
        outThread.join(800)
        errThread.join(800)
        return ShellResult(
            exitCode = runCatching { process.exitValue() }.getOrDefault(-1),
            stdout = stdout.toString().trim(),
            stderr = stderr.toString().trim()
        )
    }
}
