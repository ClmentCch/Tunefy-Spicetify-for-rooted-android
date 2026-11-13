package app.tunefy.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.ui.theme.TuneFyColors
import app.tunefy.android.util.Formatters

fun String.asColor(fallback: Color = TuneFyColors.Blue): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: Exception) {
    fallback
}

fun Category.icon(): ImageVector = when (this) {
    Category.SPOTIFY -> Icons.Rounded.GraphicEq
    Category.EXTENSIONS -> Icons.Rounded.Extension
    Category.THEMES -> Icons.Rounded.Palette
    Category.PATCHES -> Icons.Rounded.Memory
    Category.TOOLS -> Icons.Rounded.Settings
    Category.ALL -> Icons.Rounded.Verified
}

@Composable
fun AppIcon(
    item: CatalogItem,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val accent = item.accentColor.asColor()
    val isSpotify = item.categoryEnum == Category.SPOTIFY
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = if (isSpotify) {
                        listOf(Color(0xFF1DB954), Color(0xFF127A38))
                    } else {
                        listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f))
                    },
                    start = Offset.Zero,
                    end = Offset(size.value * 2.2f, size.value * 2.2f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSpotify) "S" else item.monogram,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
    }
}

@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    color: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        shape = shape,
        color = color,
        border = border,
        interactionSource = interaction,
        content = content
    )
}

@Composable
fun ItemRow(
    item: CatalogItem,
    installedVersion: String?,
    compatibility: Compatibility,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(item = item, size = 56.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (installedVersion != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Installé",
                            tint = TuneFyColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${item.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DotSeparator()
                    Text(
                        text = Formatters.bytes(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (compatibility != Compatibility.UNKNOWN) {
                        Spacer(Modifier.width(2.dp))
                        CompatBadge(compatibility)
                    }
                }
            }
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DotSeparator() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    )
}

@Composable
fun CompatBadge(compatibility: Compatibility, compact: Boolean = false) {
    val (container, content) = when (compatibility) {
        Compatibility.COMPATIBLE -> TuneFyColors.Success.copy(alpha = 0.14f) to TuneFyColors.Success
        Compatibility.EXPERIMENTAL -> TuneFyColors.Warning.copy(alpha = 0.16f) to TuneFyColors.Warning
        Compatibility.INCOMPATIBLE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        Compatibility.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(50)) {
        Text(
            text = if (compact) compatibility.label.take(1) else compatibility.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = content,
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun InfoPill(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(modifier = modifier, color = containerColor, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(74.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Réessayer", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
fun MetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(138.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ThemePreview(
    colors: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp
) {
    val palette = if (colors.size >= 2) colors.map { it.asColor() } else {
        listOf(TuneFyColors.DeepBlue, TuneFyColors.Blue)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(palette))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            listOf(18, 30, 22, 40, 26, 34, 20, 28).forEach { barHeight ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.55f))
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        )
    }
}

@Composable
fun ScreenSpacer(height: Dp = 8.dp) = Spacer(Modifier.height(height))

@Composable
fun BottomContentPadding() = Spacer(Modifier.height(96.dp))

@Composable
fun HorizontalListSpacing() = Spacer(Modifier.width(12.dp))

@Composable
fun NoInsetsScaffoldMarker() = WindowInsets(0.dp)
