package app.tunefy.android.patch

import android.content.Context
import android.util.Log
import app.morphe.patcher.apk.ApkUtils
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * The key is generated locally, kept in app-private storage and reused so a
 * later patched build can update an earlier one signed by TuneFy. Clearing
 * TuneFy's data or uninstalling it destroys the key and makes future updates
 * incompatible with previously installed patched builds; that warning is shown
 * in the UI and the diagnostics screen.
 */
object TuneFySigningKey {

    private const val TAG = "TuneFySigningKey"
    private const val ALIAS = "tunefy"
    private const val PREFERENCES = "tunefy_signing"
    private const val PASSWORD_KEY = "key_password"
    private const val CERT_NAME = "CN=TuneFy APK Patcher, O=TuneFy, C=FR"

    private val lock = Any()

    fun details(context: Context): ApkUtils.KeyStoreDetails = synchronized(lock) {
        runCatching { Security.addProvider(BouncyCastleProvider()) }
        val directory = File(context.filesDir, "signing").apply { mkdirs() }
        val store = File(directory, "tunefy-signing.bks")
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        var password = preferences.getString(PASSWORD_KEY, null)

        if (!store.exists() || password.isNullOrBlank()) {
            password = randomPassword()
            create(store, password)
            preferences.edit().putString(PASSWORD_KEY, password).apply()
        }

        ApkUtils.KeyStoreDetails(
            keyStore = store,
            keyStorePassword = null,
            alias = ALIAS,
            password = password
        )
    }

    /** Does not create a key; diagnostics can be opened before the first patch. */
    fun existingCertificateSha256(context: Context): String? = runCatching {
        runCatching { Security.addProvider(BouncyCastleProvider()) }
        val store = File(File(context.filesDir, "signing"), "tunefy-signing.bks")
        if (!store.exists()) return@runCatching null
        val password = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PASSWORD_KEY, null) ?: return@runCatching null
        val details = ApkUtils.KeyStoreDetails(store, null, ALIAS, password)
        val keyStore = ApkUtils.readKeyStore(details)
        val certificate = keyStore.getCertificate(details.alias) as? X509Certificate
            ?: return@runCatching null
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
    }.onFailure { Log.w(TAG, "Unable to read the TuneFy signing certificate", it) }.getOrNull()

    fun certificateNotBefore(context: Context): Long? = runCatching {
        val details = details(context)
        val keyStore = ApkUtils.readKeyStore(details)
        (keyStore.getCertificate(details.alias) as? X509Certificate)?.notBefore?.time
    }.getOrNull()

    private fun create(store: File, password: String) {
        val provider = BouncyCastleProvider()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()
        val now = Date()
        val end = Date(now.time + 30L * 365L * 24L * 60L * 60L * 1000L)
        val name = X500Name(CERT_NAME)
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(96, SecureRandom()),
            now,
            end,
            name,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keyPair.private)
        val certificate = JcaX509CertificateConverter().setProvider(provider)
            .getCertificate(builder.build(signer))

        val keyStore = KeyStore.getInstance("BKS", provider)
        keyStore.load(null, null)
        keyStore.setKeyEntry(ALIAS, keyPair.private, password.toCharArray(), arrayOf(certificate))
        FileOutputStream(store).use { output -> keyStore.store(output, null) }
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
