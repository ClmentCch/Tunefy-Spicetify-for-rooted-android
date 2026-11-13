package app.tunefy.android.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tunefy.android.data.model.BackupEntry
import app.tunefy.android.data.model.HistoryEntry
import app.tunefy.android.data.model.InstalledItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore(name = "tunefy_library")

/**
 * Local, on-device records: what TuneFy installed, what happened, and which
 * Spotify backups exist. Nothing here is sent anywhere.
 */
class LibraryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val installed = stringPreferencesKey("installed_items")
        val history = stringPreferencesKey("history")
        val backups = stringPreferencesKey("backups")
    }

    val installedItems: Flow<List<InstalledItem>> = context.libraryDataStore.data.map { prefs ->
        decodeList<InstalledItem>(prefs[Keys.installed])
    }

    val history: Flow<List<HistoryEntry>> = context.libraryDataStore.data.map { prefs ->
        decodeList<HistoryEntry>(prefs[Keys.history])
    }

    val backups: Flow<List<BackupEntry>> = context.libraryDataStore.data.map { prefs ->
        decodeList<BackupEntry>(prefs[Keys.backups])
    }

    suspend fun markInstalled(item: InstalledItem) {
        context.libraryDataStore.edit { prefs ->
            val current = decodeList<InstalledItem>(prefs[Keys.installed])
                .filterNot { it.itemId == item.itemId }
            prefs[Keys.installed] = json.encodeToString(current + item)
        }
    }

    suspend fun markUninstalled(itemId: String) {
        context.libraryDataStore.edit { prefs ->
            val current = decodeList<InstalledItem>(prefs[Keys.installed])
                .filterNot { it.itemId == itemId }
            prefs[Keys.installed] = json.encodeToString(current)
        }
    }

    suspend fun recordHistory(entry: HistoryEntry) {
        context.libraryDataStore.edit { prefs ->
            val current = decodeList<HistoryEntry>(prefs[Keys.history])
            val updated = (listOf(entry) + current).take(200)
            prefs[Keys.history] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        context.libraryDataStore.edit { it[Keys.history] = json.encodeToString(emptyList<HistoryEntry>()) }
    }

    suspend fun addBackup(entry: BackupEntry) {
        context.libraryDataStore.edit { prefs ->
            val current = decodeList<BackupEntry>(prefs[Keys.backups])
            prefs[Keys.backups] = json.encodeToString((listOf(entry) + current).take(30))
        }
    }

    suspend fun removeBackup(id: String) {
        context.libraryDataStore.edit { prefs ->
            val current = decodeList<BackupEntry>(prefs[Keys.backups]).filterNot { it.id == id }
            prefs[Keys.backups] = json.encodeToString(current)
        }
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
    }
}
