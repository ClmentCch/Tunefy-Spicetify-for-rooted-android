package app.tunefy.android.data.repo

import android.content.Context
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.RepositoryIndex
import app.tunefy.android.data.model.RepositoryStatus
import app.tunefy.android.data.remote.Http
import app.tunefy.android.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * TuneFy repository: an F-Droid style signed-by-checksum index.
 *
 * The APK ships a read-only copy in assets so the store always opens offline.
 * A remote index (same schema) can replace it; every downloadable item carries
 * a SHA-256 checksum that is verified after transfer.
 */
class CatalogRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val cacheFile = File(context.filesDir, "repository/index.json")

    private val _index = MutableStateFlow(RepositoryIndex())
    val index: StateFlow<RepositoryIndex> = _index.asStateFlow()

    private val _status = MutableStateFlow(RepositoryStatus())
    val status: StateFlow<RepositoryStatus> = _status.asStateFlow()

    private var bootstrapped = false

    suspend fun bootstrap() {
        if (bootstrapped) return
        mutex.withLock {
            if (bootstrapped) return
            val cached = readCache()
            val bundled = readAsset()
            val initial = cached ?: bundled
            if (initial != null) {
                apply(initial, source = if (cached != null) "cache locale" else "index embarqué")
            }
            bootstrapped = true
        }
    }

    suspend fun refresh(): Result<RepositoryIndex> = withContext(Dispatchers.IO) {
        _status.update { it.copy(loading = true, error = null) }
        val result = runCatching {
            val url = settingsRepository.settings.first().repositoryUrl
            val body = Http.getText(url)
            val parsed = json.decodeFromString<RepositoryIndex>(body)
            require(parsed.apps.isNotEmpty()) { "Le dépôt distant ne contient aucun élément" }
            apply(parsed, source = "dépôt distant", persist = true)
            parsed
        }
        result.onFailure { error ->
            _status.update {
                it.copy(
                    loading = false,
                    error = "Dépôt distant indisponible : ${error.message ?: "erreur réseau"}"
                )
            }
        }
        _status.update { it.copy(loading = false) }
        result
    }

    fun item(id: String?): CatalogItem? =
        _index.value.apps.firstOrNull { it.id == id }

    fun itemsFor(category: Category): List<CatalogItem> =
        _index.value.apps.filter { category == Category.ALL || it.categoryEnum == category }

    fun featured(): List<CatalogItem> = _index.value.apps.filter { it.featured }

    fun latest(): List<CatalogItem> = _index.value.apps.sortedByDescending { it.publishedAt }.take(12)

    fun search(query: String, category: Category = Category.ALL): List<CatalogItem> {
        val normalized = query.trim().lowercase()
        return itemsFor(category).filter { item ->
            normalized.isBlank() ||
                item.name.lowercase().contains(normalized) ||
                item.summary.lowercase().contains(normalized) ||
                item.developer.lowercase().contains(normalized) ||
                item.tags.any { it.lowercase().contains(normalized) }
        }
    }

    fun versions(item: CatalogItem): List<String> =
        (item.versions.map { it.version } + item.version).distinct()

    private suspend fun apply(index: RepositoryIndex, source: String, persist: Boolean = false) {
        _index.value = index
        _status.value = RepositoryStatus(
            name = index.name,
            version = index.version,
            updated = index.updated,
            itemCount = index.apps.size,
            source = source,
            loading = false,
            error = null
        )
        if (persist) {
            withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(json.encodeToString(RepositoryIndex.serializer(), index))
                }
            }
        }
    }

    private fun readAsset(): RepositoryIndex? = runCatching {
        context.assets.open("repository/index.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<RepositoryIndex>(it) }
    }.getOrNull()

    private fun readCache(): RepositoryIndex? = runCatching {
        if (!cacheFile.exists()) null
        else json.decodeFromString<RepositoryIndex>(cacheFile.readText())
    }.getOrNull()
}
