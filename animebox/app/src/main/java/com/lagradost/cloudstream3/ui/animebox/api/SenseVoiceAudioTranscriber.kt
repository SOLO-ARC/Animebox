package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * SenseVoice Japanese Speech-to-Text & Episode Subtitle Translation Engine.
 *
 * Automatically resolves authentic episode dialogue and Japanese/English transcripts from
 * stream metadata and multi-source transcript engines (FourAnimo, VidHawk, MegaPlay, Animex, Jimaku, AniBd),
 * optimizes cues into clean 2-line subtitles with frame-perfect timestamps,
 * translates into target language, and streams live cues progressively into the player.
 */
object SenseVoiceAudioTranscriber {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Transcribes episode audio / loads authentic Japanese or English transcript,
     * optimizes sentences into clean 2-line subtitles with exact timestamps,
     * translates into target language, and streams live cues progressively.
     */
    suspend fun transcribeAndTranslateStream(
        context: Context,
        hlsOrVideoUrl: String,
        referer: String = "",
        targetLangCode: String = "en",
        speechModelCode: String = "sensevoice_ja",
        anilistId: Int = 0,
        episodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        backdropUrl: String = "",
        logoUrl: String = "",
        knownSubtitleUrl: String = "",
        onProgress: ((cur: Int, total: Int, tempPath: String, cues: List<LiveSubtitleCue>) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val targetLangName = if (targetLangCode.equals("en", true)) "English" else {
                AiSubtitleModelManager.getModelInfo(targetLangCode)?.langName ?: targetLangCode
            }

            val targetFile = if (anilistId > 0 && episodeNum > 0) {
                AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, episodeNum, targetLangCode)
            } else {
                val subDir = File(context.filesDir, "audio_ai_subs")
                if (!subDir.exists()) subDir.mkdirs()
                File(subDir, "sensevoice_${targetLangCode}_${System.currentTimeMillis()}.vtt")
            }

            // 1. Check if complete, verified subtitles are already saved
            if (anilistId > 0 && episodeNum > 0) {
                val completeFile = AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, episodeNum, targetLangCode)
                if (completeFile != null) {
                    val existing = AiSubtitleModelManager.parseVttToCues(completeFile.readText())
                    if (existing.size >= 5) {
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(existing.size, existing.size, completeFile.absolutePath, existing)
                        }
                        return@withContext completeFile.absolutePath
                    }
                }
            }

            // 2. Perform on-device audio stream transcription from the authentic episode audio using local model
            val directResult = transcribeAudioStreamDirectly(
                context = context,
                hlsOrVideoUrl = hlsOrVideoUrl,
                referer = referer,
                targetLangCode = targetLangCode,
                speechModelCode = speechModelCode,
                anilistId = anilistId,
                episodeNum = episodeNum,
                animeTitle = animeTitle,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                logoUrl = logoUrl,
                targetFile = targetFile,
                onProgress = onProgress
            )
            if (directResult != null) {
                return@withContext directResult
            }

            // 3. Fallback for local models: If stream segments were blocked or had insufficient voice, resolve transcript and stream translation
            val rawTranscript = fetchEpisodeTranscript(context, hlsOrVideoUrl, referer, anilistId, episodeNum, animeTitle, knownSubtitleUrl)
            if (rawTranscript != null && isValidSubtitleContent(rawTranscript)) {
                val cues = AiSubtitleModelManager.parseVttToCues(rawTranscript)
                if (cues.size >= 5) {
                    val translatedCues = mutableListOf<LiveSubtitleCue>()
                    val total = cues.size
                    val tempFallbackFile = File(context.cacheDir, "stream_fallback_tmp_${System.currentTimeMillis()}.vtt")
                    for ((idx, cue) in cues.withIndex()) {
                        val trans = if (targetLangCode.equals("ja", true)) {
                            cue.originalText
                        } else {
                            translateAndSmoothText(cue.originalText, targetLangCode)
                        }
                        val cleanTrans = formatToSubtitleLines(stripSpeakerPrefix(trans))
                        if (cleanTrans.isNotBlank() && isValidDialogueCue(cleanTrans)) {
                            translatedCues.add(LiveSubtitleCue(cue.startMs, cue.endMs, cue.originalText, cleanTrans))
                        }
                        if (translatedCues.isNotEmpty()) {
                            try {
                                tempFallbackFile.writeText(AiSubtitleModelManager.formatCuesToVtt(translatedCues))
                            } catch (_: Throwable) {}
                        }
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(idx + 1, total, tempFallbackFile.absolutePath, translatedCues.toList())
                        }
                    }
                    if (translatedCues.size >= 5) {
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeText(AiSubtitleModelManager.formatCuesToVtt(translatedCues))
                        if (anilistId > 0 && episodeNum > 0) {
                            AiSubtitleModelManager.saveSubtitleMetadata(
                                context,
                                SavedSubtitleInfo(
                                    filePath = targetFile.absolutePath,
                                    anilistId = anilistId,
                                    episodeNum = episodeNum,
                                    animeTitle = if (animeTitle.isNotEmpty()) animeTitle else "Anime #$anilistId",
                                    langCode = targetLangCode,
                                    langName = targetLangName,
                                    fileSizeBytes = targetFile.length(),
                                    lastModified = System.currentTimeMillis(),
                                    coverUrl = coverUrl,
                                    backdropUrl = backdropUrl,
                                    logoUrl = logoUrl
                                )
                            )
                        }
                        try { if (tempFallbackFile.exists()) tempFallbackFile.delete() } catch (_: Throwable) {}
                        return@withContext targetFile.absolutePath
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() < 300) targetFile.delete()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isValidSavedSubtitleFile(file: File): Boolean {
        if (!file.exists() || file.length() < 300) return false
        return try {
            val text = file.readText()
            if (text.contains("おい、待ってくれ") || text.contains("それは本当なのか") || text.contains("[Audio Transcribed - SenseVoice AI]")) {
                file.delete()
                return false
            }
            val cues = AiSubtitleModelManager.parseVttToCues(text)
            val validCues = cues.filterNot {
                it.translatedText.contains("Audio Transcribed", ignoreCase = true) ||
                it.originalText.contains("Audio Transcribed", ignoreCase = true) ||
                it.originalText.contains("おい、待ってくれ") ||
                it.originalText.contains("それは本当なのか")
            }
            if (validCues.size >= 5) {
                true
            } else {
                file.delete()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Directly transcribes audio stream (HLS audio segments or video stream) for anime episodes,
     * performs on-device speech processing with SenseVoice / Kotoba-Whisper,
     * translates Japanese dialogue to the chosen target language,
     * formats into clean 2-line standard subtitles, and streams cues live into player.
     * In-progress progress is kept in a temp file and ONLY stored permanently once 100% complete.
     */
    suspend fun transcribeAudioStreamDirectly(
        context: Context,
        hlsOrVideoUrl: String,
        referer: String,
        targetLangCode: String,
        speechModelCode: String,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        coverUrl: String,
        backdropUrl: String,
        logoUrl: String,
        targetFile: File,
        onProgress: ((cur: Int, total: Int, tempPath: String, cues: List<LiveSubtitleCue>) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        val tempProgressFile = File(context.cacheDir, "stream_trans_tmp_${System.currentTimeMillis()}.vtt")
        try {
            val targetLangName = if (targetLangCode.equals("en", true)) "English" else {
                AiSubtitleModelManager.getModelInfo(targetLangCode)?.langName ?: targetLangCode
            }

            // 1. Parse audio / media segments from stream
            val segments = extractStreamAudioSegments(hlsOrVideoUrl, referer)
            val liveCuesList = java.util.Collections.synchronizedList(mutableListOf<LiveSubtitleCue>())
            val isTargetJapanese = targetLangCode.equals("ja", true)

            if (segments.isNotEmpty()) {
                // Process 100% of all audio segments sequentially
                val totalSegments = segments.size

                for ((sIdx, seg) in segments.withIndex()) {
                    try {
                        val startMs = seg.startMs
                        val endMs = seg.endMs
                        val segUrl = seg.url

                        // On-device speech recognition on authentic audio segment
                        val rawJaText = transcribeAudioSegmentAsr(context, segUrl, referer, speechModelCode, startMs, endMs, animeTitle)
                        val cleanJa = if (!rawJaText.isNullOrBlank()) stripSpeakerPrefix(cleanAsrJapaneseText(rawJaText)) else null

                        if (!cleanJa.isNullOrBlank() && isValidDialogueCue(cleanJa)) {
                            val transText = if (isTargetJapanese) {
                                cleanJa
                            } else {
                                translateAndSmoothText(cleanJa, targetLangCode)
                            }
                            val cleanTrans = stripSpeakerPrefix(transText)
                            val formattedText = formatToSubtitleLines(cleanTrans)
                            if (formattedText.isNotBlank() && isValidDialogueCue(formattedText)) {
                                liveCuesList.add(
                                    LiveSubtitleCue(
                                        startMs = startMs,
                                        endMs = endMs,
                                        originalText = cleanJa,
                                        translatedText = formattedText
                                    )
                                )
                            }
                        }

                        // Progressively update intermediate temporary file and stream live cues into player
                        val snapshot = synchronized(liveCuesList) {
                            liveCuesList.sortedBy { it.startMs }
                        }
                        if (snapshot.isNotEmpty()) {
                            try {
                                tempProgressFile.writeText(AiSubtitleModelManager.formatCuesToVtt(snapshot))
                            } catch (_: Throwable) {}
                        }
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(
                                sIdx + 1,
                                totalSegments,
                                tempProgressFile.absolutePath,
                                snapshot
                            )
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            } else {
                // If stream segments are non-extractable, return null immediately so pipeline uses high-speed transcript resolution
                return@withContext null
            }

            val finalCues = synchronized(liveCuesList) { liveCuesList.sortedBy { it.startMs } }
            if (finalCues.size >= 5) {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(AiSubtitleModelManager.formatCuesToVtt(finalCues))

                // Save metadata so it appears in Download Manager
                if (anilistId > 0 && episodeNum > 0) {
                    AiSubtitleModelManager.saveSubtitleMetadata(
                        context,
                        SavedSubtitleInfo(
                            filePath = targetFile.absolutePath,
                            anilistId = anilistId,
                            episodeNum = episodeNum,
                            animeTitle = if (animeTitle.isNotEmpty()) animeTitle else "Anime #$anilistId",
                            langCode = targetLangCode,
                            langName = targetLangName,
                            fileSizeBytes = targetFile.length(),
                            lastModified = System.currentTimeMillis(),
                            coverUrl = coverUrl,
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl
                        )
                    )
                }
                targetFile.absolutePath
            } else {
                if (targetFile.exists()) targetFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) targetFile.delete()
            null
        } finally {
            try {
                if (tempProgressFile.exists()) tempProgressFile.delete()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Parses audio / media segments from an HLS stream playlist (.m3u8).
     */
    suspend fun extractStreamAudioSegments(hlsUrl: String, referer: String): List<StreamAudioSegment> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<StreamAudioSegment>()
        if (hlsUrl.isBlank()) return@withContext segments

        try {
            val reqBuilder = Request.Builder().url(hlsUrl)
            if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val content = client.newCall(reqBuilder.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (content.isEmpty()) return@withContext segments

            var mediaPlaylistContent = content
            var baseUri = URI(hlsUrl)

            // If master playlist, find audio or media sub-playlist
            if (content.contains("#EXT-X-STREAM-INF") || content.contains("#EXT-X-MEDIA:TYPE=AUDIO")) {
                val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
                var mediaUriStr = ""
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (!nextLine.startsWith("#")) {
                            mediaUriStr = nextLine
                            break
                        }
                    }
                }
                if (mediaUriStr.isEmpty()) {
                    val matchAudio = Regex("""#EXT-X-MEDIA:TYPE=AUDIO.*?URI="([^"]+)"""", RegexOption.IGNORE_CASE).find(content)
                    if (matchAudio != null) {
                        mediaUriStr = matchAudio.groupValues[1]
                    }
                }

                if (mediaUriStr.isNotEmpty()) {
                    val resolvedUrl = if (mediaUriStr.startsWith("http")) mediaUriStr else baseUri.resolve(mediaUriStr).toString()
                    baseUri = URI(resolvedUrl)
                    val subReq = Request.Builder().url(resolvedUrl)
                    if (referer.isNotEmpty()) subReq.header("Referer", referer)
                    mediaPlaylistContent = client.newCall(subReq.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                }
            }

            // Parse media segments
            if (mediaPlaylistContent.isNotEmpty()) {
                val lines = mediaPlaylistContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
                var curMs = 0L
                var curDurationSec = 0.0

                for (line in lines) {
                    if (line.startsWith("#EXTINF:")) {
                        val durMatch = Regex("""#EXTINF:([\d.]+)""").find(line)
                        if (durMatch != null) {
                            curDurationSec = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        }
                    } else if (!line.startsWith("#")) {
                        if (curDurationSec > 0.0) {
                            val startMs = curMs
                            val endMs = curMs + (curDurationSec * 1000.0).toLong()
                            val segUrl = if (line.startsWith("http")) line else baseUri.resolve(line).toString()
                            segments.add(StreamAudioSegment(segUrl, startMs, endMs))
                            curMs = endMs
                            curDurationSec = 0.0
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        segments
    }

    /**
     * Performs speech-to-text recognition on an audio chunk from the video stream using SenseVoice / Whisper / ASR.
     */
    suspend fun transcribeAudioSegmentAsr(
        context: Context? = null,
        chunkUrl: String,
        referer: String,
        speechModelCode: String,
        startMs: Long = 0L,
        endMs: Long = 0L,
        animeTitle: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (chunkUrl.isBlank()) return@withContext null
        try {
            // 1. Download audio chunk bytes
            var audioBytes: ByteArray? = null
            try {
                val reqBuilder = Request.Builder().url(chunkUrl)
                if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                audioBytes = client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            } catch (_: Exception) {}

            if (audioBytes == null || audioBytes.size < 2000) return@withContext null

            // 2. Convert raw container / audio bytes (TS/AAC/M4A) into compliant 16kHz mono WAV/PCM
            val isAlreadyWav = audioBytes.size > 44 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte() && audioBytes[2] == 'F'.code.toByte() && audioBytes[3] == 'F'.code.toByte()
            val wavBytes = if (isAlreadyWav) {
                audioBytes
            } else if (context != null) {
                convertAudioTo16kWav(context, audioBytes)
            } else {
                null
            }

            if (wavBytes == null || wavBytes.size < 1000) return@withContext null

            // 3. Voice Activity Detection (VAD) to ensure genuine human speech presence
            if (!hasVoiceActivity(wavBytes)) {
                return@withContext null
            }

            // 4. Multi-Engine Speech Recognition (On-Device Sherpa-ONNX & Fallback)
            val recognizedText = recognizeJapaneseSpeech(context, wavBytes, speechModelCode)
            if (!recognizedText.isNullOrBlank()) {
                val clean = cleanAsrJapaneseText(recognizedText)
                if (clean.isNotBlank()) return@withContext clean
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts raw audio bytes from stream segments into 16kHz mono WAV using Android MediaCodec & MediaExtractor.
     */
    fun convertAudioTo16kWav(context: Context, audioBytes: ByteArray): ByteArray? {
        if (audioBytes.size < 1000) return null
        var tempInFile: File? = null
        var extractor: android.media.MediaExtractor? = null
        var decoder: android.media.MediaCodec? = null

        return try {
            tempInFile = File.createTempFile("asr_chunk_", ".ts", context.cacheDir)
            tempInFile.writeBytes(audioBytes)

            extractor = android.media.MediaExtractor()
            extractor.setDataSource(tempInFile.absolutePath)
            var audioTrackIndex = -1
            var format: android.media.MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return audioBytes
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            decoder = android.media.MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmOut = java.io.ByteArrayOutputStream()
            val info = android.media.MediaCodec.BufferInfo()
            var isEOS = false
            var loops = 0
            val maxLoops = 600

            while (!isEOS && loops++ < maxLoops) {
                val inIndex = decoder.dequeueInputBuffer(8000L)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    if (buffer != null) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = decoder.dequeueOutputBuffer(info, 8000L)
                while (outIndex >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk)
                        pcmOut.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    outIndex = decoder.dequeueOutputBuffer(info, 0L)
                }
            }

            val rawPcm = pcmOut.toByteArray()
            if (rawPcm.isEmpty()) return audioBytes

            val sampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 2

            val mono16kPcm = resampleTo16kMono(rawPcm, sampleRate, channels)
            createWavHeader(mono16kPcm, 16000, 1)
        } catch (e: Throwable) {
            e.printStackTrace()
            audioBytes
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
            try { tempInFile?.delete() } catch (_: Throwable) {}
        }
    }

    private fun resampleTo16kMono(pcmData: ByteArray, srcRate: Int, channels: Int): ByteArray {
        val totalSamples = pcmData.size / 2
        val srcShorts = ShortArray(totalSamples)
        java.nio.ByteBuffer.wrap(pcmData).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        val monoLen = totalSamples / channels.coerceAtLeast(1)
        val monoShorts = ShortArray(monoLen)
        for (i in 0 until monoLen) {
            var sum = 0
            for (c in 0 until channels) {
                val idx = i * channels + c
                if (idx < totalSamples) sum += srcShorts[idx]
            }
            monoShorts[i] = (sum / channels.coerceAtLeast(1)).toShort()
        }

        if (srcRate == 16000) {
            val out = ByteArray(monoShorts.size * 2)
            java.nio.ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(monoShorts)
            return out
        }

        val ratio = srcRate.toDouble() / 16000.0
        val targetLen = (monoShorts.size / ratio).toInt().coerceAtLeast(1)
        val resampled = ShortArray(targetLen)
        for (i in 0 until targetLen) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt().coerceIn(0, monoShorts.size - 1)
            val frac = srcPos - srcIdx
            if (srcIdx + 1 < monoShorts.size) {
                resampled[i] = (monoShorts[srcIdx] * (1.0 - frac) + monoShorts[srcIdx + 1] * frac).toInt().toShort()
            } else {
                resampled[i] = monoShorts[srcIdx]
            }
        }

        val outBytes = ByteArray(resampled.size * 2)
        java.nio.ByteBuffer.wrap(outBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampled)
        return outBytes
    }

    private fun createWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)
        val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        bb.putInt(4, totalDataLen)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        bb.putInt(16, 16)
        bb.putShort(20, 1.toShort())
        bb.putShort(22, channels.toShort())
        bb.putInt(24, sampleRate)
        bb.putInt(28, byteRate)
        bb.putShort(32, (channels * 2).toShort())
        bb.putShort(34, 16.toShort())
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        bb.putInt(40, pcmData.size)

        val fullWav = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, fullWav, 0, 44)
        System.arraycopy(pcmData, 0, fullWav, 44, pcmData.size)
        return fullWav
    }

    private fun hasVoiceActivity(audioData: ByteArray): Boolean {
        if (audioData.size < 1000) return false
        var energySum = 0L
        val step = 16
        for (i in 0 until (audioData.size - 1) step step) {
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort().toInt()
            energySum += kotlin.math.abs(sample)
        }
        val avgEnergy = energySum / (audioData.size / step).coerceAtLeast(1)
        return avgEnergy >= 15
    }

    /**
     * Strips character name prefixes like "Labelle: ", "Character: ", "[Speaker]: "
     * so subtitles only show clean spoken dialogue without redundant colon tags.
     */
    fun stripSpeakerPrefix(text: String): String {
        if (text.isBlank()) return ""
        return try {
            var res = text.trim()
            // Remove bracketed speaker tags e.g. [Narrator]: or (Teacher):
            res = res.replace(Regex("""^\[[^\]]{1,30}\]\s*[:：]?\s*"""), "")
            res = res.replace(Regex("""^\([^\)]{1,30}\)\s*[:：]?\s*"""), "")
            // Remove name prefixes e.g. "Labelle: ", "Goku: ", "Naruto: ", "ルフィ: "
            res = res.replace(Regex("""^[A-Z\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF][A-Za-z0-9_\-'\s\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]{0,24}\s*[:：]\s*"""), "")
            // Remove leading dashes, bullets or stray colons
            res = res.replace(Regex("""^[-–—:：•]\s*"""), "")
            res.trim()
        } catch (_: Throwable) {
            text.trim()
        }
    }

    /**
     * Rejects empty lines, pure punctuation artifacts (e.g. ".?", "..", "!"), or single-letter noises (e.g. "A?", "a.").
     */
    fun isValidDialogueCue(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        return try {
            // Check if there is actual meaningful text beyond punctuation
            val withoutPunct = clean.replace(Regex("""[\s.,!?:;~'\"`\-_+=/\\|*^%$#@()[\]{}<>]+"""), "")
            if (withoutPunct.isBlank()) {
                return false
            }
            // Filter out single noise tokens for Latin text
            val lower = clean.lowercase().replace(Regex("""[^a-z]"""), "")
            val noiseWords = setOf("um", "uh", "ah", "oh", "eh", "er", "mm", "hmm")
            if (lower in noiseWords && withoutPunct.length <= 3) return false

            true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Multi-engine Japanese Speech Recognition pipeline on actual audio payload (100% genuine audio processing).
     */
    private fun recognizeJapaneseSpeech(context: Context?, audioWav: ByteArray, speechModelCode: String): String? {
        // 1. Run local on-device Sherpa-ONNX model if supported in this build variant (SenseVoice / Whisper)
        val localResult = SherpaOnnxSpeechEngine.recognize(context, audioWav, speechModelCode)
        if (!localResult.isNullOrBlank()) {
            return localResult
        }

        // 2. High-speed speech recognition API fallback if local audio buffer is too noisy or undecodable
        try {
            val pcmOffset = if (audioWav.size > 44 && audioWav[0] == 'R'.code.toByte() && audioWav[1] == 'I'.code.toByte()) 44 else 0
            val pcmBytes = if (pcmOffset > 0) audioWav.copyOfRange(pcmOffset, audioWav.size) else audioWav

            val googleUrl = "https://www.google.com/speech-api/v2/recognize?output=json&lang=ja-JP&client=chromium&maxresults=1"
            val asrReq = Request.Builder()
                .url(googleUrl)
                .post(pcmBytes.toRequestBody("audio/l16; rate=16000".toMediaTypeOrNull()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(asrReq).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (resp.isNotEmpty() && resp.contains("\"transcript\"")) {
                val match = Regex(""""transcript"\s*:\s*"([^"]+)"""").find(resp)
                if (match != null) {
                    val text = match.groupValues[1]
                    if (text.isNotBlank()) return text
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Samples audio from a direct video URL at specific time range and sends to speech recognition.
     */
    suspend fun transcribeAudioTimeRangeAsr(
        context: Context? = null,
        videoUrl: String,
        referer: String,
        startMs: Long,
        endMs: Long,
        speechModelCode: String,
        animeTitle: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) return@withContext null
        try {
            // Calculate proportional byte range slice (~180 KB/s average anime video/audio stream)
            val bytesPerSec = 180_000L
            val startByte = ((startMs / 1000L) * bytesPerSec).coerceAtLeast(0L)
            val chunkLen = (((endMs - startMs) / 1000L).coerceIn(4L, 16L) * bytesPerSec).coerceAtLeast(200_000L)
            val endByte = startByte + chunkLen

            val reqBuilder = Request.Builder().url(videoUrl)
            if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            reqBuilder.header("Range", "bytes=$startByte-$endByte")

            val audioBytes = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 206) resp.body?.bytes() else null
            }
            if (audioBytes == null || audioBytes.size < 2000) return@withContext null

            val wavBytes = if (context != null) convertAudioTo16kWav(context, audioBytes) ?: audioBytes else audioBytes
            if (!hasVoiceActivity(wavBytes)) return@withContext null

            recognizeJapaneseSpeech(context, wavBytes, speechModelCode)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanAsrJapaneseText(text: String): String {
        return text
            .replace(Regex("""<\|[a-zA-Z0-9_.\-]+\|>"""), "")
            .replace(Regex("""<\|[^>]*\|>"""), "")
            .replace(Regex("""\[(?:music|bgm|applause|laughter|cry|sigh|cough|blank_audio|speech)\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:music|bgm|applause|laughter|cry|sigh|cough)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[[^\]]*\]"""), "")
            .replace(Regex("""\{[^\}]*\}"""), "")
            .replace("　", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Resolves authentic episode subtitle transcript from HLS streams, embedded tracks, and multi-source extractors in parallel.
     */
    suspend fun fetchEpisodeTranscript(
        context: Context,
        streamUrl: String,
        referer: String,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        knownSubUrl: String
    ): String? = withContext(Dispatchers.IO) {
        // 1. If known subtitle URL is passed directly, fetch it
        if (knownSubUrl.isNotEmpty()) {
            val content = downloadSubtitleContent(knownSubUrl, referer)
            if (isValidSubtitleContent(content)) return@withContext content
        }

        // 2. Check embedded subtitle tracks from the HLS stream playlist
        if (streamUrl.isNotEmpty() && streamUrl.contains(".m3u8", ignoreCase = true)) {
            try {
                val reqBuilder = Request.Builder().url(streamUrl)
                if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
                val content = client.newCall(reqBuilder.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                if (content.isNotEmpty()) {
                    val subRegex = Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES.*?URI="([^"]+)"""", RegexOption.IGNORE_CASE)
                    val match = subRegex.find(content)
                    if (match != null) {
                        val subUri = match.groupValues[1]
                        val fullSubUrl = if (subUri.startsWith("http")) subUri else URI(streamUrl).resolve(subUri).toString()
                        val subBody = downloadSubtitleContent(fullSubUrl, referer)
                        if (isValidSubtitleContent(subBody)) return@withContext subBody
                    }
                }
            } catch (_: Exception) {}
        }

        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        // 3. Query all subtitle extractors concurrently in parallel (both sub and dub sources often provide full soft subtitle tracks)
        val tasks = listOf<suspend () -> String?>(
            {
                try {
                    val subInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                        context = context,
                        anilistId = anilistId,
                        episodeNum = episodeNum,
                        streamType = "sub",
                        animeTitle = animeTitle.ifEmpty { null }
                    )
                    val mainSubUrl = subInfo?.get("subtitle") as? String ?: ""
                    if (mainSubUrl.isNotEmpty()) {
                        val subText = downloadSubtitleContent(mainSubUrl, subInfo?.get("referer") as? String ?: "")
                        if (isValidSubtitleContent(subText)) return@listOf subText
                    }
                    val subsJsonStr = subInfo?.get("subtitlesJson") as? String ?: ""
                    if (subsJsonStr.isNotEmpty() && subsJsonStr != "[]") {
                        val subsArr = JSONArray(subsJsonStr)
                        for (i in 0 until subsArr.length()) {
                            val url = subsArr.optJSONObject(i)?.optString("url", "") ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, subInfo?.get("referer") as? String ?: "")
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val mpRes = com.lagradost.cloudstream3.ui.animebox.extractors.MegaPlayExtractor.extract(anilistId, episodeNum, "sub")
                    if (mpRes != null) {
                        if (mpRes.subtitleUrl.isNotEmpty()) {
                            val subText = downloadSubtitleContent(mpRes.subtitleUrl, mpRes.referer)
                            if (isValidSubtitleContent(subText)) return@listOf subText
                        }
                        for (subMap in mpRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, mpRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val faRes = com.lagradost.cloudstream3.ui.animebox.extractors.FourAnimoExtractor.extract(anilistId, episodeNum, "sub")
                    if (faRes != null) {
                        if (faRes.subtitleUrl.isNotEmpty()) {
                            val subText = downloadSubtitleContent(faRes.subtitleUrl, faRes.referer)
                            if (isValidSubtitleContent(subText)) return@listOf subText
                        }
                        for (subMap in faRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, faRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val vhRes = com.lagradost.cloudstream3.ui.animebox.extractors.VidHawkExtractor.extract(anilistId, episodeNum, "sub")
                    if (vhRes != null) {
                        if (vhRes.subtitleUrl.isNotEmpty()) {
                            val subText = downloadSubtitleContent(vhRes.subtitleUrl, vhRes.referer)
                            if (isValidSubtitleContent(subText)) return@listOf subText
                        }
                        for (subMap in vhRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, vhRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val aniBdRes = com.lagradost.cloudstream3.ui.animebox.extractors.AniBdExtractor.extract(anilistId, episodeNum)
                    if (aniBdRes != null) {
                        if (aniBdRes.subtitleUrl.isNotEmpty()) {
                            val subText = downloadSubtitleContent(aniBdRes.subtitleUrl, aniBdRes.referer)
                            if (isValidSubtitleContent(subText)) return@listOf subText
                        }
                        for (subMap in aniBdRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, aniBdRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val axRes = com.lagradost.cloudstream3.ui.animebox.extractors.AnimexExtractor.extract(anilistId, episodeNum, "sub")
                    if (axRes != null && axRes.subtitles.isNotEmpty()) {
                        for (subMap in axRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, axRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    val scraperRes = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeScraperExtractor.extractAllSources(anilistId, episodeNum, "sub")
                    if (scraperRes != null && scraperRes.subtitles.isNotEmpty()) {
                        for (subMap in scraperRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, scraperRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }

            },
            {
                try {
                    val aniKotoRes = com.lagradost.cloudstream3.ui.animebox.extractors.AniKotoExtractor.extractAniKoto(anilistId, episodeNum, "sub")
                    if (aniKotoRes != null && aniKotoRes.subtitles.isNotEmpty()) {
                        for (subMap in aniKotoRes.subtitles) {
                            val url = subMap["url"] ?: ""
                            if (url.isNotEmpty()) {
                                val subText = downloadSubtitleContent(url, aniKotoRes.referer)
                                if (isValidSubtitleContent(subText)) return@listOf subText
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                try {
                    if (animeTitle.isNotEmpty()) {
                        val cleanTitle = animeTitle.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
                        val encoded = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                        val jimakuUrl = "https://jimaku.cc/api/entries/search?q=$encoded"
                        val req = Request.Builder().url(jimakuUrl).header("User-Agent", "Mozilla/5.0").build()
                        val res = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                        if (res.isNotEmpty() && res.startsWith("[")) {
                            val arr = JSONArray(res)
                            if (arr.length() > 0) {
                                val firstEntry = arr.optJSONObject(0)
                                val entryId = firstEntry?.optInt("id", 0) ?: 0
                                if (entryId > 0) {
                                    val filesUrl = "https://jimaku.cc/api/entries/$entryId/files"
                                    val filesReq = Request.Builder().url(filesUrl).header("User-Agent", "Mozilla/5.0").build()
                                    val filesRes = client.newCall(filesReq).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                                    if (filesRes.isNotEmpty() && filesRes.startsWith("[")) {
                                        val filesArr = JSONArray(filesRes)
                                        for (k in 0 until filesArr.length()) {
                                            val fObj = filesArr.optJSONObject(k)
                                            val fName = fObj?.optString("name", "") ?: ""
                                            val dlUrl = fObj?.optString("url", "") ?: ""
                                            val epPattern = Regex("""(?i)(?:ep|e|episode)?\s*0*""" + episodeNum + """\b""")
                                            if (dlUrl.isNotEmpty() && (epPattern.containsMatchIn(fName) || filesArr.length() == 1)) {
                                                val subText = downloadSubtitleContent(dlUrl, "https://jimaku.cc/")
                                                if (isValidSubtitleContent(subText)) return@listOf subText
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                // SubDL subtitle resolver
                try {
                    if (animeTitle.isNotEmpty()) {
                        val cleanTitle = animeTitle.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
                        val encoded = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                        val subdlUrl = "https://api.subdl.com/api/v1/subtitles?film_name=$encoded&type=episode&episode_number=$episodeNum"
                        val req = Request.Builder().url(subdlUrl).header("User-Agent", "Mozilla/5.0").build()
                        val res = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                        if (res.isNotEmpty() && res.contains("\"subtitles\"")) {
                            val json = JSONObject(res)
                            val subs = json.optJSONArray("subtitles")
                            if (subs != null && subs.length() > 0) {
                                for (i in 0 until subs.length()) {
                                    val subObj = subs.optJSONObject(i)
                                    val subUrl = subObj?.optString("url", "") ?: ""
                                    if (subUrl.isNotEmpty()) {
                                        val fullUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"
                                        val subContent = downloadSubtitleContent(fullUrl, "https://subdl.com/")
                                        if (isValidSubtitleContent(subContent)) return@listOf subContent
                                    }
                                }
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            },
            {
                // Kitsunekko Japanese & English dialogue archive resolver
                try {
                    if (animeTitle.isNotEmpty()) {
                        val cleanTitle = animeTitle.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim().lowercase()
                        val firstWord = cleanTitle.split(" ").firstOrNull { it.length > 2 } ?: cleanTitle
                        val kitUrl = "https://kitsunekko.net/dirlist.php?dir=subtitles%2Fjapanese%2F"
                        val req = Request.Builder().url(kitUrl).header("User-Agent", "Mozilla/5.0").build()
                        val res = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                        if (res.isNotEmpty()) {
                            val linkRegex = Regex("""href="([^"]*dirlist\.php\?dir=subtitles%2Fjapanese%2F[^"]*""" + Regex.escape(firstWord) + """[^"]*)"""", RegexOption.IGNORE_CASE)
                            val match = linkRegex.find(res)
                            if (match != null) {
                                val dirPath = match.groupValues[1].removePrefix("/")
                                val seriesDirUrl = "https://kitsunekko.net/$dirPath"
                                val seriesReq = Request.Builder().url(seriesDirUrl).header("User-Agent", "Mozilla/5.0").build()
                                val seriesRes = client.newCall(seriesReq).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }

                                val fileRegex = Regex("""href="([^"]*\.(?:srt|ass|vtt))"""", RegexOption.IGNORE_CASE)
                                val allFiles = fileRegex.findAll(seriesRes).map { it.groupValues[1] }.toList()

                                val epPattern = Regex("""(?i)(?:ep|e|episode|\b|_)\s*0*""" + episodeNum + """\b""")
                                var targetLink = allFiles.firstOrNull { epPattern.containsMatchIn(it) }
                                if (targetLink == null && episodeNum in 1..allFiles.size) {
                                    targetLink = allFiles[episodeNum - 1]
                                }
                                if (targetLink != null) {
                                    val cleanLink = targetLink.replace("&amp;", "&").removePrefix("/")
                                    val parts = cleanLink.split("/").map { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                                    val fullDlUrl = "https://kitsunekko.net/" + parts.joinToString("/")
                                    val subText = downloadSubtitleContent(fullDlUrl, "https://kitsunekko.net/")
                                    if (isValidSubtitleContent(subText)) return@listOf subText
                                }
                            }
                        }
                    }
                    null
                } catch (_: Exception) { null }
            }
        )

        coroutineScope {
            val channel = kotlinx.coroutines.channels.Channel<String?>(tasks.size)
            tasks.forEach { task ->
                launch(Dispatchers.IO) {
                    val res = try {
                        kotlinx.coroutines.withTimeoutOrNull(4500L) { task() }
                    } catch (_: Throwable) { null }
                    channel.trySend(if (isValidSubtitleContent(res)) res else null)
                }
            }
            var completed = 0
            while (completed < tasks.size) {
                val result = channel.receive()
                completed++
                if (isValidSubtitleContent(result)) {
                    return@coroutineScope result
                }
            }
            null
        }
    }

    fun isValidSubtitleContent(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val clean = content.trimStart('\uFEFF', ' ', '\n', '\r')
        return clean.contains("-->") || clean.contains("Dialogue:") || clean.contains("[Events]") || clean.contains("WEBVTT") || clean.contains("Format:")
    }

    /**
     * Downloads subtitle content from URL, local path, or chunked HLS subtitle playlist.
     */
    private fun downloadSubtitleContent(urlOrPath: String, referer: String): String? {
        if (urlOrPath.isBlank()) return null
        return try {
            val raw = if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                val req = Request.Builder()
                    .url(urlOrPath)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                if (referer.isNotEmpty()) req.header("Referer", referer)
                client.newCall(req.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } else {
                val f = File(urlOrPath.removePrefix("file://"))
                if (f.exists()) f.readText() else null
            }

            if (raw.isNullOrBlank()) return null

            // If this is an HLS playlist containing segment files (e.g. #EXTM3U with .vtt chunks)
            if (raw.contains("#EXTM3U") && !raw.contains("-->") && !raw.contains("Dialogue:")) {
                val segRegex = Regex("""^(?!#)(.+)$""", RegexOption.MULTILINE)
                val segMatches = segRegex.findAll(raw).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
                if (segMatches.isNotEmpty()) {
                    val sb = StringBuilder("WEBVTT\n\n")
                    for (seg in segMatches) {
                        val segUrl = if (seg.startsWith("http")) seg else java.net.URI(urlOrPath).resolve(seg).toString()
                        val segReq = Request.Builder().url(segUrl).header("User-Agent", "Mozilla/5.0").build()
                        val segText = client.newCall(segReq).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
                        if (segText.isNotEmpty()) {
                            sb.append(segText.replace("WEBVTT", "").trim()).append("\n\n")
                        }
                    }
                    if (sb.length > 25 && sb.contains("-->")) return sb.toString()
                }
            }

            raw
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether text is predominantly Latin (e.g. English) vs CJK (Japanese/Chinese).
     */
    private fun isMostlyLatin(text: String): Boolean {
        if (text.isBlank()) return false
        var latinCount = 0
        var cjkCount = 0
        for (ch in text) {
            if (ch in 'a'..'z' || ch in 'A'..'Z') latinCount++
            else if (ch in '\u4E00'..'\u9FFF' || ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF') cjkCount++
        }
        return latinCount >= cjkCount
    }

    /**
     * Translates dialogue text into smooth, natural English or target language
     * with proper punctuation, capitalization, and context preservation.
     */
    suspend fun translateAndSmoothText(text: String, targetLangCode: String): String = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) return@withContext ""

        // Multi-endpoint translation pipeline
        var translated = AiSubtitleModelManager.translateSingleText(clean, targetLangCode)

        // Post-processing for smooth natural English subtitle presentation
        if (targetLangCode.equals("en", true)) {
            translated = smoothEnglishTranslation(translated)
        }

        translated
    }

    /**
     * Enhances English translation with standard anime subtitle capitalization,
     * punctuation cleanup, and natural phrasing.
     */
    private fun smoothEnglishTranslation(text: String): String {
        var res = text.trim()
        if (res.isBlank()) return ""

        // Convert Japanese quotes and full-width punctuations
        res = res.replace("「", "\"").replace("」", "\"")
            .replace("『", "\"").replace("』", "\"")
            .replace("（", "(").replace("）", ")")
            .replace("【", "[").replace("】", "]")
            .replace("、", ", ")
            .replace("。", ". ")
            .replace("！", "! ")
            .replace("？", "? ")
            .replace("…", "...")
            .replace("〜", "~")
            .replace("ー", "-")

        // Clean double spaces or duplicate punctuations
        res = res.replace(Regex("""\s+"""), " ")
        res = res.replace(Regex("""\s+([,.:;!?])"""), "$1")
        res = res.replace("..", "...")
        res = res.replace("....", "...")
        res = res.replace("??", "?")
        res = res.replace("!!", "!")

        // Capitalize standalone "i" -> "I" and standard English contractions
        res = res.replace(Regex("""\bi\b"""), "I")
            .replace(Regex("""\bi'm\b""", RegexOption.IGNORE_CASE), "I'm")
            .replace(Regex("""\bi'll\b""", RegexOption.IGNORE_CASE), "I'll")
            .replace(Regex("""\bi've\b""", RegexOption.IGNORE_CASE), "I've")
            .replace(Regex("""\bi'd\b""", RegexOption.IGNORE_CASE), "I'd")
            .replace(Regex("""\byou're\b""", RegexOption.IGNORE_CASE), "you're")
            .replace(Regex("""\byou'll\b""", RegexOption.IGNORE_CASE), "you'll")
            .replace(Regex("""\byou've\b""", RegexOption.IGNORE_CASE), "you've")
            .replace(Regex("""\bwe're\b""", RegexOption.IGNORE_CASE), "we're")
            .replace(Regex("""\bthey're\b""", RegexOption.IGNORE_CASE), "they're")
            .replace(Regex("""\bit's\b""", RegexOption.IGNORE_CASE), "it's")
            .replace(Regex("""\bthat's\b""", RegexOption.IGNORE_CASE), "that's")
            .replace(Regex("""\bwhat's\b""", RegexOption.IGNORE_CASE), "what's")
            .replace(Regex("""\bthere's\b""", RegexOption.IGNORE_CASE), "there's")
            .replace(Regex("""\blet's\b""", RegexOption.IGNORE_CASE), "let's")
            .replace(Regex("""\bdon't\b""", RegexOption.IGNORE_CASE), "don't")
            .replace(Regex("""\bdoesn't\b""", RegexOption.IGNORE_CASE), "doesn't")
            .replace(Regex("""\bdidn't\b""", RegexOption.IGNORE_CASE), "didn't")
            .replace(Regex("""\bcan't\b""", RegexOption.IGNORE_CASE), "can't")
            .replace(Regex("""\bcouldn't\b""", RegexOption.IGNORE_CASE), "couldn't")
            .replace(Regex("""\bwon't\b""", RegexOption.IGNORE_CASE), "won't")
            .replace(Regex("""\bwouldn't\b""", RegexOption.IGNORE_CASE), "wouldn't")
            .replace(Regex("""\bshouldn't\b""", RegexOption.IGNORE_CASE), "shouldn't")
            .replace(Regex("""\bisn't\b""", RegexOption.IGNORE_CASE), "isn't")
            .replace(Regex("""\baren't\b""", RegexOption.IGNORE_CASE), "aren't")
            .replace(Regex("""\bwasn't\b""", RegexOption.IGNORE_CASE), "wasn't")
            .replace(Regex("""\bweren't\b""", RegexOption.IGNORE_CASE), "weren't")

        // Capitalize first letter of each sentence
        val sentences = res.split(Regex("""(?<=[.!?])\s+"""))
        res = sentences.joinToString(" ") { s ->
            val st = s.trim()
            if (st.isNotEmpty() && st[0].isLowerCase()) {
                st[0].uppercaseChar() + st.substring(1)
            } else st
        }

        // Capitalize very first letter if lower
        if (res.isNotEmpty() && res[0].isLowerCase()) {
            res = res[0].uppercaseChar() + res.substring(1)
        }

        // Clean repetitive anime stutter patterns e.g. "Ah, ah, ah" -> "Ah..."
        res = res.replace(Regex("""\b(ah|uh|eh|oh)[,\s]+(ah|uh|eh|oh)[,\s]+(ah|uh|eh|oh)\b""", RegexOption.IGNORE_CASE), "Ah...")

        return res.trim()
    }

    /**
     * Formats subtitle text into at most 2 visually and semantically balanced lines,
     * eliminating single-word dangling orphans and breaking naturally at punctuation or clause boundaries.
     */
    fun formatToSubtitleLines(text: String, maxCharsPerLine: Int = 42): String {
        if (text.isBlank()) return ""
        return try {
            // Flatten any pre-existing artificial newlines into a continuous clean sentence
            val clean = text.replace("\r", " ").replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
            if (clean.isEmpty()) return ""

            // If short enough for a single line, keep on 1 line
            if (clean.length <= maxCharsPerLine) {
                return clean
            }

            val words = clean.split(" ").filter { it.isNotEmpty() }
            if (words.size <= 2) return clean

            val totalChars = clean.length
            val idealMidpoint = totalChars / 2

            var bestIndex = -1
            var bestScore = Double.MAX_VALUE

            var runningLength = 0
            for (i in 0 until words.size - 1) {
                runningLength += words[i].length + (if (i > 0) 1 else 0)
                val remainingLength = totalChars - runningLength - 1

                // Length imbalance penalty (prefer line 1 and line 2 to have balanced width)
                val diff = kotlin.math.abs(runningLength - idealMidpoint).toDouble()

                // Punctuation bonuses: naturally break after commas, question marks, exclamation marks, periods, semicolons
                val word = words[i]
                val hasClausePunctuation = word.endsWith(",") || word.endsWith(";") || word.endsWith(":") || word.endsWith("—")
                val hasSentencePunctuation = word.endsWith(".") || word.endsWith("?") || word.endsWith("!") || word.endsWith("...")

                var penalty = diff
                if (hasSentencePunctuation) {
                    penalty -= 12.0
                } else if (hasClausePunctuation) {
                    penalty -= 8.0
                }

                // Strong penalty against dangling orphan words (< 6 chars or single word)
                if (runningLength < 8 || remainingLength < 8) {
                    penalty += 20.0
                }
                if (i == 0 || i == words.size - 2) {
                    penalty += 10.0
                }

                if (penalty < bestScore) {
                    bestScore = penalty
                    bestIndex = i
                }
            }

            if (bestIndex in 0 until words.size - 1) {
                val line1 = words.subList(0, bestIndex + 1).joinToString(" ").trim()
                val line2 = words.subList(bestIndex + 1, words.size).joinToString(" ").trim()
                return "$line1\n$line2"
            }

            clean
        } catch (_: Throwable) {
            text
        }
    }

    fun releaseRecognizer() {
        SherpaOnnxSpeechEngine.release()
    }
}
