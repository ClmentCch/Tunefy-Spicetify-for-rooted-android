package app.tunefy.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun CompactItemCard(
    item: CatalogItem,
    compatibility: Compatibility,
    installedVersion: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 184.dp
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier.width(width)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AppIcon(item = item, size = 46.dp)
                if (installedVersion != null) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Installé",
                        tint = TuneFyColors.Success,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "v${item.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (compatibility != Compatibility.UNKNOWN) CompatBadge(compatibility, compact = true)
            }
        }
    }
}

@Composable
fun ThemeCard(
    item: CatalogItem,
    compatibility: Compatibility,
    installedVersion: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column {
            ThemePreview(
                colors = item.previewColors,
                modifier = Modifier.padding(8.dp),
                height = 104.dp
            )
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
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
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = item.developer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (compatibility != Compatibility.UNKNOWN) {
                    Spacer(Modifier.height(2.dp))
                    CompatBadge(compatibility)
                }
            }
        }
    }
}

@Composable
fun LibraryItemCard(
    item: CatalogItem,
    compatibility: Compatibility,
    installedVersion: String?,
    task: DownloadTask?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(item = item, size = 54.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "v${item.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (installedVersion != null) {
                            CompatBadge(Compatibility.COMPATIBLE)
                        } else if (compatibility != Compatibility.UNKNOWN) {
                            CompatBadge(compatibility)
                        }
                    }
                }
            }

            if (task != null && task.status.isActive) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${Formatters.percent(task.progress)} · ${Formatters.speed(task.speedBytesPerSecond)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { if (task.status == DownloadStatus.PAUSED) onResume() else onPause() }) {
                            Icon(
                                if (task.status == DownloadStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (task.status == DownloadStatus.PAUSED) "Reprendre" else "Pause"
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Annuler")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (installedVersion != null) {
                        TextButton(onClick = onUninstall) { Text("Désinstaller") }
                        Surface(
                            onClick = onOpen,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Ouvrir",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = onOpen) { Text("Détails") }
                        Surface(
                            onClick = onInstall,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Installer",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledBadge(text: String = "Installé", modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = TuneFyColors.Success.copy(alpha = 0.14f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = TuneFyColors.Success,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun HorizontalCardSpacer() = Spacer(Modifier.width(12.dp))
