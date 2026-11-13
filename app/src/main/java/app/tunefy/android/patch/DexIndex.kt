package app.tunefy.android.patch

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

data class DexClassInfo(
    val type: String,
    val superclass: String?,
    val methods: Set<String>
)

/**
 * Read-only view of every class in the package. Only class names and method
 * signatures are retained; bytecode and annotations are discarded.
 */
class DexIndex(private val classes: Map<String, DexClassInfo>) {

    fun hasClass(type: String): Boolean = classes.containsKey(normalizeType(type))

    fun classInfo(type: String): DexClassInfo? = classes[normalizeType(type)]

    /**
     * Accepts either a method name (`create`) or an exact signature
     * (`<init>(I,Lp/ct71;,I)`). Superclasses are followed for inherited methods,
     * except for constructors, which are never inherited.
     */
    fun hasMethod(type: String, spec: String): Boolean {
        val normalized = normalizeType(type)
        var current: String? = normalized
        val visited = HashSet<String>()
        while (current != null && visited.add(current)) {
            val info = classes[current]
            if (info != null && info.methods.any { matchesMethod(it, spec) }) return true
            if (spec.startsWith("<init>")) return false
            current = info?.superclass?.let(::normalizeType)
        }
        return false
    }

    fun types(): Set<String> = classes.keys

    private fun matchesMethod(signature: String, spec: String): Boolean {
        if (spec.contains('(')) return signature == spec
        return signature.startsWith("$spec(")
    }

    private fun normalizeType(type: String): String =
        if (type.startsWith("L") || type.startsWith("[")) type else "L$type;"
}

object DexIndexer {

    private val dexEntry = Regex("""classes\d*\.dex""")

    /** @throws PatchRunException when a DEX cannot be decoded, which must never be silently ignored. */
    fun index(apkFiles: List<File>): DexIndex {
        val classes = HashMap<String, DexClassInfo>(32_768)
        apkFiles.filter { it.isFile }.forEach { apk ->
            try {
                ZipFile(apk).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && dexEntry.matches(it.name) }
                        .forEach { entry ->
                            zip.getInputStream(entry).use { input ->
                                // dexlib2 calls mark() on the header stream. A ZipFile
                                // InputStream is not markable, so decode from memory.
                                val bytes = input.readBytes()
                                val dex = DexBackedDexFile.fromInputStream(
                                    Opcodes.forApi(35),
                                    ByteArrayInputStream(bytes)
                                )
                                dex.classes.forEach { definition ->
                                    val methods = HashSet<String>()
                                    definition.methods.forEach { method ->
                                        methods += "${method.name}(${method.parameterTypes.joinToString(",")})"
                                    }
                                    classes[definition.type] = DexClassInfo(
                                        type = definition.type,
                                        superclass = definition.superclass,
                                        methods = methods
                                    )
                                }
                            }
                        }
                }
            } catch (error: IOException) {
                throw PatchRunException(
                    "Impossible de lire les DEX de ${apk.name}. L'APK est corrompu ou utilise un format non pris en charge.",
                    error.stackTraceToString(),
                    error
                )
            } catch (error: RuntimeException) {
                throw PatchRunException(
                    "Impossible de lire les DEX de ${apk.name}. L'APK est corrompu ou utilise un format non pris en charge.",
                    error.stackTraceToString(),
                    error
                )
            }
        }
        if (classes.isEmpty()) {
            throw PatchRunException(
                "Aucune classe DEX trouvée dans l'APK. Vérifiez qu'il s'agit bien d'une application Android complète.",
                "DexIndex is empty"
            )
        }
        return DexIndex(classes)
    }
}
