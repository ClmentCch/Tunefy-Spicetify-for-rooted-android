package app.tunefy.android.patch

import app.tunefy.android.data.model.Compatibility
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class EngineUnitTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `shipped profiles are pinned to the verified build`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }

        assertEquals(145767611L, profile.versionCode)
        assertEquals(listOf("arm64-v8a"), profile.abis)
        assertEquals("1.2.0", profiles.bundle.version)
        assertEquals(
            "85c934b567272b2205ac7022a03c03998a2ccb312146e7fa4cca12ed659fda1f",
            profiles.bundle.sha256
        )
        assertTrue(profile.requiredClasses.contains("Lcom/spotify/music/SpotifyApplication;"))
        assertTrue(profile.requiredMethods.containsKey("Lcom/spotify/music/SpotifyApplication;"))
        assertEquals(1, profiles.unsupported.size)
    }

    @Test
    fun `complete profile dexter passes structural compatibility`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }
        val verdict = evaluate(profiles, profile, dexFor(profile))

        assertTrue(verdict.reason, verdict.compatible)
        assertEquals(Compatibility.EXPERIMENTAL, verdict.compatibility)
        assertTrue(verdict.patchAvailability.values.all { it.enabled })
    }

    @Test
    fun `missing SpotifyApplication is reported as an ABI mismatch`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }
        val dex = dexFor(profile, excluded = setOf("Lcom/spotify/music/SpotifyApplication;"))
        val verdict = evaluate(profiles, profile, dex)

        assertFalse(verdict.compatible)
        assertTrue(
            verdict.reason,
            verdict.reason.startsWith(
                "Spotify settings ABI changed: missing Lcom/spotify/music/SpotifyApplication;"
            )
        )
        assertTrue(verdict.patchAvailability.values.none { it.enabled })
    }

    @Test
    fun `missing patch requirement disables only the affected patch`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }
        val dex = dexFor(profile, excluded = setOf("Lp/jb20;"))
        val verdict = SpotifyCompatibility.evaluate(
            versionName = profile.versionName,
            versionCode = profile.versionCode,
            abis = listOf("arm64-v8a"),
            deviceAbis = listOf("arm64-v8a"),
            dex = dex,
            profiles = profiles,
            patchNames = patchNames(profile),
            selected = setOf("Hide Home and Browse ads")
        )

        assertFalse(verdict.compatible)
        assertFalse(verdict.patchAvailability.getValue("Hide Home and Browse ads").enabled)
        assertTrue(verdict.patchAvailability.getValue("Hide Premium tab").enabled)
        assertTrue(verdict.reason.contains("Lp/jb20;"))
    }

    @Test
    fun `wrong ABI is rejected before any patch runs`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }
        val verdict = SpotifyCompatibility.evaluate(
            versionName = profile.versionName,
            versionCode = profile.versionCode,
            abis = listOf("armeabi-v7a"),
            deviceAbis = listOf("arm64-v8a"),
            dex = dexFor(profile),
            profiles = profiles,
            patchNames = patchNames(profile),
            selected = emptySet()
        )

        assertFalse(verdict.compatible)
        assertTrue(verdict.reason.contains("arm64-v8a"))
        assertEquals(listOf("arm64-v8a"), verdict.missingAbi)
    }

    @Test
    fun `unsupported 9_2_0_26 explains the real advertising ABI failure`() {
        val profiles = loadProfiles()
        val verdict = SpotifyCompatibility.evaluate(
            versionName = "9.2.0.26",
            versionCode = 146000000L,
            abis = listOf("arm64-v8a"),
            deviceAbis = listOf("arm64-v8a"),
            dex = DexIndex(emptyMap()),
            profiles = profiles,
            patchNames = listOf("Hide Home and Browse ads"),
            selected = setOf("Hide Home and Browse ads")
        )

        assertFalse(verdict.compatible)
        assertTrue(verdict.reason.contains("Lp/jb20;"))
        assertFalse(verdict.patchAvailability.getValue("Hide Home and Browse ads").enabled)
    }

    @Test
    fun `versionCode mismatch is rejected even with the right version name`() {
        val profiles = loadProfiles()
        val profile = profiles.profiles.single { it.versionName == "9.1.80.2221" }
        val verdict = SpotifyCompatibility.evaluate(
            versionName = profile.versionName,
            versionCode = 1L,
            abis = listOf("arm64-v8a"),
            deviceAbis = listOf("arm64-v8a"),
            dex = dexFor(profile),
            profiles = profiles,
            patchNames = patchNames(profile),
            selected = emptySet()
        )

        assertFalse(verdict.compatible)
        assertTrue(verdict.reason.contains("versionCode"))
    }

    @Test
    fun `DexIndex follows superclasses but never inherits constructors`() {
        val dex = DexIndex(
            mapOf(
                "Lp/parent;" to DexClassInfo("Lp/parent;", null, setOf("onCreate()", "<init>()")),
                "Lp/child;" to DexClassInfo("Lp/child;", "Lp/parent;", emptySet())
            )
        )

        assertTrue(dex.hasClass("p/child"))
        assertTrue(dex.hasMethod("Lp/child;", "onCreate()"))
        assertFalse(dex.hasMethod("Lp/child;", "<init>()"))
        assertTrue(dex.hasMethod("Lp/parent;", "<init>()"))
        assertFalse(dex.hasMethod("Lp/child;", "missing()"))
    }

    @Test
    fun `DexIndexer accepts a non-markable ZipFile stream`() {
        val directory = createTempDirectory("tunefy-dex").toFile()
        try {
            val apk = File(directory, "tiny.apk")
            val dex = javaClass.classLoader!!.getResourceAsStream("tiny.dex")
                ?: error("Fixture tiny.dex absente")
            ZipOutputStream(apk.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex"))
                dex.use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }

            val index = DexIndexer.index(listOf(apk))
            assertTrue(index.hasClass("Ltest/Tiny;"))
            assertTrue(index.hasMethod("Ltest/Tiny;", "value()"))
            assertEquals(150_486_785L, SpotifyOfficialApk.SIZE_BYTES)
            assertEquals(
                "f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da",
                SpotifyOfficialApk.SHA256
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `ABI detector reads split lib folders and file names`() {
        val directory = createTempDirectory("tunefy-abi").toFile()
        try {
            val split = File(directory, "split_config.arm64_v8a.apk")
            ZipOutputStream(split.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("lib/arm64-v8a/libspotify.so"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
            val x86 = File(directory, "base-x86_64.apk")
            ZipOutputStream(x86.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("lib/x86_64/libspotify.so"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }

            assertEquals(listOf("arm64-v8a"), AbiDetector.detect(listOf(split)))
            assertEquals(listOf("x86_64"), AbiDetector.detect(listOf(x86)))
            assertEquals("arm64-v8a", SpotifyCompatibility.normalizeAbi("ARM64_V8A"))
            assertEquals("armeabi-v7a", SpotifyCompatibility.normalizeAbi("arm"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun loadProfiles(): SpotifyProfiles {
        val file = projectFile("src/main/assets/patches/spotify-profiles.json")
        return json.decodeFromString<SpotifyProfiles>(file.readText())
    }

    private fun dexFor(profile: SpotifyProfile, excluded: Set<String> = emptySet()): DexIndex {
        val types = (profile.requiredClasses + profile.patchRequirements.values.flatten())
            .filterNot { it in excluded }
            .toSet()
        return DexIndex(
            types.associateWith { type ->
                DexClassInfo(
                    type = type,
                    superclass = if (type == "Lcom/spotify/music/SpotifyApplication;") {
                        "Landroid/app/Application;"
                    } else {
                        null
                    },
                    methods = profile.requiredMethods[type].orEmpty()
                        .map { spec -> if (spec.contains('(')) spec else "$spec()" }
                        .toSet()
                )
            }
        )
    }

    private fun patchNames(profile: SpotifyProfile): List<String> =
        (profile.patchRequirements.keys + listOf(
            "Clean sharing links",
            "Local files from a server",
            "Spicetify extensions",
            "Theme colors"
        )).distinct()

    private fun evaluate(profiles: SpotifyProfiles, profile: SpotifyProfile, dex: DexIndex): CompatibilityVerdict =
        SpotifyCompatibility.evaluate(
            versionName = profile.versionName,
            versionCode = profile.versionCode,
            abis = listOf("arm64-v8a"),
            deviceAbis = listOf("arm64-v8a"),
            dex = dex,
            profiles = profiles,
            patchNames = patchNames(profile),
            selected = patchNames(profile).toSet()
        )

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Introuvable depuis ${File(".").absolutePath}: $relative")
    }
}
