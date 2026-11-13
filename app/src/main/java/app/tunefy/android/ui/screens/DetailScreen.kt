package app.tunefy.android.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.data.model.DownloadType
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.StoreUi
import app.tunefy.android.ui.components.AnimatedCheck
import app.tunefy.android.ui.components.AppIcon
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.EmptyState
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.components.ThemePreview
import app.tunefy.android.ui.components.icon
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun DetailScreen(
    viewModel: AppViewModel,
    itemId: String,
    onOpenItem: (String) -> Unit,
    onOpenPatcher: () -> Unit
) {
    val index by viewModel.index.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val installed by viewModel.installedItems.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val patchBundle by viewModel.patchBundle.collectAsStateWithLifecycle()

    val item = index.apps.firstOrNull { it.id == itemId }

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                title = "Fiche introuvable",
                message = "Cet élément n'existe pas dans le dépôt chargé."
            )
        }
        return
    }

    val libraryEntry = installed.firstOrNull { it.itemId == item.id }
    val installedVersion = libraryEntry?.version
        ?: if (item.packageId == "com.spotify.music" && spotify.installed) spotify.versionName else null
    val task = StoreUi.activeTask(downloads, item.id)
    val compatibility = StoreUi.compatibility(item, spotify, patchBundle)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailHeader(item = item, installedVersion = installedVersion)
        }

        item {
            DetailActions(
                item = item,
                installedVersion = installedVersion,
                task = task,
                onInstall = { viewModel.install(item) },
                onUninstall = { viewModel.uninstall(item) },
                onOpenSource = { viewModel.openExternal(item.sourceUrl) },
                onShare = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${item.name} v${item.version} — ${item.summary}\n${Constants.PROJECT_URL}"
                        )
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(share, "Partager ${item.name}"))
                    }
                }
            )
        }

        if (task != null && (task.status.isActive || task.status == DownloadStatus.COMPLETED)) {
            item {
                DetailDownloadCard(
                    task = task,
                    onPause = { viewModel.pauseTask(task.id) },
                    onResume = { viewModel.resumeTask(task.id) },
                    onCancel = { viewModel.cancelTask(task.id) },
                    onInstall = { viewModel.installTask(task.id) },
                    onRetry = { viewModel.retryTask(task.id) },
                    onRemove = { viewModel.removeTask(task.id) }
                )
            }
        }

        if (item.previewColors.isNotEmpty()) {
            item {
                ThemePreview(colors = item.previewColors, height = 170.dp)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Description")
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.take(4).forEach { tag -> InfoPill("#$tag") }
                    }
                }
            }
        }

        if (item.warnings.isNotEmpty()) {
            item {
                WarningCard(warnings = item.warnings)
            }
        }

        item {
            InfoCard(
                item = item,
                installedVersion = installedVersion,
                onCopy = { clipboard.setText(AnnotatedString(it)) }
            )
        }

        item {
            CompatibilityCard(
                item = item,
                compatibility = compatibility,
                spotifyVersion = spotify.versionName,
                onOpenPatcher = onOpenPatcher
            )
        }

        item {
            SectionHeader(
                title = "Historique des versions",
                subtitle = "Dernières versions publiées dans le dépôt"
            )
        }
        val versions = item.versions.ifEmpty {
            listOf(
                app.tunefy.android.data.model.VersionEntry(
                    version = item.version,
                    versionCode = item.versionCode,
                    date = item.publishedAt,
                    sizeBytes = item.sizeBytes,
                    notes = "Version actuelle du dépôt.",
                    supportedSpotifyVersions = item.supportedSpotifyVersions
                )
            )
        }
        items(versions, key = { "version-${it.version}" }) { version ->
            VersionRow(version = version, isCurrent = version.version == item.version)
        }

        item {
            SecurityDetailCard()
        }

        item { BottomContentPadding() }
    }
}

@Composable
private fun DetailHeader(item: CatalogItem, installedVersion: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AppIcon(item = item, size = 88.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.developer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (installedVersion != null) {
                        androidx.compose.material3.Surface(
                            color = TuneFyColors.Success.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = TuneFyColors.Success,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Installé v$installedVersion",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TuneFyColors.Success
                                )
                            }
                        }
                    } else {
                        InfoPill("v${item.version}")
                    }
                }
            }
        }
        Text(
            text = item.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoPill(item.categoryEnum.label, icon = item.categoryEnum.icon())
            InfoPill(item.architecture)
            if (item.spicetifyCompatible) InfoPill("Spicetify", icon = Icons.Rounded.Extension)
            if (item.spicetifyCompatible) InfoPill("Patches intégrés", icon = Icons.Rounded.Memory)
        }
    }
}

@Composable
private fun DetailActions(
    item: CatalogItem,
    installedVersion: String?,
    task: DownloadTask?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSource: () -> Unit,
    onShare: () -> Unit
) {
    val busy = task?.status?.isActive == true
    val primaryLabel = when {
        busy -> "Téléchargement en cours"
        item.downloadType == DownloadType.EXTERNAL -> "Ouvrir la source officielle"
        item.downloadType == DownloadType.RECIPE -> "Configurer dans APK Patcher"
        installedVersion != null && installedVersion != item.version -> "Mettre à jour"
        installedVersion != null -> "Réinstaller"
        else -> "Installer"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onInstall,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Icon(
                if (item.downloadType == DownloadType.EXTERNAL) Icons.Rounded.OpenInNew else Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(primaryLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installedVersion != null) {
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Désinstaller")
                }
            }
            if (item.sourceUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenSource,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Source")
                }
            }
            TextButton(onClick = onShare, shape = RoundedCornerShape(50)) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Partager")
            }
        }
    }
}

@Composable
private fun DetailDownloadCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(visible = task.status == DownloadStatus.COMPLETED) {
                    AnimatedCheck(visible = true, size = 30.dp)
                }
            }
            when (task.status) {
                DownloadStatus.COMPLETED -> {
                    Text(
                        "Vérifié · ${Formatters.bytes(task.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TuneFyColors.Success
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                            Text("Installer")
                        }
                        OutlinedButton(onClick = onRemove, shape = RoundedCornerShape(50)) {
                            Text("Retirer")
                        }
                    }
                }
                DownloadStatus.FAILED -> {
                    Text(
                        task.error ?: "Échec du téléchargement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                            Text("Réessayer")
                        }
                        OutlinedButton(onClick = onRemove, shape = RoundedCornerShape(50)) { Text("Retirer") }
                    }
                }
                else -> {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Formatters.percent(task.progress)} · ${Formatters.bytes(task.downloadedBytes)} / ${Formatters.bytes(task.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${Formatters.speed(task.speedBytesPerSecond)} · ${Formatters.eta(task.etaSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = if (task.status == DownloadStatus.PAUSED) onResume else onPause,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                if (task.status == DownloadStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (task.status == DownloadStatus.PAUSED) "Reprendre" else "Pause")
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Annuler")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TuneFyColors.Warning.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, TuneFyColors.Warning.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = TuneFyColors.Warning,
                    modifier = Modifier.size(18.dp)
                )
                Text("À lire avant d'installer", style = MaterialTheme.typography.titleSmall)
            }
            warnings.forEach { warning ->
                Text(
                    "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard(item: CatalogItem, installedVersion: String?, onCopy: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            MetaRow("Version", item.version)
            if (item.versionCode > 0) MetaRow("Code de version", item.versionCode.toString())
            MetaRow("Architecture", item.architecture)
            MetaRow("Taille", Formatters.bytes(item.sizeBytes))
            MetaRow("Android minimum", Formatters.androidVersion(item.minSdk))
            MetaRow("Publication", Formatters.shortDate(item.publishedAt))
            MetaRow("Licence", item.license.ifBlank { "—" })
            if (item.packageId.isNotBlank()) MetaRow("Package", item.packageId, mono = true)
            if (installedVersion != null) MetaRow("Version installée", installedVersion)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            HashRow("Signature SHA-256", item.signatureSha256, onCopy)
            HashRow("Fichier SHA-256", item.sha256 ?: "Calculée après téléchargement", onCopy)
            if (item.permissions.isNotEmpty()) {
                MetaRow("Permissions", item.permissions.joinToString(", "))
            }
        }
    }
}

@Composable
private fun HashRow(label: String, value: String?, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(138.dp)
        )
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank() && !value.startsWith("Calculée")) {
            IconButton(onClick = { onCopy(value) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copier",
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CompatibilityCard(
    item: CatalogItem,
    compatibility: Compatibility,
    spotifyVersion: String?,
    onOpenPatcher: () -> Unit
) {
    val supported = item.supportedSpotifyVersions
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Compatibilité",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                CompatBadge(compatibility)
            }
            Text(
                text = buildString {
                    append(
                        if (spotifyVersion != null) "Spotify installé : $spotifyVersion. "
                        else "Spotify non détecté sur cet appareil. "
                    )
                    if (supported.isNotEmpty()) {
                        append("Versions prises en charge : ${supported.joinToString(", ")}.")
                    } else {
                        append("Aucune restriction de version déclarée.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (compatibility == Compatibility.INCOMPATIBLE) {
                Text(
                    "TuneFy ne propose pas l'activation tant que la version installée ne correspond pas. " +
                        "Installez la version Spotify prise en charge, puis revenez ici.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (item.morpheCompatible || item.categoryEnum == app.tunefy.android.data.model.Category.PATCHES) {
                TextButton(onClick = onOpenPatcher, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ouvrir APK Patcher")
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: app.tunefy.android.data.model.VersionEntry,
    isCurrent: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${version.version}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                if (isCurrent) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            "Actuelle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    Formatters.shortDate(version.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (version.sizeBytes > 0 || version.notes.isNotBlank()) {
                Text(
                    listOfNotNull(
                        Formatters.bytes(version.sizeBytes).takeIf { version.sizeBytes > 0 },
                        version.notes.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (version.supportedSpotifyVersions.isNotEmpty()) {
                Text(
                    "Spotify ${version.supportedSpotifyVersions.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SecurityDetailCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Installation vérifiée", style = MaterialTheme.typography.titleSmall)
                Text(
                    "TuneFy calcule l'empreinte SHA-256 avant installation, vérifie la cohérence " +
                        "des signatures des APK et refuse les fichiers modifiés. L'installation passe " +
                        "par l'installateur Android, jamais par une copie directe dans les données Spotify. " +
                        "Aucun contournement de DRM ou d'abonnement n'est proposé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
