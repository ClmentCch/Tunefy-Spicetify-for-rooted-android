package app.tunefy.android.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.PatchBundle
import app.tunefy.android.data.model.PatchCompatibility
import app.tunefy.android.data.model.PatchDefinition
import app.tunefy.android.data.model.RepositoryStatus
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.patchDataStore by preferencesDataStore(name = "tunefy_patches")

/**
 * Reads the official Spicetify patch list published by
 * https://github.com/spicetify/morphe-patches and never allows a patch that
 * does not target the installed Spotify build.
 */
class PatchRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheFile = File(context.filesDir, "morphe/patches-list.json")

    private val _bundle = MutableStateFlow<PatchBundle?>(null)
    val bundle: StateFlow<PatchBundle?> = _bundle.asStateFlow()

    private val _status = MutableStateFlow(RepositoryStatus(name = "Spicetify patches"))
    val status: StateFlow<RepositoryStatus> = _status.asStateFlow()

    val selection: Flow<Set<String>> = context.patchDataStore.data.map { prefs ->
        prefs[SELECTED] ?: emptySet()
    }

    suspend fun load(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (_bundle.value != null && !forceRefresh) return@withContext
        if (_bundle.value == null) {
            val local = readCache() ?: readAsset()
            if (local != null) {
                _bundle.value = local
                _status.value = RepositoryStatus(
                    name = "Spicetify patches",
                    version = local.patches.size,
                    updated = "",
                    itemCount = local.patches.size,
                    source = if (readCache() != null) "cache locale" else "index embarqué",
                    loading = true
                )
                seedDefaults(local)
            }
        }
        _status.update { it.copy(loading = true, error = null) }
        runCatching {
            val body = Http.getText(Constants.MORPHE_PATCHES_INDEX_URL)
            val parsed = json.decodeFromString<PatchBundle>(body)
            require(parsed.patches.isNotEmpty()) { "Liste de patches vide" }
            _bundle.value = parsed
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(body)
            _status.value = RepositoryStatus(
                name = "Spicetify patches",
                version = parsed.patches.size,
                updated = "",
                itemCount = parsed.patches.size,
                source = "github.com/spicetify/morphe-patches",
                loading = false
            )
            seedDefaults(parsed)
        }.onFailure { error ->
            _status.update {
                it.copy(
                    loading = false,
                    error = "Liste de patches hors ligne : ${error.message ?: "réseau indisponible"}"
                )
            }
        }
    }

    fun compatibility(patch: PatchDefinition, spotify: SpotifyStatus): PatchCompatibility {
        val supported = patch.supportedVersions
        if (!spotify.installed) {
            return PatchCompatibility(
                status = Compatibility.UNKNOWN,
                supportedVersions = supported,
                experimental = patch.experimental,
                reason = "Spotify n'est pas installé sur cet appareil. Versions prises en charge : " +
                    supported.joinToString(", ").ifBlank { "non précisées" }
            )
        }
        val installed = spotify.versionName.orEmpty()
        return when {
            supported.isEmpty() -> PatchCompatibility(
                status = Compatibility.INCOMPATIBLE,
                installedVersion = installed,
                reason = "Ce patch ne déclare aucune version Spotify compatible."
            )
            installed in supported -> PatchCompatibility(
                status = if (patch.experimental) Compatibility.EXPERIMENTAL else Compatibility.COMPATIBLE,
                installedVersion = installed,
                supportedVersions = supported,
                experimental = patch.experimental,
                reason = if (patch.experimental) {
                    "Version ${installed} prise en charge, mais marquée expérimentale par l'éditeur du patch."
                } else {
                    "Version ${installed} prise en charge."
                }
            )
            else -> PatchCompatibility(
                status = Compatibility.INCOMPATIBLE,
                installedVersion = installed,
                supportedVersions = supported,
                experimental = patch.experimental,
                reason = "Spotify $installed installé, patch prévu pour ${supported.joinToString(", ")}."
            )
        }
    }

    suspend fun toggle(name: String, enabled: Boolean) {
        context.patchDataStore.edit { prefs ->
            val current = prefs[SELECTED] ?: emptySet()
            prefs[SELECTED] = if (enabled) current + name else current - name
        }
    }

    suspend fun selectionSnapshot(): Set<String> = selection.first()

    fun exportSelection(patches: List<PatchDefinition>, selected: Set<String>): String {
        val payload = buildString {
            appendLine("{")
            appendLine("  \"patcher\": \"Morphe\",")
            appendLine("  \"source\": \"${Constants.MORPHE_PATCHES_REPOSITORY}\",")
            appendLine("  \"package\": \"${Constants.SPOTIFY_PACKAGE}\",")
            appendLine("  \"patches\": [")
            val chosen = patches.filter { it.name in selected }
            chosen.forEachIndexed { index, patch ->
                append("    ${'\"'}${patch.name}${'\"'}")
                if (index != chosen.lastIndex) append(',')
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        return payload
    }

    private suspend fun seedDefaults(bundle: PatchBundle) {
        context.patchDataStore.edit { prefs ->
            if (prefs[SELECTED] == null) {
                prefs[SELECTED] = bundle.patches.filter { it.default }.map { it.name }.toSet()
            }
        }
    }

    private fun readCache(): PatchBundle? = runCatching {
        if (!cacheFile.exists()) null
        else json.decodeFromString<PatchBundle>(cacheFile.readText())
    }.getOrNull()

    private fun readAsset(): PatchBundle? = runCatching {
        context.assets.open("patches/patches-list.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<PatchBundle>(it) }
    }.getOrNull()

    private companion object {
        val SELECTED = stringSetPreferencesKey("selected_patches")
    }
}
