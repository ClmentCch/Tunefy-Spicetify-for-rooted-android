package app.tunefy.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tunefy.android.ui.theme.TuneFyColors

/**
 * TuneFy's own mark: a rounded "T" over two flowing sound waves.
 * Deliberately unrelated to the F-Droid robot and to Spotify's trademark.
 */
@Composable
fun TuneFyMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    showBackground: Boolean = true
) {
    Canvas(modifier = modifier.size(markSize)) {
        val side = this.size.minDimension
        if (showBackground) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(TuneFyColors.DeepBlue, TuneFyColors.Blue),
                    start = Offset(0f, 0f),
                    end = Offset(side, side)
                ),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.27f)
            )
        }

        val cream = Color(0xFFF4F7FC)
        val barTop = side * 0.235f
        val barHeight = side * 0.115f
        drawRoundRect(
            color = cream,
            topLeft = Offset(side * 0.28f, barTop),
            size = Size(side * 0.44f, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f)
        )
        drawRoundRect(
            color = cream,
            topLeft = Offset(side * 0.443f, barTop),
            size = Size(side * 0.114f, side * 0.305f),
            cornerRadius = CornerRadius(side * 0.05f)
        )

        fun wave(yOffset: Float): Path = Path().apply {
            moveTo(side * 0.20f, side * (0.70f + yOffset))
            cubicTo(
                side * 0.30f, side * (0.60f + yOffset),
                side * 0.38f, side * (0.80f + yOffset),
                side * 0.48f, side * (0.70f + yOffset)
            )
            cubicTo(
                side * 0.58f, side * (0.60f + yOffset),
                side * 0.66f, side * (0.80f + yOffset),
                side * 0.80f, side * (0.70f + yOffset)
            )
        }
        drawPath(
            wave(0f),
            color = TuneFyColors.PastelBlue,
            style = Stroke(width = side * 0.05f, cap = StrokeCap.Round)
        )
        drawPath(
            wave(0.105f),
            color = TuneFyColors.Blue,
            style = Stroke(width = side * 0.05f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TuneFyWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TuneFyMark(markSize = if (compact) 30.dp else 38.dp)
        Column {
            Text(
                text = "TuneFy",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!compact) {
                Text(
                    text = "Le magasin Spotify",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
