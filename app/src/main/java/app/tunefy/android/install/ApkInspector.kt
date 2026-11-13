package app.tunefy.android.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.tunefy.android.util.FileHasher
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class ApkDetails(
    val file: File,
    val packageId: String?,
    val versionName: String?,
    val versionCode: Long,
    val signatureSha256: String?,
    val minSdk: Int?,
    val label: String?,
    val sha256: String
)

data class ExpandedPackage(
    val apks: List<File>,
    val primaryApk: File,
    val extracted: Boolean
)

/**
 * Reads APK metadata locally (no network, no root) and expands split bundles
 * such as .apkm, .xapk or .apks into the individual splits Android needs.
 */
object ApkInspector {

    private val archiveExtensions = setOf("apkm", "xapk", "apks", "zip")

    fun isPackageArchive(file: File): Boolean =
        file.extension.lowercase(Locale.ROOT) in archiveExtensions

    @Suppress("DEPRECATION")
    fun inspect(context: Context, file: File): ApkDetails? {
        val pm = context.packageManager
        return runCatching {
            val info = pm.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
            ) ?: return null
            info.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }
            val signatures = info.signingInfo?.apkContentsSigners
                ?: info.signingInfo?.signingCertificateHistory
            val signature = signatures?.firstOrNull()?.let { FileHasher.sha256Bytes(it.toByteArray()) }
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val label = runCatching {
                pm.getApplicationLabel(info.applicationInfo!!).toString()
            }.getOrNull()
            ApkDetails(
                file = file,
                packageId = info.packageName,
                versionName = info.versionName,
                versionCode = versionCode,
                signatureSha256 = signature,
                minSdk = info.applicationInfo?.minSdkVersion,
                label = label,
                sha256 = FileHasher.sha256(file)
            )
        }.getOrNull()
    }

    /** Signature of the installed copy of a package, used to refuse mismatched updates. */
    @Suppress("DEPRECATION")
    fun installedSignature(context: Context, packageId: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(
            packageId,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        val signatures = info.signingInfo?.apkContentsSigners
            ?: info.signingInfo?.signingCertificateHistory
        signatures?.firstOrNull()?.let { FileHasher.sha256Bytes(it.toByteArray()) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun installedVersionName(context: Context, packageId: String): Pair<String?, Long>? = runCatching {
        val info = context.packageManager.getPackageInfo(packageId, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
        info.versionName to code
    }.getOrNull()

    /**
     * Expands an archive of splits and keeps only the splits useful to this device
     * (base + matching ABI + closest density + current language). Feature splits
     * are kept because they usually carry no device-specific code.
     */
    fun expand(context: Context, file: File): ExpandedPackage? {
        if (file.extension.equals("apk", ignoreCase = true)) {
            return ExpandedPackage(listOf(file), file, extracted = false)
        }
        if (!isPackageArchive(file)) return null
        return runCatching {
            val workDir = File(context.cacheDir, "expanded/${file.nameWithoutExtension}-${file.length()}")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()
            val extracted = mutableListOf<File>()
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val name = entry.name.substringAfterLast('/')
                    if (!name.endsWith(".apk", ignoreCase = true)) return@forEach
                    val out = File(workDir, name)
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    extracted += out
                }
            }
            if (extracted.isEmpty()) return null
            val selected = selectSplits(context, extracted)
            val primary = selected.firstOrNull { isBaseSplit(it) } ?: selected.first()
            ExpandedPackage(selected, primary, extracted = true)
        }.getOrNull()
    }

    private fun selectSplits(context: Context, apks: List<File>): List<File> {
        val base = apks.filter { isBaseSplit(it) }
        val splits = apks - base.toSet()
        if (splits.isEmpty()) return apks

        val abis = Build.SUPPORTED_ABIS.map { normalize(it) }
        val density = context.resources.displayMetrics.densityDpi
        val language = Locale.getDefault().language.lowercase(Locale.ROOT)

        val selected = splits.filter { split ->
            val name = normalize(split.nameWithoutExtension)
            when {
                isAbiSplit(name) -> abis.any { name.contains(it) } ||
                    (normalize("arm64-v8a") in abis && name.contains("arm64"))
                isDensitySplit(name) -> name.contains(densityBucket(density))
                isLanguageSplit(name) -> name.endsWith(language) || name.endsWith("en")
                else -> true
            }
        }
        val withBase = (base + selected).distinct()
        return if (withBase.size == base.size && apks.size > base.size) {
            // No split matched this device: install everything rather than a broken base.
            apks
        } else {
            withBase
        }
    }

    private fun isBaseSplit(file: File): Boolean {
        val name = normalize(file.nameWithoutExtension)
        return !name.contains("split") && !isAbiSplit(name) && !isDensitySplit(name) &&
            !isLanguageSplit(name)
    }

    private fun isAbiSplit(name: String): Boolean =
        listOf("arm64", "armeabi", "x86_64", "x86", "mips").any { name.contains(it) }

    private fun isDensitySplit(name: String): Boolean =
        listOf("ldpi", "mdpi", "hdpi", "xhdpi", "tvdpi", "dpi").any { name.contains(it) }

    private fun isLanguageSplit(name: String): Boolean {
        // Language splits look like split_config_fr or ..._en; keep it conservative.
        val tail = name.substringAfterLast('_')
        return tail.length in 2..3 && tail.all { it.isLetter() } &&
            tail !in setOf("apk", "base", "v8a", "x86", "dpi")
    }

    private fun densityBucket(dpi: Int): String = when {
        dpi <= 140 -> "ldpi"
        dpi <= 200 -> "mdpi"
        dpi <= 280 -> "hdpi"
        dpi <= 400 -> "xhdpi"
        dpi <= 560 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('-', '_').replace('.', '_')

    fun deviceAbiSummary(): String = Build.SUPPORTED_ABIS.joinToString(", ")
}
