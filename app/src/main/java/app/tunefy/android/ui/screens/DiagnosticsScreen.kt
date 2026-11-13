package app.tunefy.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.patch.PatchStage
import app.tunefy.android.patch.StageStatus
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(viewModel: AppViewModel) {
    val state by viewModel.patchUi.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val bundle = remember { viewModel.patchBundleInfo() }
    val certificate = remember(state.running) { viewModel.existingSigningCertificate() }
    val logs = remember(state.log, state.running) { viewModel.patchLogs() }
    var selectedLog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Journaux et diagnostics",
                subtitle = "Tout reste sur l'appareil. Aucun envoi automatique."
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Moteur intégré", style = MaterialTheme.typography.titleSmall)
                    }
                    MetaRow("Patcher", "Morphe Patcher 2.3.0")
                    MetaRow("Chargement", "classes.dex du bundle, dans le processus TuneFy")
                    MetaRow("Bundle", bundle?.version ?: "indisponible")
                    MetaRow("Fichier", bundle?.asset ?: "—", mono = true)
                    MetaRow("SHA-256", bundle?.sha256 ?: "—", mono = true)
                    MetaRow("Source", bundle?.source ?: "—")
                    Text(
                        "TuneFy ne démarre aucune application externe et n'utilise pas Morphe Manager pour charger ce bundle.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Signature TuneFy", style = MaterialTheme.typography.titleSmall)
                    }
                    if (certificate == null) {
                        Text(
                            "La clé locale sera créée à la première signature. Elle reste dans le stockage privé de " +
                                "TuneFy et n'est jamais exportée automatiquement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        MetaRow("Empreinte SHA-256", certificate, mono = true)
                        Text(
                            "La même clé est réutilisée pour toutes les builds TuneFy, ce qui permet les mises à jour " +
                                "entre versions TuneFy. Une build TuneFy ne peut pas remplacer directement une " +
                                "installation Spotify signée par Spotify.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dernière opération", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val compatibility = state.analysis?.compatibility
                        if (compatibility != null) CompatBadge(compatibility)
                    }
                    state.analysis?.let { analysis ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoPill(analysis.versionName?.let { "Spotify $it" } ?: "Version ?")
                            InfoPill(analysis.abis.joinToString(", ").ifBlank { "ABI ?" })
                        }
                        MetaRow("Fichier source", analysis.displayName)
                        MetaRow("SHA-256 source", analysis.sha256, mono = true)
                    } ?: Text(
                        "Aucun APK analysé dans cette session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    state.stages.forEach { progress ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (progress.status == StageStatus.DONE) Icons.Rounded.CheckCircle
                                else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = when (progress.status) {
                                    StageStatus.DONE -> TuneFyColors.Success
                                    StageStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                progress.stage.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (progress.detail.isNotBlank()) {
                                Text(
                                    progress.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Journaux locaux",
                subtitle = "${logs.size} fichier${if (logs.size > 1) "s" else ""} dans le stockage privé"
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "Aucun journal pour le moment. Chaque tentative de patch crée un fichier texte local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(logs, key = { it.absolutePath }) { file ->
                LogRow(
                    file = file,
                    selected = selectedLog == file.absolutePath,
                    onSelect = {
                        selectedLog = if (selectedLog == file.absolutePath) null else file.absolutePath
                    },
                    onShare = {
                        val text = viewModel.readPatchLog(file.absolutePath).orEmpty()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, file.name)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Partager le journal"))
                    }
                )
            }
        }

        selectedLog?.let { path ->
            item {
                val content = viewModel.readPatchLog(path).orEmpty()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                File(path).name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Copier")
                            }
                        }
                        Text(
                            content.take(12_000).ifBlank { "Journal vide." },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Les journaux peuvent contenir des noms de classes et des chemins locaux. Vérifiez leur contenu avant " +
                    "de les partager. Aucun jeton GitHub ni secret n'est écrit dans ces fichiers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { BottomContentPadding() }
    }
}

@Composable
private fun LogRow(file: File, selected: Boolean, onSelect: () -> Unit, onShare: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSelect) { Text(if (selected) "Masquer" else "Afficher") }
            TextButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Partager", modifier = Modifier.size(16.dp))
            }
        }
    }
}
