package app.tunefy.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular loading indicator made of rotating blue waves.
 * Drawn by hand so it stays light: three sine rings, one Canvas, no bitmaps.
 */
@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "wave-loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = side * 0.34f * pulse
        val rings = listOf(
            color to 1f,
            color.copy(alpha = 0.55f) to 0.86f,
            color.copy(alpha = 0.28f) to 0.72f
        )
        rings.forEachIndexed { index, (ringColor, ringScale) ->
            rotate(rotation + index * 42f, pivot = center) {
                val path = Path()
                var angle = 0f
                while (angle <= 360f) {
                    val radians = Math.toRadians(angle.toDouble())
                    val wave = sin(Math.toRadians(angle * 3.0 + index * 55.0)).toFloat() * side * 0.035f
                    val radius = baseRadius * ringScale + wave
                    val x = center.x + (radius * cos(radians)).toFloat()
                    val y = center.y + (radius * sin(radians)).toFloat()
                    if (angle == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    angle += 4f
                }
                path.close()
                drawPath(
                    path = path,
                    color = ringColor,
                    style = Stroke(width = side * 0.038f, cap = StrokeCap.Round)
                )
            }
        }
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = baseRadius * 0.45f,
            center = center
        )
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Chargement du dépôt…"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        WaveLoadingIndicator(size = 78.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Success tick that draws itself, used after installs and downloads. */
@Composable
fun AnimatedCheck(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(0f)
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(color = color.copy(alpha = 0.14f), radius = side * 0.5f)
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = side * 0.42f,
            style = Stroke(width = side * 0.045f)
        )
        val path = Path().apply {
            moveTo(side * 0.27f, side * 0.53f)
            lineTo(side * 0.44f, side * 0.70f)
            lineTo(side * 0.75f, side * 0.33f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(
            path = drawn,
            color = color,
            style = Stroke(width = side * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press-scale"
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(
        modifier = modifier,
        onDraw = {
            drawRoundRect(
                color = skeletonColor.copy(alpha = skeletonColor.alpha * alpha),
                size = this.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
            )
        }
    )
}

val CardShape = RoundedCornerShape(22.dp)
