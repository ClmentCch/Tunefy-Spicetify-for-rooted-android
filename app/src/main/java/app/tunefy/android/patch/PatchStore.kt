package app.tunefy.android.patch

import android.content.Context
import app.tunefy.android.util.FileHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class PatchStore(context: Context) {

    private val root = File(context.filesDir, "patch").apply { mkdirs() }
    private val generatedDir = File(root, "generated").apply { mkdirs() }
    private val logsDir = File(root, "logs").apply { mkdirs() }
    private val generatedFile = File(root, "generated.json")
    private val historyFile = File(root, "history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()

    private val _generated = MutableStateFlow(readGenerated())
    val generated: StateFlow<List<GeneratedPatch>> = _generated.asStateFlow()

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<PatchHistoryRecord>> = _history.asStateFlow()

    fun generatedDirectory(): File = generatedDir

    fun saveGenerated(artifact: GeneratedPatch): GeneratedPatch = synchronized(lock) {
        val list = (_generated.value + artifact).sortedByDescending { it.createdAt }
        write(generatedFile, json.encodeToString(ListSerializer(GeneratedPatch.serializer()), list))
        _generated.value = list
        artifact
    }

    fun deleteGenerated(id: String) = synchronized(lock) {
        val item = _generated.value.firstOrNull { it.id == id }
        if (item != null) runCatching { File(item.path).delete() }
        val list = _generated.value.filterNot { it.id == id }
        write(generatedFile, json.encodeToString(ListSerializer(GeneratedPatch.serializer()), list))
        _generated.value = list
    }

    fun record(record: PatchHistoryRecord) = synchronized(lock) {
        val list = (_history.value + record).takeLast(200)
        write(historyFile, json.encodeToString(ListSerializer(PatchHistoryRecord.serializer()), list))
        _history.value = list
    }

    fun clearHistory() = synchronized(lock) {
        write(historyFile, "[]")
        _history.value = emptyList()
    }

    fun writeLog(id: String, text: String): File {
        val file = File(logsDir, "$id.txt")
        file.writeText(text)
        return file
    }

    fun readLog(path: String?): String? =
        path?.let { runCatching { File(it).readText() }.getOrNull() }

    fun logs(): List<File> = logsDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    fun persistedArtifact(file: File, artifact: GeneratedPatch): GeneratedPatch {
        val target = File(generatedDir, artifact.fileName)
        if (file.absolutePath != target.absolutePath) {
            file.copyTo(target, overwrite = true)
        }
        return artifact.copy(
            path = target.absolutePath,
            sizeBytes = target.length(),
            sha256 = FileHasher.sha256(target)
        )
    }

    private fun readGenerated(): List<GeneratedPatch> = runCatching {
        if (!generatedFile.exists()) return emptyList()
        json.decodeFromString(ListSerializer(GeneratedPatch.serializer()), generatedFile.readText())
    }.getOrDefault(emptyList())

    private fun readHistory(): List<PatchHistoryRecord> = runCatching {
        if (!historyFile.exists()) return emptyList()
        json.decodeFromString(ListSerializer(PatchHistoryRecord.serializer()), historyFile.readText())
    }.getOrDefault(emptyList())

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
