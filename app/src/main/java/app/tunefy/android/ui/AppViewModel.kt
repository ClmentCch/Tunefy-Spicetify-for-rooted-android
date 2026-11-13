package app.tunefy.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tunefy.android.AppContainer
import app.tunefy.android.data.model.BackupEntry
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.data.model.DownloadType
import app.tunefy.android.data.model.HistoryEntry
import app.tunefy.android.data.model.InstalledItem
import app.tunefy.android.data.model.RootStatus
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.data.model.ThemeMode
import app.tunefy.android.download.DownloadEvent
import app.tunefy.android.install.ApkInspector
import app.tunefy.android.install.InstallStart
import app.tunefy.android.util.FileHasher
import app.tunefy.android.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val context = container.appContext
    private val catalogRepository = container.catalogRepository
    private val libraryRepository = container.libraryRepository
    private val downloadManager = container.downloadManager
    private val patchRepository = container.patchRepository
    private val settingsRepository = container.settingsRepository
    private val spotifyManager = container.spotifyManager
    private val rootManager = container.rootManager
    private val apkInstaller = container.apkInstaller
    private val installCoordinator = container.installCoordinator

    val settings = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, app.tunefy.android.data.model.AppSettings()
    )
    val index = catalogRepository.index
    val repositoryStatus = catalogRepository.status
    val installedItems: StateFlow<List<InstalledItem>> = libraryRepository.installedItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val downloads: StateFlow<List<DownloadTask>> = downloadManager.tasks
    val patchBundle = patchRepository.bundle
    val patchStatus = patchRepository.status
    val patchSelection = patchRepository.selection.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()
    )
    val history: StateFlow<List<HistoryEntry>> = libraryRepository.history.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val backups: StateFlow<List<BackupEntry>> = libraryRepository.backups.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _spotify = MutableStateFlow(SpotifyStatus())
    val spotify: StateFlow<SpotifyStatus> = _spotify.asStateFlow()

    private val _root = MutableStateFlow(RootStatus())
    val root: StateFlow<RootStatus> = _root.asStateFlow()

    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    private val _importInProgress = MutableStateFlow(false)
    val importInProgress: StateFlow<Boolean> = _importInProgress.asStateFlow()

    private val _installPermissionRequest = MutableStateFlow(false)
    val installPermissionRequest: StateFlow<Boolean> = _installPermissionRequest.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            catalogRepository.bootstrap()
            catalogRepository.refresh()
            patchRepository.load()
            refreshSpotify()
        }
        viewModelScope.launch {
            downloadManager.events.collect { event ->
                when (event) {
                    is DownloadEvent.Completed -> toast("Téléchargement terminé : ${event.itemName}")
                    is DownloadEvent.Failed -> toast("Échec : ${event.message}")
                    is DownloadEvent.Installed -> toast("${event.itemName} ajouté à la bibliothèque")
                    DownloadEvent.InstallPermissionRequired -> {
                        _installPermissionRequest.value = true
                        toast("Autorisez TuneFy à installer des applications.")
                    }
                    is DownloadEvent.AwaitingConfirmation ->
                        toast("Confirmez l'installation dans la fenêtre Android.")
                }
            }
        }
        viewModelScope.launch {
            apkInstaller.results.collect { result ->
                if (result.success) {
                    toast("Installation terminée${result.itemName?.let { " : $it" } ?: ""}")
                    refreshSpotify()
                } else {
                    toast("Installation échouée : ${result.message ?: "refusée par Android"}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- catalogue

    fun refreshCatalog() {
        viewModelScope.launch {
            val result = catalogRepository.refresh()
            toast(
                result.fold(
                    onSuccess = { "Dépôt à jour · ${it.apps.size} éléments" },
                    onFailure = { "Dépôt distant indisponible, index local utilisé" }
                )
            )
        }
    }

    fun updateRepositoryUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setRepositoryUrl(url)
            catalogRepository.refresh()
        }
    }

    fun item(id: String?): CatalogItem? = catalogRepository.item(id)

    fun installedVersion(item: CatalogItem): String? {
        val libraryMatch = installedItems.value.firstOrNull { it.itemId == item.id }
        if (libraryMatch != null) return libraryMatch.version
        if (item.packageId == "com.spotify.music" && _spotify.value.installed) {
            return _spotify.value.versionName
        }
        return null
    }

    fun isInstalled(item: CatalogItem): Boolean = installedVersion(item) != null

    fun activeTaskFor(itemId: String): DownloadTask? =
        downloads.value.firstOrNull { it.itemId == itemId && (it.status.isActive || it.status == DownloadStatus.COMPLETED) }

    // ------------------------------------------------------------------- actions

    fun install(item: CatalogItem, installAfter: Boolean = true) {
        when {
            item.downloadType == DownloadType.DIRECT && !item.downloadUrl.isNullOrBlank() -> {
                val taskId = downloadManager.enqueue(item, installAfter)
                if (taskId == null) {
                    toast("Téléchargement déjà en cours pour ${item.name}")
                } else {
                    toast("Téléchargement de ${item.name}…")
                }
            }
            item.downloadType == DownloadType.RECIPE || item.morpheCompatible -> {
                toast("Ouvrez Morphe pour appliquer les patches compatibles.")
                apkInstaller.openMorpheSource()
            }
            else -> {
                toast("Source externe : ouverture du site officiel.")
                openExternal(item.downloadUrl ?: item.sourceUrl)
            }
        }
    }

    fun uninstall(item: CatalogItem) {
        viewModelScope.launch {
            val installed = installedItems.value.firstOrNull { it.itemId == item.id }
            if (installed?.kind == "apk" && installed.packageId.isNotBlank()) {
                apkInstaller.requestSystemUninstall(installed.packageId)
                libraryRepository.markUninstalled(item.id)
                toast("Confirmez la désinstallation dans Android.")
            } else if (installed != null) {
                val removed = installCoordinator.uninstallLibraryItem(item.id, item.name, item.version)
                toast(if (removed) "${item.name} désinstallé" else "Suppression impossible")
            } else if (item.packageId == "com.spotify.music" && _spotify.value.installed) {
                apkInstaller.requestSystemUninstall(item.packageId)
            } else {
                toast("${item.name} n'est pas installé par TuneFy.")
            }
        }
    }

    fun openExternal(url: String) {
        if (url.isBlank()) {
            toast("Aucun lien disponible.")
            return
        }
        if (!apkInstaller.openExternal(url)) toast("Aucune application pour ouvrir ce lien.")
    }

    fun openMorphe() {
        if (!apkInstaller.openMorpheSource()) {
            openExternal("https://morphe.software/")
        }
    }

    fun openInstallSettings() {
        apkInstaller.openUnknownSourcesSettings()
        _installPermissionRequest.value = false
    }

    fun clearInstallPermissionRequest() {
        _installPermissionRequest.value = false
    }

    /** Imports a user-picked APK / split bundle and installs it after verification. */
    fun importApk(uri: Uri, useRoot: Boolean = false) {
        if (_importInProgress.value) return
        viewModelScope.launch {
            _importInProgress.value = true
            try {
                val file = copyToCache(uri)
                if (file == null) {
                    toast("Impossible de lire le fichier sélectionné.")
                    return@launch
                }
                if (useRoot) {
                    installWithRoot(file)
                } else {
                    when (val result = installCoordinator.install(file, null)) {
                        is InstallStart.SessionStarted -> toast("Vérification OK, confirmez dans Android.")
                        is InstallStart.LibraryInstalled -> toast("Package ajouté à la bibliothèque.")
                        InstallStart.InstallPermissionRequired -> _installPermissionRequest.value = true
                        is InstallStart.Failure -> toast(result.message)
                    }
                }
            } finally {
                _importInProgress.value = false
            }
        }
    }

    private suspend fun installWithRoot(file: File) {
        val expanded = withContext(Dispatchers.IO) { ApkInspector.expand(context, file) }
        if (expanded == null || expanded.apks.isEmpty()) {
            toast("APK illisible.")
            return
        }
        val details = expanded.apks.mapNotNull { ApkInspector.inspect(context, it) }
        val signatures = details.mapNotNull { it.signatureSha256 }.distinct()
        if (signatures.size > 1) {
            toast("Signatures incohérentes : installation refusée.")
            return
        }
        val primary = details.firstOrNull { it.packageId != null }
        val installedSignature = primary?.packageId?.let { ApkInspector.installedSignature(context, it) }
        if (installedSignature != null && primary?.signatureSha256 != null &&
            !installedSignature.equals(primary.signatureSha256, ignoreCase = true)
        ) {
            toast("Signature différente de la version installée : refusé.")
            return
        }
        val result = rootManager.installApkAsRoot(expanded.apks.map { it.absolutePath })
        libraryRepository.recordHistory(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                itemId = primary?.packageId.orEmpty(),
                itemName = primary?.label ?: file.name,
                version = primary?.versionName.orEmpty(),
                action = "Installation root",
                success = result.success,
                detail = result.stdout.ifBlank { result.stderr },
                sha256 = FileHasher.sha256(file)
            )
        )
        toast(
            if (result.success) "Installation root réussie."
            else "Installation root échouée : ${result.stderr.ifBlank { result.stdout }.take(120)}"
        )
        refreshSpotify()
    }

    private suspend fun copyToCache(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: "import-${System.currentTimeMillis()}.apk"
            val directory = File(context.cacheDir, "imports").apply { mkdirs() }
            val target = File(directory, displayName.replace('/', '_'))
            val stream = resolver.openInputStream(uri) ?: throw IOException("Flux illisible")
            stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            target
        }.getOrNull()
    }

    // ----------------------------------------------------------------- downloads

    fun pauseTask(id: String) = downloadManager.pause(id)
    fun resumeTask(id: String) = downloadManager.resume(id)
    fun cancelTask(id: String) = downloadManager.cancel(id)
    fun retryTask(id: String) = downloadManager.retry(id)
    fun removeTask(id: String) = downloadManager.remove(id)
    fun installTask(id: String) = downloadManager.install(id)
    fun clearFinishedDownloads() = downloadManager.clearFinished()

    // ------------------------------------------------------------------- patches

    fun refreshPatches() {
        viewModelScope.launch {
            patchRepository.load(forceRefresh = true)
            toast("Liste des patches actualisée.")
        }
    }

    fun togglePatch(name: String, enabled: Boolean) {
        viewModelScope.launch { patchRepository.toggle(name, enabled) }
    }

    fun patchCompatibility(item: app.tunefy.android.data.model.PatchDefinition) =
        patchRepository.compatibility(item, _spotify.value)

    fun patchesExportJson(): String =
        patchRepository.exportSelection(
            patchBundle.value?.patches.orEmpty(),
            patchSelection.value
        )

    // ---------------------------------------------------------------------- root

    fun refreshSpotify() {
        viewModelScope.launch(Dispatchers.IO) { _spotify.value = spotifyManager.status() }
    }

    fun probeRoot() {
        if (!settings.value.rootFeaturesEnabled) {
            toast("Activez les fonctionnalités root dans les paramètres.")
            return
        }
        viewModelScope.launch {
            _root.value = _root.value.copy(checking = true, error = null)
            _root.value = rootManager.probe()
            refreshSpotify()
        }
    }

    fun createBackup() {
        if (_backupInProgress.value) return
        viewModelScope.launch {
            if (!_root.value.rooted) {
                toast("Une sauvegarde complète nécessite l'accès root.")
                return@launch
            }
            if (!_spotify.value.installed) {
                toast("Spotify n'est pas installé.")
                return@launch
            }
            _backupInProgress.value = true
            try {
                val destination = File(
                    context.filesDir,
                    "backups/spotify-${System.currentTimeMillis()}.tar.gz"
                )
                spotifyManager.backup(destination)
                    .onSuccess { size ->
                        libraryRepository.addBackup(
                            BackupEntry(
                                id = UUID.randomUUID().toString(),
                                sizeBytes = size,
                                path = destination.absolutePath,
                                spotifyVersion = _spotify.value.versionName.orEmpty()
                            )
                        )
                        toast("Sauvegarde créée · ${Formatters.bytes(size)}")
                    }
                    .onFailure { toast("Sauvegarde impossible : ${it.message}") }
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    fun deleteBackup(entry: BackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(entry.path).delete() }
            libraryRepository.removeBackup(entry.id)
            toast("Sauvegarde supprimée.")
        }
    }

    fun exportBackup(entry: BackupEntry, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val source = File(entry.path)
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Destination inaccessible")
                output.use { out -> source.inputStream().use { it.copyTo(out) } }
            }.onSuccess { toast("Sauvegarde exportée.") }
                .onFailure { toast("Export impossible : ${it.message}") }
        }
    }

    fun dataDirectorySize(onResult: (Long?) -> Unit) {
        viewModelScope.launch { onResult(spotifyManager.dataDirectorySize()) }
    }

    // ------------------------------------------------------------------- history

    fun clearHistory() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
            toast("Historique effacé.")
        }
    }

    // ------------------------------------------------------------------ settings

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setRootFeatures(enabled: Boolean) = viewModelScope.launch { settingsRepository.setRootFeaturesEnabled(enabled) }
    fun setAutoCheckUpdates(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoCheckUpdates(enabled) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnlyDownloads(enabled) }
    fun setInstallAfterDownload(enabled: Boolean) = viewModelScope.launch { settingsRepository.setInstallAfterDownload(enabled) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }

    fun repositoryCompatibilityLabel(): Pair<Compatibility, String> {
        val spotify = _spotify.value
        val bundle = patchBundle.value
        if (bundle == null) return Compatibility.UNKNOWN to "Liste de patches indisponible"
        val versions = bundle.patches.flatMap { it.supportedVersions }.distinct()
        return when {
            !spotify.installed -> Compatibility.UNKNOWN to
                "Spotify non détecté · cible ${versions.joinToString(", ")}"
            spotify.versionName in versions ->
                if (bundle.patches.any { it.experimental }) {
                    Compatibility.EXPERIMENTAL to "Spotify ${spotify.versionName} · patches expérimentaux"
                } else {
                    Compatibility.COMPATIBLE to "Spotify ${spotify.versionName} pris en charge"
                }
            else -> Compatibility.INCOMPATIBLE to
                "Spotify ${spotify.versionName} incompatible · cible ${versions.joinToString(", ")}"
        }
    }

    private fun toast(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
