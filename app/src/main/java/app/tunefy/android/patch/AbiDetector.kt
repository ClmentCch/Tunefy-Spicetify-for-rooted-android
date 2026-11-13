package app.tunefy.android.patch

import java.io.File
import java.util.zip.ZipFile

/** Detects native ABIs from split archives and from explicit ABI file names. */
object AbiDetector {

    private val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun detect(files: List<File>): List<String> {
        val abis = linkedSetOf<String>()
        files.forEach { file ->
            runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (!entry.isDirectory && entry.name.startsWith("lib/")) {
                            val abi = entry.name.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotBlank()) abis += SpotifyCompatibility.normalizeAbi(abi)
                        }
                    }
                }
            }
            val lowerName = file.name.lowercase()
            val fromName = knownAbis.filter { abi ->
                lowerName.contains(abi.replace("-", "_")) || lowerName.contains(abi)
            }
            // "x86" is a substring of "x86_64"; do not report both from one file name.
            abis += if ("x86_64" in fromName) fromName - "x86" else fromName
        }
        return abis.toList()
    }
}
