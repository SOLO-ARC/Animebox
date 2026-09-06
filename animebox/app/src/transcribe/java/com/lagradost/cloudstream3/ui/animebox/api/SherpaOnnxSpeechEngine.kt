package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcribe flavor implementation for on-device Sherpa-ONNX speech recognition (SenseVoice & Whisper).
 * Uses native libraries from com.bihe0832.android:lib-sherpa-onnx.
 */
object SherpaOnnxSpeechEngine {
    val isSupported: Boolean = true

    @Volatile
    private var cachedRecognizer: Pair<String, com.k2fsa.sherpa.onnx.OfflineRecognizer>? = null
    private val recognizerLock = Any()

    private fun getOrCreateRecognizer(context: Context, speechModelCode: String): com.k2fsa.sherpa.onnx.OfflineRecognizer? {
        synchronized(recognizerLock) {
            if (cachedRecognizer?.first == speechModelCode && cachedRecognizer?.second != null) {
                return cachedRecognizer!!.second
            }

            try {
                cachedRecognizer?.second?.release()
            } catch (_: Throwable) {}
            cachedRecognizer = null

            val modelDir = AiSubtitleModelManager.getModelDir(context)
            val subDir = File(modelDir, speechModelCode.lowercase())
            if (!subDir.exists()) return null

            val isSense = AiSubtitleModelManager.SENSEVOICE_MODELS.any { it.langCode.equals(speechModelCode, true) }

            val whisperSize = when {
                speechModelCode.contains("tiny", true) -> "tiny"
                speechModelCode.contains("base", true) -> "base"
                speechModelCode.contains("small", true) -> "small"
                speechModelCode.contains("medium", true) -> "medium"
                else -> "base"
            }

            val tokensFile = listOf(
                File(subDir, "tokens.txt"),
                File(subDir, "$whisperSize-tokens.txt"),
                File(subDir, "tiny-tokens.txt"),
                File(subDir, "base-tokens.txt"),
                File(subDir, "small-tokens.txt"),
                File(subDir, "medium-tokens.txt"),
                File(subDir, "tokens_${speechModelCode.lowercase()}.txt")
            ).firstOrNull { it.exists() && it.length() >= 50L }

            val senseModelFile = listOf(
                File(subDir, "model.int8.onnx"),
                File(subDir, "model.onnx"),
                File(subDir, "decoder.int8.onnx")
            ).firstOrNull { it.exists() && it.length() >= 15_000_000L }

            val whisperEncoderFile = listOf(
                File(subDir, "$whisperSize-encoder.int8.onnx"),
                File(subDir, "encoder.int8.onnx"),
                File(subDir, "$whisperSize-encoder.onnx"),
                File(subDir, "encoder.onnx")
            ).firstOrNull { it.exists() && it.length() >= 5_000_000L }

            val whisperDecoderFile = listOf(
                File(subDir, "$whisperSize-decoder.int8.onnx"),
                File(subDir, "decoder.int8.onnx"),
                File(subDir, "$whisperSize-decoder.onnx"),
                File(subDir, "decoder.onnx"),
                File(subDir, "model.int8.onnx"),
                File(subDir, "model.onnx")
            ).firstOrNull { it.exists() && it.length() >= 15_000_000L }

            if (tokensFile == null) return null
            if (isSense && senseModelFile == null) return null
            if (!isSense && (whisperEncoderFile == null || whisperDecoderFile == null)) return null

            return try {
                val config = com.k2fsa.sherpa.onnx.OfflineRecognizerConfig().apply {
                    modelConfig.tokens = tokensFile.absolutePath
                    modelConfig.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                    modelConfig.debug = false
                    if (isSense) {
                        modelConfig.senseVoice.model = senseModelFile!!.absolutePath
                        modelConfig.senseVoice.language = "ja"
                        modelConfig.senseVoice.useInverseTextNormalization = true
                    } else {
                        modelConfig.whisper.encoder = whisperEncoderFile!!.absolutePath
                        modelConfig.whisper.decoder = whisperDecoderFile!!.absolutePath
                        modelConfig.whisper.language = "ja"
                        modelConfig.whisper.task = "transcribe"
                        modelConfig.whisper.tailPaddings = -1
                    }
                }

                val recognizer = com.k2fsa.sherpa.onnx.OfflineRecognizer(null, config)
                cachedRecognizer = speechModelCode to recognizer
                recognizer
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Genuine On-Device Speech Recognition using Sherpa-ONNX (SenseVoice, Whisper)
     */
    fun recognize(context: Context?, audioWav: ByteArray, speechModelCode: String): String? {
        if (context == null || audioWav.size < 1000) return null
        return try {
            synchronized(recognizerLock) {
                val recognizer = getOrCreateRecognizer(context, speechModelCode) ?: return null
                var stream: com.k2fsa.sherpa.onnx.OfflineStream? = null
                try {
                    val pcmOffset = if (audioWav.size > 44 && audioWav[0] == 'R'.code.toByte()) 44 else 0
                    val pcmSize = audioWav.size - pcmOffset
                    val sampleCount = pcmSize / 2
                    if (sampleCount < 1600) return null

                    val samples = FloatArray(sampleCount)
                    val bb = ByteBuffer.wrap(audioWav, pcmOffset, pcmSize).order(ByteOrder.LITTLE_ENDIAN)
                    var energySum = 0.0f
                    for (i in 0 until sampleCount) {
                        val v = (bb.short.toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                        samples[i] = v
                        energySum += kotlin.math.abs(v)
                    }

                    // Avoid decoding silent or near-zero frames
                    val avgEnergy = energySum / sampleCount.coerceAtLeast(1)
                    if (avgEnergy < 0.002f) return null

                    stream = recognizer.createStream()
                    stream.acceptWaveform(samples, 16000)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream)
                    result.text.trim()
                } finally {
                    try { stream?.release() } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // Reset cached recognizer if native runtime error occurs to prevent session corruption crashes
            synchronized(recognizerLock) {
                try { cachedRecognizer?.second?.release() } catch (_: Throwable) {}
                cachedRecognizer = null
            }
            null
        }
    }

    fun release() {
        synchronized(recognizerLock) {
            try { cachedRecognizer?.second?.release() } catch (_: Throwable) {}
            cachedRecognizer = null
        }
    }
}
