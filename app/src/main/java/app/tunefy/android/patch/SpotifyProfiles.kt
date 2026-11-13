package app.tunefy.android.patch

import android.content.Context
import app.tunefy.android.data.model.Compatibility
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SpotifyBundleInfo(
    val version: String = "",
    val asset: String = "",
    val sha256: String = "",
    val source: String = "",
    val notes: String = ""
)

@Serializable
data class SpotifyProfile(
    val versionName: String,
    val versionCode: Long = 0,
    val abis: List<String> = listOf("arm64-v8a"),
    val status: String = "experimental",
    val bundleVersion: String = "",
    val notes: String = "",
    val requiredClasses: List<String> = emptyList(),
    val requiredMethods: Map<String, List<String>> = emptyMap(),
    val patchRequirements: Map<String, List<String>> = emptyMap()
)

@Serializable
data class UnsupportedSpotifyVersion(
    val versionName: String,
    val reason: String
)

@Serializable
data class SpotifyProfiles(
    val schema: Int = 1,
    val bundle: SpotifyBundleInfo = SpotifyBundleInfo(),
    val profiles: List<SpotifyProfile> = emptyList(),
    val unsupported: List<UnsupportedSpotifyVersion> = emptyList()
)

data class CompatibilityVerdict(
    val compatible: Boolean,
    val compatibility: Compatibility,
    val reason: String,
    val supportedVersions: List<String>,
    val missingAbi: List<String>,
    val patchAvailability: Map<String, PatchAvailability>
)

/**
 * Compatibility is a profile lookup, not a claim that every fingerprint will
 * match. A new supported Spotify build is added by editing the JSON profile,
 * without touching the engine or the patch bundle.
 */
object SpotifyCompatibility {

    fun evaluate(
        versionName: String?,
        versionCode: Long,
        abis: List<String>,
        deviceAbis: List<String>,
        dex: DexIndex,
        profiles: SpotifyProfiles,
        patchNames: List<String>,
        selected: Set<String>
    ): CompatibilityVerdict {
        val supportedVersions = profiles.profiles.map { it.versionName }
        val profile = profiles.profiles.firstOrNull { it.versionName == versionName }

        if (profile == null) {
            val unsupported = profiles.unsupported.firstOrNull { it.versionName == versionName }
            val reason = unsupported?.reason
                ?: if (versionName.isNullOrBlank()) {
                    "La version de Spotify n'a pas pu être lue dans l'APK."
                } else {
                    "Spotify $versionName n'est pas pris en charge par la version ${profiles.bundle.version} des patches. " +
                        "Versions prises en charge : ${supportedVersions.joinToString(", ").ifBlank { "aucune" }}. " +
                        "Aucun patch ne sera appliqué à une version non vérifiée."
                }
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = reason,
                supportedVersions = supportedVersions,
                missingAbi = emptyList(),
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "Version Spotify non prise en charge")
                }
            )
        }

        if (profile.versionCode > 0 && versionCode > 0 && profile.versionCode != versionCode) {
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Spotify ${profile.versionName} exige le versionCode ${profile.versionCode}, " +
                    "mais l'APK fourni a le versionCode $versionCode. Il ne s'agit pas de la build ARM64 vérifiée.",
                supportedVersions = supportedVersions,
                missingAbi = emptyList(),
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "versionCode différent de la build vérifiée")
                }
            )
        }

        val supportedAbis = profile.abis.map { normalizeAbi(it) }.toSet()
        val actualAbis = abis.map { normalizeAbi(it) }.toSet()
        if (actualAbis.isEmpty()) {
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Architecture non détectée dans l'APK. Le profil ${profile.versionName} exige " +
                    profile.abis.joinToString(", ") + ".",
                supportedVersions = supportedVersions,
                missingAbi = profile.abis,
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "architecture non détectée")
                }
            )
        }
        val missingAbi = profile.abis.filter { normalizeAbi(it) !in actualAbis }
        if (missingAbi.isNotEmpty()) {
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Architecture${if (missingAbi.size > 1) "s" else ""} manquante${if (missingAbi.size > 1) "s" else ""} : " +
                    missingAbi.joinToString(", ") + ". Le profil ${profile.versionName} exige " +
                    profile.abis.joinToString(", ") + ".",
                supportedVersions = supportedVersions,
                missingAbi = missingAbi,
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "architecture incompatible")
                }
            )
        }

        val normalizedDeviceAbis = deviceAbis.map { normalizeAbi(it) }
        if (normalizedDeviceAbis.isNotEmpty() && supportedAbis.none { it in normalizedDeviceAbis }) {
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Cet appareil (${deviceAbis.joinToString(", ")}) ne peut pas exécuter une build " +
                    profile.abis.joinToString(", ") + ".",
                supportedVersions = supportedVersions,
                missingAbi = profile.abis,
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "ABI non exécutable sur cet appareil")
                }
            )
        }

        val missingClasses = profile.requiredClasses.filter { !dex.hasClass(it) }
        if (missingClasses.isNotEmpty()) {
            val first = missingClasses.first()
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Spotify settings ABI changed: missing $first. " +
                    "L'APK ne correspond pas au profil ${profile.versionName} (${missingClasses.size} classe(s) attendue(s) absente(s)).",
                supportedVersions = supportedVersions,
                missingAbi = emptyList(),
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "classe $first absente du DEX")
                }
            )
        }

        val missingMethods = profile.requiredMethods.flatMap { (type, methods) ->
            methods.filterNot { dex.hasMethod(type, it) }.map { "$type->$it" }
        }
        if (missingMethods.isNotEmpty()) {
            return CompatibilityVerdict(
                compatible = false,
                compatibility = Compatibility.INCOMPATIBLE,
                reason = "Spotify settings ABI changed: missing ${missingMethods.first()}. " +
                    "Les méthodes tracées du profil ${profile.versionName} ne correspondent pas à ce DEX.",
                supportedVersions = supportedVersions,
                missingAbi = emptyList(),
                patchAvailability = patchNames.associateWith {
                    PatchAvailability(false, "méthode ${missingMethods.first()} absente")
                }
            )
        }

        val availability = patchNames.associateWith { name ->
            val required = profile.patchRequirements[name].orEmpty()
            val missing = required.filterNot(dex::hasClass)
            if (missing.isEmpty()) {
                PatchAvailability(
                    enabled = true,
                    reason = if (profile.status == "experimental") {
                        "Profil structurel ${profile.versionName} · patch expérimental"
                    } else {
                        "Profil structurel ${profile.versionName}"
                    }
                )
            } else {
                PatchAvailability(
                    enabled = false,
                    reason = "Classe${if (missing.size > 1) "s" else ""} absente${if (missing.size > 1) "s" else ""} : " +
                        missing.joinToString(", ")
                )
            }
        }

        val unavailableSelected = selected.filter { availability[it]?.enabled == false }
        val compatible = unavailableSelected.isEmpty()
        val compatibility = when {
            !compatible -> Compatibility.INCOMPATIBLE
            profile.status == "experimental" -> Compatibility.EXPERIMENTAL
            else -> Compatibility.COMPATIBLE
        }
        val reason = if (unavailableSelected.isEmpty()) {
            "${profile.notes.ifBlank { "Profil ${profile.versionName} chargé." }}"
        } else {
            "Patch${if (unavailableSelected.size > 1) "s" else ""} incompatible${if (unavailableSelected.size > 1) "s" else ""} : " +
                unavailableSelected.joinToString(", ") { name ->
                    "$name (${availability[name]?.reason.orEmpty()})"
                }
        }

        return CompatibilityVerdict(
            compatible = compatible,
            compatibility = compatibility,
            reason = reason,
            supportedVersions = supportedVersions,
            missingAbi = emptyList(),
            patchAvailability = availability
        )
    }

    fun load(context: Context): SpotifyProfiles {
        val text = context.assets.open("patches/spotify-profiles.json").bufferedReader().use { it.readText() }
        return Json { ignoreUnknownKeys = true }.decodeFromString(SpotifyProfiles.serializer(), text)
    }

    fun normalizeAbi(value: String): String = when (value.lowercase().replace('_', '-')) {
        "arm64-v8a", "arm64", "aarch64" -> "arm64-v8a"
        "armeabi-v7a", "arm", "armv7" -> "armeabi-v7a"
        "x86-64", "x86_64", "x64" -> "x86_64"
        "x86" -> "x86"
        else -> value.lowercase().replace('_', '-')
    }
}
