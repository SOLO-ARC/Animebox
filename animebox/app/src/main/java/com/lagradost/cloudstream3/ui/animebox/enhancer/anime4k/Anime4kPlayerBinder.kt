package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset
import java.util.WeakHashMap

/**
 * Attaches the Anime4K GlEffect to ExoPlayer exactly like the reference player:
 * `setVideoEffects(...)` followed by `stop()` + `prepare()`.
 *
 * Media3 (1.9.x) only builds its effects video graph inside MediaCodecVideoRenderer.onEnabled()
 * when effects are already set, and resets that decision in onReset(). Effects set while the
 * renderer is already running are therefore ignored unless the renderer is re-enabled –
 * stop()/prepare() does exactly that and keeps the playback position.
 */
@OptIn(UnstableApi::class)
object Anime4kPlayerBinder {

    private val applied = WeakHashMap<ExoPlayer, String>()

    fun modeFor(preset: EnhancerPreset): Anime4kMode? = when (preset) {
        EnhancerPreset.ULTRA_CLARITY -> Anime4kMode.MODE_A
        EnhancerPreset.ANIME4K_A -> Anime4kMode.MODE_A
        EnhancerPreset.ANIME4K_B -> Anime4kMode.MODE_B
        EnhancerPreset.ANIME4K_C -> Anime4kMode.MODE_C
        else -> null
    }

    /**
     * @param restartIfNeeded true when called during playback (re-enables the renderer so the
     *        new effect graph is really built); false right before the first prepare().
     */
    fun apply(player: ExoPlayer, enabled: Boolean, preset: EnhancerPreset, halfPrecision: Boolean, restartIfNeeded: Boolean) {
        val mode = if (enabled) modeFor(preset) else null
        val effectiveHalf = halfPrecision
        val key = if (mode == null) "off" else "${mode.name}:${if (effectiveHalf) "half" else "full"}"
        val previous = applied[player]
        if (previous == key) return
        // Nothing attached yet and nothing requested → leave the default fast path untouched.
        if (previous == null && key == "off") {
            applied[player] = key
            return
        }
        applied[player] = key
        Anime4kStatus.reset()

        val effects: List<Effect> = if (mode == null) emptyList() else listOf(Anime4kEffect(mode, effectiveHalf))
        try {
            player.setVideoEffects(effects)
        } catch (t: Throwable) {
            t.printStackTrace()
            return
        }

        if (restartIfNeeded && player.playbackState != Player.STATE_IDLE) {
            val wasPlaying = player.playWhenReady
            val position = player.currentPosition
            player.stop()
            player.prepare()
            player.seekTo(position)
            player.playWhenReady = wasPlaying
        }
    }
}
