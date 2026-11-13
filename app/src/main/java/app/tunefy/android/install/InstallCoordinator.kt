package app.tunefy.android.install

import android.content.Context
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.HistoryEntry
import app.tunefy.android.data.model.InstalledItem
import app.tunefy.android.data.repo.LibraryRepository
import app.tunefy.android.util.FileHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

sealed interface InstallStart {
    data class SessionStarted(val sessionId: Int) : InstallStart
    data class LibraryInstalled(val itemId: String) : InstallStart
    data class Failure(val message: String) : InstallStart
    data object InstallPermissionRequired : InstallStart
}

/**
 * Decides how a downloaded artifact is installed:
 * - APK / split bundles go through Android's PackageInstaller;
 * - themes, extensions and patch bundles are validated then stored in TuneFy's
 *   own library folder, where they can be removed cleanly.
 */
class InstallCoordinator(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val apkInstaller: ApkInstaller
) {

    suspend fun install(file: File, item: CatalogItem?): InstallStart = withContext(Dispatchers.IO) {
        val isApk = file.extension.equals("apk", ignoreCase = true)
        val isArchive = ApkInspector.isPackageArchive(file)
        if (!isApk && !isArchive) return@withContext installLibraryPackage(file, item)

        if (!apkInstaller.canInstallPackages()) return@withContext InstallStart.InstallPermissionRequired

        val expanded = ApkInspector.expand(context, file)
            ?: return@withContext InstallStart.Failure("Impossible de lire l'APK ou l'archive de splits.")
        if (expanded.apks.isEmpty()) {
            return@withContext InstallStart.Failure("Aucun APK valide dans cette archive.")
        }

        val details = expanded.apks.mapNotNull { ApkInspector.inspect(context, it) }
        if (details.isEmpty()) return@withContext InstallStart.Failure("Métadonnées APK illisibles.")

        val signatures = details.mapNotNull { it.signatureSha256 }.distinct()
        if (signatures.size > 1) {
            return@withContext InstallStart.Failure(
                "Les APK de ce bundle portent des signatures différentes. Installation refusée."
            )
        }
        val signature = signatures.firstOrNull()
        val declaredSignature = item?.signatureSha256?.lowercase()
        if (!declaredSignature.isNullOrBlank() && !signature.equals(declaredSignature, ignoreCase = true)) {
            return@withContext InstallStart.Failure(
                "La signature de l'APK ne correspond pas à celle publiée dans le dépôt TuneFy."
            )
        }
        val primary = details.firstOrNull { item != null && it.packageId == item.packageId }
            ?: details.firstOrNull { it.packageId != null }
            ?: return@withContext InstallStart.Failure("Package Android introuvable dans le fichier.")

        val expectedPackage = item?.packageId?.takeIf { it.isNotBlank() }
        if (expectedPackage != null && primary.packageId != expectedPackage) {
            return@withContext InstallStart.Failure(
                "Ce fichier contient ${primary.packageId}, pas $expectedPackage."
            )
        }
        val installedSignature = primary.packageId?.let {
            ApkInspector.installedSignature(context, it)
        }
        if (installedSignature != null && signature != null &&
            !installedSignature.equals(signature, ignoreCase = true)
        ) {
            return@withContext InstallStart.Failure(
                "Une version de ${primary.packageId} est déjà installée avec une autre signature. " +
                    "Désinstallez-la d'abord (Android interdit de remplacer une signature)."
            )
        }

        val archiveSha = FileHasher.sha256(file)
        val result = apkInstaller.startSession(
            apks = expanded.apks,
            packageName = primary.packageId,
            item = item,
            archiveSha256 = archiveSha,
            signatureSha256 = signature
        )
        result.fold(
            onSuccess = { sessionId ->
                libraryRepository.recordHistory(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        itemId = item?.id.orEmpty(),
                        itemName = item?.name ?: file.name,
                        version = item?.version.orEmpty(),
                        action = "Téléchargement vérifié",
                        success = true,
                        detail = "SHA-256 ${archiveSha.take(16)}… — confirmation Android demandée",
                        sha256 = archiveSha
                    )
                )
                InstallStart.SessionStarted(sessionId)
            },
            onFailure = { error ->
                InstallStart.Failure(error.message ?: "Android a refusé d'ouvrir la session d'installation.")
            }
        )
    }

    /**
     * Stores a verified TuneFy package (theme, extension, patch bundle) in the
     * app-private library. No executable code runs from these packages.
     */
    private suspend fun installLibraryPackage(file: File, item: CatalogItem?): InstallStart {
        val itemId = item?.id ?: file.nameWithoutExtension
        val json = Json { ignoreUnknownKeys = true }
        if (file.extension.equals("json", ignoreCase = true)) {
            val parsed = runCatching { json.parseToJsonElement(file.readText()) }.getOrNull()
                ?: return InstallStart.Failure("Package JSON invalide ou corrompu.")
            if (parsed !is kotlinx.serialization.json.JsonObject) {
                return InstallStart.Failure("Format de package inattendu.")
            }
        }
        val directory = File(context.filesDir, "library/$itemId").apply { mkdirs() }
        val destination = File(directory, file.name)
        if (file.absolutePath != destination.absolutePath) {
            file.copyTo(destination, overwrite = true)
        }
        val sha = FileHasher.sha256(destination)
        libraryRepository.markInstalled(
            InstalledItem(
                itemId = itemId,
                name = item?.name ?: file.name,
                version = item?.version.orEmpty(),
                packageId = item?.packageId.orEmpty(),
                kind = item?.category ?: "package",
                filePath = destination.absolutePath,
                sha256 = sha
            )
        )
        libraryRepository.recordHistory(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                itemName = item?.name ?: file.name,
                version = item?.version.orEmpty(),
                action = "Installation",
                success = true,
                detail = "Ajouté à la bibliothèque TuneFy",
                sha256 = sha
            )
        )
        return InstallStart.LibraryInstalled(itemId)
    }

    suspend fun uninstallLibraryItem(itemId: String, itemName: String, version: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "library/$itemId")
            val deleted = if (directory.exists()) directory.deleteRecursively() else true
            libraryRepository.markUninstalled(itemId)
            libraryRepository.recordHistory(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    itemId = itemId,
                    itemName = itemName,
                    version = version,
                    action = "Désinstallation",
                    success = deleted
                )
            )
            deleted
        }
}
