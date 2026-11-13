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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.data.model.PatchDefinition
import app.tunefy.android.patch.ApkAnalysis
import app.tunefy.android.patch.GeneratedPatch
import app.tunefy.android.patch.PatchAvailability
import app.tunefy.android.patch.PatchStage
import app.tunefy.android.patch.PatchUiState
import app.tunefy.android.patch.SpotifyOfficialApk
import app.tunefy.android.patch.StageProgress
import app.tunefy.android.patch.StageStatus
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.CompatBadge
import app.tunefy.android.ui.components.ErrorBanner
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.LoadingState
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun ApkPatcherScreen(
    viewModel: AppViewModel,
    onOpenGenerated: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val state by viewModel.patchUi.collectAsStateWithLifecycle()
    val catalog by viewModel.patchBundle.collectAsStateWithLifecycle()
    var saveTarget by remember { mutableStateOf<GeneratedPatch?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::selectPatchApk)
    }
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
        item { PatcherHero() }

        item {
            ImportCard(
                analyzing = state.analyzing,
                running = state.running,
                officialDownloading = state.officialDownloading,
                officialDownloadedBytes = state.officialDownloadedBytes,
                officialTotalBytes = state.officialTotalBytes,
                onPick = { picker.launch(arrayOf("*/*")) },
                onDownloadOfficial = viewModel::downloadOfficialApk,
                onOpenCompatibility = onOpenCompatibility
            )
        }

        if (state.analyzing) {
            item { LoadingState(message = "Analyse de l'APK et des classes DEX…") }
        }

        state.analysis?.let { analysis ->
            item { AnalysisCard(analysis = analysis, onOpenCompatibility = onOpenCompatibility) }
        }

        state.error?.let { error ->
            item { ErrorBanner(message = error) }
        }

        if (state.analysis?.compatible == true) {
            val patches = catalog?.patches.orEmpty()
            item {
                SectionHeader(
                    title = "Catalogue de patches",
                    subtitle = "${state.selected.size} sélectionné${if (state.selected.size > 1) "s" else ""} · " +
                        "${patches.count { state.analysis?.patchAvailability?.get(it.name)?.enabled != false }} disponibles"
                )
            }
            if (patches.isEmpty()) {
                item { LoadingState(message = "Chargement du catalogue Spicetify…") }
            } else {
                items(patches, key = { it.name }) { patch ->
                    PatchRow(
                        patch = patch,
                        availability = state.analysis?.patchAvailability?.get(patch.name),
                        selected = patch.name in state.selected,
                        enabled = !state.running && state.analysis?.patchAvailability?.get(patch.name)?.enabled != false,
                        onToggle = { viewModel.togglePatchSelection(patch.name, it) }
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::startPatch,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.running) "Patch en cours…" else "Créer l'APK patché")
                }
            }

            item {
                if (state.analysis?.signatureWillChange == true) {
                    SignatureWarning(analysis = state.analysis!!)
                }
            }
        }

        if (state.running || state.stages.any { it.status != StageStatus.PENDING }) {
            item { ProgressCard(state = state) }
        }

        state.result?.let { result ->
            item {
                ResultCard(
                    artifact = result.artifact,
                    analysis = state.analysis,
                    onInstall = { viewModel.installGenerated(result.artifact) },
                    onSave = {
                        saveTarget = result.artifact
                        saver.launch(result.artifact.fileName)
                    },
                    onOpenGenerated = onOpenGenerated,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }
        }

        item {
            QuickLinks(
                onOpenGenerated = onOpenGenerated,
                onOpenHistory = onOpenHistory,
                onOpenCompatibility = onOpenCompatibility,
                onOpenDiagnostics = onOpenDiagnostics
            )
        }

        item { BottomContentPadding() }
    }
}

@Composable
private fun PatcherHero() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Icon(
                    Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("APK Patcher", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Moteur de patch intégré à TuneFy. Importez votre APK Spotify, vérifiez sa compatibilité, " +
                        "puis créez un APK signé par TuneFy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Aucune installation ni ouverture d'une autre application de patch n'est nécessaire.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ImportCard(
    analyzing: Boolean,
    running: Boolean,
    officialDownloading: Boolean,
    officialDownloadedBytes: Long,
    officialTotalBytes: Long,
    onPick: () -> Unit,
    onDownloadOfficial: () -> Unit,
    onOpenCompatibility: () -> Unit
) {
    val busy = analyzing || running || officialDownloading
    val fraction = if (officialTotalBytes > 0L) {
        (officialDownloadedBytes.toFloat() / officialTotalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text("Choisir la base Spotify", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                InfoPill("APK · APKS · XAPK")
            }
            Text(
                "Deux chemins : votre propre fichier, ou l'APK officiel 9.1.80.2221 publié dans la release TuneFy. " +
                    "L'APK officiel est téléchargé dans TuneFy, vérifié par SHA-256, puis analysé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onPick,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (analyzing) "Analyse…" else "Choisir mon propre APK")
            }
            OutlinedButton(
                onClick = onDownloadOfficial,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (officialDownloading) "Téléchargement de l'APK officiel…"
                    else "Utiliser l'APK officiel 9.1.80.2221"
                )
            }
            if (officialDownloading) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${Formatters.bytes(officialDownloadedBytes)} / ${Formatters.bytes(officialTotalBytes)} · " +
                        "${Formatters.percent(fraction)} · ${Formatters.bytes(SpotifyOfficialApk.SIZE_BYTES)} attendus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Release ${SpotifyOfficialApk.RELEASE_TAG} · Spotify ${SpotifyOfficialApk.VERSION} · " +
                        "${Formatters.bytes(SpotifyOfficialApk.SIZE_BYTES)} · SHA-256 vérifié avant analyse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenCompatibility, contentPadding = PaddingValues(0.dp)) {
                Text("Voir les versions Spotify prises en charge")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: ApkAnalysis, onOpenCompatibility: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    analysis.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CompatBadge(analysis.compatibility)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(analysis.versionName?.let { "Spotify $it" } ?: "Version inconnue")
                InfoPill(analysis.abis.joinToString(", ").ifBlank { "ABI inconnue" })
                InfoPill("${analysis.splitCount} module${if (analysis.splitCount > 1) "s" else ""}")
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            MetaRow("Package", analysis.packageName ?: "inconnu", mono = true)
            MetaRow("VersionCode", analysis.versionCode.toString())
            MetaRow("Taille", Formatters.bytes(analysis.sizeBytes))
            MetaRow("SHA-256", analysis.sha256, mono = true)
            analysis.signatureSha256?.let { MetaRow("Signature actuelle", it.take(24) + "…", mono = true) }
            Text(analysis.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!analysis.compatible) {
                Text(
                    "Versions prises en charge : ${analysis.supportedVersions.joinToString(", ").ifBlank { "aucune" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onOpenCompatibility, contentPadding = PaddingValues(0.dp)) {
                    Text("Ouvrir la page Compatibilité")
                }
            } else {
                Text(
                    "Les classes et méthodes du profil ont été retrouvées dans le DEX. Un échec de fingerprint " +
                        "reste possible et sera affiché comme un échec, jamais comme un succès.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatchRow(
    patch: PatchDefinition,
    availability: PatchAvailability?,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(checked = selected, onCheckedChange = onToggle, enabled = enabled)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(patch.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (patch.experimental) InfoPill("Expérimental", containerColor = TuneFyColors.Warning.copy(alpha = 0.14f), contentColor = TuneFyColors.Warning)
                }
                Text(
                    patch.description.ifBlank { "Aucune description fournie par la source." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (patch.supportedVersions.isNotEmpty()) {
                    Text(
                        "Spotify ${patch.supportedVersions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (patch.dependencies.isNotEmpty()) {
                    Text(
                        "Dépendances techniques : ${patch.dependencies.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (availability?.enabled == false) {
                    Text(
                        availability.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: PatchUiState) {
    val done = state.stages.count { it.status == StageStatus.DONE }
    val fraction = (done.toFloat() / state.stages.size).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Progression", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("$done/${state.stages.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (state.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            state.stages.forEach { StageRow(it) }
        }
    }
}

@Composable
private fun StageRow(progress: StageProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (progress.status) {
            StageStatus.DONE -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = TuneFyColors.Success,
                modifier = Modifier.size(18.dp)
            )
            StageStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            StageStatus.FAILED -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            StageStatus.PENDING -> Icon(
                Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                progress.stage.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.status == StageStatus.PENDING) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            if (progress.detail.isNotBlank()) {
                Text(
                    progress.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.status == StageStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    artifact: GeneratedPatch,
    analysis: ApkAnalysis?,
    onInstall: () -> Unit,
    onSave: () -> Unit,
    onOpenGenerated: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TuneFyColors.Success.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, TuneFyColors.Success.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TuneFyColors.Success, modifier = Modifier.size(22.dp))
                Text("APK généré et vérifié", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(artifact.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaRow("Version Spotify", artifact.spotifyVersion.ifBlank { "—" })
            MetaRow("Architecture", artifact.architecture.ifBlank { "—" })
            MetaRow("Patches appliqués", artifact.patches.joinToString(", ").ifBlank { "—" })
            MetaRow("Taille", Formatters.bytes(artifact.sizeBytes))
            MetaRow("SHA-256", artifact.sha256, mono = true)
            MetaRow("Signature", (artifact.signatureSha256 ?: "—").take(24) + if (artifact.signatureSha256 != null) "…" else "")
            Text(
                "Signature TuneFy vérifiée par apksig. Le nom de fichier et la taille sont contrôlés après signature.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (analysis?.signatureWillChange == true) {
                Text(
                    "La signature TuneFy diffère de celle de Spotify. Android refusera probablement la mise à jour " +
                        "par-dessus l'application officielle. TuneFy ne désinstalle jamais Spotify et ne supprime aucune donnée. " +
                        "Sauvegardez vos données et confirmez explicitement toute désinstallation vous-même.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuneFyColors.Warning
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Installer")
                }
                OutlinedButton(onClick = onSave, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enregistrer")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenGenerated, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mes APK")
                }
                TextButton(onClick = onOpenDiagnostics, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Journaux")
                }
            }
        }
    }
}

@Composable
private fun SignatureWarning(analysis: ApkAnalysis) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TuneFyColors.Warning.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, TuneFyColors.Warning.copy(alpha = 0.28f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = TuneFyColors.Warning, modifier = Modifier.size(19.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Signature différente de Spotify", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append("L'APK importé est signé ${analysis.signatureSha256?.take(12)}… ; TuneFy le signera avec sa propre clé. ")
                        if (analysis.installedSignatureSha256 != null) {
                            append("L'application installée utilise ${analysis.installedSignatureSha256.take(12)}… . ")
                        }
                        append("Un APK signé par TuneFy ne pourra pas remplacer directement l'application officielle. ")
                        append("Aucune désinstallation ni suppression de données n'est automatique.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickLinks(
    onOpenGenerated: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            LinkRow("Mes APK générés", Icons.Rounded.Folder, onOpenGenerated)
            LinkRow("Historique des modifications", Icons.Rounded.History, onOpenHistory)
            LinkRow("Compatibilité Spotify", Icons.Rounded.Shield, onOpenCompatibility)
            LinkRow("Journaux et diagnostics", Icons.Rounded.ErrorOutline, onOpenDiagnostics)
        }
    }
}

@Composable
private fun LinkRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}
