package com.lagradost.cloudstream3.ui.animebox.news

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * High-performance, hardware-accelerated animated newspaper / book turning pages.
 * Replaces the generic loading spinner with a smooth, native 60fps page-turning effect.
 */
@Composable
fun NewspaperPageFlipAnimation(
    modifier: Modifier = Modifier,
    bookWidth: Float = 56f,
    bookHeight: Float = 40f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "page_flip_transition")
    val pageTurnProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "page_turn_progress"
    )

    val halfWidth = bookWidth / 2f
    // Rotation goes from 0 degrees (flat on right) to -180 degrees (flat on left)
    val rotationY = -180f * pageTurnProgress

    Box(
        modifier = modifier
            .width(bookWidth.dp)
            .height(bookHeight.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Static base (Left Page & Right Page)
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left page
            Box(
                modifier = Modifier
                    .width(halfWidth.dp)
                    .height(bookHeight.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(Color(0xFFE2E2EA))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Header / headline bar
                    drawRoundRect(
                        color = Color(0xFF636375),
                        topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
                        size = Size(w - 7.dp.toPx(), 3.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Text line 1
                    drawRoundRect(
                        color = Color(0xFF9696A8),
                        topLeft = Offset(4.dp.toPx(), 12.dp.toPx()),
                        size = Size(w - 9.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Text line 2
                    drawRoundRect(
                        color = Color(0xFF9696A8),
                        topLeft = Offset(4.dp.toPx(), 17.dp.toPx()),
                        size = Size(w - 6.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Text line 3
                    drawRoundRect(
                        color = Color(0xFFB5B5C4),
                        topLeft = Offset(4.dp.toPx(), 22.dp.toPx()),
                        size = Size(w - 12.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Spine shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color(0x33000000)),
                            startX = w - 4.dp.toPx(),
                            endX = w
                        ),
                        topLeft = Offset(w - 4.dp.toPx(), 0f),
                        size = Size(4.dp.toPx(), h)
                    )
                }
            }

            // Right page
            Box(
                modifier = Modifier
                    .width(halfWidth.dp)
                    .height(bookHeight.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(Color(0xFFEDEDF4))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Spine shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0x33000000), Color.Transparent),
                            startX = 0f,
                            endX = 4.dp.toPx()
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(4.dp.toPx(), h)
                    )
                    // Image placeholder block
                    drawRoundRect(
                        color = Color(0xFFB0B0C2),
                        topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
                        size = Size(w - 8.dp.toPx(), 10.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    // Text line 1
                    drawRoundRect(
                        color = Color(0xFF9696A8),
                        topLeft = Offset(4.dp.toPx(), 18.dp.toPx()),
                        size = Size(w - 7.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Text line 2
                    drawRoundRect(
                        color = Color(0xFFB5B5C4),
                        topLeft = Offset(4.dp.toPx(), 23.dp.toPx()),
                        size = Size(w - 11.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
        }

        // 2. Animated Turning Page
        // Anchored at the spine (x = halfWidth) and rotating about Y axis
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    this.translationX = halfWidth * density
                    this.transformOrigin = TransformOrigin(0f, 0.5f)
                    this.rotationY = rotationY
                    this.cameraDistance = 16f * density
                }
                .width(halfWidth.dp)
                .height(bookHeight.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(if (rotationY > -90f) Color(0xFFF5F5FA) else Color(0xFFDDDDE5))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val isFront = rotationY > -90f
                if (isFront) {
                    // Front side of turning page
                    drawRoundRect(
                        color = Color(0xFF707084),
                        topLeft = Offset(3.dp.toPx(), 5.dp.toPx()),
                        size = Size(w - 6.dp.toPx(), 3.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFA0A0B2),
                        topLeft = Offset(3.dp.toPx(), 12.dp.toPx()),
                        size = Size(w - 8.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFA0A0B2),
                        topLeft = Offset(3.dp.toPx(), 17.dp.toPx()),
                        size = Size(w - 5.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                } else {
                    // Back side of turning page (seen after crossing 90 degrees)
                    drawRoundRect(
                        color = Color(0xFF8A8A9C),
                        topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                        size = Size(w - 8.dp.toPx(), 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFA8A8B8),
                        topLeft = Offset(4.dp.toPx(), 12.dp.toPx()),
                        size = Size(w - 6.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFA8A8B8),
                        topLeft = Offset(4.dp.toPx(), 17.dp.toPx()),
                        size = Size(w - 10.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
        }

        // 3. Central Spine Line
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(bookHeight.dp)
                .background(Color(0xFF32323D))
        )
    }
}
