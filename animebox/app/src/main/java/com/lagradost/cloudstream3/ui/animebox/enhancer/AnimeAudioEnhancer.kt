package com.lagradost.cloudstream3.ui.animebox.enhancer

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import com.lagradost.cloudstream3.R

enum class AudioEnhancerPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val defaultBass: Float,        // 0f to 100f
    val defaultVocal: Float,       // 0f to 100f
    val defaultTreble: Float,      // 0f to 100f
    val defaultVirtualizer: Float, // 0f to 100f
    val defaultLoudness: Float     // 0f to 100f
) {
    STUDIO_BALANCED(
        id = "studio_balanced",
        title = "Studio Balanced",
        subtitle = "Mastered Reference Studio Curve",
        description = "Optimized studio monitor equalization providing warmth, crisp instrument isolation, and acoustic voice presence.",
        defaultBass = 35f,
        defaultVocal = 45f,
        defaultTreble = 40f,
        defaultVirtualizer = 20f,
        defaultLoudness = 15f
    ),
    BASS_BOOST(
        id = "bass_boost",
        title = "Thunder Bass",
        subtitle = "Punchy Low-End & Seismic Rumble",
        description = "Deep sub-bass amplification delivering chest-thumping energy for anime battle impacts, explosions, and soundtracks.",
        defaultBass = 80f,
        defaultVocal = 15f,
        defaultTreble = 20f,
        defaultVirtualizer = 40f,
        defaultLoudness = 25f
    ),
    VOCAL_CLARITY(
        id = "vocal_clarity",
        title = "Dialogue & Dub Clarity",
        subtitle = "Crystal-Clear Character Voices",
        description = "Mid-range harmonic boost that lifts dialogue and anime character voices effortlessly over background music and effects.",
        defaultBass = 10f,
        defaultVocal = 85f,
        defaultTreble = 40f,
        defaultVirtualizer = 20f,
        defaultLoudness = 20f
    ),
    CINEMATIC_SURROUND(
        id = "cinematic_surround",
        title = "Cinematic Theater",
        subtitle = "Spacious 3D Soundstage",
        description = "Expansive acoustic stereo widening and room virtualizer that replicates a high-end cinema surround experience on any headphones.",
        defaultBass = 55f,
        defaultVocal = 45f,
        defaultTreble = 50f,
        defaultVirtualizer = 85f,
        defaultLoudness = 30f
    ),
    ANIME_ACTION(
        id = "anime_action",
        title = "Shonen Action Crisp",
        subtitle = "High-Energy Dynamic Boost",
        description = "Ultra-vibrant punchy frequency profile combining razor-sharp treble with powerful bass response for intense anime fight sequences.",
        defaultBass = 70f,
        defaultVocal = 40f,
        defaultTreble = 75f,
        defaultVirtualizer = 60f,
        defaultLoudness = 35f
    ),
    NIGHT_MODE(
        id = "night_mode",
        title = "Night Mode",
        subtitle = "Dynamic Compression & Soft SFX",
        description = "Intelligently suppresses harsh sudden blast spikes while maintaining crystal-clear conversational voice clarity at lower listening levels.",
        defaultBass = 0f,
        defaultVocal = 60f,
        defaultTreble = 0f,
        defaultVirtualizer = 10f,
        defaultLoudness = 0f
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Tuning",
        subtitle = "Manual Equalizer & Audio FX",
        description = "Full manual control over bass boost, speech clarity, high-frequency treble, 3D virtualization, and loudness gain.",
        defaultBass = 50f,
        defaultVocal = 50f,
        defaultTreble = 50f,
        defaultVirtualizer = 50f,
        defaultLoudness = 20f
    );

    companion object {
        fun fromId(id: String): AudioEnhancerPreset {
            return entries.firstOrNull { it.id == id } ?: STUDIO_BALANCED
        }
    }
}

/**
 * Real-time Hardware Audio Enhancement Engine.
 * Utilizes Android's hardware AudioEffect subsystem:
 * - Equalizer (multi-band shaping for vocal clarity & treble crispness)
 * - BassBoost (low-end sub-bass resonance)
 * - Virtualizer (spatial 3D surround sound widening)
 * - LoudnessEnhancer (perceptual acoustic gain without distortion)
 *
 * Applies seamlessly to both ExoPlayer playback and the built-in demo sound testing player.
 */
object AnimeAudioEnhancer {

    private const val PREFS_NAME = "anime_audio_enhancer_prefs"
    const val KEY_ENABLED = "audio_enhancer_enabled"
    const val KEY_PRESET_ID = "audio_enhancer_preset_id"
    const val KEY_BASS = "audio_enhancer_bass"
    const val KEY_VOCAL = "audio_enhancer_vocal"
    const val KEY_TREBLE = "audio_enhancer_treble"
    const val KEY_VIRTUALIZER = "audio_enhancer_virtualizer"
    const val KEY_LOUDNESS = "audio_enhancer_loudness"

    private var activeSessionId: Int = -1
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Demo Audio Player state
    private var demoPlayer: MediaPlayer? = null
    private var isDemoPlaying: Boolean = false
    private var demoSessionId: Int = -1
    private var demoEqualizer: Equalizer? = null
    private var demoBassBoost: BassBoost? = null
    private var demoVirtualizer: Virtualizer? = null
    private var demoLoudnessEnhancer: LoudnessEnhancer? = null

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Attach and apply audio effects to a given audio session ID (e.g. from ExoPlayer)
     */
    @Synchronized
    fun attachToSession(
        audioSessionId: Int,
        enabled: Boolean,
        bass: Float,
        vocal: Float,
        treble: Float,
        virtual: Float,
        loudness: Float
    ) {
        if (audioSessionId <= 0) return

        if (activeSessionId != audioSessionId) {
            releaseEffects()
            activeSessionId = audioSessionId
            try {
                equalizer = Equalizer(0, audioSessionId)
                bassBoost = BassBoost(0, audioSessionId)
                virtualizer = Virtualizer(0, audioSessionId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        applyParameters(
            equalizer = equalizer,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            loudnessEnhancer = loudnessEnhancer,
            enabled = enabled,
            bass = bass,
            vocal = vocal,
            treble = treble,
            virtual = virtual,
            loudness = loudness
        )
    }

    private fun applyParameters(
        equalizer: Equalizer?,
        bassBoost: BassBoost?,
        virtualizer: Virtualizer?,
        loudnessEnhancer: LoudnessEnhancer?,
        enabled: Boolean,
        bass: Float,
        vocal: Float,
        treble: Float,
        virtual: Float,
        loudness: Float
    ) {
        try {
            // BassBoost
            bassBoost?.let { bb ->
                bb.enabled = enabled && bass > 0f
                if (bb.strengthSupported) {
                    bb.setStrength((bass.coerceIn(0f, 100f) * 10f).toInt().toShort()) // 0 to 1000
                }
            }

            // Virtualizer (3D Surround)
            virtualizer?.let { virt ->
                virt.enabled = enabled && virtual > 0f
                if (virt.strengthSupported) {
                    virt.setStrength((virtual.coerceIn(0f, 100f) * 10f).toInt().toShort()) // 0 to 1000
                }
            }

            // LoudnessEnhancer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer?.let { le ->
                    le.enabled = enabled && loudness > 0f
                    val gainMb = (loudness.coerceIn(0f, 100f) * 8f).toInt() // 0 to 800 mB (~8dB boost)
                    le.setTargetGain(gainMb)
                }
            }

            // Equalizer (Vocal clarity & Treble shaping)
            equalizer?.let { eq ->
                eq.enabled = enabled && (vocal > 0f || treble > 0f || bass > 0f)
                val numBands = eq.numberOfBands
                val bandRange = eq.bandLevelRange // [minLevel, maxLevel] usually -1500 to 1500 mB
                val minLevel = bandRange[0]
                val maxLevel = bandRange[1]

                for (band in 0 until numBands) {
                    val centerFreq = eq.getCenterFreq(band.toShort()) // in mHz, e.g. 60000 = 60Hz, 1000000 = 1kHz
                    val targetLevel = when {
                        // Low Frequencies (Sub & Mid Bass: < 250Hz)
                        centerFreq < 250_000 -> {
                            (bass / 100f) * maxLevel * 0.7f
                        }
                        // Vocal Mid-range Frequencies (1kHz - 4kHz)
                        centerFreq in 800_000..4_000_000 -> {
                            (vocal / 100f) * maxLevel * 0.85f
                        }
                        // Treble High Frequencies (> 5kHz)
                        centerFreq > 5_000_000 -> {
                            (treble / 100f) * maxLevel * 0.9f
                        }
                        else -> 0f
                    }
                    val clamped = targetLevel.toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                    eq.setBandLevel(band.toShort(), clamped)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Synchronized
    fun releaseEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer?.release()
            }
        } catch (_: Throwable) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        activeSessionId = -1
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DEMO AUDIO TESTING PLAYER
    // ─────────────────────────────────────────────────────────────────────────────

    fun isDemoAudioPlaying(): Boolean = isDemoPlaying

    fun playDemoAudio(
        context: Context,
        enabled: Boolean,
        bass: Float,
        vocal: Float,
        treble: Float,
        virtual: Float,
        loudness: Float,
        onPlaybackStateChanged: (Boolean) -> Unit = {}
    ) {
        // Test demo audio removed to save app storage space
        stopDemoAudio()
        isDemoPlaying = false
        onPlaybackStateChanged(false)
    }

    fun updateDemoParameters(
        enabled: Boolean,
        bass: Float,
        vocal: Float,
        treble: Float,
        virtual: Float,
        loudness: Float
    ) {
        if (isDemoPlaying && demoSessionId > 0) {
            applyParameters(
                equalizer = demoEqualizer,
                bassBoost = demoBassBoost,
                virtualizer = demoVirtualizer,
                loudnessEnhancer = demoLoudnessEnhancer,
                enabled = enabled,
                bass = bass,
                vocal = vocal,
                treble = treble,
                virtual = virtual,
                loudness = loudness
            )
        }
    }

    fun updateDemoAudio(
        enabled: Boolean,
        bass: Float = 0f,
        vocal: Float = 0f,
        treble: Float = 0f,
        virtual: Float = 0f,
        loudness: Float = 0f,
        bassBoostStrength: Float = bass,
        vocalClarity: Float = vocal,
        trebleClarity: Float = treble,
        virtualizer: Float = virtual,
        loudnessGain: Float = loudness
    ) {
        val b = if (bassBoostStrength != 0f) bassBoostStrength else bass
        val v = if (vocalClarity != 0f) vocalClarity else vocal
        val t = if (trebleClarity != 0f) trebleClarity else treble
        val virt = if (virtualizer != 0f) virtualizer else virtual
        val l = if (loudnessGain != 0f) loudnessGain else loudness
        updateDemoParameters(enabled, b, v, t, virt, l)
    }

    fun stopDemoAudio() {
        try {
            demoPlayer?.stop()
            demoPlayer?.release()
            demoEqualizer?.release()
            demoBassBoost?.release()
            demoVirtualizer?.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                demoLoudnessEnhancer?.release()
            }
        } catch (_: Throwable) {}
        demoPlayer = null
        demoEqualizer = null
        demoBassBoost = null
        demoVirtualizer = null
        demoLoudnessEnhancer = null
        demoSessionId = -1
        isDemoPlaying = false
    }

    @Synchronized
    fun release() {
        stopDemoAudio()
        releaseEffects()
    }
}
