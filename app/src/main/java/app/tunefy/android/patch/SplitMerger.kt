package app.tunefy.android.patch

import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Merges a base APK and its split modules into one standalone APK.
 * This is the same operation Android installers perform for a split install;
 * doing it before patching lets the patcher rewrite one resource table and one
 * set of DEX files. The split-only manifest attributes are removed so the
 * result installs as a normal monolithic package.
 */
object SplitMerger {

    fun merge(modules: List<File>, output: File, workDirectory: File, log: (String) -> Unit): File {
        if (modules.size <= 1) {
            modules.firstOrNull()?.copyTo(output, overwrite = true)
            return output
        }
        val work = File(workDirectory, "split-merge-${System.currentTimeMillis()}").apply { mkdirs() }
        val closeables = mutableSetOf<Closeable>()
        try {
            val copied = modules.mapIndexed { index, file ->
                file.copyTo(File(work, "%03d-%s".format(index, file.name)), overwrite = true)
            }
            val logger = object : APKLogger {
                override fun logMessage(msg: String) = log(msg)
                override fun logError(msg: String, tr: Throwable?) = log("$msg${tr?.let { ": ${it.message}" }.orEmpty()}")
                override fun logVerbose(msg: String) = Unit
            }
            val bundle = ApkBundle().apply {
                setAPKLogger(logger)
                loadApkDirectory(work)
            }
            closeables += bundle
            val merged = bundle.mergeModules(false).apply {
                setAPKLogger(logger)
                setLoadDefaultFramework(false)
            }
            closeables += merged

            merged.androidManifest.apply {
                arrayOf(
                    AndroidManifest.ID_isSplitRequired,
                    AndroidManifest.ID_requiredSplitTypes,
                    AndroidManifest.ID_splitTypes
                ).forEach { id ->
                    applicationElement.removeAttributesWithId(id)
                    manifestElement.removeAttributesWithId(id)
                }
                arrayOf(
                    AndroidManifest.NAME_requiredSplitTypes,
                    AndroidManifest.NAME_splitTypes
                ).forEach { name ->
                    manifestElement.removeAttributeIf { it.name == name }
                }
                manifestElement.removeElementsIf { it.name == "uses-split" }
                arrayOf("splitName", "split").forEach { name ->
                    manifestElement.removeAttributeIf { it.name == name }
                    applicationElement.removeAttributeIf { it.name == name }
                }
                applicationElement.removeElementsIf { element ->
                    if (element.name != AndroidManifest.TAG_meta_data) return@removeElementsIf false
                    val name = element
                        .getAttributes { it.nameId == AndroidManifest.ID_name }
                        .asSequence()
                        .firstOrNull()
                        ?.valueString
                        ?: return@removeElementsIf false
                    name == "com.android.dynamic.apk.fused.modules" ||
                        name.startsWith("com.android.vending.") ||
                        name.startsWith("com.android.stamp.")
                }
                refresh()
            }
            merged.refreshTable()
            merged.refreshManifest()
            merged.setExtractNativeLibs(merged.androidManifest.isExtractNativeLibs)
            output.parentFile?.mkdirs()
            merged.writeApk(output)
            if (!output.exists() || output.length() == 0L) {
                throw IOException("ARSCLib produced an empty APK")
            }
            log("Splits fusionnés : ${copied.size} modules → ${output.name} (${output.length()} octets)")
            return output
        } catch (error: Throwable) {
            throw PatchRunException(
                "Fusion des modules split impossible : ${error.message ?: "structure non prise en charge"}.",
                error.stackTraceToString(),
                error
            )
        } finally {
            closeables.forEach { runCatching { it.close() } }
            runCatching { work.deleteRecursively() }
        }
    }

}
