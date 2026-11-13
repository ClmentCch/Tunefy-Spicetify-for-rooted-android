package app.tunefy.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.patch.PatchHistoryRecord
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
fun ModHistoryScreen(viewModel: AppViewModel) {
    val history by viewModel.modificationHistory.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Historique des modifications",
                    subtitle = "${history.size} opération${if (history.size > 1) "s" else ""} enregistrée${if (history.size > 1) "s" else ""}",
                    modifier = Modifier.weight(1f)
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearModificationHistory) { Text("Effacer") }
                }
            }
        }
        if (history.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Historique vide",
                    message = "Chaque tentative de patch, réussie ou refusée, sera consignée ici avec sa cause."
                )
            }
        }
        items(history, key = { it.id }) { record ->
            HistoryCard(record)
        }
        item { BottomContentPadding() }
    }
}

@Composable
private fun HistoryCard(record: PatchHistoryRecord) {
    val accent = if (record.success) TuneFyColors.Success else MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (record.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    record.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                InfoPill(if (record.success) "Réussi" else "Échec")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill("Spotify ${record.spotifyVersion.ifBlank { "?" }}")
                InfoPill("${record.patches.size} patch${if (record.patches.size > 1) "s" else ""}")
            }
            Text(
                record.patches.joinToString(", ").ifBlank { "Aucun patch appliqué" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MetaRow("Date", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.createdAt)))
            Text(
                record.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (record.success) MaterialTheme.colorScheme.onSurfaceVariant else accent
            )
            record.sha256?.let { MetaRow("SHA-256", it, mono = true) }
        }
    }
}
