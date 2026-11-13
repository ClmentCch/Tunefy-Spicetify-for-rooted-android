package app.tunefy.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tunefy.android.AppContainer
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.BackupEntry
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.data.model.DownloadType
import app.tunefy.android.data.model.HistoryEntry
import app.tunefy.android.data.model.InstalledItem
import app.tunefy.android.data.model.PatchDefinition
import app.tunefy.android.data.model.RootStatus
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.data.model.ThemeMode
import app.tunefy.android.data.remote.Http
import app.tunefy.android.download.DownloadEvent
import app.tunefy.android.install.ApkInspector
import app.tunefy.android.install.InstallStart
import app.tunefy.android.patch.ApkAnalysis
import app.tunefy.android.patch.GeneratedPatch
import app.tunefy.android.patch.PatchHistoryRecord
import app.tunefy.android.patch.PatchRunException
import app.tunefy.android.patch.PatchStage
import app.tunefy.android.patch.PatchUiState
import app.tunefy.android.patch.StageProgress
import app.tunefy.android.patch.SpotifyOfficialApk
import app.tunefy.android.patch.StageStatus
import app.tunefy.android.patch.TuneFySigningKey
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
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
    private val patchEngine = container.patchEngine
    private val patchStore = container.patchStore

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
    val generatedPatches: StateFlow<List<GeneratedPatch>> = patchStore.generated
    val modificationHistory: StateFlow<List<PatchHistoryRecord>> = patchStore.history

    private val _patchUi = MutableStateFlow(PatchUiState())
    val patchUi: StateFlow<PatchUiState> = _patchUi.asStateFlow()

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
        viewModelScope.launch {
            patchBundle.collect { bundle ->
                val defaults = bundle?.patches.orEmpty().filter { it.default }.map { it.name }.toSet()
                _patchUi.update { state ->
                    if (state.selected.isEmpty() && !state.running && !state.analyzing) {
                        val available = state.analysis?.patchAvailability.orEmpty()
                        state.copy(selected = defaults.filter { available[it]?.enabled != false }.toSet())
                    } else {
                        state
                    }
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
                toast("Ouvrez « APK Patcher » dans le menu pour appliquer les patches avec le moteur TuneFy.")
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

    // -------------------------------------------------------------------- patcher

    fun selectPatchApk(uri: Uri) {
        if (_patchUi.value.analyzing || _patchUi.value.running || _patchUi.value.officialDownloading) return
        viewModelScope.launch { runAnalysis { patchEngine.analyze(uri) } }
    }

    /**
     * Downloads the exact 9.1.80.2221 APK published on the TuneFy GitHub release,
     * verifies its pinned SHA-256, then analyzes it without leaving TuneFy.
     */
    fun downloadOfficialApk() {
        val state = _patchUi.value
        if (state.analyzing || state.running || state.officialDownloading) return
        viewModelScope.launch {
            _patchUi.update {
                it.copy(
                    officialDownloading = true,
                    officialDownloadedBytes = 0L,
                    officialTotalBytes = SpotifyOfficialApk.SIZE_BYTES,
                    error = null,
                    result = null
                )
            }
            val file = try {
                fetchOfficialApk()
            } catch (error: Exception) {
                _patchUi.update {
                    it.copy(
                        officialDownloading = false,
                        error = "Téléchargement de l'APK officiel impossible : ${error.message}"
                    )
                }
                toast("Téléchargement impossible : ${error.message}")
                return@launch
            }
            _patchUi.update {
                it.copy(
                    officialDownloading = false,
                    officialDownloadedBytes = file.length(),
                    officialTotalBytes = file.length()
                )
            }
            runAnalysis { patchEngine.analyzeFile(file) }
        }
    }

    private suspend fun fetchOfficialApk(): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "patch/official").apply { mkdirs() }
        val target = File(directory, "Spotify-${SpotifyOfficialApk.VERSION}.apk")
        if (target.isFile && FileHasher.sha256(target).equals(SpotifyOfficialApk.SHA256, ignoreCase = true)) {
            return@withContext target
        }
        val part = File(directory, "${target.name}.part")
        if (part.exists()) part.delete()
        val request = Request.Builder()
            .url(SpotifyOfficialApk.DOWNLOAD_URL)
            .header("User-Agent", Constants.USER_AGENT)
            .header("Accept", "*/*")
            .build()
        val client = Http.client.newBuilder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub a répondu HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Réponse vide")
            val total = body.contentLength().takeIf { it > 0L } ?: SpotifyOfficialApk.SIZE_BYTES
            _patchUi.update { it.copy(officialTotalBytes = total) }
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 250L) {
                            lastUpdate = now
                            _patchUi.update { it.copy(officialDownloadedBytes = downloaded) }
                        }
                    }
                }
            }
        }
        val actual = FileHasher.sha256(part)
        if (!actual.equals(SpotifyOfficialApk.SHA256, ignoreCase = true)) {
            part.delete()
            throw IOException("SHA-256 incorrect : $actual")
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        target
    }

    private suspend fun runAnalysis(load: suspend () -> ApkAnalysis) {
        _patchUi.update {
            it.copy(
                analyzing = true,
                analysis = null,
                error = null,
                result = null,
                log = "",
                stages = PatchStage.entries.map { stage -> StageProgress(stage) }
            )
        }
        try {
            displayAnalysis(load())
        } catch (error: PatchRunException) {
            _patchUi.update { it.copy(analyzing = false, error = error.userMessage) }
            toast("APK refusé : ${error.userMessage}")
        } catch (error: Exception) {
            _patchUi.update {
                it.copy(analyzing = false, error = error.message ?: "Analyse de l'APK impossible")
            }
            toast("Analyse impossible : ${error.message}")
        }
    }

    private fun displayAnalysis(analysis: ApkAnalysis) {
        val defaults = patchBundle.value?.patches.orEmpty()
            .filter { it.default }
            .map { it.name }
            .filter { analysis.patchAvailability[it]?.enabled != false }
            .toSet()
        _patchUi.value = PatchUiState(
            analysis = analysis,
            analyzing = false,
            selected = defaults,
            stages = PatchStage.entries.map { stage ->
                when (stage) {
                    PatchStage.ANALYZE -> StageProgress(stage, StageStatus.DONE, analysis.displayName)
                    PatchStage.COMPATIBILITY -> StageProgress(stage, StageStatus.DONE, analysis.reason)
                    else -> StageProgress(stage)
                }
            },
            log = buildString {
                appendLine("Fichier : ${analysis.displayName}")
                appendLine("Package : ${analysis.packageName ?: "inconnu"}")
                appendLine("Version : ${analysis.versionName ?: "inconnue"} (${analysis.versionCode})")
                appendLine("ABI : ${analysis.abis.joinToString(", ").ifBlank { "non détectée" }}")
                appendLine("Modules : ${analysis.splitCount}")
                appendLine("SHA-256 : ${analysis.sha256}")
                appendLine("Compatibilité : ${analysis.reason}")
            }
        )
        toast(
            if (analysis.compatible) "APK analysé · Spotify ${analysis.versionName}"
            else "APK incompatible : ${analysis.reason}"
        )
    }

    fun togglePatchSelection(name: String, enabled: Boolean) {
        _patchUi.update { state ->
            if (state.running) return@update state
            if (enabled && state.analysis?.patchAvailability?.get(name)?.enabled == false) return@update state
            state.copy(
                selected = if (enabled) state.selected + name else state.selected - name
            )
        }
    }

    /** Selects every patch that the compatibility check marked as usable. */
    fun selectAllPatches() = updatePatchSelection { true }

    /** Restores the selection exported by the bundle as the recommended default. */
    fun resetPatchSelection() = updatePatchSelection { it.default }

    private fun updatePatchSelection(include: (PatchDefinition) -> Boolean) {
        _patchUi.update { state ->
            if (state.running || state.analyzing) return@update state
            val availability = state.analysis?.patchAvailability.orEmpty()
            val names = patchBundle.value?.patches.orEmpty()
                .filter(include)
                .filter { availability[it.name]?.enabled != false }
                .map { it.name }
                .toSet()
            state.copy(selected = names)
        }
    }

    fun startPatch() {
        val state = _patchUi.value
        val analysis = state.analysis ?: run {
            toast("Importez d'abord un APK Spotify.")
            return
        }
        if (state.running) return
        if (!analysis.compatible) {
            toast("APK incompatible : ${analysis.reason}")
            return
        }
        if (state.selected.isEmpty()) {
            toast("Sélectionnez au moins un patch.")
            return
        }
        viewModelScope.launch {
            _patchUi.update {
                it.copy(
                    running = true,
                    error = null,
                    result = null,
                    log = "",
                    stages = PatchStage.entries.map { stage -> StageProgress(stage) }
                )
            }
            try {
                val result = patchEngine.patch(
                    analysis = analysis,
                    selected = state.selected,
                    onStage = { stage, status, detail ->
                        _patchUi.update { current ->
                            current.copy(
                                stages = current.stages.map { progress ->
                                    if (progress.stage == stage) progress.copy(status = status, detail = detail)
                                    else progress
                                }
                            )
                        }
                    },
                    onLog = { line ->
                        _patchUi.update { current ->
                            current.copy(log = (current.log + line + "\n").takeLast(24_000))
                        }
                    }
                )
                _patchUi.update { it.copy(running = false, result = result, log = result.log) }
                toast("APK créé : ${result.artifact.fileName}")
            } catch (error: PatchRunException) {
                _patchUi.update {
                    it.copy(
                        running = false,
                        error = error.userMessage,
                        log = (it.log + "\n" + error.detail).takeLast(24_000)
                    )
                }
            } catch (error: Exception) {
                _patchUi.update {
                    it.copy(running = false, error = error.message ?: "Échec du patch")
                }
            }
        }
    }

    fun resetPatcher() {
        if (_patchUi.value.running) return
        _patchUi.value = PatchUiState()
    }

    fun installGenerated(artifact: GeneratedPatch) {
        val file = artifact.file()
        if (!file.exists()) {
            toast("APK introuvable : ${artifact.fileName}")
            return
        }
        if (!apkInstaller.canInstallPackages()) {
            _installPermissionRequest.value = true
            toast("Autorisez TuneFy à installer des applications.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            apkInstaller.startSession(
                apks = listOf(file),
                packageName = "com.spotify.music",
                item = null,
                archiveSha256 = artifact.sha256,
                signatureSha256 = artifact.signatureSha256
            ).onSuccess {
                toast("Vérification OK, confirmez l'installation dans Android.")
            }.onFailure {
                toast("Installation impossible : ${it.message}")
            }
        }
    }

    fun saveGenerated(artifact: GeneratedPatch, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val source = artifact.file()
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Destination inaccessible")
                output.use { destination -> source.inputStream().use { it.copyTo(destination) } }
            }.onSuccess { toast("APK enregistré : ${artifact.fileName}") }
                .onFailure { toast("Enregistrement impossible : ${it.message}") }
        }
    }

    fun deleteGenerated(artifact: GeneratedPatch) {
        patchStore.deleteGenerated(artifact.id)
        toast("APK supprimé : ${artifact.fileName}")
    }

    fun clearModificationHistory() {
        patchStore.clearHistory()
        toast("Historique des modifications effacé.")
    }

    fun patchProfiles() = runCatching { patchEngine.profiles() }.getOrNull()

    fun patchBundleInfo() = patchEngine.bundleInfo()

    fun existingSigningCertificate(): String? = TuneFySigningKey.existingCertificateSha256(context)

    fun patchLogs(): List<File> = patchStore.logs()

    fun readPatchLog(path: String): String? = patchStore.readLog(path)

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
