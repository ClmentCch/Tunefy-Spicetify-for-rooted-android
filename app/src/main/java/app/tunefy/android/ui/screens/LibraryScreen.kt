package app.tunefy.android.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.StoreUi
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.EmptyState
import app.tunefy.android.ui.components.LibraryItemCard
import app.tunefy.android.ui.components.ThemePreview
import app.tunefy.android.ui.components.icon
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    category: Category,
    onOpenItem: (String) -> Unit
) {
    val index by viewModel.index.collectAsStateWithLifecycle()
    val installed by viewModel.installedItems.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val patchBundle by viewModel.patchBundle.collectAsStateWithLifecycle()

    val items = index.apps.filter { it.categoryEnum == category }

    fun installedVersion(item: CatalogItem): String? =
        installed.firstOrNull { it.itemId == item.id }?.version

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryHeader(
            category = category,
            count = items.size,
            installedCount = items.count { installedVersion(it) != null }
        )

        if (items.isEmpty()) {
            EmptyState(
                icon = category.icon(),
                title = "Bibliothèque vide",
                message = "Le dépôt ne contient aucune ressource dans cette catégorie pour le moment."
            )
            return@Column
        }

        if (category == Category.THEMES) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(items, key = { it.id }) { item ->
                    ThemeLibraryCard(
                        item = item,
                        compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                        installedVersion = installedVersion(item),
                        task = StoreUi.activeTask(downloads, item.id),
                        onOpen = { onOpenItem(item.id) },
                        onInstall = { viewModel.install(item) },
                        onUninstall = { viewModel.uninstall(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    LibraryItemCard(
                        item = item,
                        compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                        installedVersion = installedVersion(item),
                        task = StoreUi.activeTask(downloads, item.id),
                        onOpen = { onOpenItem(item.id) },
                        onInstall = { viewModel.install(item) },
                        onUninstall = { viewModel.uninstall(item) },
                        onCancel = { StoreUi.activeTask(downloads, item.id)?.let { viewModel.cancelTask(it.id) } },
                        onPause = { StoreUi.activeTask(downloads, item.id)?.let { viewModel.pauseTask(it.id) } },
                        onResume = { StoreUi.activeTask(downloads, item.id)?.let { viewModel.resumeTask(it.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(category: Category, count: Int, installedCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(TuneFyColors.DeepBlue, TuneFyColors.Blue),
                    start = Offset.Zero,
                    end = Offset(700f, 400f)
                )
            )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Icon(
                    category.icon(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (category == Category.THEMES) "Thèmes Spotify" else "Extensions Spicetify",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = StoreUi.categoryDescription(category),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$count disponibles · $installedCount installés",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ThemeLibraryCard(
    item: CatalogItem,
    compatibility: Compatibility,
    installedVersion: String?,
    task: DownloadTask?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            ThemePreview(colors = item.previewColors, height = 96.dp)
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (installedVersion != null) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Installé",
                            tint = TuneFyColors.Success,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    "${item.developer} · v${item.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task != null && task.status.isActive) {
                    Text(
                        "${Formatters.percent(task.progress)} · ${Formatters.speed(task.speedBytesPerSecond)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (compatibility != Compatibility.UNKNOWN) {
                            CompatBadge(compatibility)
                        } else {
                            Spacer(Modifier.height(1.dp))
                        }
                        TextButton(
                            onClick = { if (installedVersion != null) onUninstall() else onInstall() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                if (installedVersion != null) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                if (installedVersion != null) "Retirer" else "Installer",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
