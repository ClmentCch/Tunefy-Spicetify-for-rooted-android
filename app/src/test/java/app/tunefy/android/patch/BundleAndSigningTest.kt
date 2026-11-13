package app.tunefy.android.patch

import app.morphe.patcher.apk.ApkUtils
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.Date
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

class BundleAndSigningTest {

    @Test
    fun `shipped bundle matches its pinned hash and contains loadable patch classes`() {
        val bundle = projectFile("src/main/assets/patches/patches-1.2.0-tunefy.mpp")
        assertTrue("Bundle absent", bundle.isFile)
        assertEquals(PatchBundleStore.EXPECTED_SHA256, sha256(bundle))

        val classes = ZipFile(bundle).use { zip ->
            val entry = zip.getEntry("classes.dex")
                ?: error("classes.dex absent du bundle")
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            DexBackedDexFile.fromInputStream(Opcodes.forApi(35), ByteArrayInputStream(bytes))
                .classes
                .map { it.type }
                .toSet()
        }

        assertTrue(classes.contains("Lapp/spicetify/patches/spotify/settings/SettingsPatchKt;"))
        assertTrue(classes.contains("Lapp/spicetify/patches/spotify/ads/BrandAdsPatchKt;"))
        assertTrue(classes.contains("Lapp/spicetify/patches/spotify/ads/PlayerAdCardsPatchKt;"))
    }

    @Test
    fun `catalog exposes every patch from the bundle manifest`() {
        val catalog = projectFile("src/main/assets/patches/patches-list.json")
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(catalog.readText()).jsonObject
        val patches = root.getValue("patches").jsonArray

        assertEquals("1.2.0", root.getValue("version").jsonPrimitive.content)
        assertEquals(8, patches.size)
        val names = patches.map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet()
        assertTrue(names.contains("Clean sharing links"))
        assertTrue(names.contains("Hide Home and Browse ads"))
        assertTrue(names.contains("Spicetify extensions"))
    }

    @Test
    fun `ARSCLib rebuilds a valid APK from the fixture`() {
        val directory = createTempDirectory("tunefy-rebuild").toFile()
        try {
            val fixture = javaClass.classLoader?.getResourceAsStream("minimal.apk")
                ?: error("Fixture minimal.apk absente")
            val input = File(directory, "input.apk")
            fixture.use { source -> input.outputStream().use { source.copyTo(it) } }
            val output = File(directory, "rebuilt.apk")

            com.reandroid.apk.ApkModule.loadApkFile(input).use { module ->
                module.writeApk(output)
            }

            assertTrue(output.length() > 0)
            com.reandroid.apk.ApkModule.loadApkFile(output).use { rebuilt ->
                assertTrue(rebuilt.hasAndroidManifest())
                assertEquals("app.tunefy.patchtest", rebuilt.packageName)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `TuneFy style BKS key signs and verifies a synthetic APK`() {
        val directory = createTempDirectory("tunefy-signing").toFile()
        try {
            val unsigned = File(directory, "unsigned.apk")
            val fixture = javaClass.classLoader.getResourceAsStream("minimal.apk")
                ?: error("Fixture minimal.apk absente")
            fixture.use { input -> unsigned.outputStream().use { output -> input.copyTo(output) } }
            val keyStoreFile = File(directory, "tunefy-signing.bks")
            val details = createKeyStore(keyStoreFile, "test-password")
            val signed = File(directory, "signed.apk")

            ApkUtils.signApk(unsigned, signed, "TuneFy", details, 35)
            assertTrue(signed.length() > 0)

            val certificates = ApkUtils.verifiedSigningCertificates(signed, 35).orEmpty()
            assertTrue("La signature TuneFy n'a pas été vérifiée", certificates.isNotEmpty())
            assertTrue(
                certificates.first().subjectX500Principal.name,
                certificates.first().subjectX500Principal.name.contains("TuneFy")
            )

            val reversed = signed.readBytes()
            reversed[reversed.size / 2] = (reversed[reversed.size / 2].toInt() xor 0xFF).toByte()
            val tampered = File(directory, "tampered.apk")
            tampered.writeBytes(reversed)
            val tamperedCertificates = runCatching {
                ApkUtils.verifiedSigningCertificates(tampered, 35)
            }.getOrNull().orEmpty()
            assertTrue("Un APK altéré ne doit pas être accepté", tamperedCertificates.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createKeyStore(file: File, password: String): ApkUtils.KeyStoreDetails {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val provider = BouncyCastleProvider()
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()
        val now = Date()
        val end = Date(now.time + 30L * 365L * 24L * 60L * 60L * 1000L)
        val name = X500Name("CN=TuneFy APK Patcher, O=TuneFy, C=FR")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(96, SecureRandom()),
            now,
            end,
            name,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.private)
        val certificate = JcaX509CertificateConverter().setProvider(provider)
            .getCertificate(builder.build(signer))

        val keyStore = KeyStore.getInstance("BKS", provider)
        keyStore.load(null, null)
        keyStore.setKeyEntry("tunefy", pair.private, password.toCharArray(), arrayOf(certificate))
        file.outputStream().use { output -> keyStore.store(output, null) }

        return ApkUtils.KeyStoreDetails(
            keyStore = file,
            keyStorePassword = null,
            alias = "tunefy",
            password = password
        )
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Introuvable depuis ${File(".").absolutePath}: $relative")
    }
}
