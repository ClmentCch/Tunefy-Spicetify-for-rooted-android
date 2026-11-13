package app.tunefy.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Root document of a TuneFy repository index.
 * The index is bundled in the APK and can be replaced by a remote copy
 * (same schema) hosted on any HTTPS endpoint.
 */
@Serializable
data class RepositoryIndex(
    val schema: Int = 1,
    val name: String = "TuneFy Repository",
    val version: Int = 1,
    val updated: String = "",
    val apps: List<CatalogItem> = emptyList()
)

@Serializable
data class CatalogItem(
    val id: String,
    val name: String,
    val packageId: String = "",
    val category: String = Category.ALL.key,
    val developer: String = "",
    val summary: String = "",
    val description: String = "",
    val version: String = "",
    val versionCode: Long = 0,
    val architecture: String = "universal",
    val sizeBytes: Long = 0,
    val minSdk: Int = 30,
    val publishedAt: String = "",
    val license: String = "",
    val sourceUrl: String = "",
    val downloadUrl: String? = null,
    val downloadType: String = DownloadType.EXTERNAL,
    val fileName: String? = null,
    val sha256: String? = null,
    val signatureSha256: String? = null,
    val spicetifyCompatible: Boolean = false,
    val morpheCompatible: Boolean = false,
    val supportedSpotifyVersions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val accentColor: String = "#78AFFF",
    val previewColors: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val versions: List<VersionEntry> = emptyList(),
    val featured: Boolean = false,
    val downloadCount: Long = 0,
    val warnings: List<String> = emptyList(),
    val notes: String = ""
) {
    val categoryEnum: Category
        get() = Category.fromKey(category)

    val isInstallable: Boolean
        get() = downloadType == DownloadType.DIRECT && !downloadUrl.isNullOrBlank()

    val monogram: String
        get() = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "TF" }
}

@Serializable
data class VersionEntry(
    val version: String,
    val versionCode: Long = 0,
    val date: String = "",
    val sizeBytes: Long = 0,
    val notes: String = "",
    val sha256: String? = null,
    val supportedSpotifyVersions: List<String> = emptyList()
)

object DownloadType {
    /** File hosted by the TuneFy repository or an upstream project. */
    const val DIRECT = "direct"

    /** Item links out to an official store or website. */
    const val EXTERNAL = "external"

    /** Item is a patching recipe applied by Morphe, not an APK. */
    const val RECIPE = "recipe"
}

enum class Category(val key: String, val label: String, val shortLabel: String) {
    ALL("all", "Tout", "Tout"),
    SPOTIFY("spotify", "Spotify", "Spotify"),
    EXTENSIONS("extensions", "Extensions", "Extensions"),
    THEMES("themes", "Thèmes", "Thèmes"),
    PATCHES("patches", "Patches", "Patches"),
    TOOLS("tools", "Outils", "Outils");

    companion object {
        fun fromKey(key: String): Category = entries.firstOrNull { it.key == key } ?: ALL
        val catalogFilters: List<Category> get() = listOf(ALL, SPOTIFY, EXTENSIONS, THEMES, PATCHES, TOOLS)
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class Compatibility(val label: String) {
    COMPATIBLE("Compatible"),
    EXPERIMENTAL("Expérimental"),
    INCOMPATIBLE("Incompatible"),
    UNKNOWN("Non vérifié")
}

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == VERIFYING || this == PAUSED
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELED
}

@Serializable
data class DownloadTask(
    val id: String,
    val itemId: String,
    val itemName: String,
    val fileName: String,
    val url: String,
    val kind: String = "package",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBytesPerSecond: Long = 0L,
    val error: String? = null,
    val filePath: String? = null,
    val expectedSha256: String? = null,
    val actualSha256: String? = null,
    val verified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val installAfterDownload: Boolean = false
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == DownloadStatus.COMPLETED) 1f else 0f

    val remainingBytes: Long get() = (totalBytes - downloadedBytes).coerceAtLeast(0L)

    val etaSeconds: Long?
        get() {
            if (speedBytesPerSecond <= 0L || remainingBytes <= 0L) return null
            return remainingBytes / speedBytesPerSecond
        }
}

@Serializable
data class InstalledItem(
    val itemId: String,
    val name: String,
    val version: String,
    val versionCode: Long = 0,
    val packageId: String = "",
    val kind: String = "package",
    val installedAt: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val sha256: String? = null,
    val signatureSha256: String? = null
)

@Serializable
data class HistoryEntry(
    val id: String,
    val itemId: String,
    val itemName: String,
    val version: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    val detail: String = "",
    val sha256: String? = null
)

@Serializable
data class BackupEntry(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0,
    val path: String,
    val spotifyVersion: String = "",
    val encrypted: Boolean = false
)

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val repositoryUrl: String = DEFAULT_REPOSITORY_URL,
    val rootFeaturesEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val installAfterDownload: Boolean = false,
    val notificationsEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_REPOSITORY_URL =
            "https://raw.githubusercontent.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/main/repository/index.json"
    }
}

enum class RootFlavor(val label: String) {
    MAGISK("Magisk"),
    KERNELSU("KernelSU"),
    APATCH("APatch"),
    OTHER("Su générique"),
    NONE("Aucun")
}

data class RootStatus(
    val rooted: Boolean = false,
    val flavor: RootFlavor = RootFlavor.NONE,
    val suPath: String? = null,
    val version: String? = null,
    val checking: Boolean = false,
    val error: String? = null
)

data class SpotifyStatus(
    val installed: Boolean = false,
    val packageId: String = "com.spotify.music",
    val versionName: String? = null,
    val versionCode: Long = 0,
    val signatureSha256: String? = null,
    val dataDirReadable: Boolean = false,
    val splits: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

data class PatchCompatibility(
    val status: Compatibility = Compatibility.UNKNOWN,
    val installedVersion: String? = null,
    val supportedVersions: List<String> = emptyList(),
    val experimental: Boolean = false,
    val reason: String = ""
)

@Serializable
data class PatchBundle(
    val version: String = "",
    val patches: List<PatchDefinition> = emptyList()
)

@Serializable
data class PatchDefinition(
    val name: String = "",
    val description: String = "",
    val default: Boolean = false,
    val category: String? = null,
    val dependencies: List<String> = emptyList(),
    @SerialName("compatiblePackages") val compatiblePackages: List<CompatiblePackage> = emptyList(),
    val options: List<JsonElement> = emptyList()
) {
    val spotifyPackage: CompatiblePackage?
        get() = compatiblePackages.firstOrNull { it.packageName == "com.spotify.music" }

    val supportedVersions: List<String>
        get() = spotifyPackage?.targets?.map { it.version }?.distinct().orEmpty()

    val experimental: Boolean
        get() = spotifyPackage?.targets?.any { it.isExperimental } ?: false
}

@Serializable
data class CompatiblePackage(
    val packageName: String = "",
    val name: String = "",
    val description: String? = null,
    val apkFileType: String? = null,
    val appIconColor: String? = null,
    val signatures: JsonElement? = null,
    val targets: List<PatchTarget> = emptyList()
)

@Serializable
data class PatchTarget(
    val version: String = "",
    val versionCodes: Map<String, Long> = emptyMap(),
    val isExperimental: Boolean = false,
    val minSdk: Int? = null,
    val description: String? = null
)

/** Repository metadata status shown on the home screen and in settings. */
data class RepositoryStatus(
    val name: String = "TuneFy Repository",
    val version: Int = 0,
    val updated: String = "",
    val itemCount: Int = 0,
    val source: String = "index local",
    val loading: Boolean = false,
    val error: String? = null
)
