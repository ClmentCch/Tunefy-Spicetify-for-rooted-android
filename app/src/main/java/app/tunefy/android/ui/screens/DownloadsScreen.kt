package app.tunefy.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.DownloadStatus
import app.tunefy.android.data.model.DownloadTask
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.AnimatedCheck
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.EmptyState
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun DownloadsScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit
) {
    val tasks by viewModel.downloads.collectAsStateWithLifecycle()
    val root by viewModel.root.collectAsStateWithLifecycle()
    val importInProgress by viewModel.importInProgress.collectAsStateWithLifecycle()

    var showImportDialog by remember { mutableStateOf(false) }
    var useRoot by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importApk(uri, useRoot)
    }

    val active = tasks.filter { it.status.isActive }
    val finished = tasks.filter { it.status.isFinished }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
            title = { Text("Importer un APK") },
            text = {
                Text(
                    "Sélectionnez un APK, APKM ou XAPK téléchargé manuellement. " +
                        "TuneFy vérifie les signatures et l'empreinte SHA-256, puis passe par " +
                        "l'installateur Android officiel. Une installation root est possible si " +
                        "votre appareil est rooté et que vous l'autorisez explicitement."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    useRoot = false
                    showImportDialog = false
                    picker.launch(arrayOf("*/*"))
                }) { Text("Installation Android") }
            },
            dismissButton = {
                Row {
                    if (root.rooted) {
                        TextButton(onClick = {
                            useRoot = true
                            showImportDialog = false
                            picker.launch(arrayOf("*/*"))
                        }) { Text("Via root") }
                    }
                    TextButton(onClick = { showImportDialog = false }) { Text("Annuler") }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DownloadsHeader(
                active = active,
                finished = finished,
                importInProgress = importInProgress,
                onImport = { showImportDialog = true }
            )
        }

        if (tasks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Download,
                    title = "Aucun téléchargement",
                    message = "Les fichiers téléchargés depuis TuneFy apparaîtront ici, avec leur " +
                        "progression, leur vitesse et leur vérification SHA-256.",
                    actionLabel = "Importer un APK",
                    onAction = { showImportDialog = true }
                )
            }
        }

        if (active.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "En cours",
                    subtitle = "${active.size} téléchargement${if (active.size > 1) "s" else ""}"
                )
            }
            items(active, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onPause = { viewModel.pauseTask(task.id) },
                    onResume = { viewModel.resumeTask(task.id) },
                    onCancel = { viewModel.cancelTask(task.id) },
                    onInstall = { viewModel.installTask(task.id) },
                    onRetry = { viewModel.retryTask(task.id) },
                    onRemove = { viewModel.removeTask(task.id) },
                    onOpenItem = { onOpenItem(task.itemId) }
                )
            }
        }

        if (finished.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = "Terminés",
                        subtitle = "${finished.size} fichier${if (finished.size > 1) "s" else ""}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearFinishedDownloads) {
                        Text("Tout effacer")
                    }
                }
            }
            items(finished, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onPause = { viewModel.pauseTask(task.id) },
                    onResume = { viewModel.resumeTask(task.id) },
                    onCancel = { viewModel.cancelTask(task.id) },
                    onInstall = { viewModel.installTask(task.id) },
                    onRetry = { viewModel.retryTask(task.id) },
                    onRemove = { viewModel.removeTask(task.id) },
                    onOpenItem = { onOpenItem(task.itemId) }
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Un fichier dont le SHA-256 ne correspond pas est supprimé immédiatement. " +
                            "Les fichiers restent dans le stockage privé de TuneFy jusqu'à suppression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { BottomContentPadding() }
    }
}

@Composable
private fun DownloadsHeader(
    active: List<DownloadTask>,
    finished: List<DownloadTask>,
    importInProgress: Boolean,
    onImport: () -> Unit
) {
    val speed = active.sumOf { it.speedBytesPerSecond }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gestionnaire de téléchargements", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (active.isEmpty()) "${finished.size} fichiers terminés"
                        else "${active.size} actifs · ${Formatters.speed(speed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (importInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importer un APK")
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenItem: () -> Unit
) {
    Surface(
        onClick = onOpenItem,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = when (task.kind) {
                            "theme" -> Icons.Rounded.CheckCircle
                            "patch" -> Icons.Rounded.Shield
                            else -> Icons.Rounded.FileDownload
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.itemName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        task.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(task)
            }

            when (task.status) {
                DownloadStatus.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimatedCheck(visible = true, size = 24.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${Formatters.bytes(task.totalBytes)} · terminé",
                                style = MaterialTheme.typography.bodySmall,
                                color = TuneFyColors.Success
                            )
                            Text(
                                if (task.verified) "SHA-256 vérifié"
                                else "Empreinte enregistrée · ${task.actualSha256?.take(12) ?: "—"}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onInstall) { Text("Installer") }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                    if (task.error != null) {
                        Text(
                            task.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onRemove) { Text("Retirer") }
                        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Réessayer")
                        }
                    }
                }
                DownloadStatus.VERIFYING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Vérification de l'empreinte SHA-256…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = if (task.status == DownloadStatus.PAUSED) onResume else onPause
                        ) {
                            Icon(
                                if (task.status == DownloadStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (task.status == DownloadStatus.PAUSED) "Reprendre" else "Pause"
                            )
                        }
                        Text(
                            if (task.status == DownloadStatus.PAUSED) "En pause" else "En cours",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
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
private fun StatusPill(task: DownloadTask) {
    val (label, color) = when (task.status) {
        DownloadStatus.QUEUED -> "En attente" to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.RUNNING -> "Téléchargement" to MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> "Pause" to TuneFyColors.Warning
        DownloadStatus.VERIFYING -> "Vérification" to MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED -> "Terminé" to TuneFyColors.Success
        DownloadStatus.FAILED -> "Échec" to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELED -> "Annulé" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
