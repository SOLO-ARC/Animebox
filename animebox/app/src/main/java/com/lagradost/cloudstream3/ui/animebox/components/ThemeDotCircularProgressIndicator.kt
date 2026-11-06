package com.lagradost.cloudstream3.ui.animebox.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern 8-dot circular fading activity spinner styled with the app theme color.
 * Faithfully matches User Reference Image 8 (8 dots arranged in a ring, fading and scaling
 * smoothly as they spin).
 */
@Composable
fun ThemeDotCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Dp = 32.dp
) {
    val context = LocalContext.current
    val effectiveColor = if (color.isSpecified) color else AnimeBoxThemeHelper.getPrimaryColor(context)

    val transition = rememberInfiniteTransition(label = "ThemeDotSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val width = this.size.width
        val height = this.size.height
        val center = Offset(width / 2f, height / 2f)
        val ringRadius = minOf(width, height) * 0.36f
        val baseDotRadius = minOf(width, height) * 0.085f

        rotate(degrees = rotation, pivot = center) {
            val dotCount = 8
            for (i in 0 until dotCount) {
                // Angle for dot i in radians (0 to 2*PI)
                val angle = (i.toFloat() / dotCount) * 2f * Math.PI.toFloat()
                val cx = center.x + ringRadius * cos(angle)
                val cy = center.y + ringRadius * sin(angle)

                // Fading opacity and proportional dot radius:
                // i = 0 is largest & most opaque (leading dot)
                // i = 7 is smallest & most transparent (trailing dot)
                val factor = (dotCount - i).toFloat() / dotCount.toFloat()
                val alpha = (0.12f + 0.88f * (factor * factor)).coerceIn(0.12f, 1f)
                val currentDotRadius = baseDotRadius * (0.6f + 0.55f * factor)

                drawCircle(
                    color = effectiveColor.copy(alpha = alpha),
                    radius = currentDotRadius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
