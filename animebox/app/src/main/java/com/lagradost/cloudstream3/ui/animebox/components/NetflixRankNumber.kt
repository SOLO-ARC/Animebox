package com.lagradost.cloudstream3.ui.animebox.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.lagradost.cloudstream3.R

@Composable
fun NetflixRankNumber(
    rank: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.netflix_sans_black)
                ?: ResourcesCompat.getFont(context, R.font.netflix_sans)
                ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    Canvas(modifier = modifier) {
        val text = rank.toString()
        val textSizePx = size.height * 0.94f

        // Sleek thin outline scaled proportionally with font height (~1.4% of font height)
        val outlineWidth = (textSizePx * 0.014f).coerceIn(1.8f, 5.5f)

        val fillPaint = Paint().apply {
            isAntiAlias = true
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = android.graphics.Color.WHITE // Reversed: crisp white fill
            style = Paint.Style.FILL
        }

        val shadowPaint = Paint().apply {
            isAntiAlias = true
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            this.strokeWidth = outlineWidth + 2.0f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val strokePaint = Paint().apply {
            isAntiAlias = true
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = android.graphics.Color.parseColor("#0A0A0C") // Reversed: sleek dark outline
            style = Paint.Style.STROKE
            this.strokeWidth = outlineWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val bounds = Rect()
            fillPaint.getTextBounds(text, 0, text.length, bounds)

            // Baseline alignment
            val x = 0f
            val y = size.height - (size.height * 0.04f)

            val textPath = Path()
            fillPaint.getTextPath(text, 0, text.length, x, y, textPath)

            // 1. Subtle dark drop shadow
            nativeCanvas.drawPath(textPath, shadowPaint)
            // 2. Pitch black solid fill (Theme-locked)
            nativeCanvas.drawPath(textPath, fillPaint)
            // 3. Crisp thin white outline (Theme-locked)
            nativeCanvas.drawPath(textPath, strokePaint)
        }
    }
}
