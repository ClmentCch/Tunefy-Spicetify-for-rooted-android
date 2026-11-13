package app.tunefy.android.patch

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.PatcherProgress
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.patch.loadPatchesFromDex
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.PatchBundle
import app.tunefy.android.install.ApkInspector
import app.tunefy.android.install.ExpandedPackage
import app.tunefy.android.util.FileHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.zip.ZipFile
import kotlin.math.min

class PatchEngine(
    private val context: Context,
    private val store: PatchStore
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val profiles: SpotifyProfiles by lazy { SpotifyCompatibility.load(context) }

    fun profiles(): SpotifyProfiles = profiles

    fun bundleInfo(): SpotifyBundleInfo = profiles.bundle

    fun catalog(): PatchBundle = runCatching {
        val text = context.assets.open("patches/patches-list.json").bufferedReader().use { it.readText() }
        json.decodeFromString(PatchBundle.serializer(), text)
    }.getOrDefault(PatchBundle())

    suspend fun analyze(uri: Uri, displayName: String? = null): ApkAnalysis = withContext(Dispatchers.IO) {
        val name = displayName ?: queryName(uri)
        analyzeImported(copyImport(uri, name), name)
    }

    /** Analyzes a file already inside TuneFy's private storage, without copying it again. */
    suspend fun analyzeFile(file: File): ApkAnalysis = withContext(Dispatchers.IO) {
        analyzeImported(file, file.name)
    }

    private fun analyzeImported(imported: File, name: String?): ApkAnalysis {
        val expanded = expand(imported)
        val base = expanded.primaryApk
        val details = ApkInspector.inspect(context, base)
            ?: throw PatchRunException(
                "APK illisible : Android ne reconnaît pas de manifeste valide dans ${name ?: imported.name}.",
                "PackageManager.getPackageArchiveInfo returned null for ${base.absolutePath}"
            )

        val modules = expanded.apks
        val dex = DexIndexer.index(modules)
        val abis = AbiDetector.detect(modules)
        val names = catalog().patches.map { it.name }
        val verdict = SpotifyCompatibility.evaluate(
            versionName = details.versionName,
            versionCode = details.versionCode,
            abis = abis,
            deviceAbis = Build.SUPPORTED_ABIS.toList(),
            dex = dex,
            profiles = profiles,
            patchNames = names,
            selected = emptySet()
        )
        val installedSignature = details.packageId?.let { ApkInspector.installedSignature(context, it) }
        val tuneFySignature = TuneFySigningKey.existingCertificateSha256(context)
        return ApkAnalysis(
            displayName = name ?: imported.name,
            packageName = details.packageId,
            versionName = details.versionName,
            versionCode = details.versionCode,
            minSdk = details.minSdk,
            abis = abis,
            splitCount = modules.size,
            isArchive = modules.size > 1,
            signatureSha256 = details.signatureSha256,
            installedSignatureSha256 = installedSignature,
            // TuneFy always signs with its own key. Warn unless the imported APK is
            // already signed with that same key (for example a TuneFy rebuild).
            signatureWillChange = details.signatureSha256 != null &&
                !details.signatureSha256.equals(tuneFySignature, ignoreCase = true),
            sizeBytes = imported.length(),
            sha256 = details.sha256,
            compatible = verdict.compatible,
            compatibility = verdict.compatibility,
            reason = verdict.reason,
            supportedVersions = verdict.supportedVersions,
            missingAbi = verdict.missingAbi,
            patchAvailability = verdict.patchAvailability,
            baseApk = base,
            modules = modules,
            sourceFile = imported
        )
    }

    suspend fun patch(
        analysis: ApkAnalysis,
        selected: Set<String>,
        onStage: (PatchStage, StageStatus, String) -> Unit,
        onLog: (String) -> Unit
    ): PatchRunResult = withContext(Dispatchers.IO) {
        if (!analysis.compatible) {
            throw PatchRunException(analysis.reason)
        }
        val unavailable = selected.filter { analysis.patchAvailability[it]?.enabled == false }
        if (unavailable.isNotEmpty()) {
            throw PatchRunException(
                "Patch incompatible : " + unavailable.joinToString(", ") { name ->
                    "$name (${analysis.patchAvailability[name]?.reason.orEmpty()})"
                }
            )
        }
        if (selected.isEmpty()) throw PatchRunException("Sélectionnez au moins un patch.")

        val runId = UUID.randomUUID().toString()
        val runDir = File(context.cacheDir, "patch-run/$runId").apply { mkdirs() }
        val log = StringBuilder()
        val logLock = Any()
        val logLines = ArrayList<String>()

        fun append(line: String) {
            synchronized(logLock) {
                logLines += line
                log.append(line).append('\n')
                onLog(line)
            }
        }

        val rootLogger = Logger.getLogger("")
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                val level = when (record.level) {
                    Level.SEVERE -> "ERREUR"
                    Level.WARNING -> "AVERTISSEMENT"
                    else -> "INFO"
                }
                append("[$level] ${record.message}")
            }

            override fun flush() = Unit
            override fun close() = Unit
        }
        rootLogger.addHandler(handler)

        var patcher: Patcher? = null
        try {
            onStage(PatchStage.ANALYZE, StageStatus.DONE, analysis.displayName)
            onStage(PatchStage.COMPATIBILITY, StageStatus.DONE, "Profil ${analysis.versionName} · ${analysis.abis.joinToString(", ")}")

            onStage(PatchStage.PREPARE, StageStatus.RUNNING, "Préparation de l'APK d'entrée")
            val input = prepareInput(analysis, runDir, ::append)
            onStage(PatchStage.PREPARE, StageStatus.DONE, input.name)

            val bundle = PatchBundleStore.ensure(context)
            append("Bundle ${PatchBundleStore.VERSION} · SHA-256 ${PatchBundleStore.EXPECTED_SHA256}")
            append("Entrée ${input.name} · ${input.length()} octets")

            onStage(PatchStage.APPLY, StageStatus.RUNNING, "Chargement des patches")
            val patches = loadPatchesFromDex(setOf(bundle)).byPatchesFile[bundle].orEmpty()
            if (patches.isEmpty()) {
                throw PatchRunException(
                    "Le bundle de patches ne contient aucun patch chargeable.",
                    "PatchLoader.Dex returned no patches for ${bundle.absolutePath}"
                )
            }
            val patchNames = patches.mapNotNull { it.name }.toSet()
            val missing = selected.filterNot { it in patchNames }
            if (missing.isNotEmpty()) {
                throw PatchRunException(
                    "Patch introuvable dans le bundle : ${missing.joinToString(", ")}.",
                    "Bundle patches: ${patchNames.sorted()}"
                )
            }
            val selectedPatches = patches.filter { it.name in selected }.toSet()
            append("Patches sélectionnés : ${selectedPatches.mapNotNull { it.name }.sorted().joinToString(", ")}")

            patcher = Patcher(
                PatcherConfig(
                    apkFile = input,
                    temporaryFilesPath = File(runDir, "temp").apply { mkdirs() },
                    fileWorkspacePath = File(runDir, "workspace").apply { mkdirs() },
                    progress = PatcherProgress { task, done, total ->
                        val percent = if (total > 0) min(100, (done * 100 / total).toInt()) else 0
                        append("$task $percent % ($done/$total)")
                    }
                )
            )
            patcher += selectedPatches
            patcher { patch -> append("Exécution : ${patch.name ?: "patch sans nom"}") }
                .collect { result ->
                    val failure = result.exception
                    if (failure != null) {
                        throw PatchRunException(
                            "Le patch « ${result.patch.name ?: "sans nom"} » a échoué : ${failure.message}",
                            failure.stackTraceToString(),
                            failure
                        )
                    }
                    append("✓ ${result.patch.name ?: "patch sans nom"}")
                }
            onStage(
                PatchStage.APPLY,
                StageStatus.DONE,
                "${selectedPatches.size} patch${if (selectedPatches.size > 1) "s" else ""} appliqué${if (selectedPatches.size > 1) "s" else ""}"
            )

            onStage(PatchStage.REBUILD, StageStatus.RUNNING, "Reconstruction de l'APK")
            val unsigned = File(runDir, "patched-unsigned.apk")
            patcher.writeApk(unsigned)
            if (!unsigned.exists() || unsigned.length() == 0L) {
                throw PatchRunException("La reconstruction n'a produit aucun APK.", "writeApk produced no file")
            }
            onStage(PatchStage.REBUILD, StageStatus.DONE, "${unsigned.length()} octets avant signature")

            onStage(PatchStage.SIGN, StageStatus.RUNNING, "Clé TuneFy")
            val signed = File(runDir, "patched-signed.apk")
            val signingDetails = TuneFySigningKey.details(context)
            ApkUtils.signApk(unsigned, signed, "TuneFy", signingDetails, 30)
            onStage(PatchStage.SIGN, StageStatus.DONE, "Signature TuneFy appliquée")

            onStage(PatchStage.VERIFY, StageStatus.RUNNING, "Vérification de la signature et du manifeste")
            val certificates = ApkUtils.verifiedSigningCertificates(signed, Build.VERSION.SDK_INT)
            val certList = certificates.orEmpty()
            if (certList.isEmpty()) {
                throw PatchRunException(
                    "L'APK signé ne passe pas la vérification de signature Android.",
                    "ApkUtils.verifiedSigningCertificates returned ${certificates?.size ?: "null"}"
                )
            }
            val certificateSha256 = MessageDigest.getInstance("SHA-256")
                .digest(certList.first().encoded)
                .joinToString("") { "%02x".format(it) }
            val signedDetails = ApkInspector.inspect(context, signed)
            if (signedDetails?.packageId != "com.spotify.music") {
                throw PatchRunException(
                    "L'APK généré n'a pas le package Spotify attendu.",
                    "package=${signedDetails?.packageId}"
                )
            }
            if (analysis.versionName != null && signedDetails.versionName != analysis.versionName) {
                throw PatchRunException(
                    "La version Spotify de l'APK généré ne correspond plus à l'entrée.",
                    "expected=${analysis.versionName} actual=${signedDetails.versionName}"
                )
            }
            val sha256 = FileHasher.sha256(signed)
            append("Vérification OK · certificat $certificateSha256 · SHA-256 $sha256")
            onStage(PatchStage.VERIFY, StageStatus.DONE, "Signature et intégrité vérifiées")

            val fileName = buildFileName(analysis.versionName, selected.size)
            val artifact = store.persistedArtifact(
                signed,
                GeneratedPatch(
                    id = runId,
                    fileName = fileName,
                    path = signed.absolutePath,
                    spotifyVersion = analysis.versionName.orEmpty(),
                    architecture = analysis.abis.joinToString(", "),
                    patches = selected.toList().sorted(),
                    sizeBytes = signed.length(),
                    sha256 = sha256,
                    signatureSha256 = certificateSha256,
                    createdAt = System.currentTimeMillis(),
                    sourceName = analysis.displayName,
                    verified = true
                )
            )
            store.saveGenerated(artifact)
            val logText = synchronized(logLock) { log.toString() }
            val logFile = store.writeLog(runId, logText)
            store.record(
                PatchHistoryRecord(
                    id = runId,
                    createdAt = artifact.createdAt,
                    fileName = artifact.fileName,
                    spotifyVersion = artifact.spotifyVersion,
                    patches = artifact.patches,
                    success = true,
                    detail = "Signature ${artifact.signatureSha256?.take(16)}… · ${artifact.architecture}",
                    sha256 = artifact.sha256
                )
            )
            runCatching { unsigned.delete() }
            runCatching { signed.copyTo(artifact.file(), overwrite = true).also { signed.delete() } }
            PatchRunResult(artifact, logText, logFile.absolutePath)
        } catch (error: Throwable) {
            val message = (error as? PatchRunException)?.userMessage ?: (error.message ?: "Échec du patch")
            val current = when {
                error is PatchRunException -> currentStage(error.userMessage)
                else -> PatchStage.PREPARE
            }
            onStage(current, StageStatus.FAILED, message)
            append("ÉCHEC : $message")
            if (error !is PatchRunException) append(error.stackTraceToString())
            val logText = synchronized(logLock) { log.toString() }
            runCatching { store.writeLog(runId, logText) }
            runCatching {
                store.record(
                    PatchHistoryRecord(
                        id = runId,
                        createdAt = System.currentTimeMillis(),
                        fileName = analysis.displayName,
                        spotifyVersion = analysis.versionName.orEmpty(),
                        patches = selected.toList().sorted(),
                        success = false,
                        detail = message
                    )
                )
            }
            throw if (error is PatchRunException) error else PatchRunException(message, error.stackTraceToString(), error)
        } finally {
            runCatching { patcher?.close() }
            rootLogger.removeHandler(handler)
            runCatching { runDir.deleteRecursively() }
        }
    }

    private fun currentStage(message: String): PatchStage = when {
        message.contains("patch", ignoreCase = true) -> PatchStage.APPLY
        message.contains("signature", ignoreCase = true) -> PatchStage.SIGN
        message.contains("reconstruction", ignoreCase = true) -> PatchStage.REBUILD
        else -> PatchStage.PREPARE
    }

    private fun prepareInput(analysis: ApkAnalysis, runDir: File, log: (String) -> Unit): File {
        val modules = analysis.modules.filter { it.exists() }
        if (modules.isEmpty()) {
            throw PatchRunException("L'APK d'entrée a été supprimé du cache. Réimportez-le.")
        }
        val input = File(runDir, "input.apk")
        if (modules.size > 1) {
            return SplitMerger.merge(modules, input, runDir, log)
        }
        modules.first().copyTo(input, overwrite = true)
        return input
    }

    private fun expand(imported: File): ExpandedPackage {
        if (!ApkInspector.isPackageArchive(imported)) {
            return ExpandedPackage(listOf(imported), imported, extracted = false)
        }
        return ApkInspector.expand(context, imported)
            ?: throw PatchRunException(
                "Archive APK illisible ou ne contenant aucune APK.",
                "ApkInspector.expand returned null for ${imported.absolutePath}"
            )
    }

    private fun copyImport(uri: Uri, name: String?): File {
        val directory = File(context.filesDir, "patch/imports").apply { mkdirs() }
        val safeName = (name ?: "spotify.apk")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "spotify.apk" }
        val target = File(directory, "${System.currentTimeMillis()}-$safeName")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("openInputStream returned null")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (error: IOException) {
            throw PatchRunException(
                "Impossible de lire le fichier sélectionné.",
                error.stackTraceToString(),
                error
            )
        }
        if (target.length() == 0L) {
            target.delete()
            throw PatchRunException("Le fichier sélectionné est vide.")
        }
        return target
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun buildFileName(version: String?, patchCount: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val normalized = (version ?: "spotify").replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "TuneFy-$normalized-${patchCount}p-$stamp.apk"
    }
}
