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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.patch.GeneratedPatch
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.EmptyState
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeneratedApksScreen(viewModel: AppViewModel) {
    val items by viewModel.generatedPatches.collectAsStateWithLifecycle()
    var saveTarget by remember { mutableStateOf<GeneratedPatch?>(null) }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val target = saveTarget
        if (uri != null && target != null) viewModel.saveGenerated(target, uri)
        saveTarget = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Mes APK générés",
                subtitle = "${items.size} fichier${if (items.size > 1) "s" else ""} signé${if (items.size > 1) "s" else ""} par TuneFy"
            )
        }
        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "Aucun APK généré",
                    message = "Utilisez APK Patcher pour créer votre première build Spotify signée par TuneFy."
                )
            }
        }
        items(items, key = { it.id }) { artifact ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TuneFyColors.Success,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            artifact.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.deleteGenerated(artifact) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", modifier = Modifier.size(19.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoPill("Spotify ${artifact.spotifyVersion.ifBlank { "?" }}")
                        InfoPill(artifact.architecture.ifBlank { "ABI ?" })
                        InfoPill("${artifact.patches.size} patch${if (artifact.patches.size > 1) "s" else ""}")
                    }
                    Text(
                        artifact.patches.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MetaRow("Taille", Formatters.bytes(artifact.sizeBytes))
                    MetaRow("Créé le", formatDate(artifact.createdAt))
                    MetaRow("SHA-256", artifact.sha256, mono = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.installGenerated(artifact) },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Installer")
                        }
                        OutlinedButton(
                            onClick = {
                                saveTarget = artifact
                                saver.launch(artifact.fileName)
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enregistrer")
                        }
                    }
                }
            }
        }
        item { BottomContentPadding() }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
