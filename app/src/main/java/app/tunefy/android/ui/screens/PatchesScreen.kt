package app.tunefy.android.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.PatchDefinition
import app.tunefy.android.data.model.RepositoryStatus
import app.tunefy.android.data.model.SpotifyStatus
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.ErrorBanner
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.LoadingState
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun PatchesScreen(viewModel: AppViewModel) {
    val bundle by viewModel.patchBundle.collectAsStateWithLifecycle()
    val status by viewModel.patchStatus.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val selection by viewModel.patchSelection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val (compatibility, compatibilityLabel) = viewModel.repositoryCompatibilityLabel()

    if (bundle == null && status.loading) {
        LoadingState(message = "Chargement des patches Spicetify…")
        return
    }

    val patches = bundle?.patches.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PatchesHeader(
                patchCount = patches.size,
                spotify = spotify,
                compatibility = compatibility,
                compatibilityLabel = compatibilityLabel,
                statusSource = status,
                onRefresh = viewModel::refreshPatches,
                onMorphe = viewModel::openMorphe
            )
        }

        if (status.error != null) {
            item { ErrorBanner(message = status.error!!, onRetry = viewModel::refreshPatches) }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text("Application par Morphe uniquement", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "TuneFy lit la liste officielle des patches, vérifie la version Spotify installée et " +
                            "transmet votre sélection. Le patchage lui-même est réalisé par Morphe, avec votre " +
                            "confirmation. Aucun patch incompatible ne peut être activé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (patches.isEmpty()) {
            item {
                ErrorBanner("Aucun patch chargé. Vérifiez votre connexion puis actualisez.")
            }
        }

        item {
            SectionHeader(
                title = "Patches disponibles",
                subtitle = "${selection.size} sélectionné${if (selection.size > 1) "s" else ""} sur ${patches.size}"
            )
        }

        items(patches, key = { it.name }) { patch ->
            PatchCard(
                patch = patch,
                selected = patch.name in selection,
                compatibility = viewModel.patchCompatibility(patch),
                enabled = compatibility != Compatibility.INCOMPATIBLE,
                onToggle = { viewModel.togglePatch(patch.name, it) }
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exporter la sélection", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Générez un fichier JSON décrivant vos patches choisis pour le transmettre à " +
                            "Morphe ou le conserver avec votre configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::openMorphe,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ouvrir Morphe")
                        }
                        OutlinedButton(
                            onClick = {
                                val payload = viewModel.patchesExportJson()
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                    putExtra(Intent.EXTRA_SUBJECT, "TuneFy — sélection de patches Spicetify")
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Exporter la sélection"))
                                }
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exporter")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = TuneFyColors.Warning.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, TuneFyColors.Warning.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = TuneFyColors.Warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Les patches Spicetify sont expérimentaux et dépendent d'une version précise de Spotify. " +
                            "TuneFy n'agit ni sur votre abonnement ni sur les protections DRM. " +
                            "Sauvegardez vos données Spotify avant toute manipulation.",
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
private fun PatchesHeader(
    patchCount: Int,
    spotify: SpotifyStatus,
    compatibility: Compatibility,
    compatibilityLabel: String,
    statusSource: RepositoryStatus,
    onRefresh: () -> Unit,
    onMorphe: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spicetify · Morphe", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$patchCount patches · ${statusSource.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser les patches")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompatBadge(compatibility)
                Text(
                    compatibilityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (spotify.installed) {
                    InfoPill("Spotify ${spotify.versionName ?: "?"}")
                    InfoPill(if (spotify.dataDirReadable) "Données accessibles" else "Données protégées")
                } else {
                    InfoPill("Spotify non installé")
                }
                InfoPill("Cible 9.1.80.2221")
            }

            Button(
                onClick = onMorphe,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Ajouter la source dans Morphe")
            }
            Text(
                Constants.MORPHE_PATCHES_INDEX_URL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PatchCard(
    patch: PatchDefinition,
    selected: Boolean,
    compatibility: app.tunefy.android.data.model.PatchCompatibility,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(patch.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (patch.default) "Activé par défaut par Spicetify" else "Optionnel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = selected && enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled
                )
            }
            Text(
                patch.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompatBadge(compatibility.status)
                if (patch.experimental) {
                    InfoPill(
                        "Expérimental",
                        icon = Icons.Rounded.Warning,
                        containerColor = TuneFyColors.Warning.copy(alpha = 0.14f),
                        contentColor = TuneFyColors.Warning
                    )
                }
                patch.spotifyPackage?.apkFileType?.let { InfoPill(it) }
            }
            Text(
                compatibility.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (compatibility.status == Compatibility.INCOMPATIBLE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (compatibility.supportedVersions.isNotEmpty()) {
                Text(
                    "Spotify pris en charge : ${compatibility.supportedVersions.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (selected && !enabled) {
                Text(
                    "Sélection inutilisable : version de Spotify incompatible.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
