package app.tunefy.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tunefy.android.BuildConfig
import app.tunefy.android.core.Constants
import app.tunefy.android.ui.components.BottomContentPadding
import app.tunefy.android.ui.components.TuneFyMark
import app.tunefy.android.ui.theme.TuneFyColors

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun open(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TuneFyMark(markSize = 88.dp)
            Text("TuneFy", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Le magasin open source dédié à Spotify",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · GPL-3.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ce que fait TuneFy", style = MaterialTheme.typography.titleMedium)
                AboutBullet(
                    Icons.Rounded.Extension,
                    "Un catalogue Spotify uniquement : application officielle, extensions Spicetify, thèmes, patches et outils."
                )
                AboutBullet(
                    Icons.Rounded.Security,
                    "Téléchargements réels avec progression, vitesse, reprise et vérification SHA-256."
                )
                AboutBullet(
                    Icons.Rounded.Shield,
                    "Installations via l'installateur Android, contrôle des signatures et des packages, journal local."
                )
                AboutBullet(
                    Icons.Rounded.Memory,
                    "APK Patcher intégré : analyse, sélection de patches Spicetify, reconstruction et signature TuneFy, sans Morphe Manager."
                )
                AboutBullet(
                    Icons.Rounded.Security,
                    "Support optionnel de Magisk, KernelSU et APatch : détection Spotify, sauvegardes en lecture seule."
                )
                AboutBullet(
                    Icons.Rounded.Code,
                    "Architecture Kotlin, Jetpack Compose, Material 3, MVVM, coroutines, sans publicité ni traqueur."
                )
                Text(
                    "TuneFy ne fournit aucun moyen de contourner un abonnement ou une protection DRM. " +
                        "Les patches sont édités par Spicetify ; TuneFy intègre leur application via une " +
                        "bibliothèque dérivée de Morphe Patcher (GPL-3.0), sans dépendre de Morphe Manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Attributions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "TuneFy est un projet indépendant. Son organisation de navigation s'inspire de F-Droid " +
                        "(GPL-3.0), sans réutiliser son logo ou son identité de marque. Morphe Patcher 2.3.0 " +
                        "est intégré sous GPL-3.0 ; les patches et extensions appartiennent à leurs auteurs " +
                        "Spicetify, sous leurs licences respectives. Spotify est une marque de Spotify AB, " +
                        "utilisée ici à titre descriptif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                LinkRow("Dépôt GitHub", Constants.GITHUB_REPOSITORY) { open(Constants.PROJECT_URL) }
                LinkRow("Patches Spicetify", "github.com/spicetify/morphe-patches") {
                    open(Constants.MORPHE_PATCHES_REPOSITORY)
                }
                LinkRow("Morphe", "morphe.software") { open(Constants.MORPHE_URL) }
                LinkRow("Spicetify", "spicetify.app") { open(Constants.SPICETIFY_REPOSITORY) }
                LinkRow("F-Droid", "fdroid.org") { open("https://f-droid.org/") }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = TuneFyColors.PastelBlue.copy(alpha = 0.18f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Logique open source", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Le dépôt TuneFy est un simple index JSON versionné. Chacun peut auditer, forker ou " +
                        "auto-héberger un dépôt compatible, sans clé propriétaire ni serveur obligatoire.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { open(Constants.PROJECT_URL) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Code source")
            }
            OutlinedButton(
                onClick = { open("${Constants.PROJECT_URL}/releases") },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Releases")
            }
        }

        Text(
            "© 2026 Contributeurs de TuneFy. Distribué sous GNU General Public License v3.0.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BottomContentPadding()
    }
}

@Composable
private fun AboutBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
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
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
