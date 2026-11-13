package app.tunefy.android.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.BuildConfig
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.ThemeMode
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.util.Formatters

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenAbout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRoot: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.repositoryStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var repositoryDraft by remember(settings.repositoryUrl) { mutableStateOf(settings.repositoryUrl) }
    var allFilesAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    LifecycleResumeEffect(Unit) {
        allFilesAccess = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader(title = "Apparence", subtitle = "Thème et identité visuelle") }
        item {
            SettingsCard {
                Text("Thème", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = if (settings.themeMode == mode) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
                Text(
                    "Le mode Système suit le réglage Android 16/17 (matériel You).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Couleurs dynamiques",
                    subtitle = "Reprend la palette Material You de votre appareil",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
        }

        item { SectionHeader(title = "Dépôt TuneFy", subtitle = "Index du magasin et mises à jour") }
        item {
            SettingsCard {
                OutlinedTextField(
                    value = repositoryDraft,
                    onValueChange = { repositoryDraft = it },
                    label = { Text("URL de l'index") },
                    supportingText = {
                        Text(
                            "Schéma identique à l'index embarqué. HTTPS recommandé.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.updateRepositoryUrl(repositoryDraft) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enregistrer")
                    }
                    OutlinedButton(
                        onClick = viewModel::refreshCatalog,
                        shape = RoundedCornerShape(50)
                    ) { Text("Actualiser") }
                }
                Text(
                    "${status.name} · v${status.version} · ${status.itemCount} éléments · ${status.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status.error != null) {
                    Text(
                        status.error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
                SettingClickRow(
                    title = "Vérifier les mises à jour",
                    subtitle = "Ouvrir les releases GitHub de TuneFy",
                    icon = Icons.Rounded.Update,
                    onClick = { openUrl("${Constants.PROJECT_URL}/releases") }
                )
            }
        }

        item { SectionHeader(title = "Téléchargements", subtitle = "Réseau et installation") }
        item {
            SettingsCard {
                SettingSwitchRow(
                    title = "Wi-Fi uniquement",
                    subtitle = "Bloque les téléchargements sur données mobiles",
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnly
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Installer après téléchargement",
                    subtitle = "Ouvre la confirmation Android dès la vérification terminée",
                    checked = settings.installAfterDownload,
                    onCheckedChange = viewModel::setInstallAfterDownload
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Notifications",
                    subtitle = "Progression et résultat des installations",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifications
                )
            }
        }

        item { SectionHeader(title = "Accès aux fichiers", subtitle = "APK, stockage et autorisations") }
        item {
            SettingsCard {
                Text("Sélecteur de fichiers", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pour APK Patcher, le sélecteur Android accorde l'accès uniquement au fichier choisi. " +
                        "L'accès global ci-dessous est optionnel et n'est pas nécessaire pour patcher un APK.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Autoriser l'accès à tous les fichiers",
                    subtitle = if (allFilesAccess) {
                        "Accordé · tous les dossiers publics sont accessibles"
                    } else {
                        "Non accordé · appuyez pour ouvrir le réglage Android"
                    },
                    icon = Icons.Rounded.Folder,
                    onClick = { openAllFilesAccess(context) }
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Réglages de l'application",
                    subtitle = "Installations, notifications et permissions Android",
                    icon = Icons.Rounded.Settings,
                    onClick = { openAppSettings(context) }
                )
            }
        }

        item { SectionHeader(title = "Root et sécurité", subtitle = "Outils optionnels") }
        item {
            SettingsCard {
                SettingSwitchRow(
                    title = "Fonctionnalités root",
                    subtitle = "Détection Magisk, KernelSU, APatch et sauvegardes",
                    checked = settings.rootFeaturesEnabled,
                    onCheckedChange = viewModel::setRootFeatures
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Outils root",
                    subtitle = "Spotify, sauvegardes, installation root",
                    icon = Icons.Rounded.Build,
                    onClick = onOpenRoot
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Historique des installations",
                    subtitle = "Journal local des opérations et empreintes",
                    icon = Icons.Rounded.History,
                    onClick = onOpenHistory
                )
            }
        }

        item { SectionHeader(title = "Mises à jour et sources", subtitle = "Transparence") }
        item {
            SettingsCard {
                SettingSwitchRow(
                    title = "Vérifier le dépôt au démarrage",
                    subtitle = "Actualise l'index TuneFy à l'ouverture",
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = viewModel::setAutoCheckUpdates
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Source des patches Spicetify",
                    subtitle = "github.com/spicetify/morphe-patches",
                    icon = Icons.Rounded.Memory,
                    onClick = { openUrl(Constants.MORPHE_PATCHES_REPOSITORY) }
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Code source TuneFy",
                    subtitle = "Licence GPL-3.0 · dépôt public",
                    icon = Icons.Rounded.Info,
                    onClick = { openUrl(Constants.PROJECT_URL) }
                )
                HorizontalDivider()
                SettingClickRow(
                    title = "Stockage TuneFy",
                    subtitle = "Téléchargements et bibliothèque privée",
                    icon = Icons.Rounded.Storage,
                    onClick = onOpenAbout
                )
            }
        }

        item { SectionHeader(title = "Appareil") }
        item {
            SettingsCard {
                InfoLine("Android", Formatters.androidVersion(Build.VERSION.SDK_INT))
                InfoLine("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
                InfoLine("TuneFy", "${BuildConfig.VERSION_NAME} (GPL-3.0)")
                InfoLine("Dépôt", Constants.GITHUB_REPOSITORY)
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenAbout,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("À propos de TuneFy")
            }
        }

        item {
            Text(
                "TuneFy n'est affilié ni à Spotify AB, ni à F-Droid, ni à Spicetify. " +
                    "Aucune publicité, aucun compte, aucun traqueur.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item { BottomContentPadding() }
    }
}

private fun openAllFilesAccess(context: Context) {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val appIntent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    val allIntent = Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    runCatching { context.startActivity(appIntent) }
        .recoverCatching { context.startActivity(allIntent) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingClickRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
