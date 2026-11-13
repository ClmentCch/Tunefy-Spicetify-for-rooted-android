package app.tunefy.android.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import app.tunefy.android.core.Constants
import app.tunefy.android.core.Notifications
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.data.model.DownloadType
import app.tunefy.android.data.repo.CatalogRepository
import app.tunefy.android.data.repo.LibraryRepository
import app.tunefy.android.data.settings.SettingsRepository
import app.tunefy.android.data.remote.Http
import app.tunefy.android.install.InstallCoordinator
import app.tunefy.android.install.InstallStart
import app.tunefy.android.util.FileHasher
import app.tunefy.android.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadEvent {
    data class Completed(val taskId: String, val itemName: String) : DownloadEvent
    data class Failed(val taskId: String, val message: String) : DownloadEvent
    data class Installed(val itemId: String, val itemName: String) : DownloadEvent
    data object InstallPermissionRequired : DownloadEvent
    data class AwaitingConfirmation(val itemName: String) : DownloadEvent
}

/**
 * Real resumable HTTP downloads with live speed, ETA, SHA-256 verification and
 * a foreground service notification. Files land in app-private storage.
 */
class DownloadManager(
    private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val installCoordinator: InstallCoordinator
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val downloadDirectory = File(context.filesDir, "downloads").apply { mkdirs() }
    private val stateFile = File(downloadDirectory, "tasks.json")
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    init {
        restoreState()
    }

    fun enqueue(item: CatalogItem, installAfter: Boolean = false): String? {
        val url = item.downloadUrl
        if (url.isNullOrBlank() || item.downloadType != DownloadType.DIRECT) return null
        if (_tasks.value.any { it.itemId == item.id && it.status.isActive }) return null

        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            itemId = item.id,
            itemName = item.name,
            fileName = item.fileName?.takeIf { it.isNotBlank() } ?: fileNameFor(item, url),
            url = url,
            kind = kindFor(item),
            totalBytes = item.sizeBytes,
            expectedSha256 = item.sha256?.lowercase(),
            installAfterDownload = installAfter
        )
        _tasks.update { listOf(task) + it }
        persistAsync()
        start(task)
        return task.id
    }

    fun pause(taskId: String) {
        val task = task(taskId) ?: return
        if (!task.status.isActive) return
        update(taskId) { it.copy(status = DownloadStatus.PAUSED, speedBytesPerSecond = 0) }
        jobs[taskId]?.cancel()
        persistAsync()
    }

    fun cancel(taskId: String) {
        val task = task(taskId) ?: return
        update(taskId) { it.copy(status = DownloadStatus.CANCELED, speedBytesPerSecond = 0) }
        jobs[taskId]?.cancel()
        val part = File(downloadDirectory, "${task.fileName}.part")
        if (part.exists()) part.delete()
        persistAsync()
    }

    fun cancelAll() {
        _tasks.value.filter { it.status.isActive }.forEach { cancel(it.id) }
        DownloadService.stop(context)
    }

    fun resume(taskId: String) {
        val task = task(taskId)?.copy(status = DownloadStatus.QUEUED) ?: return
        update(taskId) { task.copy(error = null, speedBytesPerSecond = 0) }
        start(task)
    }

    fun retry(taskId: String) {
        val task = task(taskId) ?: return
        val part = File(downloadDirectory, "${task.fileName}.part")
        if (part.exists()) part.delete()
        update(taskId) {
            it.copy(
                status = DownloadStatus.QUEUED,
                downloadedBytes = 0,
                speedBytesPerSecond = 0,
                error = null
            )
        }
        start(task.copy(downloadedBytes = 0))
    }

    fun remove(taskId: String) {
        val task = task(taskId)
        jobs.remove(taskId)?.cancel()
        if (task != null && task.status != DownloadStatus.RUNNING) {
            File(downloadDirectory, task.fileName).delete()
            File(downloadDirectory, "${task.fileName}.part").delete()
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        persistAsync()
    }

    fun clearFinished() {
        val finished = _tasks.value.filter { it.status.isFinished }
        finished.forEach { task ->
            File(downloadDirectory, task.fileName).delete()
            File(downloadDirectory, "${task.fileName}.part").delete()
        }
        _tasks.update { list -> list.filterNot { it.status.isFinished } }
        persistAsync()
    }

    fun task(taskId: String): DownloadTask? = _tasks.value.firstOrNull { it.id == taskId }

    fun taskForItem(itemId: String): DownloadTask? = _tasks.value.firstOrNull { it.itemId == itemId }

    /** Installs or adds a completed download to the library. */
    fun install(taskId: String) {
        val task = task(taskId) ?: return
        if (task.status != DownloadStatus.COMPLETED) return
        scope.launch { performInstall(task) }
    }

    private fun start(task: DownloadTask) {
        jobs[task.id]?.cancel()
        jobs[task.id] = scope.launch { runDownload(task) }
    }

    private suspend fun runDownload(task: DownloadTask) {
        val part = File(downloadDirectory, "${task.fileName}.part")
        try {
            DownloadService.ensureRunning(context)
            val settings = settingsRepository.settings.first()
            if (settings.wifiOnlyDownloads && !isOnWifi()) {
                throw IOException("Téléchargement en Wi-Fi uniquement : activez un réseau Wi-Fi.")
            }
            val resumeFrom = if (part.exists()) part.length() else 0L
            update(task.id) {
                it.copy(
                    status = DownloadStatus.RUNNING,
                    downloadedBytes = resumeFrom,
                    speedBytesPerSecond = 0,
                    error = null
                )
            }

            val requestBuilder = Request.Builder()
                .url(task.url)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Accept", "*/*")
            if (resumeFrom > 0L) requestBuilder.header("Range", "bytes=$resumeFrom-")
            val request = requestBuilder.build()

            Http.client.newCall(request).execute().use { response ->
                if (response.code == 416) {
                    // Nothing left to fetch, the partial file is already complete.
                } else if (!response.isSuccessful) {
                    throw IOException("Le serveur a répondu ${response.code}")
                } else {
                    val body = response.body ?: throw IOException("Réponse vide")
                    val declared = body.contentLength()
                    val total = when {
                        response.code == 206 && declared > 0L -> resumeFrom + declared
                        declared > 0L -> declared
                        else -> task.totalBytes
                    }
                    var written = resumeFrom
                    var windowBytes = 0L
                    var windowStart = SystemClock.elapsedRealtime()
                    var lastUpdate = 0L
                    FileOutputStream(part, resumeFrom > 0L).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                windowBytes += read
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 300) {
                                    val elapsed = (now - windowStart).coerceAtLeast(1L)
                                    val speed = windowBytes * 1000L / elapsed
                                    update(task.id) {
                                        it.copy(
                                            downloadedBytes = written,
                                            totalBytes = if (total > 0) total else written,
                                            speedBytesPerSecond = speed
                                        )
                                    }
                                    lastUpdate = now
                                    if (elapsed >= 1_200L) {
                                        windowStart = now
                                        windowBytes = 0L
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }

            val target = File(downloadDirectory, task.fileName)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            update(task.id) {
                it.copy(
                    status = DownloadStatus.VERIFYING,
                    downloadedBytes = target.length(),
                    totalBytes = target.length(),
                    speedBytesPerSecond = 0,
                    filePath = target.absolutePath
                )
            }

            val actualHash = FileHasher.sha256(target)
            val expected = task.expectedSha256?.lowercase()
            if (expected != null && expected != actualHash.lowercase()) {
                target.delete()
                update(task.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        actualSha256 = actualHash,
                        verified = false,
                        error = "Échec de la vérification SHA-256. Le fichier corrompu a été supprimé."
                    )
                }
                libraryRepository.recordHistory(
                    app.tunefy.android.data.model.HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        itemId = task.itemId,
                        itemName = task.itemName,
                        version = "",
                        action = "Vérification",
                        success = false,
                        detail = "SHA-256 attendu ${expected.take(16)}…, obtenu ${actualHash.take(16)}…"
                    )
                )
                _events.emit(DownloadEvent.Failed(task.id, "Checksum SHA-256 invalide"))
                return
            }

            update(task.id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = target.length(),
                    totalBytes = target.length(),
                    speedBytesPerSecond = 0,
                    actualSha256 = actualHash,
                    verified = expected != null,
                    filePath = target.absolutePath
                )
            }
            _events.emit(DownloadEvent.Completed(task.id, task.itemName))
            Notifications.showDownloadFinished(
                context,
                "Téléchargement terminé",
                "${task.itemName} · ${Formatters.bytes(target.length())}",
                true
            )
            val settingsNow = settingsRepository.settings.first()
            if (task.installAfterDownload || settingsNow.installAfterDownload) {
                performInstall(task.copy(filePath = target.absolutePath, actualSha256 = actualHash))
            }
        } catch (_: CancellationException) {
            // Pause/cancel already stored the intended status.
        } catch (error: Exception) {
            update(task.id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    speedBytesPerSecond = 0,
                    error = error.message ?: "Erreur réseau inconnue"
                )
            }
            _events.emit(DownloadEvent.Failed(task.id, error.message ?: "Erreur réseau"))
            Notifications.showDownloadFinished(
                context,
                "Téléchargement interrompu",
                task.itemName,
                false
            )
        } finally {
            jobs.remove(task.id)
            persist()
        }
    }

    private suspend fun performInstall(task: DownloadTask) {
        val file = task.filePath?.let(::File) ?: return
        if (!file.exists()) return
        val item = catalogRepository.item(task.itemId)
        when (val result = installCoordinator.install(file, item)) {
            is InstallStart.SessionStarted -> {
                _events.emit(DownloadEvent.AwaitingConfirmation(task.itemName))
            }
            is InstallStart.LibraryInstalled -> {
                _events.emit(DownloadEvent.Installed(task.itemId, task.itemName))
            }
            is InstallStart.InstallPermissionRequired -> {
                _events.emit(DownloadEvent.InstallPermissionRequired)
            }
            is InstallStart.Failure -> {
                _events.emit(DownloadEvent.Failed(task.id, result.message))
                libraryRepository.recordHistory(
                    app.tunefy.android.data.model.HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        itemId = task.itemId,
                        itemName = task.itemName,
                        version = item?.version.orEmpty(),
                        action = "Installation",
                        success = false,
                        detail = result.message
                    )
                )
            }
        }
    }

    private fun update(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
        Notifications.notifyDownloadProgress(context, _tasks.value)
    }

    private fun persistAsync() {
        scope.launch { persist() }
    }

    private suspend fun persist() {
        runCatching {
            downloadDirectory.mkdirs()
            stateFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DownloadTask.serializer()), _tasks.value.take(60)))
        }
    }

    private fun restoreState() {
        runCatching {
            if (!stateFile.exists()) return
            val restored = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(DownloadTask.serializer()),
                stateFile.readText()
            )
            _tasks.value = restored.take(60).map { task ->
                when (task.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.VERIFYING ->
                        task.copy(status = DownloadStatus.PAUSED, speedBytesPerSecond = 0)
                    else -> task
                }
            }
        }
    }

    private fun fileNameFor(item: CatalogItem, url: String): String {
        val candidate = url.substringAfterLast('/').substringBefore('?')
        if (candidate.contains('.') && candidate.length < 120) return candidate
        return when (item.categoryEnum) {
            Category.EXTENSIONS, Category.THEMES -> "${item.id}.json"
            Category.PATCHES -> "${item.id}.mpp"
            else -> "${item.id}.zip"
        }
    }

    private fun kindFor(item: CatalogItem): String = when (item.categoryEnum) {
        Category.THEMES -> "theme"
        Category.EXTENSIONS -> "extension"
        Category.PATCHES -> "patch"
        Category.TOOLS -> "tool"
        else -> "apk"
    }

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
