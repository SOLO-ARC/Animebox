package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class GroqTranscribeResult {
    data class Success(val subtitleFilePath: String, val cuesCount: Int) : GroqTranscribeResult()
    data class Error(
        val message: String,
        val isRateLimit: Boolean = false,
        val isInvalidApiKey: Boolean = false
    ) : GroqTranscribeResult()
}

/**
 * Cloud-Based AI Audio Transcription Engine using Groq Whisper API (whisper-large-v3).
 * Decodes stream audio segments to standard 16kHz mono WAV before uploading,
 * displays detailed live progress, and streams verified cues into the player.
 */
object GroqCloudTranscriber {

    private const val TAG = "GroqCloudTranscriber"
    private const val GROQ_TRANSCRIPTIONS_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val GROQ_TRANSLATIONS_ENDPOINT = "https://api.groq.com/openai/v1/audio/translations"
    private const val GROQ_MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Verifies if a given Groq API key is valid.
     */
    suspend fun verifyApiKey(apiKey: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Pair(false, "API key cannot be empty.")
        }
        try {
            val req = Request.Builder()
                .url(GROQ_MODELS_ENDPOINT)
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", "AnimeBox-GroqCloud/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Pair(true, null)
                } else if (resp.code == 401) {
                    Pair(false, "Invalid Groq API key. Please check the key entered.")
                } else {
                    val body = resp.body?.string() ?: ""
                    val errMsg = try {
                        JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                    } catch (_: Exception) {
                        "HTTP ${resp.code}"
                    }
                    Pair(false, errMsg)
                }
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Network connection error.")
        }
    }

    /**
     * Resolves an audio track label, streamType, or language code from the video player
     * (e.g. "Japanese (Original)", "English", "dub", "Hindi", "French (VF)", "Spanish (Latino)")
     * into the ISO-639-1 language code expected by Groq's Whisper API.
     */
    fun resolveWhisperLanguageCode(rawAudioOrStreamType: String): String {
        val norm = rawAudioOrStreamType.trim().lowercase()
        return when {
            norm.isEmpty() -> "ja"
            norm == "en" || norm == "eng" || norm == "dub" || norm.contains("english") -> "en"
            norm == "hi" || norm == "hin" || norm.contains("hindi") -> "hi"
            norm == "ta" || norm == "tam" || norm.contains("tamil") -> "ta"
            norm == "te" || norm == "tel" || norm.contains("telugu") -> "te"
            norm == "ml" || norm == "mal" || norm.contains("malayalam") -> "ml"
            norm == "kn" || norm == "kan" || norm.contains("kannada") -> "kn"
            norm == "bn" || norm == "ben" || norm.contains("bengali") || norm.contains("bangla") -> "bn"
            norm == "mr" || norm == "mar" || norm.contains("marathi") -> "mr"
            norm == "ur" || norm == "urd" || norm.contains("urdu") -> "ur"
            norm == "fr" || norm == "fra" || norm == "fre" || norm == "vf" || norm.contains("french") || norm.contains("français") -> "fr"
            norm == "it" || norm == "ita" || norm.contains("italian") -> "it"
            norm == "es" || norm == "spa" || norm.contains("spanish") || norm.contains("español") || norm.contains("latino") || norm.contains("castellano") -> "es"
            norm == "de" || norm == "ger" || norm == "deu" || norm.contains("german") || norm.contains("deutsch") -> "de"
            norm == "pt" || norm == "por" || norm.contains("portuguese") || norm.contains("português") -> "pt"
            norm == "ru" || norm == "rus" || norm.contains("russian") -> "ru"
            norm == "ar" || norm == "ara" || norm.contains("arabic") -> "ar"
            norm == "ko" || norm == "kor" || norm.contains("korean") -> "ko"
            norm == "zh" || norm == "chi" || norm == "zho" || norm.contains("chinese") || norm.contains("mandarin") -> "zh"
            norm == "id" || norm == "ind" || norm.contains("indonesian") -> "id"
            norm == "ms" || norm == "may" || norm == "msa" || norm.contains("malay") -> "ms"
            norm == "vi" || norm == "vie" || norm.contains("vietnamese") -> "vi"
            norm == "th" || norm == "tha" || norm.contains("thai") -> "th"
            norm == "tr" || norm == "tur" || norm.contains("turkish") -> "tr"
            norm == "pl" || norm == "pol" || norm.contains("polish") -> "pl"
            norm == "ja" || norm == "jpn" || norm == "sub" || norm == "hardsub" || norm == "raw" || norm.contains("japanese") -> "ja"
            else -> "ja"
        }
    }

    /**
     * Transcribes episode audio from HLS / video stream using Groq Whisper API (whisper-large-v3)
     * and generates verified WebVTT subtitles.
     */
    suspend fun transcribeEpisodeAudio(
        context: Context,
        apiKey: String,
        hlsOrVideoUrl: String,
        referer: String = "",
        targetLangCode: String = "en",
        sourceAudioLang: String = "ja",
        anilistId: Int = 0,
        episodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        backdropUrl: String = "",
        logoUrl: String = "",
        onProgress: ((current: Int, total: Int, message: String, cues: List<LiveSubtitleCue>) -> Unit)? = null
    ): GroqTranscribeResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val resolvedSourceLang = resolveWhisperLanguageCode(sourceAudioLang)
        if (cleanKey.isBlank()) {
            return@withContext GroqTranscribeResult.Error(
                message = "Groq API key not configured. Please enter your Groq API key to transcribe.",
                isInvalidApiKey = true
            )
        }

        // 1. Check if complete, verified subtitles already exist (and are not polluted by legacy prompt echoes)
        if (anilistId > 0 && episodeNum > 0) {
            val completeFile = AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, episodeNum, targetLangCode)
            if (completeFile != null) {
                val rawContent = completeFile.readText()
                val hasPromptPollution = rawContent.contains("transcribe", ignoreCase = true) &&
                    rawContent.contains("dialogue", ignoreCase = true)
                if (hasPromptPollution) {
                    try { completeFile.delete() } catch (_: Throwable) {}
                } else {
                    val existing = AiSubtitleModelManager.parseVttToCues(rawContent)
                    if (existing.size >= 15) {
                        return@withContext GroqTranscribeResult.Success(completeFile.absolutePath, existing.size)
                    }
                }
            }
        }

        val targetFile = if (anilistId > 0 && episodeNum > 0) {
            AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, episodeNum, targetLangCode)
        } else {
            val subDir = File(context.filesDir, "audio_ai_subs")
            if (!subDir.exists()) subDir.mkdirs()
            File(subDir, "groq_${targetLangCode}_${System.currentTimeMillis()}.vtt")
        }

        try {
            // 2. Extract audio segments from the video/HLS stream
            onProgress?.invoke(0, 100, "Analyzing audio playlist...", emptyList())
            val rawSegments = SenseVoiceAudioTranscriber.extractStreamAudioSegments(hlsOrVideoUrl, referer)
            Log.d(TAG, "Extracted ${rawSegments.size} audio segments from stream. InitUrl: ${rawSegments.firstOrNull()?.initUrl}")
            if (rawSegments.isEmpty()) {
                return@withContext GroqTranscribeResult.Error(
                    message = "Could not extract audio segments from the selected video source. Please try another server."
                )
            }

            // 3. Batch segments into adaptive audio chunks with a 1-segment overlap across chunk boundaries
            // so a sentence spoken right at the cut between Part N and Part N+1 is never sliced in half!
            val chunks = mutableListOf<List<StreamAudioSegment>>()
            var currentChunk = mutableListOf<StreamAudioSegment>()
            var currentChunkDurationMs = 0L

            for (seg in rawSegments) {
                if (currentChunk.isEmpty() && chunks.isNotEmpty()) {
                    // Prepend the last segment of the previous chunk (~2-5s overlap) so boundary speech is never cut
                    val overlapSeg = chunks.last().lastOrNull()
                    if (overlapSeg != null && (overlapSeg.endMs - overlapSeg.startMs) <= 8_000L && overlapSeg.url != seg.url) {
                        currentChunk.add(overlapSeg)
                        currentChunkDurationMs += (overlapSeg.endMs - overlapSeg.startMs).coerceAtLeast(1000L)
                    }
                }
                currentChunk.add(seg)
                currentChunkDurationMs += (seg.endMs - seg.startMs).coerceAtLeast(1000L)
                val targetDuration = if (chunks.isEmpty()) 28_000L else 42_000L
                if (currentChunkDurationMs >= targetDuration) {
                    chunks.add(currentChunk.toList())
                    currentChunk = mutableListOf()
                    currentChunkDurationMs = 0L
                }
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toList())
            }

            val totalChunks = chunks.size
            val allCues = mutableListOf<LiveSubtitleCue>()
            val isTargetEnglish = targetLangCode.equals("en", true)
            var undecodableChunkCount = 0

            for ((chunkIdx, chunk) in chunks.withIndex()) {
                val partNum = chunkIdx + 1
                val chunkStartTimeMs = chunk.first().startMs

                onProgress?.invoke(
                    partNum,
                    totalChunks,
                    "Downloading audio (Part $partNum/$totalChunks)...",
                    allCues.toList()
                )

                // Optional fMP4 init segment download (with retry + logging)
                var initBytes: ByteArray? = null
                val initUrl = chunk.firstOrNull()?.initUrl
                if (!initUrl.isNullOrEmpty()) {
                    for (attempt in 1..2) {
                        try {
                            val reqBuilder = Request.Builder().url(initUrl)
                            if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
                            val ua = if (attempt == 1) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                     else "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            reqBuilder.header("User-Agent", ua)
                            initBytes = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val bytes = resp.body?.bytes()
                                    Log.d(TAG, "Init segment downloaded: ${bytes?.size ?: 0} bytes (attempt $attempt)")
                                    bytes
                                } else {
                                    Log.w(TAG, "Init segment download failed: HTTP ${resp.code} (attempt $attempt) url=$initUrl")
                                    null
                                }
                            }
                            if (initBytes != null && initBytes.isNotEmpty()) break
                        } catch (e: Exception) {
                            Log.w(TAG, "Init segment download exception (attempt $attempt): ${e.message}")
                        }
                    }
                }

                // HLS players transparently decrypt AES-128 media segments before decoding.
                // Do the same here, otherwise MediaExtractor receives ciphertext and reports
                // a false "no audio track" result (as seen with MegaPlay streams).
                val hlsKeys = ConcurrentHashMap<String, ByteArray>()
                for (keyUrl in chunk.mapNotNull { it.encryptionKeyUrl }.distinct()) {
                    val keyBytes = downloadHlsAesKey(keyUrl, referer)
                    if (keyBytes != null) {
                        hlsKeys[keyUrl] = keyBytes
                    } else {
                        Log.w(TAG, "Chunk $partNum: unable to download HLS AES-128 key")
                    }
                }

                // Parallel segment download with coroutines
                val downloadedSegments = ConcurrentHashMap<Int, ByteArray>()
                var downloadFailCount = 0
                coroutineScope {
                    chunk.mapIndexed { sIdx, seg ->
                        async(Dispatchers.IO) {
                            try {
                                val reqBuilder = Request.Builder().url(seg.url)
                                if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
                                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val segBytes = resp.body?.bytes()
                                        if (segBytes != null && segBytes.size > 100) {
                                            val decodedBytes = seg.encryptionKeyUrl?.let { keyUrl ->
                                                val key = hlsKeys[keyUrl]
                                                if (key == null) {
                                                    null
                                                } else {
                                                    decryptHlsAes128Segment(
                                                        encryptedBytes = segBytes,
                                                        key = key,
                                                        ivHex = seg.encryptionIvHex,
                                                        mediaSequence = seg.mediaSequence
                                                    )
                                                }
                                            } ?: if (seg.encryptionKeyUrl == null) segBytes else null

                                            if (decodedBytes != null && decodedBytes.size > 100) {
                                                val sanitizedBytes = stripGarbageSegmentHeader(decodedBytes)
                                                downloadedSegments[sIdx] = sanitizedBytes
                                            } else {
                                                Log.w(TAG, "Chunk $partNum seg $sIdx: HLS AES-128 decryption failed")
                                                downloadFailCount++
                                            }
                                        } else {
                                            Log.w(TAG, "Chunk $partNum seg $sIdx: empty body (${segBytes?.size ?: 0} bytes)")
                                            downloadFailCount++
                                        }
                                    } else {
                                        Log.w(TAG, "Chunk $partNum seg $sIdx: HTTP ${resp.code} for ${seg.url.take(100)}")
                                        downloadFailCount++
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Chunk $partNum seg $sIdx: download exception: ${e.message}")
                                downloadFailCount++
                            }
                        }
                    }.awaitAll()
                }

                val orderedPairs = (0 until chunk.size).mapNotNull { idx ->
                    val bytes = downloadedSegments[idx] ?: return@mapNotNull null
                    chunk[idx] to bytes
                }
                val orderedSegments = orderedPairs.map { it.second }
                Log.d(TAG, "Chunk $partNum: downloaded ${orderedSegments.size}/${chunk.size} segments (${downloadFailCount} failed), totalBytes=${orderedSegments.sumOf { it.size }}")
                if (orderedSegments.isEmpty()) {
                    Log.w(TAG, "Chunk $partNum: no segments downloaded. Skipping.")
                    continue
                }

                onProgress?.invoke(
                    partNum,
                    totalChunks,
                    "Isolating vocals & decoding audio (Part $partNum/$totalChunks)...",
                    allCues.toList()
                )

                val chunkEndTimeMs = chunk.last().endMs
                val chunkTotalDurMs = (chunkEndTimeMs - chunkStartTimeMs).coerceAtLeast(1000L)
                // Expected 16kHz 16-bit mono PCM size is 32,000 bytes/sec (32 bytes/ms)
                val minExpectedPcmBytes = ((chunkTotalDurMs * 32L) * 72L / 100L).toInt().coerceAtLeast(16000)

                // ── STEP A: Decode each segment with exact timeline tracking + continuous chunk vocal DSP ──
                Log.d(TAG, "Chunk $partNum: Decoding ${orderedPairs.size} segments (initBytes=${initBytes?.size ?: 0}, minExpectedPcm=$minExpectedPcmBytes)")
                val segmentSlices = ArrayList<WavSegmentSlice>(orderedPairs.size)
                val rawMono16kOut = ByteArrayOutputStream()
                var cumulativeWavMs = 0L

                for ((segInfo, segBytes) in orderedPairs) {
                    val segRawPcm = decodeSegmentToRaw16kMonoPcm(context, segBytes, initBytes)
                    if (segRawPcm != null && segRawPcm.isNotEmpty()) {
                        val segWavDurMs = (segRawPcm.size / 32L).coerceAtLeast(1L)
                        segmentSlices.add(
                            WavSegmentSlice(
                                wavStartMs = cumulativeWavMs,
                                wavEndMs = cumulativeWavMs + segWavDurMs,
                                streamStartMs = segInfo.startMs,
                                streamEndMs = segInfo.endMs
                            )
                        )
                        rawMono16kOut.write(segRawPcm)
                        cumulativeWavMs += segWavDurMs
                    }
                }

                var pcmBytes: ByteArray? = if (rawMono16kOut.size() >= minExpectedPcmBytes / 2) {
                    // Run the 5-stage Vocal Formant Isolation & Music Suppression DSP continuously across the whole chunk
                    enhanceRaw16kMonoPcmBytes(rawMono16kOut.toByteArray())
                } else {
                    null
                }

                if (pcmBytes == null || pcmBytes.size < minExpectedPcmBytes) {
                    Log.d(TAG, "Chunk $partNum: Per-segment decode produced ${pcmBytes?.size ?: 0} bytes (< $minExpectedPcmBytes). Trying full-chunk decode.")
                    val chunkDecoded = decodeSegmentsChunkTo16kPcm(context, orderedSegments, initBytes)
                    if (chunkDecoded != null && chunkDecoded.size > (pcmBytes?.size ?: 0)) {
                        pcmBytes = chunkDecoded
                        segmentSlices.clear()
                    }
                }

                // Determine the audio data and MIME type to send to Groq
                val audioBytes: ByteArray
                val audioFileName: String
                val audioMimeType: String

                if (pcmBytes != null && pcmBytes.size >= 2000) {
                    // ✅ MediaCodec decode succeeded → send as WAV
                    Log.d(TAG, "Chunk $partNum: MediaCodec decode OK (${pcmBytes.size} PCM bytes). Sending as WAV.")
                    audioBytes = wrapPcmInWavHeader(pcmBytes, sampleRate = 16000, channels = 1)
                    audioFileName = "chunk_${chunkIdx}.wav"
                    audioMimeType = "audio/wav"
                } else {
                    // ── STEP B: MediaMuxer audio extraction ──
                    // MediaCodec decode failed. Use MediaMuxer to extract JUST the audio track
                    // from the video segments into a clean .m4a container (no decoding needed).
                    Log.w(TAG, "Chunk $partNum: MediaCodec decode FAILED. Attempting MediaMuxer audio extraction.")

                    val m4aBytes = remuxAudioToM4a(context, orderedSegments, initBytes)

                    if (m4aBytes != null && m4aBytes.size >= 500) {
                        // ✅ MediaMuxer remux succeeded → send as .m4a
                        Log.d(TAG, "Chunk $partNum: MediaMuxer remux OK (${m4aBytes.size} bytes). Sending as .m4a")
                        audioBytes = m4aBytes
                        audioFileName = "chunk_${chunkIdx}.m4a"
                        audioMimeType = "audio/mp4"
                    } else {
                        // HLS fragments are not a valid MP4/M4A upload. Do not relabel and
                        // send them to Groq: that causes its misleading "no audio track" error.
                        undecodableChunkCount++
                        Log.w(TAG, "Chunk $partNum: no decodable/remuxable audio track; not uploading invalid fragments to Groq.")
                        onProgress?.invoke(partNum, totalChunks, "Audio format unavailable in this part; trying the next part...", allCues.toList())
                        continue

                        // ── STEP C: Raw passthrough as last resort ──
                        Log.w(TAG, "Chunk $partNum: MediaMuxer remux also failed. Trying raw passthrough.")
                        val rawOut = ByteArrayOutputStream()
                        if (initBytes != null && initBytes.isNotEmpty()) {
                            rawOut.write(initBytes)
                        }
                        for (segBytes in orderedSegments) {
                            // Strip ID3 headers if present (common in HLS AAC)
                            if (segBytes.size > 10 && segBytes[0] == 'I'.code.toByte() && segBytes[1] == 'D'.code.toByte() && segBytes[2] == '3'.code.toByte()) {
                                val id3Size = 10 + (((segBytes[6].toInt() and 0x7F) shl 21) or
                                        ((segBytes[7].toInt() and 0x7F) shl 14) or
                                        ((segBytes[8].toInt() and 0x7F) shl 7) or
                                        (segBytes[9].toInt() and 0x7F))
                                if (segBytes.size > id3Size) {
                                    rawOut.write(segBytes, id3Size, segBytes.size - id3Size)
                                } else {
                                    rawOut.write(segBytes)
                                }
                            } else {
                                rawOut.write(segBytes)
                            }
                        }
                        audioBytes = rawOut.toByteArray()

                        if (audioBytes.size < 500) {
                            Log.w(TAG, "Chunk $partNum: Raw audio too small (${audioBytes.size} bytes). Skipping.")
                            continue
                        }

                        audioFileName = "chunk_${chunkIdx}.mp4"
                        audioMimeType = "audio/mp4"
                        Log.d(TAG, "Chunk $partNum: Raw passthrough: ${audioBytes.size} bytes as $audioMimeType")
                    }
                }

                val audioSizeKb = audioBytes.size / 1024
                onProgress?.invoke(
                    partNum,
                    totalChunks,
                    "Cloud AI Transcribing (Part $partNum/$totalChunks, ${audioSizeKb}KB)...",
                    allCues.toList()
                )

                val mediaType = audioMimeType.toMediaTypeOrNull()
                val filePart = audioBytes.toRequestBody(mediaType)

                // Primary Pass: Use whisper-large-v3 on /audio/transcriptions locked to the active audio language
                // the user is watching (e.g., "ja" for Japanese, "en" for English dub, "hi" for Hindi, etc.)
                // so Whisper never misdetects language or leaks unrelated foreign movie training data.
                val formBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFileName, filePart)
                    .addFormDataPart("model", "whisper-large-v3")
                    .addFormDataPart("language", resolvedSourceLang)
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("timestamp_granularities[]", "segment")
                    .addFormDataPart("timestamp_granularities[]", "word")
                    .addFormDataPart("temperature", "0.0")

                val groqReq = Request.Builder()
                    .url(GROQ_TRANSCRIPTIONS_ENDPOINT)
                    .header("Authorization", "Bearer $cleanKey")
                    .header("User-Agent", "AnimeBox-GroqCloud/1.0")
                    .post(formBuilder.build())
                    .build()

                val responseJson = httpClient.newCall(groqReq).execute().use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    Log.d(TAG, "Chunk $partNum: Groq API response code=${resp.code}, bodyLen=${respBody.length}")
                    when (resp.code) {
                        200 -> respBody
                        401 -> return@withContext GroqTranscribeResult.Error(
                            message = "Invalid Groq API key (HTTP 401). Please check your key in My Space.",
                            isInvalidApiKey = true
                        )
                        429 -> return@withContext GroqTranscribeResult.Error(
                            message = "Groq Whisper API rate limit reached (HTTP 429). Please wait a moment or enter a different API key.",
                            isRateLimit = true
                        )
                        else -> {
                            val msg = try {
                                JSONObject(respBody).optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                            } catch (_: Exception) {
                                "HTTP ${resp.code}: $respBody"
                            }
                            return@withContext GroqTranscribeResult.Error("Groq API error: $msg")
                        }
                    }
                }

                // Parse verbose_json segments + word-level timestamps with OP/ED vocal preservation & exact timeline mapping
                val jsonObj = JSONObject(responseJson)
                val segmentsArray = jsonObj.optJSONArray("segments")
                val wordsArray = jsonObj.optJSONArray("words")
                val fullText = stripMusicNotesAndTags(jsonObj.optString("text", "").trim())
                val rawCues = mutableListOf<Triple<Long, Long, String>>()
                Log.d(TAG, "Chunk $partNum API response: segments=${segmentsArray?.length() ?: 0}, words=${wordsArray?.length() ?: 0}, textLen=${fullText.length}, text=${fullText.take(200)}")

                if (segmentsArray != null && segmentsArray.length() > 0) {
                    for (i in 0 until segmentsArray.length()) {
                        val sObj = segmentsArray.getJSONObject(i)
                        var relStartSec = sObj.optDouble("start", 0.0)
                        var relEndSec = sObj.optDouble("end", 0.0)
                        val rawSegText = sObj.optString("text", "").trim()
                        val text = stripMusicNotesAndTags(rawSegText)
                        val noSpeechProb = sObj.optDouble("no_speech_prob", 0.0)
                        val avgLogProb = sObj.optDouble("avg_logprob", 0.0)
                        val compressionRatio = sObj.optDouble("compression_ratio", 1.0)

                        val isLowConfidenceMusicOrNoise = (noSpeechProb > 0.85 && avgLogProb < -1.10) ||
                            compressionRatio > 2.65 ||
                            avgLogProb < -1.75 ||
                            (noSpeechProb > 0.90 && text.length < 10)

                        if (!isLowConfidenceMusicOrNoise && text.isNotBlank() && (relEndSec - relStartSec) >= 0.10) {
                            // Snap segment start/end to exact first/last spoken word timestamps if word timings are available
                            if (wordsArray != null && wordsArray.length() > 0) {
                                var firstWordStart = -1.0
                                var lastWordEnd = -1.0
                                for (wIdx in 0 until wordsArray.length()) {
                                    val wObj = wordsArray.optJSONObject(wIdx) ?: continue
                                    val wStart = wObj.optDouble("start", -1.0)
                                    val wEnd = wObj.optDouble("end", -1.0)
                                    val wText = stripMusicNotesAndTags(wObj.optString("word", ""))
                                    if (wText.isNotBlank() && wStart >= relStartSec - 0.15 && wEnd <= relEndSec + 0.25) {
                                        if (firstWordStart < 0.0) firstWordStart = wStart
                                        lastWordEnd = maxOf(lastWordEnd, wEnd)
                                    }
                                }
                                if (firstWordStart >= 0.0 && lastWordEnd > firstWordStart) {
                                    // Eliminate any pre-speech BGM padding that Whisper included before the first spoken word
                                    if (firstWordStart > relStartSec + 0.15) {
                                        relStartSec = firstWordStart
                                    }
                                    if (lastWordEnd < relEndSec - 0.15) {
                                        relEndSec = lastWordEnd + 0.08
                                    }
                                }
                            }

                            val cleanText = SenseVoiceAudioTranscriber.stripSpeakerPrefix(text)
                            if (cleanText.isNotBlank() && !isWhisperHallucination(cleanText, noSpeechProb, avgLogProb, false)) {
                                // Only split if Whisper bundled multiple distinct sentences into an unusually long segment (> 4.5s)
                                val segSpanSec = (relEndSec - relStartSec).coerceAtLeast(0.20)
                                val sentenceParts = if (segSpanSec > 4.5 && cleanText.length > 28) {
                                    cleanText.split(Regex("""(?<=[.?!。！？])\s*""")).map { it.trim() }.filter { it.isNotBlank() }
                                } else {
                                    listOf(cleanText)
                                }

                                if (sentenceParts.size >= 2) {
                                    val totalChars = sentenceParts.sumOf { it.length.coerceAtLeast(1) }.toDouble()
                                    var cursorSec = relStartSec
                                    for ((sIdx, part) in sentenceParts.withIndex()) {
                                        val weight = part.length.coerceAtLeast(1) / totalChars
                                        val partDur = if (sIdx == sentenceParts.lastIndex) (relEndSec - cursorSec) else (segSpanSec * weight)
                                        val pEnd = (cursorSec + partDur).coerceAtMost(relEndSec)
                                        if (!isWhisperHallucination(part, noSpeechProb, avgLogProb, false)) {
                                            val (tightStartMs, tightEndMs) = tightenCueTimestamps(
                                                chunkStartTimeMs = chunkStartTimeMs,
                                                chunkEndTimeMs = chunkEndTimeMs,
                                                relStartSec = cursorSec,
                                                relEndSec = maxOf(pEnd, cursorSec + 0.35),
                                                cleanText = part,
                                                noSpeechProb = noSpeechProb,
                                                segmentSlices = segmentSlices
                                            )
                                            rawCues.add(Triple(tightStartMs, tightEndMs, part))
                                        }
                                        cursorSec = pEnd
                                    }
                                } else {
                                    val (tightStartMs, tightEndMs) = tightenCueTimestamps(
                                        chunkStartTimeMs = chunkStartTimeMs,
                                        chunkEndTimeMs = chunkEndTimeMs,
                                        relStartSec = relStartSec,
                                        relEndSec = relEndSec,
                                        cleanText = cleanText,
                                        noSpeechProb = noSpeechProb,
                                        segmentSlices = segmentSlices
                                    )
                                    rawCues.add(Triple(tightStartMs, tightEndMs, cleanText))
                                }
                            }
                        }
                    }
                }

                // If segments array was omitted or empty, use fullText and split into dialogue cues across chunk
                if (rawCues.isEmpty() && fullText.isNotBlank() && !isWhisperHallucination(fullText, 0.0, 0.0, false)) {
                    val rawCuesFromText = splitTextIntoCues(fullText, chunkStartTimeMs, chunkEndTimeMs)
                    rawCues.addAll(rawCuesFromText)
                }

                // Dual-pass gap recovery: Check if cues are empty OR if there is an uncovered dialogue gap (>= 5.5s) in this chunk,
                // and recover any missed lines via /audio/translations (whisper-large-v3).
                val hasLargeGapInChunk = run {
                    if (rawCues.isEmpty()) true
                    else {
                        val sorted = rawCues.sortedBy { it.first }
                        var maxGap = sorted.first().first - chunkStartTimeMs
                        for (idx in 0 until sorted.size - 1) {
                            val gap = sorted[idx + 1].first - sorted[idx].second
                            if (gap > maxGap) maxGap = gap
                        }
                        val tailGap = chunkEndTimeMs - sorted.last().second
                        if (tailGap > maxGap) maxGap = tailGap
                        maxGap >= 5_500L && chunkTotalDurMs >= 12_000L
                    }
                }

                if (rawCues.isEmpty() || hasLargeGapInChunk) {
                    Log.d(TAG, "Chunk $partNum: Running dual-pass Whisper translation recovery (empty=${rawCues.isEmpty()}, gap=$hasLargeGapInChunk)")
                    try {
                        val fbFilePart = audioBytes.toRequestBody(mediaType)
                        val txFormBuilder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", audioFileName, fbFilePart)
                            .addFormDataPart("model", "whisper-large-v3")
                            .addFormDataPart("response_format", "verbose_json")
                            .addFormDataPart("temperature", "0.0")
                        val txReq = Request.Builder()
                            .url(GROQ_TRANSLATIONS_ENDPOINT)
                            .header("Authorization", "Bearer $cleanKey")
                            .header("User-Agent", "AnimeBox-GroqCloud/1.0")
                            .post(txFormBuilder.build())
                            .build()
                        val txResponse = httpClient.newCall(txReq).execute().use { it.body?.string() ?: "" }
                        val txJson = JSONObject(txResponse)
                        val txSegs = txJson.optJSONArray("segments")
                        val txText = stripMusicNotesAndTags(txJson.optString("text", "").trim())
                        if (txSegs != null && txSegs.length() > 0) {
                            for (i in 0 until txSegs.length()) {
                                val sObj = txSegs.getJSONObject(i)
                                val relStartSec = sObj.optDouble("start", 0.0)
                                val relEndSec = sObj.optDouble("end", 0.0)
                                val text = stripMusicNotesAndTags(sObj.optString("text", "").trim())
                                val noSpeechProb = sObj.optDouble("no_speech_prob", 0.0)
                                val avgLogProb = sObj.optDouble("avg_logprob", 0.0)
                                val compressionRatio = sObj.optDouble("compression_ratio", 1.0)

                                val isLowConfidence = (noSpeechProb > 0.85 && avgLogProb < -1.10) ||
                                    compressionRatio > 2.65 ||
                                    avgLogProb < -1.75

                                if (!isLowConfidence && text.isNotBlank() && (relEndSec - relStartSec) >= 0.10) {
                                    val cleanText = SenseVoiceAudioTranscriber.stripSpeakerPrefix(text)
                                    if (cleanText.isNotBlank() && !isWhisperHallucination(cleanText, noSpeechProb, avgLogProb, false)) {
                                        val (absStart, absEnd) = tightenCueTimestamps(
                                            chunkStartTimeMs = chunkStartTimeMs,
                                            chunkEndTimeMs = chunkEndTimeMs,
                                            relStartSec = relStartSec,
                                            relEndSec = relEndSec,
                                            cleanText = cleanText,
                                            noSpeechProb = noSpeechProb,
                                            segmentSlices = segmentSlices
                                        )
                                        val overlapsExisting = rawCues.any { existing ->
                                            absStart < existing.second - 250L && absEnd > existing.first + 250L
                                        }
                                        if (!overlapsExisting) {
                                            rawCues.add(Triple(absStart, absEnd, cleanText))
                                        }
                                    }
                                }
                            }
                        }
                        if (rawCues.isEmpty() && txText.isNotBlank() && !isWhisperHallucination(txText, 0.0, 0.0, false)) {
                            val fbCues = splitTextIntoCues(txText, chunkStartTimeMs, chunkEndTimeMs)
                            rawCues.addAll(fbCues)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Chunk $partNum: Dual-pass recovery failed", e)
                    }
                }

                if (rawCues.isNotEmpty()) {
                    rawCues.sortBy { it.first }
                    val hasNonAsciiOrJapanese = rawCues.any { cue ->
                        cue.third.any { c -> c in '\u3040'..'\u30ff' || c in '\u4e00'..'\u9fff' || c.code > 127 }
                    }
                    val needsTranslation = !targetLangCode.equals(resolvedSourceLang, true) && (hasNonAsciiOrJapanese || !isTargetEnglish || !resolvedSourceLang.equals("en", true))
                    val recentContext = allCues.takeLast(4).map { it.translatedText }

                    val translatedTexts = if (needsTranslation) {
                        onProgress?.invoke(
                            partNum,
                            totalChunks,
                            "AI Translating & Verifying dialogue (Part $partNum/$totalChunks)...",
                            allCues.toList()
                        )
                        val textsToTrans = rawCues.map { it.third }
                        translateBatchWithGroq(cleanKey, textsToTrans, targetLangCode, animeTitle, recentContext)
                            ?: textsToTrans.map { t ->
                                GoogleMlKitTranslatorManager.translateText(t, resolvedSourceLang, targetLangCode) ?: t
                            }
                    } else if (isTargetEnglish) {
                        onProgress?.invoke(
                            partNum,
                            totalChunks,
                            "Double-checking subtitle accuracy (Part $partNum/$totalChunks)...",
                            allCues.toList()
                        )
                        val textsToVerify = rawCues.map { it.third }
                        verifyAndPolishEnglishBatchWithGroq(cleanKey, textsToVerify, animeTitle, recentContext) ?: textsToVerify
                    } else {
                        rawCues.map { it.third }
                    }

                    val chunkProcessedCues = mutableListOf<LiveSubtitleCue>()
                    for (i in rawCues.indices) {
                        val (absStart, absEnd, origText) = rawCues[i]
                        var transText = (translatedTexts.getOrNull(i) ?: origText).trim()
                        if (transText.contains("[HALLUCINATION]", ignoreCase = true) || isWhisperHallucination(transText, 0.0, 0.0, false)) {
                            continue
                        }
                        // If target is not Japanese and any CJK characters remain in the translated line, run a fallback translation
                        if (!targetLangCode.equals("ja", true) && transText.any { c -> c in '\u3040'..'\u30ff' || c in '\u4e00'..'\u9fff' }) {
                            val mlFallback = GoogleMlKitTranslatorManager.translateText(transText, "ja", targetLangCode)
                            if (!mlFallback.isNullOrBlank()) {
                                transText = mlFallback.trim()
                            }
                        }
                        val formatted = SenseVoiceAudioTranscriber.formatToSubtitleLines(transText)
                        if (formatted.isNotBlank() && !isWhisperHallucination(formatted, 0.0, 0.0, false)) {
                            val maxDurMs = (formatted.length * 135L).coerceIn(1050L, 4300L)
                            val clampedEnd = minOf(absEnd, absStart + maxDurMs).coerceAtLeast(absStart + 500L)
                            chunkProcessedCues.add(
                                LiveSubtitleCue(
                                    startMs = absStart,
                                    endMs = clampedEnd,
                                    originalText = origText,
                                    translatedText = formatted
                                )
                            )
                        }
                    }
                    allCues.addAll(chunkProcessedCues)
                }

                sanitizeAndClampCuesInPlace(allCues)

                onProgress?.invoke(
                    partNum,
                    totalChunks,
                    "Live: ${allCues.size} subtitles ready...",
                    allCues.toList()
                )
            }

            if (allCues.isEmpty()) {
                return@withContext GroqTranscribeResult.Error(
                    message = if (undecodableChunkCount == totalChunks) {
                        "This source does not expose a readable audio stream for subtitles. Try another server or quality; Groq was not sent invalid video fragments."
                    } else {
                        "No dialogue could be detected in the episode audio by Groq Whisper."
                    }
                )
            }

            // Final pass: sort, deduplicate, clamp lingering durations, and enforce non-overlapping timestamps
            sanitizeAndClampCuesInPlace(allCues)

            // Write final verified WebVTT file
            val sb = java.lang.StringBuilder("WEBVTT\n\n")
            for (cue in allCues) {
                val startStr = formatMsToVttTime(cue.startMs)
                val endStr = formatMsToVttTime(cue.endMs)
                sb.append("$startStr --> $endStr\n")
                sb.append(cue.translatedText)
                sb.append("\n\n")
            }

            targetFile.parentFile?.mkdirs()
            targetFile.writeText(sb.toString())

            // Mark subtitle metadata as verified
            if (anilistId > 0 && episodeNum > 0) {
                AiSubtitleModelManager.saveSubtitleMetadata(
                    context = context,
                    info = SavedSubtitleInfo(
                        filePath = targetFile.absolutePath,
                        anilistId = anilistId,
                        episodeNum = episodeNum,
                        animeTitle = animeTitle,
                        langCode = targetLangCode,
                        langName = if (isTargetEnglish) "English (Groq AI)" else targetLangCode,
                        fileSizeBytes = targetFile.length(),
                        lastModified = System.currentTimeMillis(),
                        coverUrl = coverUrl,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl
                    )
                )
            }

            onProgress?.invoke(totalChunks, totalChunks, "Complete! Generated ${allCues.size} subtitles.", allCues)
            GroqTranscribeResult.Success(targetFile.absolutePath, allCues.size)

        } catch (e: Exception) {
            Log.e(TAG, "Groq transcription failed", e)
            GroqTranscribeResult.Error(
                message = e.localizedMessage ?: "Unexpected error during cloud transcription."
            )
        }
    }

    private data class WavSegmentSlice(
        val wavStartMs: Long,
        val wavEndMs: Long,
        val streamStartMs: Long,
        val streamEndMs: Long
    )

    private data class WordTimingToken(
        val word: String,
        val startSec: Double,
        val endSec: Double
    )

    private data class PcmVocalWindowStats(
        val avgRms: Float,
        val maxFrameRms: Float,
        val activeSpeechRatio: Float,
        val firstVocalOnsetSec: Double,
        val lastVocalOffsetSec: Double
    )

    /**
     * Analyzes 16kHz 16-bit mono PCM audio (32 bytes per ms) inside [relStartSec, relEndSec]
     * using 20ms (320-sample) frames to detect true vocal energy, active speech ratio,
     * and exact vocal onset/offset timestamps.
     */
    private fun analyzePcmWindowStats(
        pcmBytes: ByteArray?,
        relStartSec: Double,
        relEndSec: Double
    ): PcmVocalWindowStats {
        if (pcmBytes == null || pcmBytes.size < 640 || relEndSec <= relStartSec) {
            return PcmVocalWindowStats(
                avgRms = 1200f,
                maxFrameRms = 1800f,
                activeSpeechRatio = 0.6f,
                firstVocalOnsetSec = relStartSec,
                lastVocalOffsetSec = relEndSec
            )
        }

        val totalSamples = pcmBytes.size / 2
        val startSample = (relStartSec * 16000.0).toInt().coerceIn(0, (totalSamples - 1).coerceAtLeast(0))
        val endSample = (relEndSec * 16000.0).toInt().coerceIn(startSample + 1, totalSamples)
        val windowSamples = endSample - startSample
        if (windowSamples < 160) {
            return PcmVocalWindowStats(
                avgRms = 1000f,
                maxFrameRms = 1400f,
                activeSpeechRatio = 0.5f,
                firstVocalOnsetSec = relStartSec,
                lastVocalOffsetSec = relEndSec
            )
        }

        val frameSamples = 320 // 20ms at 16kHz
        val numFrames = ((windowSamples + frameSamples - 1) / frameSamples).coerceAtLeast(1)
        var totalSq = 0.0
        var maxRms = 0f
        var activeFrames = 0
        var firstOnsetFrame = -1
        var lastOffsetFrame = -1
        val vocalFrameThreshold = 235f

        for (f in 0 until numFrames) {
            val fStart = startSample + f * frameSamples
            val fEnd = minOf(fStart + frameSamples, endSample)
            val count = (fEnd - fStart).coerceAtLeast(1)
            var fSq = 0.0
            for (sIdx in fStart until fEnd) {
                val byteIdx = sIdx * 2
                val lo = pcmBytes[byteIdx].toInt() and 0xFF
                val hi = pcmBytes[byteIdx + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toDouble()
                fSq += sample * sample
            }
            totalSq += fSq
            val fRms = kotlin.math.sqrt(fSq / count).toFloat()
            if (fRms > maxRms) maxRms = fRms
            if (fRms >= vocalFrameThreshold) {
                activeFrames++
                if (firstOnsetFrame < 0) firstOnsetFrame = f
                lastOffsetFrame = f
            }
        }

        val avgRms = kotlin.math.sqrt(totalSq / windowSamples).toFloat()
        val activeRatio = activeFrames.toFloat() / numFrames.toFloat()
        val onsetSec = if (firstOnsetFrame >= 0) {
            (startSample + firstOnsetFrame * frameSamples) / 16000.0
        } else relStartSec
        val offsetSec = if (lastOffsetFrame >= 0) {
            (startSample + (lastOffsetFrame + 1) * frameSamples) / 16000.0
        } else relEndSec

        return PcmVocalWindowStats(
            avgRms = avgRms,
            maxFrameRms = maxRms,
            activeSpeechRatio = activeRatio,
            firstVocalOnsetSec = onsetSec.coerceIn(relStartSec, relEndSec),
            lastVocalOffsetSec = offsetSec.coerceIn(onsetSec.coerceAtLeast(relStartSec), relEndSec)
        )
    }

    /**
     * Splits multi-sentence Whisper segments into tight, natural sub-cues along word-level pause boundaries
     * (`wordsArray`), snaps each cue to exact word & PCM vocal onset/offset, and filters out silence/BGM
     * 1-3 word hallucinations using real PCM vocal energy analysis.
     */
    private fun extractSubCuesFromSegmentAndWords(
        segStartSec: Double,
        segEndSec: Double,
        segText: String,
        noSpeechProb: Double,
        avgLogProb: Double,
        wordsArray: org.json.JSONArray?,
        pcmBytes: ByteArray?,
        chunkStartTimeMs: Long,
        chunkEndTimeMs: Long,
        segmentSlices: List<WavSegmentSlice>
    ): List<Triple<Long, Long, String>> {
        val results = mutableListOf<Triple<Long, Long, String>>()
        val matchedWords = mutableListOf<WordTimingToken>()

        if (wordsArray != null && wordsArray.length() > 0) {
            for (wIdx in 0 until wordsArray.length()) {
                val wObj = wordsArray.optJSONObject(wIdx) ?: continue
                val wStart = wObj.optDouble("start", -1.0)
                val wEnd = wObj.optDouble("end", -1.0)
                val wRaw = stripMusicNotesAndTags(wObj.optString("word", ""))
                if (wRaw.isNotBlank() && wStart >= segStartSec - 0.22 && wEnd <= segEndSec + 0.30 && wEnd >= wStart) {
                    matchedWords.add(WordTimingToken(wRaw, wStart, maxOf(wEnd, wStart + 0.06)))
                }
            }
        }

        // Build candidate sub-segments (startSec, endSec, text)
        val candidateSubSegments = mutableListOf<Triple<Double, Double, String>>()
        val segDurSec = (segEndSec - segStartSec).coerceAtLeast(0.10)

        if (matchedWords.size >= 2) {
            // Group words into sub-cues whenever there is a natural speech pause or sentence boundary
            var groupStartSec = matchedWords.first().startSec
            var groupEndSec = matchedWords.first().endSec
            val currentWords = mutableListOf<String>(matchedWords.first().word)

            for (idx in 1 until matchedWords.size) {
                val prevWord = matchedWords[idx - 1]
                val curWord = matchedWords[idx]
                val gapSec = (curWord.startSec - prevWord.endSec).coerceAtLeast(0.0)
                val currentSpanSec = curWord.endSec - groupStartSec
                val lastChar = prevWord.word.trim().lastOrNull()
                val endsWithSentencePunct = lastChar != null && lastChar in charArrayOf('.', '?', '!', '。', '？', '！')
                val endsWithClausePunct = lastChar != null && lastChar in charArrayOf(',', ';', ':', '、', '，')

                val shouldSplit = (gapSec >= 0.48) ||
                    (endsWithSentencePunct && (gapSec >= 0.16 || currentSpanSec >= 2.2 || currentWords.size >= 5)) ||
                    (endsWithClausePunct && currentSpanSec >= 3.0 && gapSec >= 0.14) ||
                    (currentSpanSec >= 3.8 && currentWords.size >= 7) ||
                    (currentWords.size >= 14)

                if (shouldSplit) {
                    val joined = joinWordTokens(currentWords)
                    if (joined.isNotBlank()) {
                        candidateSubSegments.add(Triple(groupStartSec, groupEndSec, joined))
                    }
                    currentWords.clear()
                    groupStartSec = curWord.startSec
                }
                currentWords.add(curWord.word)
                groupEndSec = maxOf(groupEndSec, curWord.endSec)
            }

            if (currentWords.isNotEmpty()) {
                val joined = joinWordTokens(currentWords)
                if (joined.isNotBlank()) {
                    candidateSubSegments.add(Triple(groupStartSec, groupEndSec, joined))
                }
            }
        }

        // If word-level tokens did not cover the segment (e.g. CJK without word spaces or fallback endpoint),
        // split long multi-sentence segment text proportionally across sentences so nothing is cut off at 4.2s.
        val joinedCandidatesLen = candidateSubSegments.sumOf { it.third.length }
        if (candidateSubSegments.isEmpty() || joinedCandidatesLen < (segText.length * 0.55).toInt()) {
            candidateSubSegments.clear()
            val startBound = matchedWords.firstOrNull()?.startSec?.takeIf { it > segStartSec + 0.15 } ?: segStartSec
            val endBound = matchedWords.lastOrNull()?.endSec?.takeIf { it < segEndSec - 0.15 }?.plus(0.08) ?: segEndSec
            val spanSec = (endBound - startBound).coerceAtLeast(0.25)

            val sentenceParts = segText.split(Regex("""(?<=[.?!。！？])\s*""")).map { it.trim() }.filter { it.isNotBlank() }
            if (sentenceParts.size >= 2 && (spanSec > 3.6 || segText.length > 42)) {
                val totalChars = sentenceParts.sumOf { it.length.coerceAtLeast(1) }.toDouble()
                var cursorSec = startBound
                for ((sIdx, part) in sentenceParts.withIndex()) {
                    val weight = part.length.coerceAtLeast(1) / totalChars
                    val partDur = if (sIdx == sentenceParts.lastIndex) (endBound - cursorSec) else (spanSec * weight)
                    val pEnd = (cursorSec + partDur).coerceAtMost(endBound)
                    candidateSubSegments.add(Triple(cursorSec, maxOf(pEnd, cursorSec + 0.35), part))
                    cursorSec = pEnd
                }
            } else {
                candidateSubSegments.add(Triple(startBound, maxOf(endBound, startBound + 0.30), segText))
            }
        }

        for ((rawSubStartSec, rawSubEndSec, subRawText) in candidateSubSegments) {
            val cleanText = SenseVoiceAudioTranscriber.stripSpeakerPrefix(subRawText).trim()
            if (cleanText.isBlank()) continue

            val pcmStats = analyzePcmWindowStats(pcmBytes, rawSubStartSec, rawSubEndSec)
            val isLowVocalEnergy = pcmStats.maxFrameRms < 245f ||
                (pcmStats.avgRms < 155f && pcmStats.activeSpeechRatio < 0.12f)
            val isCompleteSilence = pcmStats.maxFrameRms < 140f && pcmStats.avgRms < 95f

            if (isCompleteSilence) {
                Log.d(TAG, "Dropped cue during complete PCM silence (maxRms=${pcmStats.maxFrameRms}): '$cleanText'")
                continue
            }

            if (isWhisperHallucination(cleanText, noSpeechProb, avgLogProb, isLowVocalEnergy)) {
                Log.d(TAG, "Filtered short/silence hallucination (noSpeech=$noSpeechProb, logProb=$avgLogProb, lowEnergy=$isLowVocalEnergy): '$cleanText'")
                continue
            }

            // Refine start/end with PCM vocal onset when Whisper's segment started early during silence
            var refinedStartSec = rawSubStartSec
            var refinedEndSec = rawSubEndSec
            if (pcmStats.firstVocalOnsetSec > rawSubStartSec + 0.16 && (rawSubEndSec - pcmStats.firstVocalOnsetSec) >= 0.25) {
                refinedStartSec = pcmStats.firstVocalOnsetSec - 0.04
            }
            if (pcmStats.lastVocalOffsetSec > refinedStartSec + 0.25 && pcmStats.lastVocalOffsetSec < rawSubEndSec - 0.22) {
                refinedEndSec = pcmStats.lastVocalOffsetSec + 0.10
            }

            val (tightStartMs, tightEndMs) = tightenCueTimestamps(
                chunkStartTimeMs = chunkStartTimeMs,
                chunkEndTimeMs = chunkEndTimeMs,
                relStartSec = refinedStartSec,
                relEndSec = refinedEndSec,
                cleanText = cleanText,
                noSpeechProb = noSpeechProb,
                segmentSlices = segmentSlices,
                hasWordTiming = matchedWords.isNotEmpty()
            )
            results.add(Triple(tightStartMs, tightEndMs, cleanText))
        }

        return results
    }

    private fun joinWordTokens(tokens: List<String>): String {
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for (raw in tokens) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(t)
            } else {
                val prevChar = sb.last()
                val curChar = t.first()
                val prevIsCjk = prevChar in '\u3040'..'\u30ff' || prevChar in '\u4e00'..'\u9fff'
                val curIsCjk = curChar in '\u3040'..'\u30ff' || curChar in '\u4e00'..'\u9fff'
                val curIsPunct = curChar in charArrayOf('.', ',', '!', '?', ';', ':', '\'', ')', ']', '。', '、', '！', '？')
                if (prevIsCjk && curIsCjk || curIsPunct) {
                    sb.append(t)
                } else {
                    sb.append(' ').append(t)
                }
            }
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Maps a relative timestamp (in ms) within the decoded WAV chunk to the exact HLS stream timeline,
     * eliminating cumulative drift between decoded audio frames and #EXTINF segment durations.
     */
    private fun mapWavTimeToStreamTimeMs(
        wavRelMs: Long,
        segmentSlices: List<WavSegmentSlice>,
        chunkStartTimeMs: Long,
        chunkEndTimeMs: Long
    ): Long {
        if (segmentSlices.isEmpty()) {
            return (chunkStartTimeMs + wavRelMs).coerceIn(chunkStartTimeMs, chunkEndTimeMs)
        }
        for (slice in segmentSlices) {
            if (wavRelMs <= slice.wavEndMs) {
                val wavSpan = (slice.wavEndMs - slice.wavStartMs).coerceAtLeast(1L)
                val streamSpan = (slice.streamEndMs - slice.streamStartMs).coerceAtLeast(1L)
                val offsetInWav = (wavRelMs - slice.wavStartMs).coerceIn(0L, wavSpan)
                return slice.streamStartMs + (offsetInWav * streamSpan) / wavSpan
            }
        }
        val last = segmentSlices.last()
        val extraMs = (wavRelMs - last.wavEndMs).coerceAtLeast(0L)
        return (last.streamEndMs + extraMs).coerceAtMost(chunkEndTimeMs)
    }

    /**
     * Tightens Whisper segment timestamps using exact per-segment PCM-to-HLS timeline mapping
     * and prevents subtitles from appearing late or lingering on screen after speech finishes.
     */
    private fun tightenCueTimestamps(
        chunkStartTimeMs: Long,
        chunkEndTimeMs: Long,
        relStartSec: Double,
        relEndSec: Double,
        cleanText: String,
        noSpeechProb: Double,
        segmentSlices: List<WavSegmentSlice> = emptyList(),
        hasWordTiming: Boolean = false
    ): Pair<Long, Long> {
        val wavStartMs = kotlin.math.round(relStartSec * 1000.0).toLong().coerceAtLeast(0L)
        val wavEndMs = kotlin.math.round(relEndSec * 1000.0).toLong().coerceAtLeast(wavStartMs + 220L)

        // Map relative WAV timestamps directly to the exact HLS segment stream timeline
        // Apply -95ms perceptual sync lead-in so subtitles appear right as the character begins speaking
        val leadInMs = if (hasWordTiming) 95L else 80L
        val rawStartMs = (mapWavTimeToStreamTimeMs(wavStartMs, segmentSlices, chunkStartTimeMs, chunkEndTimeMs) - leadInMs)
            .coerceAtLeast(chunkStartTimeMs)
        val rawEndMs = (mapWavTimeToStreamTimeMs(wavEndMs, segmentSlices, chunkStartTimeMs, chunkEndTimeMs) + 90L)
            .coerceAtLeast(rawStartMs + 420L)
        val rawDurMs = (rawEndMs - rawStartMs).coerceAtLeast(420L)

        // Natural speaking/reading duration based on character count (handles CJK & Latin scripts)
        val hasCjk = cleanText.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' }
        val msPerChar = if (hasCjk) 200L else 125L
        val naturalDurMs = (cleanText.length * msPerChar).coerceIn(1050L, 4400L)

        var adjustedStartMs = rawStartMs
        // Only trim leading silence when word timings were unavailable AND a segment is abnormally long with high no_speech_prob
        if (!hasWordTiming && rawDurMs > naturalDurMs + 2400L && noSpeechProb > 0.45) {
            val leadTrim = minOf(350L, (rawDurMs - naturalDurMs) / 4)
            adjustedStartMs = rawStartMs + leadTrim
        }

        val clampedEndMs = minOf(rawEndMs, adjustedStartMs + naturalDurMs).coerceAtLeast(adjustedStartMs + 520L)
        return adjustedStartMs to clampedEndMs
    }

    /**
     * Sorts cues, removes consecutive or overlapping duplicate cues (including 1-segment chunk overlap boundary cues),
     * clamps excessive durations, and guarantees strict non-overlap between consecutive subtitle cues.
     */
    private fun sanitizeAndClampCuesInPlace(cues: MutableList<LiveSubtitleCue>) {
        if (cues.isEmpty()) return
        cues.sortWith(compareBy<LiveSubtitleCue> { it.startMs }.thenByDescending { it.endMs - it.startMs })

        val cleaned = ArrayList<LiveSubtitleCue>(cues.size)
        for (cue in cues) {
            val prev = cleaned.lastOrNull()
            if (prev != null) {
                val normPrev = prev.translatedText.lowercase().replace(Regex("""[^a-z0-9\u3040-\u9fff]"""), "")
                val normCur = cue.translatedText.lowercase().replace(Regex("""[^a-z0-9\u3040-\u9fff]"""), "")
                val origPrev = prev.originalText.lowercase().replace(Regex("""[^a-z0-9\u3040-\u9fff]"""), "")
                val origCur = cue.originalText.lowercase().replace(Regex("""[^a-z0-9\u3040-\u9fff]"""), "")

                // Drop consecutive identical repetitions within 2.5s (classic OP/ED or silence echo)
                if (normCur.isNotEmpty() && normCur == normPrev && (cue.startMs - prev.endMs) < 2500L) {
                    continue
                }
                if (origCur.isNotEmpty() && origCur == origPrev && (cue.startMs - prev.endMs) < 2500L) {
                    continue
                }
                // Drop overlapping chunk-boundary duplicates where the same spoken line was captured in both Part N and Part N+1
                val overlapMs = minOf(prev.endMs, cue.endMs) - maxOf(prev.startMs, cue.startMs)
                val minDurMs = minOf(prev.endMs - prev.startMs, cue.endMs - cue.startMs).coerceAtLeast(1L)
                if (overlapMs > (minDurMs * 55L / 100L) && kotlin.math.abs(cue.startMs - prev.startMs) < 1200L) {
                    val isSimilar = (origCur.isNotEmpty() && origPrev.isNotEmpty() && (origCur.contains(origPrev) || origPrev.contains(origCur))) ||
                        (normCur.isNotEmpty() && normPrev.isNotEmpty() && (normCur.contains(normPrev) || normPrev.contains(normCur)))
                    if (isSimilar) {
                        continue
                    }
                }
            }
            val maxDur = (cue.translatedText.length * 130L).coerceIn(1100L, 4200L)
            val clampedEnd = minOf(cue.endMs, cue.startMs + maxDur).coerceAtLeast(cue.startMs + 500L)
            cleaned.add(cue.copy(endMs = clampedEnd))
        }

        for (i in 0 until cleaned.size - 1) {
            val cur = cleaned[i]
            val next = cleaned[i + 1]
            if (cur.endMs > next.startMs - 50L) {
                val newEnd = (next.startMs - 50L).coerceAtLeast(cur.startMs + 350L)
                cleaned[i] = cur.copy(endMs = newEnd)
            }
        }

        cues.clear()
        cues.addAll(cleaned)
    }

    /**
     * Translates a batch of Japanese/mixed subtitle texts into the target language using Groq's high-speed
     * LLaMA 3.3 70B model with sliding dialogue context and strict 1-to-1 line preservation.
     */
    private suspend fun translateBatchWithGroq(
        apiKey: String,
        texts: List<String>,
        targetLangCode: String,
        animeTitle: String = "",
        previousContextLines: List<String> = emptyList()
    ): List<String>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val targetLangName = java.util.Locale(targetLangCode).displayLanguage.ifBlank { targetLangCode }
        val animeContext = if (animeTitle.isNotBlank()) " for the anime \"$animeTitle\"" else ""
        val contextSection = if (previousContextLines.isNotEmpty()) {
            "Previous dialogue context (for pronoun/character continuity only — do NOT include these in your numbered output):\n" +
                previousContextLines.joinToString("\n") { "- $it" } + "\n\nLines to translate:\n"
        } else ""
        try {
            val promptBuilder = StringBuilder(contextSection)
            texts.forEachIndexed { i, t ->
                promptBuilder.append("${i + 1}. $t\n")
            }

            val payload = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.15)
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "You are an expert anime subtitle translator and quality verifier$animeContext. " +
                                "Translate and refine every single numbered line into natural, accurate, conversational $targetLangName suitable for anime subtitles. " +
                                "Double-check context across neighboring lines so pronouns, character names, and emotional tone are 100% accurate. " +
                                "If a line is clearly a non-dialogue subtitle watermark, website credit, news broadcast sign-off, or foreign movie training-data leak (e.g., OpenSubtitles, Subtitles by, MBC News, Sponsor credit), output `N. [HALLUCINATION]` for that line; otherwise translate ALL actual character dialogue lines 1-to-1 into $targetLangName without skipping or merging. " +
                                "Never leave Japanese characters untranslated when $targetLangName is not Japanese. " +
                                "Output ONLY the numbered lines (`1. Translated text`), one per line, with no extra commentary."
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptBuilder.toString().trim())
                    })
                }
                put("messages", messages)
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val resultMap = mutableMapOf<Int, String>()
                    val lineRegex = Regex("""^(\d+)[\.\:\-\)]\s*(.*)$""")
                    for (l in lines) {
                        val m = lineRegex.find(l)
                        if (m != null) {
                            val idx = m.groupValues[1].toIntOrNull() ?: continue
                            val cleaned = m.groupValues[2].trim().removePrefix("[SKIP]").trim()
                            if (cleaned.isNotBlank()) {
                                resultMap[idx - 1] = cleaned
                            }
                        }
                    }
                    texts.mapIndexed { idx, orig ->
                        resultMap[idx]?.takeIf { it.isNotBlank() } ?: orig
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Groq LLM translation fallback to ML Kit", e)
            null
        }
    }

    /**
     * Double-checks and polishes English Whisper translations using LLaMA 3.3 70B:
     * - Fixes awkward literal translations or remaining Japanese/Romaji fragments
     * - Preserves exact 1-to-1 line numbering without dropping dialogue lines.
     */
    private suspend fun verifyAndPolishEnglishBatchWithGroq(
        apiKey: String,
        texts: List<String>,
        animeTitle: String = "",
        previousContextLines: List<String> = emptyList()
    ): List<String>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val animeContext = if (animeTitle.isNotBlank()) " for the anime \"$animeTitle\"" else ""
        val contextSection = if (previousContextLines.isNotEmpty()) {
            "Previous dialogue context (for reference only — do NOT output these):\n" +
                previousContextLines.joinToString("\n") { "- $it" } + "\n\nNumbered lines to verify:\n"
        } else ""
        try {
            val promptBuilder = StringBuilder(contextSection)
            texts.forEachIndexed { i, t ->
                promptBuilder.append("${i + 1}. $t\n")
            }

            val payload = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.1)
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "You are an anime subtitle editor and accuracy verifier$animeContext. " +
                                "Review each numbered subtitle line generated by Whisper speech-to-text: " +
                                "Keep accurate dialogue intact, translating any remaining Japanese/Romaji words and polishing awkward phrasing into natural, accurate English dialogue. " +
                                "If a line is clearly a non-dialogue subtitle watermark, website credit, news broadcast sign-off, or foreign movie training-data leak, output `N. [HALLUCINATION]` for that line; otherwise preserve ALL actual dialogue lines 1-to-1. " +
                                "Output ONLY the numbered lines (`1. Polished English text`), one per line."
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptBuilder.toString().trim())
                    })
                }
                put("messages", messages)
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val resultMap = mutableMapOf<Int, String>()
                    val lineRegex = Regex("""^(\d+)[\.\:\-\)]\s*(.*)$""")
                    for (l in lines) {
                        val m = lineRegex.find(l)
                        if (m != null) {
                            val idx = m.groupValues[1].toIntOrNull() ?: continue
                            val cleaned = m.groupValues[2].trim().removePrefix("[SKIP]").trim()
                            if (cleaned.isNotBlank()) {
                                resultMap[idx - 1] = cleaned
                            }
                        }
                    }
                    texts.mapIndexed { idx, orig ->
                        resultMap[idx]?.takeIf { it.isNotBlank() } ?: orig
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Groq English verification pass skipped", e)
            null
        }
    }

    /** Downloads a standard 16-byte HLS AES-128 content key using the stream's request context. */
    private fun downloadHlsAesKey(keyUrl: String, referer: String): ByteArray? {
        return try {
            val request = Request.Builder().url(keyUrl).apply {
                if (referer.isNotEmpty()) header("Referer", referer)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.build()
            httpClient.newCall(request).execute().use { response ->
                response.body?.bytes()?.takeIf { response.isSuccessful && it.size == 16 }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HLS AES-128 key download failed", e)
            null
        }
    }

    /**
     * Decrypts a standard EXT-X-KEY METHOD=AES-128 segment. This is the normal HLS
     * transport decryption performed by a media player; DRM/SAMPLE-AES streams are not handled.
     */
    private fun decryptHlsAes128Segment(
        encryptedBytes: ByteArray,
        key: ByteArray,
        ivHex: String?,
        mediaSequence: Long
    ): ByteArray? {
        if (key.size != 16 || encryptedBytes.isEmpty() || encryptedBytes.size % 16 != 0) return null
        return try {
            val iv = hlsInitializationVector(ivHex, mediaSequence)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plaintext = cipher.doFinal(encryptedBytes)
            stripPkcs7Padding(plaintext)
        } catch (e: Exception) {
            Log.w(TAG, "HLS AES-128 segment decrypt failed", e)
            null
        }
    }

    private fun hlsInitializationVector(ivHex: String?, mediaSequence: Long): ByteArray {
        val cleanHex = ivHex?.removePrefix("0x")?.removePrefix("0X")?.trim().orEmpty()
        if (cleanHex.isNotEmpty()) {
            val padded = cleanHex.padStart(32, '0').takeLast(32)
            return ByteArray(16) { index ->
                padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }

        // RFC 8216 default IV: the media sequence number as an unsigned 128-bit big-endian value.
        return ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).putLong(8, mediaSequence).array()
    }

    private fun stripPkcs7Padding(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        val paddingLength = bytes.last().toInt() and 0xFF
        if (paddingLength !in 1..16 || paddingLength > bytes.size) return bytes
        val paddingStart = bytes.size - paddingLength
        return if ((paddingStart until bytes.size).all { (bytes[it].toInt() and 0xFF) == paddingLength }) {
            bytes.copyOf(paddingStart)
        } else {
            bytes
        }
    }

    fun stripGarbageSegmentHeader(raw: ByteArray): ByteArray {
        if (raw.size < 256) return raw
        // 1. TS packet sync byte 0x47
        if (raw[0] == 0x47.toByte()) return raw
        // 2. ISO BMFF (fMP4) box type at offset 4
        if (isFmp4Header(raw, 0)) return raw
        // 3. ID3 header
        if (raw[0] == 'I'.code.toByte() && raw[1] == 'D'.code.toByte() && raw[2] == '3'.code.toByte()) return raw
        // 4. ADTS AAC header
        if (raw[0] == 0xFF.toByte() && (raw[1].toInt() and 0xF0) == 0xF0) return raw

        // Check offset 252 (MegaPlay SegmentStrip padding)
        if (raw[252] == 0x47.toByte() || isFmp4Header(raw, 252)) {
            return raw.copyOfRange(252, raw.size)
        }

        // Search for first valid sync marker in first 512 bytes
        val maxSearch = minOf(512, raw.size - 8)
        for (i in 1..maxSearch) {
            if (raw[i] == 0x47.toByte()) {
                if (i + 188 < raw.size && raw[i + 188] == 0x47.toByte()) {
                    return raw.copyOfRange(i, raw.size)
                }
            }
            if (isFmp4Header(raw, i)) {
                return raw.copyOfRange(i, raw.size)
            }
        }
        return raw
    }

    private fun isFmp4Header(raw: ByteArray, offset: Int): Boolean {
        if (offset + 8 > raw.size) return false
        val type = String(raw, offset + 4, 4, Charsets.US_ASCII)
        return type == "ftyp" || type == "styp" || type == "moof" || type == "mdat" || type == "free" || type == "skip"
    }

    /**
     * Uses MediaMuxer to extract JUST the audio track from video segments into a clean .m4a container.
     * No decoding needed — copies encoded audio samples directly. This creates a file Groq can parse.
     */
    private fun remuxAudioToM4a(
        context: Context,
        segments: List<ByteArray>,
        initBytes: ByteArray? = null
    ): ByteArray? {
        if (segments.isEmpty()) return null

        var inputFile: File? = null
        var outputFile: File? = null
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            // Detect container format for temp file extension
            val firstBytes = if (initBytes != null && initBytes.isNotEmpty()) initBytes else segments.first()
            val isTs = firstBytes.size > 4 && firstBytes[0] == 0x47.toByte()
            val ext = if (isTs) ".ts" else ".mp4"

            // Write all segments to a temp input file
            inputFile = File.createTempFile("groq_remux_in_", ext, context.cacheDir)
            inputFile.outputStream().use { fos ->
                if (initBytes != null && initBytes.isNotEmpty()) {
                    fos.write(initBytes)
                }
                for (seg in segments) {
                    // Strip ID3 headers for AAC segments
                    if (seg.size > 10 && seg[0] == 'I'.code.toByte() && seg[1] == 'D'.code.toByte() && seg[2] == '3'.code.toByte()) {
                        val id3Size = 10 + (((seg[6].toInt() and 0x7F) shl 21) or
                                ((seg[7].toInt() and 0x7F) shl 14) or
                                ((seg[8].toInt() and 0x7F) shl 7) or
                                (seg[9].toInt() and 0x7F))
                        if (seg.size > id3Size) {
                            fos.write(seg, id3Size, seg.size - id3Size)
                        } else {
                            fos.write(seg)
                        }
                    } else {
                        fos.write(seg)
                    }
                }
            }

            Log.d(TAG, "remuxAudioToM4a: Input file ${inputFile.length()} bytes (ext=$ext)")

            // Open with MediaExtractor and find audio track
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                Log.d(TAG, "remuxAudioToM4a: Track $i mime=$mime")
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.w(TAG, "remuxAudioToM4a: No audio track found (trackCount=${extractor.trackCount})")
                return null
            }

            Log.d(TAG, "remuxAudioToM4a: Found audio track $audioTrackIndex: ${audioFormat.getString(MediaFormat.KEY_MIME)}")
            extractor.selectTrack(audioTrackIndex)

            // Create output .m4a file with MediaMuxer
            outputFile = File.createTempFile("groq_remux_out_", ".m4a", context.cacheDir)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            // Copy all audio samples from extractor to muxer
            val buffer = ByteBuffer.allocate(512 * 1024) // 512KB buffer
            val bufferInfo = MediaCodec.BufferInfo()
            var sampleCount = 0

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                sampleCount++
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            extractor = null

            val result = outputFile.readBytes()
            Log.d(TAG, "remuxAudioToM4a: Success! $sampleCount samples, output ${result.size} bytes")
            return result

        } catch (e: Throwable) {
            Log.e(TAG, "remuxAudioToM4a: Exception during remux", e)
            return null
        } finally {
            try { muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
            try { inputFile?.delete() } catch (_: Throwable) {}
            try { outputFile?.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Decodes all audio/video segments of a chunk into 16kHz mono 16-bit PCM in a single pass.
     * This avoids hundreds of MediaCodec decoder initializations on Android.
     */
    private fun decodeSegmentsChunkTo16kPcm(
        context: Context,
        segments: List<ByteArray>,
        initBytes: ByteArray? = null
    ): ByteArray? {
        if (segments.isEmpty()) return null

        val firstBytes = segments.first()
        val isAac = (firstBytes.size > 2 && firstBytes[0] == 0xFF.toByte() && (firstBytes[1].toInt() and 0xF0) == 0xF0)
        val isTs = firstBytes.size > 4 && firstBytes[0] == 0x47.toByte()
        val isMp4 = isMp4Segment(firstBytes, initBytes)
        val ext = when {
            isAac -> ".aac"
            isTs -> ".ts"
            else -> ".mp4"  // Default to .mp4 — MediaExtractor handles it better than .ts for unknown formats
        }
        Log.d(TAG, "decodeChunk: segments=${segments.size}, initBytes=${initBytes?.size ?: 0}, isAac=$isAac, isTs=$isTs, isMp4=$isMp4, ext=$ext, firstSegSize=${firstBytes.size}")

        var tempFile: File? = null
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        return try {
            tempFile = File.createTempFile("groq_chunk_", ext, context.cacheDir)
            tempFile.outputStream().use { fos ->
                if (initBytes != null && initBytes.isNotEmpty()) {
                    fos.write(initBytes)
                }
                for (seg in segments) {
                    if (seg.size > 10 && seg[0] == 'I'.code.toByte() && seg[1] == 'D'.code.toByte() && seg[2] == '3'.code.toByte()) {
                        val id3Size = 10 + (((seg[6].toInt() and 0x7F) shl 21) or
                                ((seg[7].toInt() and 0x7F) shl 14) or
                                ((seg[8].toInt() and 0x7F) shl 7) or
                                (seg[9].toInt() and 0x7F))
                        if (seg.size > id3Size) {
                            fos.write(seg, id3Size, seg.size - id3Size)
                        } else {
                            fos.write(seg)
                        }
                    } else {
                        fos.write(seg)
                    }
                }
            }

            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                Log.w(TAG, "decodeChunk: No audio track found in temp file (trackCount=${extractor.trackCount})")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmOut = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputEOS = false
            var outputEOS = false
            var loopsWithoutProgress = 0
            val maxLoops = 25000
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            while (!outputEOS && loopsWithoutProgress++ < maxLoops) {
                if (!inputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(8000L)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        if (buffer != null) {
                            buffer.clear()
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEOS = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                                loopsWithoutProgress = 0
                            }
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
                        loopsWithoutProgress = 0
                    }
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEOS = true
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    outIndex = decoder.dequeueOutputBuffer(info, 0L)
                }

                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            val rawPcm = pcmOut.toByteArray()
            Log.d(TAG, "decodeChunk: Decoded rawPcm=${rawPcm.size} bytes, sampleRate=$sampleRate, channels=$channels")
            if (rawPcm.isEmpty()) return null

            resampleTo16kMonoPcm(rawPcm, sampleRate, channels)
        } catch (e: Throwable) {
            Log.e(TAG, "decodeChunk: Exception during chunk decoding", e)
            null
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
            try { tempFile?.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Decodes a single audio/video segment into un-filtered 16kHz mono 16-bit PCM (with Stereo Mid-Side
     * center vocal extraction applied), ready to be concatenated across the chunk before running continuous
     * chunk-wide Biquad Vocal Formant Isolation & AGC.
     */
    private fun decodeSegmentToRaw16kMonoPcm(context: Context, rawBytes: ByteArray, initBytes: ByteArray? = null): ByteArray? {
        if (rawBytes.size < 300) return null

        val actualBytes: ByteArray
        if (rawBytes.size > 10 && rawBytes[0] == 'I'.code.toByte() && rawBytes[1] == 'D'.code.toByte() && rawBytes[2] == '3'.code.toByte()) {
            val id3Size = 10 + (((rawBytes[6].toInt() and 0x7F) shl 21) or
                    ((rawBytes[7].toInt() and 0x7F) shl 14) or
                    ((rawBytes[8].toInt() and 0x7F) shl 7) or
                    (rawBytes[9].toInt() and 0x7F))
            actualBytes = if (rawBytes.size > id3Size) rawBytes.copyOfRange(id3Size, rawBytes.size) else rawBytes
        } else {
            actualBytes = rawBytes
        }

        val isAac = (actualBytes.size > 2 && actualBytes[0] == 0xFF.toByte() && (actualBytes[1].toInt() and 0xF0) == 0xF0)
        val isTs = actualBytes.size > 4 && actualBytes[0] == 0x47.toByte()
        val ext = when {
            isAac -> ".aac"
            isTs -> ".ts"
            else -> ".mp4"
        }

        var tempFile: File? = null
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        return try {
            tempFile = File.createTempFile("groq_seg_", ext, context.cacheDir)
            tempFile.outputStream().use { fos ->
                if (initBytes != null && initBytes.isNotEmpty()) {
                    fos.write(initBytes)
                }
                fos.write(actualBytes)
            }

            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmOut = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputEOS = false
            var outputEOS = false
            var loops = 0
            val maxLoops = 3500
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            while (!outputEOS && loops++ < maxLoops) {
                if (!inputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(8000L)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        if (buffer != null) {
                            buffer.clear()
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEOS = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
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
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEOS = true
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    outIndex = decoder.dequeueOutputBuffer(info, 0L)
                }

                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            val rawPcm = pcmOut.toByteArray()
            if (rawPcm.isEmpty()) return null

            val resampledShorts = extractCenterVocalsAndResampleTo16kShorts(rawPcm, sampleRate, channels)
            val outBytes = ByteArray(resampledShorts.size * 2)
            ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledShorts)
            outBytes
        } catch (e: Throwable) {
            Log.e(TAG, "decodeSeg: Exception during segment decoding", e)
            null
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
            try { tempFile?.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Runs the 5-stage Biquad Vocal Formant Isolation & Music-Bed Suppression DSP across an already
     * resampled 16kHz mono 16-bit PCM byte array.
     */
    private fun enhanceRaw16kMonoPcmBytes(raw16kMonoBytes: ByteArray): ByteArray {
        val totalSamples = raw16kMonoBytes.size / 2
        if (totalSamples <= 0) return raw16kMonoBytes
        val shorts = ShortArray(totalSamples)
        ByteBuffer.wrap(raw16kMonoBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val enhanced = enhanceVocalClarityAndSuppressMusic(shorts)
        val out = ByteArray(enhanced.size * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(enhanced)
        return out
    }

    /**
     * Extracts center-panned vocal dialogue via non-crushing Stereo Mid-Side spectral weighting
     * and resamples to 16kHz mono shorts.
     */
    private fun extractCenterVocalsAndResampleTo16kShorts(pcmData: ByteArray, srcRate: Int, channels: Int): ShortArray {
        val totalSamples = pcmData.size / 2
        if (totalSamples <= 0) return ShortArray(0)
        val srcShorts = ShortArray(totalSamples)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        val chCount = channels.coerceAtLeast(1)
        val monoLen = totalSamples / chCount
        val monoShorts = ShortArray(monoLen)

        if (chCount >= 2) {
            // Stereo Mid-Side Vocal Center Extraction:
            // Character dialogue in anime is mixed dead-center (Mid), whereas OP/ED songs, BGM guitars/synths,
            // and ambient SFX have strong stereo Side energy. We use a gentle Wiener-style vocal mask that
            // suppresses stereo-wide instrumentals while keeping center speech strong (>= 0.48x) even during loud OP/EDs.
            var envMid = 120f
            var envSide = 20f
            val alpha = 0.012f
            for (i in 0 until monoLen) {
                val base = i * chCount
                val left = srcShorts[base].toFloat()
                val right = if (base + 1 < totalSamples) srcShorts[base + 1].toFloat() else left
                val mid = 0.5f * (left + right)
                val side = 0.5f * kotlin.math.abs(left - right)

                envMid += alpha * (kotlin.math.abs(mid) - envMid)
                envSide += alpha * (side - envSide)

                val vocalMask = (envMid / (envMid + 0.42f * envSide + 1f)).coerceIn(0.48f, 1.0f)
                val isolated = (mid * vocalMask).toInt().coerceIn(-32767, 32767)
                monoShorts[i] = isolated.toShort()
            }
        } else {
            for (i in 0 until monoLen) {
                monoShorts[i] = srcShorts[i]
            }
        }

        return if (srcRate == 16000) {
            monoShorts
        } else {
            val ratio = srcRate.toDouble() / 16000.0
            val targetLen = (monoLen / ratio).toInt()
            val outShorts = ShortArray(targetLen)
            for (i in 0 until targetLen) {
                val srcIdx = i * ratio
                val baseIdx = srcIdx.toInt()
                val frac = srcIdx - baseIdx
                if (baseIdx + 1 < monoLen) {
                    outShorts[i] = ((1.0 - frac) * monoShorts[baseIdx] + frac * monoShorts[baseIdx + 1]).toInt().toShort()
                } else if (baseIdx < monoLen) {
                    outShorts[i] = monoShorts[baseIdx]
                }
            }
            outShorts
        }
    }

    private fun resampleTo16kMonoPcm(pcmData: ByteArray, srcRate: Int, channels: Int): ByteArray {
        val resampled16k = extractCenterVocalsAndResampleTo16kShorts(pcmData, srcRate, channels)
        if (resampled16k.isEmpty()) return ByteArray(0)
        val enhanced16k = enhanceVocalClarityAndSuppressMusic(resampled16k)
        val out = ByteArray(enhanced16k.size * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(enhanced16k)
        return out
    }

    /**
     * Studio-grade 5-Stage 16kHz Vocal Formant Isolator & Music-Bed Suppressor for anime audio:
     * - Stage 1 & 2: Dual Cascaded 2nd-order High-Pass Biquads at 125 Hz & 140 Hz (24 dB/oct roll-off removes bass guitar, kick drums, explosions)
     * - Stage 3: Low-Mid Instrumental Mud Cut (-3.5 dB at 340 Hz, Q = 1.4) to carve out BGM accompaniment
     * - Stage 4: Dual Vocal Formant & Articulation Boost (+3.0 dB at 1150 Hz for vowel formants, +4.5 dB at 2350 Hz for consonant clarity)
     * - Stage 5: 2nd-order Butterworth Low-Pass at 3950 Hz (removes cymbals, hi-hats, shrill synth harmonics)
     * - Stage 6: Non-Gating Syllabic Speech Leveler (AGC) + Soft Tanh Limiter so dialogue over loud OP/ED/insert songs is crystal clear
     */
    private fun enhanceVocalClarityAndSuppressMusic(samples: ShortArray): ShortArray {
        val n = samples.size
        if (n < 320) return samples

        val floats = FloatArray(n) { samples[it].toFloat() }

        // Stage 1 & 2: Steep 4th-order High-Pass (125 Hz + 140 Hz) to strip OP/ED basslines, drums, and rumble
        applyBiquadFilter(floats, type = 0, freqHz = 125.0, sampleRate = 16000.0, q = 0.707, gainDb = 0.0)
        applyBiquadFilter(floats, type = 0, freqHz = 140.0, sampleRate = 16000.0, q = 0.707, gainDb = 0.0)
        // Stage 3: Carve out low-mid instrumental mud (340 Hz, -3.5 dB)
        applyBiquadFilter(floats, type = 2, freqHz = 340.0, sampleRate = 16000.0, q = 1.4, gainDb = -3.5)
        // Stage 4a: Primary vowel formant boost (1150 Hz, +3.0 dB)
        applyBiquadFilter(floats, type = 2, freqHz = 1150.0, sampleRate = 16000.0, q = 1.1, gainDb = 3.0)
        // Stage 4b: Consonant articulation & vocal presence boost (2350 Hz, +4.5 dB)
        applyBiquadFilter(floats, type = 2, freqHz = 2350.0, sampleRate = 16000.0, q = 1.0, gainDb = 4.5)
        // Stage 5: Low-Pass at 3950 Hz to strip high-frequency cymbals/synth fizz
        applyBiquadFilter(floats, type = 1, freqHz = 3950.0, sampleRate = 16000.0, q = 0.707, gainDb = 0.0)

        // Stage 6: Frame-based (20ms = 320 samples) Adaptive Speech Leveler (AGC)
        val frameSize = 320
        val numFrames = (n + frameSize - 1) / frameSize
        val frameRms = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * frameSize
            val end = minOf(start + frameSize, n)
            var sumSq = 0.0
            for (i in start until end) {
                val v = floats[i].toDouble()
                sumSq += v * v
            }
            frameRms[f] = kotlin.math.sqrt(sumSq / (end - start).coerceAtLeast(1)).toFloat()
        }

        val activeRms = frameRms.filter { it > 35f }.sorted()
        val noiseFloor = if (activeRms.isNotEmpty()) activeRms[(activeRms.size * 0.18).toInt().coerceIn(0, activeRms.size - 1)] else 50f
        val speechRef = if (activeRms.isNotEmpty()) activeRms[(activeRms.size * 0.80).toInt().coerceIn(0, activeRms.size - 1)] else 2400f

        val targetSpeechRms = 3600f
        val baseAgcGain = if (speechRef > 100f) (targetSpeechRms / speechRef).coerceIn(0.85f, 3.4f) else 1.0f

        val out = ShortArray(n)
        var smoothedGain = baseAgcGain
        for (f in 0 until numFrames) {
            val rms = frameRms[f]
            val wStart = (f - 5).coerceAtLeast(0)
            val wEnd = (f + 5).coerceAtMost(numFrames - 1)
            var localMin = Float.MAX_VALUE
            var localMax = 0f
            for (w in wStart..wEnd) {
                val r = frameRms[w]
                if (r < localMin) localMin = r
                if (r > localMax) localMax = r
            }
            val modulationDepth = if (localMax > 45f) (localMax - localMin) / localMax else 0f

            // Never gate active dialogue over loud BGM; only gently duck quiet stationary inter-speech hum
            val frameTargetGain = when {
                rms < noiseFloor * 0.95f && modulationDepth < 0.25f -> baseAgcGain * 0.50f
                modulationDepth >= 0.35f && rms > noiseFloor * 1.15f -> baseAgcGain * 1.12f
                else -> baseAgcGain
            }

            val start = f * frameSize
            val end = minOf(start + frameSize, n)
            for (i in start until end) {
                smoothedGain += 0.010f * (frameTargetGain - smoothedGain)
                val scaled = floats[i] * smoothedGain
                val limited = when {
                    scaled > 23500f -> 23500f + 8500f * kotlin.math.tanh(((scaled - 23500f) / 8500f).toDouble()).toFloat()
                    scaled < -23500f -> -23500f - 8500f * kotlin.math.tanh(((-scaled - 23500f) / 8500f).toDouble()).toFloat()
                    else -> scaled
                }
                out[i] = limited.toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    /**
     * In-place Direct Form II Transposed Biquad filter:
     * type 0 = High-Pass, type 1 = Low-Pass, type 2 = Peaking EQ
     */
    private fun applyBiquadFilter(
        samples: FloatArray,
        type: Int,
        freqHz: Double,
        sampleRate: Double,
        q: Double,
        gainDb: Double
    ) {
        val omega = 2.0 * Math.PI * freqHz / sampleRate
        val sinW = kotlin.math.sin(omega)
        val cosW = kotlin.math.cos(omega)
        val alpha = sinW / (2.0 * q)

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double

        when (type) {
            0 -> { // High-Pass
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha
            }
            1 -> { // Low-Pass
                b0 = (1.0 - cosW) / 2.0
                b1 = 1.0 - cosW
                b2 = (1.0 - cosW) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha
            }
            else -> { // Peaking EQ
                val a = Math.pow(10.0, gainDb / 40.0)
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha / a
            }
        }

        val nb0 = (b0 / a0).toFloat()
        val nb1 = (b1 / a0).toFloat()
        val nb2 = (b2 / a0).toFloat()
        val na1 = (a1 / a0).toFloat()
        val na2 = (a2 / a0).toFloat()

        var z1 = 0f
        var z2 = 0f
        for (i in samples.indices) {
            val x = samples[i]
            val y = nb0 * x + z1
            z1 = nb1 * x - na1 * y + z2
            z2 = nb2 * x - na2 * y
            samples[i] = y
        }
    }

    private fun wrapPcmInWavHeader(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16 // 16 bits
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val out = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, out, 0, 44)
        System.arraycopy(pcmData, 0, out, 44, pcmData.size)
        return out
    }

    private fun formatMsToVttTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    /**
     * Comprehensive MP4/fMP4 segment detection.
     * Checks for various MP4 box header magic bytes and the presence of initialization bytes.
     * Handles: ftyp, moof, styp, mdat, sidx, emsg, moov — covers ALL fMP4 segment types from HLS streams.
     */
    private fun isMp4Segment(bytes: ByteArray, initBytes: ByteArray?): Boolean {
        if (initBytes != null && initBytes.isNotEmpty()) return true
        if (bytes.size < 8) return false

        // Check the 4-byte box type at offset 4 (standard MP4 box layout: [4-byte size][4-byte type])
        val boxType = try {
            String(bytes, 4, 4, Charsets.US_ASCII)
        } catch (_: Exception) { "" }

        return boxType in setOf("ftyp", "moof", "styp", "mdat", "sidx", "emsg", "moov", "free", "skip")
    }

    /**
     * Strips musical note symbols (♪, ♫, 🎵, 🎶) and non-speech closed-caption tags like [Music], (Music), [Applause]
     * while preserving any actual spoken dialogue that occurred over background music or OP/ED themes.
     */
    private fun stripMusicNotesAndTags(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace("🎵", " ")
            .replace("🎶", " ")
            .replace('♪', ' ')
            .replace('♫', ' ')
            .replace(Regex("""(?i)\[\s*(music|bgm|applause|cheering|laughter|silence|instrumental|intro|outro|singing)\s*\]"""), " ")
            .replace(Regex("""(?i)\(\s*(music|bgm|applause|cheering|laughter|silence|instrumental|intro|outro|singing)\s*\)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Detects Whisper "hallucination" patterns — when the model fails to detect speech
     * and instead generates repeated phrases, training-data subtitle watermarks (OpenSubtitles,
     * foreign movie credits, news broadcasts), or silence fillers.
     */
    private fun isWhisperHallucination(
        text: String,
        noSpeechProb: Double = 0.0,
        avgLogProb: Double = 0.0,
        isLowVocalEnergy: Boolean = false
    ): Boolean {
        val trimmed = stripMusicNotesAndTags(text)
        if (trimmed.isBlank()) return true

        // Filter pure punctuation or short bracketed tags like [Music], (Applause), etc.
        val tagStripped = trimmed.replace(Regex("""\[.*?\]|\(.*?\)|\{.*?\}"""), "").trim()
        if (tagStripped.isBlank()) return true

        if (tagStripped.all { it in "♪♫ \t\r\n-_.~…,!?・…" }) return true

        // Block foreign script leaks (Cyrillic, Arabic, Hebrew, Hangul, Thai, Devanagari, Greek)
        // when Whisper training data leaks non-anime foreign movie subtitles during music/SFX
        val hasForeignMovieScriptLeak = tagStripped.any { c ->
            c in '\u0400'..'\u04FF' || // Cyrillic (Russian/Ukrainian movie subs)
            c in '\u0600'..'\u06FF' || // Arabic
            c in '\u0590'..'\u05FF' || // Hebrew
            c in '\uAC00'..'\uD7AF' || // Korean Hangul
            c in '\u0E00'..'\u0E7F'    // Thai
        }
        if (hasForeignMovieScriptLeak) return true

        val lower = trimmed.lowercase().trimEnd('.', '!', '?', ',', ' ', '。', '！', '？', '、')
        // Strip any Whisper prompt-echo, URL, or foreign movie / OpenSubtitles training data leak
        val trainingDataLeakSubstrings = listOf(
            "transcribe spoken", "transcribe the spoken", "character dialogue only",
            "spoken character dialogue", "ignore opening", "ending song lyrics",
            "accurate anime dialogue", "natural english for",
            "セリフのみを正確に", "書き起こしてください", "歌詞やbgmは無視",
            "opensubtitles", "subtitles by", "subtitle by", "translated by",
            "captions by", "transcribed by", "amara.org", "addic7ed", "subscene",
            "yify", "yts.", "www.", "http://", "https://", ".com", ".org", ".net",
            "sync and corrections", "synced by", "corrected by", "ripped by",
            "encoded by", "english sdh", "forom.com", "sous-titres", "untertitel",
            "sottotitoli", "legendas", "subtítulos", "subtitulos", "napisy",
            "mbcニュース", "nhkニュース", "ご視聴ありがとう", "ご清聴ありがとう",
            "チャンネル登録", "高評価", "ご覧のスポンサー", "提供でお送り"
        )
        if (trainingDataLeakSubstrings.any { lower.contains(it) }) {
            return true
        }

        val hallucinationPhrases = setOf(
            "thank you for watching", "thanks for watching",
            "thank you for viewing", "thanks for listening",
            "please subscribe", "like and subscribe",
            "see you next time", "see you in the next video",
            "bye bye", "bye-bye",
            "[music]", "(music)", "[applause]", "[cheering]",
            "[laughter]", "[silence]", "thank you", "thanks",
            "thank you very much", "thank you so much",
            "opening theme", "ending theme", "insert song",
            "lyrics by", "composed by", "arranged by",
            "ご視聴ありがとうございました", "ご清聴ありがとうございました",
            "おやすみなさい", "この番組は", "字幕", "提供"
        )
        for (phrase in hallucinationPhrases) {
            if (lower == phrase || lower.startsWith("$phrase ") || lower.endsWith(" $phrase")) {
                return true
            }
        }

        val words = lower.split(Regex("""[\s,.\-!?;:]+""")).filter { it.isNotBlank() }
        val isShortPhrase = words.size in 1..3 && lower.length <= 18

        // Only filter 1-3 word phrases when Whisper itself reports high no_speech_prob / very low logprob
        if (isShortPhrase) {
            if (noSpeechProb >= 0.72 || avgLogProb <= -1.25 || (noSpeechProb >= 0.58 && avgLogProb <= -0.95)) {
                return true
            }
            val silenceFillers = setOf(
                "you", "thank you", "thanks", "bye", "amen", "peace",
                "the end", "to be continued", "ご視聴", "ありがとうございました"
            )
            if (lower in silenceFillers && (noSpeechProb >= 0.38 || avgLogProb <= -0.75 || isLowVocalEnergy)) {
                return true
            }
        }

        // Detect repeated word/lyric loops within a single line (e.g. "la la la la", "yeah yeah yeah", or repeated 2-3 word chunks)
        if (words.size >= 5) {
            val topWord = words.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (topWord != null && topWord.value >= 4 && topWord.value.toDouble() / words.size >= 0.55) {
                return true
            }
        }

        // Check for repeated short phrases (e.g., "Thank you." repeated 2+ times)
        val sentences = trimmed.split(Regex("""[.!?。！？]+""")).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (sentences.size >= 2) {
            val mostFrequent = sentences.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (mostFrequent != null && mostFrequent.value >= (sentences.size * 0.6)) {
                Log.d(TAG, "Detected Whisper hallucination: '${mostFrequent.key}' repeated ${mostFrequent.value}x in ${sentences.size} sentences")
                return true
            }
        }

        // Check for extremely repetitive character patterns (e.g., "aaaa..." or ".....")
        if (trimmed.length > 10 && trimmed.toSet().size <= 3) return true

        return false
    }

    /**
     * Splits raw transcription text into timed subtitle cues distributed across a time range.
     * Uses multiple splitting strategies: sentences first, then commas/semicolons for long text.
     */
    private fun splitTextIntoCues(text: String, startMs: Long, endMs: Long): List<Triple<Long, Long, String>> {
        val result = mutableListOf<Triple<Long, Long, String>>()
        val chunkDurMs = (endMs - startMs).coerceAtLeast(1000L)

        // Split on sentence boundaries, keeping short sentences together
        val rawSentences = text.split(Regex("""(?<=[.?!。！？\n])\s*""")).map { it.trim() }.filter { it.isNotBlank() }

        // If we only got 1 sentence but it's long, try splitting on commas/semicolons
        val sentences = if (rawSentences.size <= 1 && text.length > 80) {
            text.split(Regex("""(?<=[,;、])\s*""")).map { it.trim() }.filter { it.isNotBlank() }
        } else {
            rawSentences
        }

        if (sentences.isEmpty()) return result

        val stepMs = chunkDurMs / sentences.size.coerceAtLeast(1)
        for ((idx, sText) in sentences.withIndex()) {
            val absStart = startMs + (idx * stepMs)
            val maxDur = minOf(stepMs, 6500L)
            val absEnd = minOf(absStart + maxDur, endMs)
            val clean = SenseVoiceAudioTranscriber.stripSpeakerPrefix(sText)
            if (clean.isNotBlank() && !isWhisperHallucination(clean)) {
                result.add(Triple(absStart, absEnd, clean))
            }
        }
        return result
    }
}
