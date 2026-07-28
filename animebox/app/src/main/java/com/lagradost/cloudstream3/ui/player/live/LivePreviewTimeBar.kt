package com.lagradost.cloudstream3.ui.player.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import java.lang.ref.WeakReference

@OptIn(UnstableApi::class)
class LivePreviewTimeBar(val ctx: Context, attrs: AttributeSet) : PreviewTimeBar(ctx, attrs) {

    private var _currentPlayerView: WeakReference<PlayerView>? = null
    val currentPlayer: Player? get() = _currentPlayerView?.get()?.player

    var videoStamps: List<VideoSkipStamp> = emptyList()
        set(value) {
            field = value
            postInvalidate()
        }

    private val stampPaint = Paint().apply {
        color = Color.parseColor("#FFCC00") // Premium Gold Yellow
        style = Paint.Style.FILL
    }

    fun registerPlayerView(player: PlayerView?) {
        _currentPlayerView = WeakReference(player)
        val controller =
            _currentPlayerView?.get()?.findViewById<PlayerControlView>(R.id.exo_controller)

        controller?.setProgressUpdateListener { position, bufferedPosition ->
            currentPlayer?.let { player ->
                if (isAtLiveEdge()) {
                    setPosition(player.duration)
                }
            }
        }
    }

    fun isAtLiveEdge(): Boolean {
        return LiveHelper.getLiveManager(currentPlayer)?.isAtLiveEdge() == true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val player = currentPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return

        try {
            var clazz: Class<*>? = this.javaClass
            var field: java.lang.reflect.Field? = null
            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField("progressBar")
                } catch (e: Exception) {
                    clazz = clazz.superclass
                }
            }
            if (field != null) {
                field.isAccessible = true
                val rect = field.get(this) as? Rect
                if (rect != null) {
                    videoStamps.forEach { stamp ->
                        val startMs = stamp.timestamp.startMs
                        val endMs = stamp.timestamp.endMs
                        if (startMs in 0..duration && endMs in startMs..duration) {
                            val startX = rect.left + (startMs.toFloat() / duration) * rect.width()
                            val endX = rect.left + (endMs.toFloat() / duration) * rect.width()
                            canvas.drawRect(startX, rect.top.toFloat(), endX, rect.bottom.toFloat(), stampPaint)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}