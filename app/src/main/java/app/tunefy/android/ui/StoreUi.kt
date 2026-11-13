package app.tunefy.android.ui

import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.data.model.PatchBundle
import app.tunefy.android.data.model.SpotifyStatus

object StoreUi {

    fun compatibility(
        item: CatalogItem,
        spotify: SpotifyStatus,
        patchBundle: PatchBundle?
    ): Compatibility {
        val supported = when {
            item.categoryEnum == Category.PATCHES ->
                patchBundle?.patches?.flatMap { it.supportedVersions }?.distinct().orEmpty()
                    .ifEmpty { item.supportedSpotifyVersions }
            item.supportedSpotifyVersions.isNotEmpty() -> item.supportedSpotifyVersions
            else -> emptyList()
        }
        if (supported.isEmpty()) return Compatibility.UNKNOWN
        if (!spotify.installed) return Compatibility.UNKNOWN
        val installed = spotify.versionName.orEmpty()
        return when {
            installed in supported ->
                if (item.warnings.isNotEmpty() || item.notes.contains("expérimental", ignoreCase = true)) {
                    Compatibility.EXPERIMENTAL
                } else {
                    Compatibility.COMPATIBLE
                }
            else -> Compatibility.INCOMPATIBLE
        }
    }

    fun supportsMorphe(item: CatalogItem): Boolean =
        item.morpheCompatible || item.categoryEnum == Category.PATCHES ||
            item.categoryEnum == Category.EXTENSIONS

    fun activeTask(tasks: List<DownloadTask>, itemId: String): DownloadTask? =
        tasks.firstOrNull {
            it.itemId == itemId && (it.status.isActive || it.status == DownloadStatus.COMPLETED)
        }

    fun categoryDescription(category: Category): String = when (category) {
        Category.EXTENSIONS ->
            "Extensions Spicetify validées pour l'application Spotify sur Android. " +
                "Elles s'activent dans Spotify après application des patches compatibles."
        Category.THEMES ->
            "Thèmes de couleurs et palettes prêtes à appliquer à Spotify via les patches Spicetify."
        Category.PATCHES ->
            "Patches publiés par Spicetify, appliqués par le moteur TuneFy après vérification de compatibilité."
        Category.TOOLS ->
            "Outils de personnalisation, de patch et de gestion du dépôt TuneFy."
        Category.SPOTIFY ->
            "Spotify officiel et versions préparées pour les patches communautaires."
        Category.ALL -> "Tout l'univers Spotify, vérifié par le dépôt TuneFy."
    }
}
