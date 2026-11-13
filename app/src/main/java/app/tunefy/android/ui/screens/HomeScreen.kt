package app.tunefy.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.PatchBundle
import app.tunefy.android.data.model.RepositoryStatus
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.StoreUi
import app.tunefy.android.ui.components.AppIcon
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.CompactItemCard
import app.tunefy.android.ui.components.ErrorBanner
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
    onOpenCompatibility: () -> Unit,
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
    val updates = items.sortedByDescending { it.publishedAt }.take(4)
    val featured = spotifyItems.firstOrNull { it.featured }
        ?: spotifyItems.firstOrNull { it.downloadType == "external" }
        ?: spotifyItems.firstOrNull()
    val activeDownloads = downloads.count { it.status.isActive }

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

    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        background,
                        background
                    ),
                    endY = 900f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HomeIntro() }

            item { SearchLauncher(onClick = onOpenSearch) }

            if (featured != null) {
                item {
                    SpotifyHero(
                        item = featured,
                        installedVersion = installedVersion(featured),
                        patchCount = patchBundle?.patches?.size ?: 0,
                        onOpen = { onOpenItem(featured.id) },
                        onInstall = { viewModel.install(featured, installAfter = false) }
                    )
                }
            }

            item {
                QuickActions(
                    patchCount = patchBundle?.patches?.size ?: 0,
                    activeDownloads = activeDownloads,
                    onOpenPatches = onOpenPatches,
                    onOpenDownloads = onOpenDownloads,
                    onOpenCompatibility = onOpenCompatibility
                )
            }

            item { RepositoryStrip(status = status, onRefresh = viewModel::refreshCatalog) }

            if (status.error != null) {
                item {
                    ErrorBanner(message = status.error!!, onRetry = viewModel::refreshCatalog)
                }
            }

            if (spotifyItems.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(
                            title = "Spotify",
                            subtitle = "La bonne base pour vos patches",
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
                                    onClick = { onOpenItem(item.id) },
                                    width = 196.dp
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
                            title = "Extensions",
                            subtitle = "Des fonctions en plus, sans friction",
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
                            title = "Thèmes",
                            subtitle = "Changez l'ambiance en un geste",
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
                        title = "À découvrir",
                        subtitle = "Les derniers ajouts du dépôt",
                        actionLabel = "Tout le catalogue",
                        onAction = { onOpenCatalog(Category.ALL) }
                    )
                }
                item {
                    UpdateList(
                        updates = updates,
                        spotify = spotify,
                        patchBundle = patchBundle,
                        onOpenItem = onOpenItem
                    )
                }
            }

            item { TrustFooter() }
            item { BottomContentPadding() }
        }
    }
}

@Composable
private fun HomeIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                "PERSONNALISATION",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "Votre Spotify,\nà votre façon.",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Extensions, thèmes et patches vérifiés pour transformer votre écoute.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchLauncher(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
            Text(
                "Rechercher une extension, un thème…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SpotifyHero(
    item: CatalogItem,
    installedVersion: String?,
    patchCount: Int,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1D4E7A),
                        TuneFyColors.DeepBlue,
                        Color(0xFF102B46)
                    ),
                    start = Offset.Zero,
                    end = Offset(1000f, 760f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = (-54).dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.055f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 28.dp, y = 34.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(TuneFyColors.PastelBlue.copy(alpha = 0.08f))
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "À LA UNE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                    color = Color.White.copy(alpha = 0.62f)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (installedVersion != null) {
                        TuneFyColors.Success.copy(alpha = 0.22f)
                    } else {
                        Color.White.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (installedVersion != null) TuneFyColors.Success else TuneFyColors.PastelBlue)
                        )
                        Text(
                            if (installedVersion != null) "Installé" else "Prêt à patcher",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppIcon(item = item, size = 62.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(
                        if (installedVersion != null) "Installé · v$installedVersion"
                        else "${item.developer.ifBlank { "Spotify officiel" }} · v${item.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("Version", item.version, Modifier.weight(1f))
                HeroStat("Taille", Formatters.bytes(item.sizeBytes), Modifier.weight(1f))
                HeroStat("Patches", patchCount.toString(), Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    onClick = onOpen,
                    color = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Découvrir",
                            style = MaterialTheme.typography.labelLarge,
                            color = TuneFyColors.DeepBlue
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = TuneFyColors.DeepBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    onClick = onInstall,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (installedVersion != null) "Mettre à jour" else "Installer",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                color = Color.White.copy(alpha = 0.54f)
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActions(
    patchCount: Int,
    activeDownloads: Int,
    onOpenPatches: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenCompatibility: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        QuickActionTile(
            title = "Patcher",
            subtitle = "$patchCount patches",
            icon = Icons.Rounded.Memory,
            onClick = onOpenPatches,
            emphasized = true,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            title = "Téléch.",
            subtitle = if (activeDownloads > 0) "$activeDownloads en cours" else "Mes fichiers",
            icon = Icons.Rounded.Download,
            onClick = onOpenDownloads,
            badge = activeDownloads,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            title = "Vérifier",
            subtitle = "Spotify & ABI",
            icon = Icons.Rounded.Verified,
            onClick = onOpenCompatibility,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    badge: Int = 0
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(19.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp).size(19.dp)
                    )
                }
                if (badge > 0) {
                    Badge(modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp)) {
                        Text(badge.toString())
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RepositoryStrip(status: RepositoryStatus, onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (status.error != null) TuneFyColors.Warning else TuneFyColors.Success)
            )
            Text(
                if (status.error != null) "Dépôt indisponible" else "Dépôt à jour",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "· ${status.itemCount} éléments" +
                    if (status.updated.isNotBlank()) " · ${Formatters.shortDate(status.updated)}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (status.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(8.dp).size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Actualiser le dépôt",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateList(
    updates: List<CatalogItem>,
    spotify: SpotifyStatus,
    patchBundle: PatchBundle?,
    onOpenItem: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column {
            updates.forEachIndexed { index, item ->
                Surface(
                    onClick = { onOpenItem(item.id) },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIcon(item = item, size = 44.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "v${item.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val compatibility = StoreUi.compatibility(item, spotify, patchBundle)
                                if (compatibility != Compatibility.UNKNOWN) {
                                    CompatBadge(compatibility, compact = true)
                                }
                            }
                        }
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (index != updates.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 69.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustFooter() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Contenus vérifiés", style = MaterialTheme.typography.titleSmall)
                Text(
                    "SHA-256, signatures contrôlées et données Spotify intactes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "GPL-3.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
