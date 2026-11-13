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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import app.tunefy.android.data.model.BackupEntry
import app.tunefy.android.data.model.RootFlavor
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

@Composable
fun RootScreen(viewModel: AppViewModel) {
    val root by viewModel.root.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val backupInProgress by viewModel.backupInProgress.collectAsStateWithLifecycle()
    var dataSize by remember { mutableStateOf<Long?>(null) }
    var exportEntry by remember { mutableStateOf<BackupEntry?>(null) }
    var importWithRoot by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        val entry = exportEntry
        if (uri != null && entry != null) viewModel.exportBackup(entry, uri)
        exportEntry = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importApk(uri, importWithRoot) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Fonctionnalités root optionnelles", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "TuneFy fonctionne sans root. Avec Magisk, KernelSU ou APatch, il peut analyser " +
                            "l'installation Spotify, créer une sauvegarde en lecture seule et proposer " +
                            "l'installation via le gestionnaire de paquets root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Activer les outils root",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.rootFeaturesEnabled,
                            onCheckedChange = viewModel::setRootFeatures
                        )
                    }
                }
            }
        }

        item {
            RootStatusCard(
                rooted = root.rooted,
                checking = root.checking,
                flavor = root.flavor,
                version = root.version,
                suPath = root.suPath,
                error = root.error,
                enabled = settings.rootFeaturesEnabled,
                onProbe = viewModel::probeRoot
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
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
                        Text("Spotify détecté", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        if (spotify.installed) {
                            InfoPill("Installé", icon = Icons.Rounded.CheckCircle)
                        } else {
                            InfoPill("Absent", icon = Icons.Rounded.ErrorOutline)
                        }
                    }
                    if (spotify.installed) {
                        HorizontalDivider()
                        MetaRow("Version", spotify.versionName ?: "—")
                        MetaRow("Code", spotify.versionCode.toString())
                        MetaRow("Splits", "${spotify.splits.size} APK")
                        MetaRow("Permissions", "${spotify.permissions.size} demandées")
                        MetaRow("Données privées", if (spotify.dataDirReadable) "lisibles" else "protégées")
                        val signature = spotify.signatureSha256
                        if (!signature.isNullOrBlank()) {
                            MetaRow(
                                "Signature",
                                signature.take(24) + "…",
                                mono = true
                            )
                        }
                        if (dataSize != null) {
                            MetaRow("Taille des données", Formatters.bytes(dataSize!!))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.dataDirectorySize { dataSize = it } },
                                shape = RoundedCornerShape(50),
                                enabled = root.rooted
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Mesurer les données")
                            }
                            TextButton(onClick = viewModel::refreshSpotify) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Analyser")
                            }
                        }
                    } else {
                        Text(
                            "Installez Spotify pour activer la détection, la vérification des patches et les sauvegardes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = viewModel::refreshSpotify) { Text("Relancer la détection") }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sauvegardes Spotify", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "La sauvegarde copie les données de Spotify dans le stockage privé de TuneFy. " +
                            "TuneFy ne les réécrit jamais dans Spotify : c'est une lecture seule, exportable " +
                            "vers l'emplacement de votre choix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (backupInProgress) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Sauvegarde en cours…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.createBackup() },
                            enabled = root.rooted && !backupInProgress,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Créer une sauvegarde")
                        }
                        OutlinedButton(
                            onClick = {
                                importWithRoot = true
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            shape = RoundedCornerShape(50),
                            enabled = root.rooted
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (backups.isEmpty()) {
                        Text(
                            "Aucune sauvegarde pour l'instant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (backups.isNotEmpty()) {
            item { SectionHeader(title = "Sauvegardes locales", subtitle = "${backups.size} archive(s)") }
            items(backups, key = { it.id }) { entry ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Formatters.timestamp(entry.createdAt),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${Formatters.bytes(entry.sizeBytes)} · Spotify ${entry.spotifyVersion.ifBlank { "?" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            exportEntry = entry
                            exportLauncher.launch("tunefy-spotify-${entry.createdAt}.tar.gz")
                        }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "Exporter")
                        }
                        IconButton(onClick = { viewModel.deleteBackup(entry) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Installations root", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Vous pouvez injecter un APK ou un bundle de splits via pm install / pm install-multiple. " +
                            "TuneFy vérifie d'abord l'empreinte SHA-256, la cohérence des signatures et " +
                            "la correspondance de package. Aucun fichier non vérifié n'est installé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            importWithRoot = root.rooted
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (root.rooted) "Importer un APK (installateur root)"
                            else "Importer un APK (installateur Android)"
                        )
                    }
                    if (!root.rooted) {
                        Text(
                            "Mode root non détecté : l'import utilisera l'installateur Android standard.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TuneFyColors.Warning.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, TuneFyColors.Warning.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = TuneFyColors.Warning,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        "Le root ne contourne pas les protections Android. TuneFy utilise les mécanismes " +
                            "d'installation officiels, vérifie les signatures et ne modifie jamais directement " +
                            "les données privées de Spotify. Les sauvegardes restent dans votre stockage.",
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
private fun RootStatusCard(
    rooted: Boolean,
    checking: Boolean,
    flavor: RootFlavor,
    version: String?,
    suPath: String?,
    error: String?,
    enabled: Boolean,
    onProbe: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (rooted) TuneFyColors.Success.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        if (rooted) Icons.Rounded.CheckCircle else Icons.Rounded.Build,
                        contentDescription = null,
                        tint = if (rooted) TuneFyColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(9.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            checking -> "Analyse du root…"
                            rooted -> "Root détecté · ${flavor.label}"
                            else -> "Root non détecté"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        when {
                            checking -> "Exécution de su -c id"
                            rooted -> "Les fonctions root sont disponibles."
                            else -> "TuneFy reste pleinement utilisable sans root."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onProbe, enabled = enabled) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Analyser le root")
                    }
                }
            }
            if (rooted) {
                HorizontalDivider()
                MetaRow("Gestionnaire", flavor.label)
                MetaRow("Binaire", suPath ?: "su (PATH)")
                if (!version.isNullOrBlank()) {
                    MetaRow("Version", version, mono = true)
                }
            }
            if (error != null && !rooted) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!enabled) {
                Text(
                    "Activez les outils root ci-dessus pour lancer l'analyse.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuneFyColors.Warning
                )
            }
            TextButton(onClick = onProbe, enabled = enabled && !checking) {
                Text(if (rooted) "Revérifier l'accès root" else "Tester l'accès root")
            }
        }
    }
}
