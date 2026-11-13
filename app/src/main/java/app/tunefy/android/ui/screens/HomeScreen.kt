package app.tunefy.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.RepositoryStatus
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.StoreUi
import app.tunefy.android.ui.components.AppIcon
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompactItemCard
import app.tunefy.android.ui.components.ErrorBanner
import app.tunefy.android.ui.components.ItemRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.components.ThemeCard
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit,
    onOpenCatalog: (Category) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPatches: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val index by viewModel.index.collectAsStateWithLifecycle()
    val status by viewModel.repositoryStatus.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val installed by viewModel.installedItems.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val patchBundle by viewModel.patchBundle.collectAsStateWithLifecycle()

    val items = index.apps
    val spotifyItems = items.filter { it.categoryEnum == Category.SPOTIFY }
    val extensions = items.filter { it.categoryEnum == Category.EXTENSIONS }
    val themes = items.filter { it.categoryEnum == Category.THEMES }
    val updates = items.sortedByDescending { it.publishedAt }.take(6)
    val featured = items.firstOrNull { it.featured }
        ?: spotifyItems.firstOrNull { it.downloadType == "external" }
        ?: spotifyItems.firstOrNull()

    fun installedVersion(item: CatalogItem): String? {
        installed.firstOrNull { it.itemId == item.id }?.let { return it.version }
        if (item.packageId == "com.spotify.music" && spotify.installed) return spotify.versionName
        return null
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingOrEmpty(isLoading = status.loading, message = status.error)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SearchLauncher(onClick = onOpenSearch)
        }

        if (featured != null) {
            item {
                SpotifyHero(
                    item = featured,
                    installedVersion = installedVersion(featured),
                    activeDownloads = downloads.count { it.status.isActive },
                    onOpen = { onOpenItem(featured.id) },
                    onInstall = { viewModel.install(featured, installAfter = false) },
                    onDownloads = onOpenDownloads
                )
            }
        }

        item {
            RepositoryStrip(status = status, onRefresh = viewModel::refreshCatalog)
        }

        if (status.error != null) {
            item {
                ErrorBanner(message = status.error!!, onRetry = viewModel::refreshCatalog)
            }
        }

        if (spotifyItems.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Dernières versions",
                        subtitle = "Spotify officiel et builds préparés pour les patches",
                        actionLabel = "Tout voir",
                        onAction = { onOpenCatalog(Category.SPOTIFY) }
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(spotifyItems, key = { it.id }) { item ->
                            CompactItemCard(
                                item = item,
                                compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                                installedVersion = installedVersion(item),
                                onClick = { onOpenItem(item.id) }
                            )
                        }
                    }
                }
            }
        }

        if (extensions.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Extensions populaires",
                        subtitle = "Ajoutez des fonctions à Spotify",
                        actionLabel = "Bibliothèque",
                        onAction = onOpenExtensions
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(extensions, key = { it.id }) { item ->
                            CompactItemCard(
                                item = item,
                                compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                                installedVersion = installedVersion(item),
                                onClick = { onOpenItem(item.id) }
                            )
                        }
                    }
                }
            }
        }

        if (themes.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Thèmes recommandés",
                        subtitle = "Palettes prêtes pour Spotify",
                        actionLabel = "Tous les thèmes",
                        onAction = onOpenThemes
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(themes, key = { it.id }) { item ->
                            ThemeCard(
                                item = item,
                                compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                                installedVersion = installedVersion(item),
                                onClick = { onOpenItem(item.id) },
                                modifier = Modifier.width(224.dp)
                            )
                        }
                    }
                }
            }
        }

        if (updates.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Dernières mises à jour",
                    subtitle = "Le plus récent du dépôt TuneFy"
                )
            }
            items(updates, key = { "update-${it.id}" }) { item ->
                ItemRow(
                    item = item,
                    installedVersion = installedVersion(item),
                    compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                    onClick = { onOpenItem(item.id) }
                )
            }
        }

        item {
            CompatibilityShortcut(
                patchCount = patchBundle?.patches?.size ?: 0,
                onClick = onOpenPatches
            )
        }

        item { SecurityCard() }

        item { BottomContentPadding() }
    }
}

@Composable
private fun LoadingOrEmpty(isLoading: Boolean, message: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        if (isLoading) {
            app.tunefy.android.ui.components.WaveLoadingIndicator(size = 84.dp)
            Text("Chargement du dépôt TuneFy…", style = MaterialTheme.typography.bodyMedium)
        } else {
            Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(40.dp))
            Text("Dépôt indisponible", style = MaterialTheme.typography.titleMedium)
            Text(
                message ?: "Vérifiez l'URL du dépôt dans les paramètres.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchLauncher(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Rechercher une extension, un thème…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    "Rechercher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SpotifyHero(
    item: CatalogItem,
    installedVersion: String?,
    activeDownloads: Int,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onDownloads: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TuneFyColors.DeepBlue, Color(0xFF1E3A5A), Color(0xFF13293D)),
                    start = Offset.Zero,
                    end = Offset(900f, 700f)
                )
            )
    ) {
        // Decorative rings, drawn without images so they stay crisp everywhere.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 18.dp)
                .size(118.dp)
                .clip(CircleShape)
                .background(TuneFyColors.Blue.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 26.dp, end = 42.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(TuneFyColors.PastelBlue.copy(alpha = 0.10f))
        )

        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(item = item, size = 58.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = if (installedVersion != null) "Installé · v$installedVersion"
                        else "${item.developer} · v${item.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "Officiel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip("${item.architecture.uppercase()} · ${Formatters.bytes(item.sizeBytes)}")
                HeroChip("Patches compatibles")
                if (activeDownloads > 0) HeroChip("$activeDownloads actif(s)")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpen,
                    color = Color.White,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Voir la fiche",
                            style = MaterialTheme.typography.labelLarge,
                            color = TuneFyColors.DeepBlue
                        )
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = TuneFyColors.DeepBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Surface(
                    onClick = onInstall,
                    color = TuneFyColors.SpotifyGreen,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (installedVersion != null) "Mettre à jour" else "Installer",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF06281A),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "Téléchargements vérifiés SHA-256 · installation officielle Android",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Téléch.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuneFyColors.PastelBlue,
                    modifier = Modifier.clickableNoRipple(onDownloads)
                )
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun HeroChip(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun RepositoryStrip(status: RepositoryStatus, onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (status.error != null) TuneFyColors.Warning else TuneFyColors.Success)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.name.ifBlank { "Dépôt TuneFy" },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${status.itemCount} éléments · ${status.source}" +
                        if (status.updated.isNotBlank()) " · ${Formatters.shortDate(status.updated)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (status.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser le dépôt")
                }
            }
        }
    }
}

@Composable
private fun CompatibilityShortcut(patchCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("$patchCount patches Spicetify disponibles", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Compatibilité vérifiée avec la version de Spotify installée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Badge { Text("Morphe") }
        }
    }
}

@Composable
private fun SecurityCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text("Sécurité et transparence", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Chaque fichier est vérifié par empreinte SHA-256 avant installation. " +
                    "Les APK passent par l'installateur officiel Android et les signatures sont contrôlées. " +
                    "TuneFy ne contourne ni DRM ni abonnement, et ne modifie jamais les données privées de Spotify.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Code source GPL-3.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableNoRipple {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(app.tunefy.android.core.Constants.PROJECT_URL)
                                )
                            )
                        }
                    }
                )
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Aucune publicité, aucun compte",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
