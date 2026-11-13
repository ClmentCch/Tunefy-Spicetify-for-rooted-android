package app.tunefy.android.patch

import app.tunefy.android.data.model.Compatibility
import kotlinx.serialization.Serializable
import java.io.File

/** The seven steps shown while a patched APK is produced. */
enum class PatchStage(val label: String, val shortLabel: String) {
    ANALYZE("Analyse de l'APK", "Analyse"),
    COMPATIBILITY("Vérification de compatibilité", "Compatibilité"),
    PREPARE("Préparation des fichiers", "Préparation"),
    APPLY("Application des patches", "Patches"),
    REBUILD("Reconstruction de l'APK", "Reconstruction"),
    SIGN("Signature TuneFy", "Signature"),
    VERIFY("Vérification finale", "Vérification")
}

enum class StageStatus { PENDING, RUNNING, DONE, FAILED }

data class StageProgress(
    val stage: PatchStage,
    val status: StageStatus = StageStatus.PENDING,
    val detail: String = ""
)

data class PatchAvailability(
    val enabled: Boolean,
    val reason: String
)

/**
 * Local, read-only analysis of an imported Spotify package.
 * Structural checks are deliberately separate from the actual patch run: a
 * structurally valid file can still fail a fingerprint, and that is reported
 * instead of being turned into a false success.
 */
data class ApkAnalysis(
    val displayName: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val abis: List<String>,
    val splitCount: Int,
    val isArchive: Boolean,
    val signatureSha256: String?,
    val installedSignatureSha256: String?,
    val signatureWillChange: Boolean,
    val sizeBytes: Long,
    val sha256: String,
    val compatible: Boolean,
    val compatibility: Compatibility,
    val reason: String,
    val supportedVersions: List<String>,
    val missingAbi: List<String>,
    val patchAvailability: Map<String, PatchAvailability>,
    val baseApk: File,
    val modules: List<File>,
    val sourceFile: File
)

@Serializable
data class GeneratedPatch(
    val id: String,
    val fileName: String,
    val path: String,
    val spotifyVersion: String,
    val architecture: String,
    val patches: List<String>,
    val sizeBytes: Long,
    val sha256: String,
    val signatureSha256: String?,
    val createdAt: Long,
    val sourceName: String,
    val verified: Boolean
) {
    fun file(): File = File(path)
}

@Serializable
data class PatchHistoryRecord(
    val id: String,
    val createdAt: Long,
    val fileName: String,
    val spotifyVersion: String,
    val patches: List<String>,
    val success: Boolean,
    val detail: String,
    val sha256: String? = null
)

data class PatchRunResult(
    val artifact: GeneratedPatch,
    val log: String,
    val logPath: String
)

class PatchRunException(
    val userMessage: String,
    val detail: String = userMessage,
    cause: Throwable? = null
) : Exception(userMessage, cause)

data class PatchUiState(
    val analysis: ApkAnalysis? = null,
    val analyzing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val stages: List<StageProgress> = PatchStage.entries.map { StageProgress(it) },
    val running: Boolean = false,
    val error: String? = null,
    val result: PatchRunResult? = null,
    val log: String = "",
    val officialDownloading: Boolean = false,
    val officialDownloadedBytes: Long = 0L,
    val officialTotalBytes: Long = SpotifyOfficialApk.SIZE_BYTES
) {
    val canStart: Boolean
        get() = analysis?.compatible == true && !running && !analyzing && selected.isNotEmpty()
}
