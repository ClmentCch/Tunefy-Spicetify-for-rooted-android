package app.tunefy.android.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.patch.SpotifyProfile
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.InfoPill
import app.tunefy.android.ui.components.MetaRow
import app.tunefy.android.ui.components.SectionHeader
import app.tunefy.android.ui.theme.TuneFyColors

@Composable
fun CompatibilityScreen(viewModel: AppViewModel) {
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val patchState by viewModel.patchUi.collectAsStateWithLifecycle()
    val profiles = remember { viewModel.patchProfiles() }
    val bundle = remember { viewModel.patchBundleInfo() }
    val analysis = patchState.analysis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Compatibilité Spotify",
                subtitle = "Profils structurels vérifiés avant tout patch"
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Appareil", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoPill(Build.SUPPORTED_ABIS.joinToString(", "))
                        InfoPill("Android ${Build.VERSION.RELEASE}")
                    }
                    MetaRow("Spotify installé", if (spotify.installed) spotify.versionName ?: "installé" else "non détecté")
                    MetaRow("VersionCode installé", spotify.versionCode.toString())
                    analysis?.let {
                        MetaRow("Dernier APK analysé", "${it.displayName} · ${it.versionName ?: "?"}")
                    }
                }
            }
        }

        if (profiles == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "Les profils de compatibilité intégrés n'ont pas pu être lus.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            item {
                SectionHeader(
                    title = "Versions prises en charge",
                    subtitle = "${profiles.profiles.size} profil${if (profiles.profiles.size > 1) "s" else ""} · bundle ${profiles.bundle.version}"
                )
            }
            items(profiles.profiles, key = { it.versionName }) { profile ->
                ProfileCard(profile)
            }

            if (profiles.unsupported.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Versions refusées",
                        subtitle = "Aucun patch n'est tenté sur ces builds"
                    )
                }
                items(profiles.unsupported, key = { "unsupported-${it.versionName}-${it.reason.hashCode()}" }) { unsupported ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = TuneFyColors.Warning.copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, TuneFyColors.Warning.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = TuneFyColors.Warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Spotify ${unsupported.versionName}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    unsupported.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Moteur intégré", style = MaterialTheme.typography.titleSmall)
                        MetaRow("Patcher", "Morphe Patcher 2.3.0")
                        MetaRow("Bundle", profiles.bundle.version)
                        MetaRow("SHA-256 bundle", profiles.bundle.sha256, mono = true)
                        MetaRow("Source", profiles.bundle.source)
                        Text(
                            profiles.bundle.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "TuneFy charge ce bundle dans son propre processus Android. Aucune application Morphe " +
                                "Manager n'est installée ou ouverte.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item { BottomContentPadding() }
    }
}

@Composable
private fun ProfileCard(profile: SpotifyProfile) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TuneFyColors.Success.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = TuneFyColors.Success,
                    modifier = Modifier.size(20.dp)
                )
                Text("Spotify ${profile.versionName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                InfoPill(if (profile.status == "experimental") "Expérimental" else "Compatible")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(profile.abis.joinToString(", "))
                InfoPill("versionCode ${profile.versionCode}")
                InfoPill("${profile.requiredClasses.size} classes")
            }
            Text(profile.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (profile.requiredMethods.isNotEmpty()) {
                Text(
                    "Contrôles de méthodes : " + profile.requiredMethods.entries.joinToString(" · ") { (type, methods) ->
                        "$type ${methods.joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (profile.patchRequirements.isNotEmpty()) {
                Text(
                    "Contrôles par patch : " + profile.patchRequirements.entries.joinToString(" · ") { (patch, classes) ->
                        "$patch → ${classes.joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
