package app.tunefy.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tunefy.android.data.model.AppSettings
import app.tunefy.android.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tunefy_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val repositoryUrl = stringPreferencesKey("repository_url")
        val rootFeatures = booleanPreferencesKey("root_features")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
        val installAfterDownload = booleanPreferencesKey("install_after_download")
        val notifications = booleanPreferencesKey("notifications")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.themeMode]),
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            repositoryUrl = prefs[Keys.repositoryUrl]?.takeIf { it.isNotBlank() } ?: defaults.repositoryUrl,
            rootFeaturesEnabled = prefs[Keys.rootFeatures] ?: defaults.rootFeaturesEnabled,
            autoCheckUpdates = prefs[Keys.autoCheckUpdates] ?: defaults.autoCheckUpdates,
            wifiOnlyDownloads = prefs[Keys.wifiOnly] ?: defaults.wifiOnlyDownloads,
            installAfterDownload = prefs[Keys.installAfterDownload] ?: defaults.installAfterDownload,
            notificationsEnabled = prefs[Keys.notifications] ?: defaults.notificationsEnabled
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.key }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

    suspend fun setRepositoryUrl(url: String) = edit {
        it[Keys.repositoryUrl] = url.trim().ifBlank { AppSettings.DEFAULT_REPOSITORY_URL }
    }

    suspend fun setRootFeaturesEnabled(enabled: Boolean) = edit { it[Keys.rootFeatures] = enabled }

    suspend fun setAutoCheckUpdates(enabled: Boolean) = edit { it[Keys.autoCheckUpdates] = enabled }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) = edit { it[Keys.wifiOnly] = enabled }

    suspend fun setInstallAfterDownload(enabled: Boolean) = edit { it[Keys.installAfterDownload] = enabled }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.notifications] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
