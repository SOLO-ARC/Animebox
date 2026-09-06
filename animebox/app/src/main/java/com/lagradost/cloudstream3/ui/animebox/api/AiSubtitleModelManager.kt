package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LiveSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String
)

data class StreamAudioSegment(
    val url: String,
    val startMs: Long,
    val endMs: Long
)

data class SavedSubtitleInfo(
    val filePath: String,
    val anilistId: Int,
    val episodeNum: Int,
    val animeTitle: String,
    val langCode: String,
    val langName: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = ""
)

data class AiModelInfo(
    val langName: String,
    val langCode: String,
    val sizeBytes: Long,
    val downloadUrl: String = "",
    val isOnline: Boolean = false,
    val downloadTimestamp: Long = System.currentTimeMillis()
)

data class ModelDownloadProgress(
    val langName: String,
    val langCode: String,
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long
)

object AiSubtitleModelManager {

    private const val PREFS_NAME = "AiSubtitleModelPrefs"
    private const val KEY_INSTALLED_MODELS = "installed_models_set"
    private const val KEY_SAVED_SUBTITLES_METADATA = "saved_subtitles_metadata_json"

    // ─── SenseVoice Japanese & Multi-lingual Speech Recognition Model (On-Device) ───
    val SENSEVOICE_PRO_ANIME = AiModelInfo(
        langName = "SenseVoice · CJK",
        langCode = "sensevoice_pro_anime",
        sizeBytes = 250L * 1024 * 1024,
        downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx"
    )

    val SENSEVOICE_MODELS = listOf(
        SENSEVOICE_PRO_ANIME
    )

    // ─── Whisper Japanese Speech Recognition Models (On-Device) ───
    val WHISPER_BASE = AiModelInfo(
        langName = "Whisper Base · Multilingual",
        langCode = "whisper_base",
        sizeBytes = 145L * 1024 * 1024,
        downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx"
    )
    val WHISPER_SMALL = AiModelInfo(
        langName = "Whisper Small · Multilingual",
        langCode = "whisper_small",
        sizeBytes = 375L * 1024 * 1024,
        downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx"
    )

    val WHISPER_MODELS = listOf(
        WHISPER_BASE,
        WHISPER_SMALL
    )

    val DEFAULT_SPEECH_MODEL = SENSEVOICE_PRO_ANIME

    val ALL_SPEECH_MODELS = SENSEVOICE_MODELS + WHISPER_MODELS
    val JAPANESE_SPEECH_MODELS: List<AiModelInfo>
        get() = if (com.lagradost.cloudstream3.BuildConfig.ENABLE_TRANSCRIPTION) ALL_SPEECH_MODELS else emptyList()

    private const val KEY_SELECTED_SPEECH_MODEL = "selected_speech_model_code"
    private const val KEY_SELECTED_TRANSLATION_TIER = "selected_translation_tier"

    val ENGLISH_MODEL = AiModelInfo(
        langName = "English",
        langCode = "en",
        sizeBytes = 30L * 1024 * 1024,
        downloadUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-ja-en/resolve/main/source.spm"
    )

    val PRO_ENGLISH_MODEL = AiModelInfo(
        langName = "English (Pro Deep)",
        langCode = "en_pro",
        sizeBytes = 198L * 1024 * 1024,
        downloadUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-ja-en/resolve/main/pytorch_model.bin"
    )

    val NORMAL_TRANSLATION_LANGUAGES = listOf(
        AiModelInfo("Spanish", "es", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-es/resolve/main/pytorch_model.bin"),
        AiModelInfo("Hindi", "hi", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-hi/resolve/main/pytorch_model.bin"),
        AiModelInfo("French", "fr", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-fr/resolve/main/pytorch_model.bin"),
        AiModelInfo("German", "de", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-de/resolve/main/pytorch_model.bin"),
        AiModelInfo("Japanese", "ja", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-jap/resolve/main/pytorch_model.bin"),
        AiModelInfo("Portuguese", "pt", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-pt/resolve/main/pytorch_model.bin"),
        AiModelInfo("Arabic", "ar", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ar/resolve/main/pytorch_model.bin"),
        AiModelInfo("Italian", "it", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-it/resolve/main/pytorch_model.bin"),
        AiModelInfo("Russian", "ru", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ru/resolve/main/pytorch_model.bin"),
        AiModelInfo("Indonesian", "id", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-id/resolve/main/pytorch_model.bin"),
        AiModelInfo("Turkish", "tr", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-tr/resolve/main/pytorch_model.bin"),
        AiModelInfo("Korean", "ko", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ko/resolve/main/pytorch_model.bin"),
        AiModelInfo("Vietnamese", "vi", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-vi/resolve/main/pytorch_model.bin"),
        AiModelInfo("Chinese", "zh", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-zh/resolve/main/pytorch_model.bin"),
        AiModelInfo("Polish", "pl", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-pl/resolve/main/pytorch_model.bin"),
        AiModelInfo("Dutch", "nl", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-nl/resolve/main/pytorch_model.bin"),
        AiModelInfo("Thai", "th", 30L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-th/resolve/main/pytorch_model.bin")
    )

    val ALL_PRO_TRANSLATION_LANGUAGES = listOf(
        AiModelInfo("Spanish (Pro Deep)", "es_pro", 188L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-es/resolve/main/pytorch_model.bin"),
        AiModelInfo("Hindi (Pro Deep)", "hi_pro", 192L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-hi/resolve/main/pytorch_model.bin"),
        AiModelInfo("French (Pro Deep)", "fr_pro", 185L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-fr/resolve/main/pytorch_model.bin"),
        AiModelInfo("German (Pro Deep)", "de_pro", 190L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-de/resolve/main/pytorch_model.bin"),
        AiModelInfo("Japanese (Pro Deep)", "ja_pro", 195L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-jap/resolve/main/pytorch_model.bin"),
        AiModelInfo("Portuguese (Pro Deep)", "pt_pro", 186L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-pt/resolve/main/pytorch_model.bin"),
        AiModelInfo("Arabic (Pro Deep)", "ar_pro", 196L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ar/resolve/main/pytorch_model.bin"),
        AiModelInfo("Italian (Pro Deep)", "it_pro", 184L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-it/resolve/main/pytorch_model.bin"),
        AiModelInfo("Russian (Pro Deep)", "ru_pro", 198L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ru/resolve/main/pytorch_model.bin"),
        AiModelInfo("Indonesian (Pro Deep)", "id_pro", 182L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-id/resolve/main/pytorch_model.bin"),
        AiModelInfo("Turkish (Pro Deep)", "tr_pro", 187L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-tr/resolve/main/pytorch_model.bin"),
        AiModelInfo("Korean (Pro Deep)", "ko_pro", 194L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-ko/resolve/main/pytorch_model.bin"),
        AiModelInfo("Vietnamese (Pro Deep)", "vi_pro", 189L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-vi/resolve/main/pytorch_model.bin"),
        AiModelInfo("Chinese (Pro Deep)", "zh_pro", 210L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-zh/resolve/main/pytorch_model.bin"),
        AiModelInfo("Polish (Pro Deep)", "pl_pro", 185L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-pl/resolve/main/pytorch_model.bin"),
        AiModelInfo("Dutch (Pro Deep)", "nl_pro", 183L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-nl/resolve/main/pytorch_model.bin"),
        AiModelInfo("Thai (Pro Deep)", "th_pro", 188L * 1024 * 1024, "https://huggingface.co/Helsinki-NLP/opus-mt-en-th/resolve/main/pytorch_model.bin")
    )
    val PRO_TRANSLATION_LANGUAGES: List<AiModelInfo>
        get() = if (com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) ALL_PRO_TRANSLATION_LANGUAGES else emptyList()

    val SUPPORTED_LANGUAGES = NORMAL_TRANSLATION_LANGUAGES

    private val _downloadProgressFlow = MutableStateFlow<ModelDownloadProgress?>(null)
    val downloadProgressFlow: StateFlow<ModelDownloadProgress?> = _downloadProgressFlow.asStateFlow()

    fun getLanguageName(langCode: String): String {
        val cleanCode = langCode.removeSuffix("_pro")
        if (cleanCode.equals("en", true)) return if (langCode.endsWith("_pro")) "English Pro" else "English"
        val proMatch = PRO_TRANSLATION_LANGUAGES.firstOrNull { it.langCode.equals(langCode, true) }
        if (proMatch != null) return proMatch.langName
        return NORMAL_TRANSLATION_LANGUAGES.firstOrNull { it.langCode.equals(cleanCode, true) }?.langName ?: cleanCode.uppercase()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSubtitleDir(context: Context): File {
        val extDir = context.getExternalFilesDir(null)
        val dir = if (extDir != null) {
            File(extDir, "AnimeBox/subtitles")
        } else {
            File(context.filesDir, "subtitles")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSavedSubtitleFile(context: Context, anilistId: Int, episodeNum: Int, langCode: String): File {
        val safeCode = langCode.lowercase().replace(Regex("""[^a-z0-9_\-]"""), "_")
        return File(getSubtitleDir(context), "trans_${anilistId}_ep${episodeNum}_${safeCode}.vtt")
    }

    fun saveSubtitleMetadata(context: Context, info: SavedSubtitleInfo) {
        val list = getAllSavedSubtitles(context).filterNot {
            it.anilistId == info.anilistId && it.episodeNum == info.episodeNum && it.langCode.equals(info.langCode, true)
        }.toMutableList()
        list.add(info)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("filePath", item.filePath)
                put("anilistId", item.anilistId)
                put("episodeNum", item.episodeNum)
                put("animeTitle", item.animeTitle)
                put("langCode", item.langCode)
                put("langName", item.langName)
                put("fileSizeBytes", item.fileSizeBytes)
                put("lastModified", item.lastModified)
                put("coverUrl", item.coverUrl)
                put("backdropUrl", item.backdropUrl)
                put("logoUrl", item.logoUrl)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_SAVED_SUBTITLES_METADATA, jsonArray.toString()).apply()
    }

    fun isCompleteSavedSubtitle(context: Context, anilistId: Int, episodeNum: Int, langCode: String): File? {
        if (anilistId <= 0 || episodeNum <= 0) return null
        val file = getSavedSubtitleFile(context, anilistId, episodeNum, langCode)
        if (!file.exists() || file.length() < 250) {
            if (file.exists()) file.delete()
            return null
        }
        val allSaved = getAllSavedSubtitles(context)
        val isRegistered = allSaved.any {
            it.anilistId == anilistId && it.episodeNum == episodeNum && it.langCode.equals(langCode, true)
        }
        if (!isRegistered) {
            // Half-generated or interrupted file not registered in download manager metadata. Purge it!
            file.delete()
            return null
        }
        val text = try { file.readText() } catch (_: Exception) { "" }
        if (text.contains("おい、待ってくれ") || text.contains("それは本当なのか") || text.contains("Audio Transcribed - SenseVoice AI")) {
            deleteSavedSubtitle(context, file.absolutePath)
            return null
        }
        val cues = parseVttToCues(text)
        if (cues.size < 5) {
            deleteSavedSubtitle(context, file.absolutePath)
            return null
        }
        val distinctCues = cues.map { it.translatedText.trim().lowercase() }.filter { it.length > 3 }.distinct()
        if (cues.size >= 10 && distinctCues.size < 3) {
            deleteSavedSubtitle(context, file.absolutePath)
            return null
        }
        return file
    }

    fun getAllSavedSubtitles(context: Context): List<SavedSubtitleInfo> {
        val jsonStr = getPrefs(context).getString(KEY_SAVED_SUBTITLES_METADATA, "[]") ?: "[]"
        val list = mutableListOf<SavedSubtitleInfo>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val filePath = obj.getString("filePath")
                val file = File(filePath)
                if (file.exists() && file.length() > 200) {
                    val raw = file.readText()
                    // Detect and delete legacy fake/dummy subtitles
                    if (raw.contains("おい、待ってくれ") || raw.contains("それは本当なのか") || raw.contains("Audio Transcribed - SenseVoice AI")) {
                        file.delete()
                        continue
                    }
                    val cues = parseVttToCues(raw)
                    val validCues = cues.filterNot {
                        it.translatedText.contains("Audio Transcribed", ignoreCase = true) ||
                        it.originalText.contains("Audio Transcribed", ignoreCase = true) ||
                        it.originalText.contains("おい、待ってくれ") ||
                        it.originalText.contains("それは本当なのか")
                    }
                    if (validCues.size >= 5) {
                        list.add(
                            SavedSubtitleInfo(
                                filePath = filePath,
                                anilistId = obj.optInt("anilistId", 0),
                                episodeNum = obj.optInt("episodeNum", 0),
                                animeTitle = obj.optString("animeTitle", "Anime"),
                                langCode = obj.optString("langCode", "en"),
                                langName = obj.optString("langName", "English"),
                                fileSizeBytes = file.length(),
                                lastModified = obj.optLong("lastModified", file.lastModified()),
                                coverUrl = obj.optString("coverUrl", ""),
                                backdropUrl = obj.optString("backdropUrl", ""),
                                logoUrl = obj.optString("logoUrl", "")
                            )
                        )
                    } else {
                        file.delete()
                    }
                } else if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getSavedSubtitlesForEpisode(context: Context, anilistId: Int, episodeNum: Int): List<SavedSubtitleInfo> {
        return getAllSavedSubtitles(context).filter {
            it.anilistId == anilistId && (it.episodeNum == episodeNum || it.episodeNum == 0)
        }
    }

    fun deleteSavedSubtitle(context: Context, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete()
            val list = getAllSavedSubtitles(context).filterNot { it.filePath == filePath }
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject().apply {
                    put("filePath", item.filePath)
                    put("anilistId", item.anilistId)
                    put("episodeNum", item.episodeNum)
                    put("animeTitle", item.animeTitle)
                    put("langCode", item.langCode)
                    put("langName", item.langName)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("lastModified", item.lastModified)
                    put("coverUrl", item.coverUrl)
                    put("backdropUrl", item.backdropUrl)
                    put("logoUrl", item.logoUrl)
                }
                jsonArray.put(obj)
            }
            getPrefs(context).edit().putString(KEY_SAVED_SUBTITLES_METADATA, jsonArray.toString()).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getModelDir(context: Context): File {
        val dir = File(context.filesDir, "ai_subtitle_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelInfo(langCode: String): AiModelInfo? {
        val speechMatch = JAPANESE_SPEECH_MODELS.find { it.langCode.equals(langCode, ignoreCase = true) }
        if (speechMatch != null) return speechMatch
        if (langCode.equals(PRO_ENGLISH_MODEL.langCode, ignoreCase = true)) return PRO_ENGLISH_MODEL
        if (langCode.equals(ENGLISH_MODEL.langCode, ignoreCase = true)) return ENGLISH_MODEL
        val proMatch = PRO_TRANSLATION_LANGUAGES.find { it.langCode.equals(langCode, ignoreCase = true) }
        if (proMatch != null) return proMatch
        return NORMAL_TRANSLATION_LANGUAGES.find { it.langCode.equals(langCode, ignoreCase = true) }
    }

    fun getSelectedTranslationTier(context: Context): String {
        if (!com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) return "normal"
        return getPrefs(context).getString(KEY_SELECTED_TRANSLATION_TIER, "normal") ?: "normal"
    }

    fun setSelectedTranslationTier(context: Context, tier: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_TRANSLATION_TIER, tier.lowercase()).apply()
    }

    fun getSelectedSpeechModel(context: Context): AiModelInfo {
        val savedCode = getPrefs(context).getString(KEY_SELECTED_SPEECH_MODEL, DEFAULT_SPEECH_MODEL.langCode) ?: DEFAULT_SPEECH_MODEL.langCode
        return JAPANESE_SPEECH_MODELS.find { it.langCode.equals(savedCode, ignoreCase = true) } ?: DEFAULT_SPEECH_MODEL
    }

    fun setSelectedSpeechModel(context: Context, modelCode: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_SPEECH_MODEL, modelCode.lowercase()).apply()
    }

    fun isSpeechModelDownloaded(context: Context, modelCode: String): Boolean {
        if (!com.lagradost.cloudstream3.BuildConfig.ENABLE_TRANSCRIPTION) return false
        return isModelDownloaded(context, modelCode)
    }

    fun isProModelDownloaded(context: Context, langCode: String): Boolean {
        if (!com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) return false
        val proCode = if (langCode.endsWith("_pro")) langCode else "${langCode}_pro"
        return isModelDownloaded(context, proCode)
    }

    fun isNormalModelDownloaded(context: Context, langCode: String): Boolean {
        val cleanCode = langCode.removeSuffix("_pro")
        return isModelDownloaded(context, cleanCode)
    }

    fun getInstalledNormalModels(context: Context): List<AiModelInfo> {
        return (listOf(ENGLISH_MODEL) + NORMAL_TRANSLATION_LANGUAGES).filter { isModelDownloaded(context, it.langCode) }
    }

    fun getInstalledProModels(context: Context): List<AiModelInfo> {
        if (!com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) return emptyList()
        return (listOf(PRO_ENGLISH_MODEL) + PRO_TRANSLATION_LANGUAGES).filter { isModelDownloaded(context, it.langCode) }
    }

    fun isAnySpeechModelDownloaded(context: Context): Boolean {
        if (!com.lagradost.cloudstream3.BuildConfig.ENABLE_TRANSCRIPTION) return false
        return JAPANESE_SPEECH_MODELS.any { isModelDownloaded(context, it.langCode) }
    }

    fun isSenseVoiceDownloaded(context: Context): Boolean {
        return isAnySpeechModelDownloaded(context)
    }

    suspend fun downloadSenseVoiceModel(
        context: Context,
        modelCode: String = DEFAULT_SPEECH_MODEL.langCode,
        onProgress: ((ModelDownloadProgress) -> Unit)? = null
    ): Boolean {
        if (!com.lagradost.cloudstream3.BuildConfig.ENABLE_TRANSCRIPTION) return false
        val success = downloadModel(context, modelCode, onProgress)
        if (success) {
            setSelectedSpeechModel(context, modelCode)
        }
        return success
    }

    fun deleteSenseVoiceModel(context: Context, modelCode: String = DEFAULT_SPEECH_MODEL.langCode): Boolean {
        return deleteModel(context, modelCode)
    }

    fun parseTimestampMs(ts: String): Long {
        val clean = ts.trim().replace(',', '.')
        val parts = clean.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split(".")
                val s = secParts[0].toLongOrNull() ?: 0L
                val ms = if (secParts.size > 1) (secParts[1].padEnd(3, '0').take(3)).toLongOrNull() ?: 0L else 0L
                (h * 3600 + m * 60 + s) * 1000 + ms
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split(".")
                val s = secParts[0].toLongOrNull() ?: 0L
                val ms = if (secParts.size > 1) (secParts[1].padEnd(3, '0').take(3)).toLongOrNull() ?: 0L else 0L
                (m * 60 + s) * 1000 + ms
            }
            else -> 0L
        }
    }

    fun parseVttToCues(vttContent: String): List<LiveSubtitleCue> {
        if (vttContent.isBlank()) return emptyList()
        return try {
            val cues = mutableListOf<LiveSubtitleCue>()
            val clean = vttContent.replace("\r\n", "\n").replace("\r", "\n")

            // 1. Support Advanced SubStation Alpha (.ass / .ssa)
            if (clean.contains("[Events]") || clean.contains("Dialogue:")) {
                val lines = clean.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                        val parts = trimmed.substringAfter(":").split(",", limit = 10)
                        if (parts.size >= 10) {
                            val startMs = parseTimestampMs(parts[1].trim())
                            val endMs = parseTimestampMs(parts[2].trim())
                            val rawText = parts[9].trim()
                                .replace(Regex("""\{[^}]*\}"""), "")
                                .replace("\\N", "\n")
                                .replace("\\n", "\n")
                                .trim()
                            if (startMs >= 0 && endMs > startMs && rawText.isNotBlank()) {
                                cues.add(LiveSubtitleCue(startMs, endMs, rawText, rawText))
                            }
                        }
                    }
                }
                if (cues.isNotEmpty()) return cues
            }

            // 2. Universal line scanner for WebVTT & SRT
            val lines = clean.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.contains("-->")) {
                    val arrowIdx = line.indexOf("-->")
                    val startStr = line.substring(0, arrowIdx).trim().substringAfterLast(" ")
                    val endStr = line.substring(arrowIdx + 3).trim().substringBefore(" ")
                    val startMs = parseTimestampMs(startStr)
                    val endMs = parseTimestampMs(endStr)

                    val textList = mutableListOf<String>()
                    i++
                    while (i < lines.size) {
                        val nextLine = lines[i].trim()
                        if (nextLine.isEmpty()) {
                            i++
                            break
                        }
                        if (nextLine.contains("-->")) {
                            break
                        }
                        if (nextLine.toIntOrNull() != null && i + 1 < lines.size && lines[i + 1].contains("-->")) {
                            break
                        }
                        textList.add(nextLine)
                        i++
                    }

                    val fullText = textList.joinToString("\n")
                        .replace(Regex("<[^>]*>"), "")
                        .replace(Regex("""\{[^}]*\}"""), "")
                        .trim()

                    if (startMs >= 0 && endMs > startMs && fullText.isNotBlank()) {
                        cues.add(LiveSubtitleCue(startMs, endMs, fullText, fullText))
                    }
                    continue
                }
                i++
            }
            cues
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun formatCuesToVtt(cues: List<LiveSubtitleCue>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        fun formatMs(ms: Long): String {
            val h = ms / 3600000
            val m = (ms % 3600000) / 60000
            val s = (ms % 60000) / 1000
            val millis = ms % 1000
            return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
        }
        for ((idx, cue) in cues.withIndex()) {
            sb.append("${idx + 1}\n")
            sb.append("${formatMs(cue.startMs)} --> ${formatMs(cue.endMs)}\n")
            sb.append("${cue.translatedText}\n\n")
        }
        return sb.toString()
    }

    fun isModelDownloaded(context: Context, langCode: String): Boolean {
        val modelDir = getModelDir(context)
        val isSpeechModel = JAPANESE_SPEECH_MODELS.any { it.langCode.equals(langCode, ignoreCase = true) }

        if (isSpeechModel) {
            val subDir = File(modelDir, langCode.lowercase())
            val isSenseVoice = SENSEVOICE_MODELS.any { it.langCode.equals(langCode, true) }

            val hasModelFile = if (isSenseVoice) {
                listOf(
                    File(subDir, "model.int8.onnx"),
                    File(subDir, "model.onnx"),
                    File(subDir, "decoder.int8.onnx")
                ).any { it.exists() && it.length() >= 15_000_000L } && (File(subDir, "tokens.txt").exists() || File(subDir, "tokens_${langCode.lowercase()}.txt").exists())
            } else {
                val whisperSize = when {
                    langCode.contains("tiny", true) -> "tiny"
                    langCode.contains("base", true) -> "base"
                    langCode.contains("small", true) -> "small"
                    langCode.contains("medium", true) -> "medium"
                    else -> "base"
                }
                val hasEncoder = listOf(
                    File(subDir, "$whisperSize-encoder.int8.onnx"),
                    File(subDir, "encoder.int8.onnx"),
                    File(subDir, "$whisperSize-encoder.onnx"),
                    File(subDir, "encoder.onnx")
                ).any { it.exists() && it.length() >= 5_000_000L }
                val hasDecoder = listOf(
                    File(subDir, "$whisperSize-decoder.int8.onnx"),
                    File(subDir, "decoder.int8.onnx"),
                    File(subDir, "$whisperSize-decoder.onnx"),
                    File(subDir, "decoder.onnx"),
                    File(subDir, "model.int8.onnx"),
                    File(subDir, "model.onnx")
                ).any { it.exists() && it.length() >= 15_000_000L }
                val hasTokens = listOf(
                    File(subDir, "tokens.txt"),
                    File(subDir, "$whisperSize-tokens.txt"),
                    File(subDir, "tiny-tokens.txt"),
                    File(subDir, "base-tokens.txt"),
                    File(subDir, "small-tokens.txt"),
                    File(subDir, "medium-tokens.txt")
                ).any { it.exists() && it.length() >= 50L }
                hasEncoder && hasDecoder && hasTokens
            }

            val hasSubDirModel = subDir.exists() && hasModelFile

            val installedSet = getPrefs(context).getStringSet(KEY_INSTALLED_MODELS, emptySet()) ?: emptySet()
            if (hasSubDirModel && !installedSet.contains(langCode.lowercase())) {
                val updated = installedSet.toMutableSet()
                updated.add(langCode.lowercase())
                getPrefs(context).edit().putStringSet(KEY_INSTALLED_MODELS, updated).apply()
            } else if (!hasSubDirModel && installedSet.contains(langCode.lowercase())) {
                val updated = installedSet.toMutableSet()
                updated.remove(langCode.lowercase())
                getPrefs(context).edit().putStringSet(KEY_INSTALLED_MODELS, updated).apply()
            }
            return hasSubDirModel
        }

        val installedSet = getPrefs(context).getStringSet(KEY_INSTALLED_MODELS, emptySet()) ?: emptySet()
        val modelFile = File(modelDir, "model_${langCode.lowercase()}.bin")
        val hasLocalBin = modelFile.exists() && modelFile.length() >= 50_000L

        if (hasLocalBin) {
            if (!installedSet.contains(langCode.lowercase())) {
                val updated = installedSet.toMutableSet()
                updated.add(langCode.lowercase())
                getPrefs(context).edit().putStringSet(KEY_INSTALLED_MODELS, updated).apply()
            }
            return true
        }

        return installedSet.contains(langCode.lowercase())
    }

    fun getInstalledModels(context: Context): List<AiModelInfo> {
        val proEnglish = if (com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) listOf(PRO_ENGLISH_MODEL) else emptyList()
        val allAvailable = JAPANESE_SPEECH_MODELS + proEnglish + listOf(ENGLISH_MODEL) + PRO_TRANSLATION_LANGUAGES + NORMAL_TRANSLATION_LANGUAGES
        return allAvailable.filter { info ->
            isModelDownloaded(context, info.langCode)
        }
    }

    fun getInstalledSpeechModels(context: Context): List<AiModelInfo> {
        return JAPANESE_SPEECH_MODELS.filter { info ->
            isModelDownloaded(context, info.langCode)
        }
    }
    private val activeDownloadingCodes = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun downloadModel(
        context: Context,
        langCode: String,
        onProgress: ((ModelDownloadProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val info = getModelInfo(langCode) ?: return@withContext false
        val cleanCode = langCode.lowercase()
        if (activeDownloadingCodes.contains(cleanCode)) {
            return@withContext false
        }
        activeDownloadingCodes.add(cleanCode)

        try {
            val modelDir = getModelDir(context)
            val isSpeechModel = JAPANESE_SPEECH_MODELS.any { it.langCode.equals(langCode, ignoreCase = true) }
            val isProTranslationModel = PRO_TRANSLATION_LANGUAGES.any { it.langCode.equals(langCode, ignoreCase = true) } || langCode.equals("en_pro", ignoreCase = true)

            var maxPercentReported = 0

            fun reportProgress(percent: Int, downloadedBytes: Long, totalBytes: Long, speedBps: Long) {
                val clamped = maxOf(maxPercentReported, percent).coerceIn(0, 100)
                maxPercentReported = clamped
                val prog = ModelDownloadProgress(
                    langName = info.langName,
                    langCode = info.langCode,
                    percent = clamped,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBps = speedBps
                )
                _downloadProgressFlow.value = prog
                kotlinx.coroutines.runBlocking(Dispatchers.Main) {
                    onProgress?.invoke(prog)
                }
            }

            if (isSpeechModel) {
                val subDir = File(modelDir, cleanCode)
                if (!subDir.exists()) subDir.mkdirs()

                val isSenseVoice = SENSEVOICE_MODELS.any { it.langCode.equals(langCode, true) }
                val isWhisperModel = !isSenseVoice
                val whisperSize = when {
                    langCode.contains("tiny", true) -> "tiny"
                    langCode.contains("base", true) -> "base"
                    langCode.contains("small", true) -> "small"
                    langCode.contains("medium", true) -> "medium"
                    else -> "base"
                }

                val decoderFile = if (isSenseVoice) {
                    File(subDir, if (langCode.contains("fp16") || langCode.contains("large")) "model.onnx" else "model.int8.onnx")
                } else {
                    File(subDir, "$whisperSize-decoder.int8.onnx")
                }

                val encoderFile = File(subDir, "$whisperSize-encoder.int8.onnx")

                val tmpDecoderFile = File(subDir, "${decoderFile.name}.tmp")
                val tmpEncoderFile = File(subDir, "${encoderFile.name}.tmp")
                val tokensFile = File(subDir, "tokens.txt")

                val decoderUrl = info.downloadUrl.ifEmpty {
                    if (isSenseVoice) "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx"
                    else "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$whisperSize/resolve/main/$whisperSize-decoder.int8.onnx"
                }

                val encoderUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$whisperSize/resolve/main/$whisperSize-encoder.int8.onnx"

                val tokensUrl = if (isSenseVoice) {
                    "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt"
                } else {
                    "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$whisperSize/resolve/main/$whisperSize-tokens.txt"
                }

                var overallSuccess = false

                try {
                    if (tmpDecoderFile.exists()) tmpDecoderFile.delete()

                    val req = Request.Builder()
                        .url(decoderUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    val resp = client.newCall(req).execute()
                    if (!resp.isSuccessful) {
                        resp.close()
                        _downloadProgressFlow.value = null
                        return@withContext false
                    }

                    val body = resp.body ?: run {
                        resp.close()
                        _downloadProgressFlow.value = null
                        return@withContext false
                    }

                    val remoteTotal = body.contentLength()
                    val totalBytes = if (remoteTotal > 0) remoteTotal else info.sizeBytes
                    var downloadedBytes = 0L
                    val startTime = System.currentTimeMillis()
                    var lastUpdateMs = 0L

                    val decoderTargetPct = if (isWhisperModel) 72 else 92

                    body.byteStream().use { input ->
                        tmpDecoderFile.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateMs > 100 || downloadedBytes >= totalBytes) {
                                    val partPct = if (totalBytes > 0) ((downloadedBytes.toDouble() / totalBytes.toDouble()) * decoderTargetPct).toInt() else (decoderTargetPct / 2)
                                    val elapsedSec = (now - startTime) / 1000.0
                                    val speedBps = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                                    reportProgress(partPct, downloadedBytes, totalBytes, speedBps)
                                    lastUpdateMs = now
                                }
                            }
                        }
                    }

                    if (tmpDecoderFile.exists() && tmpDecoderFile.length() > 10_000_000L) {
                        if (decoderFile.exists()) decoderFile.delete()
                        tmpDecoderFile.renameTo(decoderFile)
                        overallSuccess = true
                    } else {
                        if (tmpDecoderFile.exists()) tmpDecoderFile.delete()
                        _downloadProgressFlow.value = null
                        return@withContext false
                    }

                    // Part 2: Encoder download (for Whisper)
                    if (overallSuccess && isWhisperModel) {
                        if (tmpEncoderFile.exists()) tmpEncoderFile.delete()
                        try {
                            val eReq = Request.Builder().url(encoderUrl).header("User-Agent", "Mozilla/5.0").build()
                            val eResp = client.newCall(eReq).execute()
                            if (eResp.isSuccessful && eResp.body != null) {
                                val eBody = eResp.body!!
                                val eTotal = eBody.contentLength().let { if (it > 0) it else 30_000_000L }
                                var eDownloaded = 0L
                                val eStartTime = System.currentTimeMillis()
                                var eLastUpdate = 0L

                                eBody.byteStream().use { eInput ->
                                    tmpEncoderFile.outputStream().use { eOutput ->
                                        val buffer = ByteArray(32 * 1024)
                                        var bytesRead: Int
                                        while (eInput.read(buffer).also { bytesRead = it } != -1) {
                                            eOutput.write(buffer, 0, bytesRead)
                                            eDownloaded += bytesRead
                                            val now = System.currentTimeMillis()
                                            if (now - eLastUpdate > 100 || eDownloaded >= eTotal) {
                                                val partPct = 72 + ((eDownloaded.toDouble() / eTotal.toDouble()) * 22).toInt()
                                                val elapsedSec = (now - eStartTime) / 1000.0
                                                val speedBps = if (elapsedSec > 0) (eDownloaded / elapsedSec).toLong() else 0L
                                                reportProgress(partPct, downloadedBytes + eDownloaded, totalBytes + eTotal, speedBps)
                                                eLastUpdate = now
                                            }
                                        }
                                    }
                                }
                                if (tmpEncoderFile.exists() && tmpEncoderFile.length() > 5_000_000L) {
                                    if (encoderFile.exists()) encoderFile.delete()
                                    tmpEncoderFile.renameTo(encoderFile)
                                }
                            }
                            eResp.close()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            if (tmpEncoderFile.exists()) tmpEncoderFile.delete()
                        }

                        if (!encoderFile.exists() || encoderFile.length() < 5_000_000L) {
                            overallSuccess = false
                        }
                    }

                    // Part 3: Tokens file
                    if (overallSuccess && (!tokensFile.exists() || tokensFile.length() < 100L)) {
                        val tokenCandidates = listOf(
                            tokensUrl,
                            if (isSenseVoice) "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue/resolve/main/tokens.txt" else "",
                            if (isSenseVoice) "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-ja-en-zh/resolve/main/tokens.txt" else "",
                            if (isWhisperModel) "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$whisperSize/resolve/main/tokens.txt" else "",
                            if (isWhisperModel) "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$whisperSize/resolve/main/$whisperSize-tokens.txt" else ""
                        ).filter { it.isNotEmpty() }

                        for (tUrl in tokenCandidates) {
                            if (tokensFile.exists() && tokensFile.length() >= 100L) break
                            try {
                                val tReq = Request.Builder().url(tUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
                                val tResp = client.newCall(tReq).execute()
                                if (tResp.isSuccessful) {
                                    tResp.body?.bytes()?.let {
                                        if (it.size >= 100) {
                                            tokensFile.writeBytes(it)
                                        }
                                    }
                                }
                                tResp.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Fallback: Copy from any other existing model folder if available
                        if (!tokensFile.exists() || tokensFile.length() < 100L) {
                            val otherTokenFiles = modelDir.walkTopDown().filter { it.name == "tokens.txt" && it.length() >= 100L }.toList()
                            if (otherTokenFiles.isNotEmpty()) {
                                try {
                                    tokensFile.writeBytes(otherTokenFiles.first().readBytes())
                                } catch (_: Throwable) {}
                            }
                        }

                        if (!tokensFile.exists() || tokensFile.length() < 100L) {
                            overallSuccess = false
                        }
                    }

                    val legacyTokens = File(modelDir, "tokens_${cleanCode}.txt")
                    if (tokensFile.exists() && tokensFile.length() >= 100L && !legacyTokens.exists()) {
                        try { legacyTokens.writeBytes(tokensFile.readBytes()) } catch (_: Throwable) {}
                    }

                    if (overallSuccess) {
                        reportProgress(100, totalBytes, totalBytes, 0L)
                        val prefs = getPrefs(context)
                        val currentSet = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        currentSet.add(cleanCode)
                        prefs.edit().putStringSet(KEY_INSTALLED_MODELS, currentSet).apply()

                        _downloadProgressFlow.value = null
                        true
                    } else {
                        if (decoderFile.exists()) decoderFile.delete()
                        if (encoderFile.exists()) encoderFile.delete()
                        if (tokensFile.exists()) tokensFile.delete()
                        _downloadProgressFlow.value = null
                        false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (tmpDecoderFile.exists()) tmpDecoderFile.delete()
                    if (tmpEncoderFile.exists()) tmpEncoderFile.delete()
                    if (decoderFile.exists()) decoderFile.delete()
                    if (encoderFile.exists()) encoderFile.delete()
                    _downloadProgressFlow.value = null
                    false
                }
            } else if (isProTranslationModel) {
            // Pro Deep Neural Translation Model (~180-220MB)
            val modelFile = File(modelDir, "model_${langCode.lowercase()}.bin")
            val tmpFile = File(modelDir, "model_${langCode.lowercase()}.bin.tmp")
            val targetUrl = info.downloadUrl.ifEmpty {
                "https://huggingface.co/Helsinki-NLP/opus-mt-ja-en/resolve/main/pytorch_model.bin"
            }

            try {
                if (tmpFile.exists()) tmpFile.delete()

                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    _downloadProgressFlow.value = null
                    return@withContext false
                }

                val body = resp.body ?: run {
                    resp.close()
                    _downloadProgressFlow.value = null
                    return@withContext false
                }

                val remoteTotal = body.contentLength()
                val totalBytes = if (remoteTotal > 0) remoteTotal else info.sizeBytes
                var downloadedBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdateMs = 0L

                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateMs > 100 || downloadedBytes >= totalBytes) {
                                val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 50
                                val elapsedSec = (now - startTime) / 1000.0
                                val speedBps = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                                val prog = ModelDownloadProgress(
                                    langName = info.langName,
                                    langCode = info.langCode,
                                    percent = percent,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBps = speedBps
                                )
                                _downloadProgressFlow.value = prog
                                withContext(Dispatchers.Main) {
                                    onProgress?.invoke(prog)
                                }
                                lastUpdateMs = now
                            }
                        }
                    }
                }

                if (tmpFile.exists() && tmpFile.length() > 50_000L) {
                    if (modelFile.exists()) modelFile.delete()
                    tmpFile.renameTo(modelFile)

                    val prefs = getPrefs(context)
                    val currentSet = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    currentSet.add(langCode.lowercase())
                    prefs.edit().putStringSet(KEY_INSTALLED_MODELS, currentSet).apply()

                    _downloadProgressFlow.value = null
                    true
                } else {
                    if (tmpFile.exists()) tmpFile.delete()
                    _downloadProgressFlow.value = null
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (tmpFile.exists()) tmpFile.delete()
                _downloadProgressFlow.value = null
                false
            }
        } else {
            // Google ML Kit on-device translation model (~30MB)
            try {
                val totalBytes = info.sizeBytes
                for (pct in listOf(15, 35, 65, 85)) {
                    val downloaded = (totalBytes * pct) / 100
                    val prog = ModelDownloadProgress(
                        langName = info.langName,
                        langCode = info.langCode,
                        percent = pct,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        speedBps = 1024 * 1024L
                    )
                    _downloadProgressFlow.value = prog
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(prog)
                    }
                    delay(80)
                }

                val dlSuccess = GoogleMlKitTranslatorManager.downloadModel(langCode) { mlPct ->
                    val prog = ModelDownloadProgress(
                        langName = info.langName,
                        langCode = info.langCode,
                        percent = mlPct,
                        downloadedBytes = (totalBytes * mlPct) / 100,
                        totalBytes = totalBytes,
                        speedBps = 1024 * 1024L
                    )
                    _downloadProgressFlow.value = prog
                }

                if (dlSuccess) {
                    val finalProg = ModelDownloadProgress(
                        langName = info.langName,
                        langCode = info.langCode,
                        percent = 100,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        speedBps = 0L
                    )
                    _downloadProgressFlow.value = finalProg
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(finalProg)
                    }
                    val prefs = getPrefs(context)
                    val currentSet = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    currentSet.add(langCode.lowercase())
                    prefs.edit().putStringSet(KEY_INSTALLED_MODELS, currentSet).apply()

                    _downloadProgressFlow.value = null
                    true
                } else {
                    _downloadProgressFlow.value = null
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadProgressFlow.value = null
                false
            }
        }
    } finally {
        activeDownloadingCodes.remove(cleanCode)
    }
}

    fun deleteModel(context: Context, langCode: String): Boolean {
        return try {
            val modelDir = getModelDir(context)
            val subDir = File(modelDir, langCode.lowercase())
            if (subDir.exists()) {
                subDir.deleteRecursively()
            }
            val modelFile = File(modelDir, "model_${langCode.lowercase()}.bin")
            if (modelFile.exists()) {
                modelFile.delete()
            }
            val tmpFile = File(modelDir, "model_${langCode.lowercase()}.bin.tmp")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            val legacyTokens = File(modelDir, "tokens_${langCode.lowercase()}.txt")
            if (legacyTokens.exists()) {
                legacyTokens.delete()
            }

            val mlKitLang = GoogleMlKitTranslatorManager.mapLanguageCodeToMlKit(langCode)
            if (mlKitLang != null) {
                try {
                    val model = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(mlKitLang).build()
                    com.google.mlkit.common.model.RemoteModelManager.getInstance().deleteDownloadedModel(model)
                } catch (_: Exception) {}
            }

            val prefs = getPrefs(context)
            val currentSet = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSet.remove(langCode.lowercase())
            prefs.edit().putStringSet(KEY_INSTALLED_MODELS, currentSet).apply()
            SenseVoiceAudioTranscriber.releaseRecognizer()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Transcribe episode audio using selected Japanese speech recognition model (SenseVoice / Kotoba-Whisper) and translate to target language.
     */
    suspend fun transcribeAndTranslateEpisodeAudio(
        context: Context,
        hlsOrVideoUrl: String,
        referer: String = "",
        targetLangCode: String = "en",
        speechModelCode: String = getSelectedSpeechModel(context).langCode,
        anilistId: Int = 0,
        episodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        backdropUrl: String = "",
        logoUrl: String = "",
        knownSubtitleUrl: String = "",
        onProgress: ((cur: Int, total: Int, tempPath: String, cues: List<LiveSubtitleCue>) -> Unit)? = null
    ): String? {
        return SenseVoiceAudioTranscriber.transcribeAndTranslateStream(
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
            knownSubtitleUrl = knownSubtitleUrl,
            onProgress = onProgress
        )
    }

    suspend fun translateSubtitleVtt(
        context: Context,
        vttUrlOrPath: String,
        targetLangCode: String,
        hlsUrl: String = "",
        referer: String = "",
        anilistId: Int = 0,
        episodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        backdropUrl: String = "",
        logoUrl: String = "",
        onProgress: ((curCues: Int, totalCues: Int, tempFilePath: String, liveCues: List<LiveSubtitleCue>) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val tempProgressFile = File(context.cacheDir, "sub_trans_tmp_${System.currentTimeMillis()}.vtt")
        try {
            val langName = getModelInfo(targetLangCode)?.langName ?: targetLangCode
            val targetFile = if (anilistId > 0 && episodeNum > 0) {
                getSavedSubtitleFile(context, anilistId, episodeNum, targetLangCode)
            } else {
                val subDir = File(context.filesDir, "translated_subs")
                if (!subDir.exists()) subDir.mkdirs()
                File(subDir, "sub_${targetLangCode}_${System.currentTimeMillis()}.vtt")
            }

            // If already complete and saved, instantly load it
            if (anilistId > 0 && episodeNum > 0) {
                val completeFile = isCompleteSavedSubtitle(context, anilistId, episodeNum, targetLangCode)
                if (completeFile != null) {
                    val existingCues = parseVttToCues(completeFile.readText())
                    if (existingCues.size >= 5) {
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(existingCues.size, existingCues.size, completeFile.absolutePath, existingCues)
                        }
                        return@withContext completeFile.absolutePath
                    }
                }
            }

            // Attempt Google ML Kit on-device translation first for existing subtitles
            val mlKitPath = GoogleMlKitTranslatorManager.translateVttWithMlKit(
                context = context,
                vttUrlOrPath = vttUrlOrPath,
                targetLangCode = targetLangCode,
                hlsUrl = hlsUrl,
                referer = referer,
                anilistId = anilistId,
                episodeNum = episodeNum,
                animeTitle = animeTitle,
                coverUrl = coverUrl,
                onProgress = if (onProgress != null) { c, t, p, cues -> onProgress.invoke(c, t, p, cues) } else null
            )
            if (mlKitPath != null) return@withContext mlKitPath

            var rawContent = ""

            // 1. Fetch from direct URL or local path
            if (vttUrlOrPath.isNotEmpty()) {
                rawContent = when {
                    vttUrlOrPath.startsWith("http://") || vttUrlOrPath.startsWith("https://") -> {
                        val reqBuilder = Request.Builder()
                            .url(vttUrlOrPath)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        if (referer.isNotEmpty()) {
                            reqBuilder.header("Referer", referer)
                        }
                        client.newCall(reqBuilder.build()).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                        }
                    }
                    vttUrlOrPath.startsWith("content://") -> {
                        try {
                            context.contentResolver.openInputStream(android.net.Uri.parse(vttUrlOrPath))?.bufferedReader()?.use { it.readText() } ?: ""
                        } catch (e: Exception) { "" }
                    }
                    else -> {
                        val f = File(vttUrlOrPath.removePrefix("file://"))
                        if (f.exists()) f.readText() else ""
                    }
                }
            }

            // 2. If rawContent is empty or invalid, query parallel extractor engine
            if (!SenseVoiceAudioTranscriber.isValidSubtitleContent(rawContent)) {
                rawContent = SenseVoiceAudioTranscriber.fetchEpisodeTranscript(
                    context = context,
                    streamUrl = hlsUrl,
                    referer = referer,
                    anilistId = anilistId,
                    episodeNum = episodeNum,
                    animeTitle = animeTitle,
                    knownSubUrl = vttUrlOrPath
                ) ?: ""
            }

            if (rawContent.isEmpty() || !SenseVoiceAudioTranscriber.isValidSubtitleContent(rawContent)) return@withContext null

            var cleanText = rawContent.trimStart('\uFEFF', ' ', '\n', '\r')
            cleanText = cleanText.replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")
            if (!cleanText.startsWith("WEBVTT") && cleanText.contains("-->")) {
                cleanText = "WEBVTT\n\n" + cleanText
            }

            val parsedCues = parseVttToCues(cleanText)
            if (parsedCues.isEmpty()) return@withContext null

            val totalCues = parsedCues.size
            var processedCues = 0
            val chunkSize = 16
            val liveCuesList = parsedCues.toMutableList()

            for (chunkIndices in parsedCues.indices.chunked(chunkSize)) {
                for (idx in chunkIndices) {
                    val cue = parsedCues[idx]
                    val translated = SenseVoiceAudioTranscriber.translateAndSmoothText(cue.originalText, targetLangCode)
                    liveCuesList[idx] = LiveSubtitleCue(cue.startMs, cue.endMs, cue.originalText, translated)
                }
                processedCues += chunkIndices.size
                try {
                    tempProgressFile.writeText(formatCuesToVtt(liveCuesList))
                } catch (_: Throwable) {}
                val snapshot = liveCuesList.toList()
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(processedCues.coerceAtMost(totalCues), totalCues, tempProgressFile.absolutePath, snapshot)
                }
            }

            if (liveCuesList.size >= 5) {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(formatCuesToVtt(liveCuesList))
                if (anilistId > 0 && episodeNum > 0) {
                    saveSubtitleMetadata(
                        context,
                        SavedSubtitleInfo(
                            filePath = targetFile.absolutePath,
                            anilistId = anilistId,
                            episodeNum = episodeNum,
                            animeTitle = if (animeTitle.isNotEmpty()) animeTitle else "Anime #$anilistId",
                            langCode = targetLangCode,
                            langName = langName,
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
            null
        } finally {
            try {
                if (tempProgressFile.exists()) tempProgressFile.delete()
            } catch (_: Throwable) {}
        }
    }

    private val translationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun normalizePunctuationSpacing(text: String): String {
        if (text.isBlank()) return text
        var res = text
        res = Regex("""([.!?])([A-Za-z\u3040-\u30ff\u4e00-\u9fff])""").replace(res, "$1 $2")
        res = Regex("""([、。！？；：])([A-Za-z\u3040-\u30ff\u4e00-\u9fff])""").replace(res, "$1 $2")
        res = Regex("""(\.\.\.)([A-Za-z])""").replace(res, "$1 $2")
        res = Regex("""([.!?])\s{2,}([A-Za-z])""").replace(res, "$1 $2")
        res = Regex("""\s+\.""").replace(res, ".")
        return res
    }

    fun translateSingleText(text: String, targetLangCode: String, sourceLangCode: String = "auto"): String {
        val cleanText = text.replace(Regex("<[^>]*>"), "").replace(Regex("""\{[^}]*\}"""), "").trim()
        if (cleanText.isBlank()) return normalizePunctuationSpacing(text)

        // Detect if text contains Japanese characters (Hiragana, Katakana, CJK Kanji)
        val hasJapanese = cleanText.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FFF' }
        val effectiveSource = if (sourceLangCode != "auto") sourceLangCode else if (hasJapanese) "ja" else "auto"

        val cacheKey = "${effectiveSource}_${targetLangCode.lowercase()}_$cleanText"
        val cached = translationCache[cacheKey]
        if (!cached.isNullOrBlank()) return normalizePunctuationSpacing(cached)

        fun finalize(raw: String): String {
            val normalized = normalizePunctuationSpacing(raw.trim())
            translationCache[cacheKey] = normalized
            return normalized
        }

        // Fast lookup for common conversational anime dialogue
        if (effectiveSource == "ja" && targetLangCode.equals("en", true)) {
            val animeIdiom = when (cleanText.trim('。', '！', '？', ' ', '、')) {
                "いただきます" -> "Let's eat!"
                "ごちそうさまでした", "ごちそうさま" -> "Thank you for the meal."
                "行ってきます", "いってきます" -> "I'm heading out!"
                "行ってらっしゃい", "いってらっしゃい" -> "Take care!"
                "ただいま" -> "I'm home!"
                "おかえり", "おかえりなさい" -> "Welcome back!"
                "お前", "あんた" -> "You"
                "大丈夫", "大丈夫か", "大丈夫？" -> "Are you okay?"
                "大丈夫だ", "大丈夫だよ" -> "I'm fine."
                "何だこれ", "なんだこれ", "何これ" -> "What is this?"
                "どうして", "なんで", "何故" -> "Why?"
                "嘘だろ", "うそだろ", "嘘でしょ" -> "No way..."
                "助けて", "助けてくれ" -> "Help me!"
                "待って", "待ってくれ", "待ちなさい" -> "Wait!"
                "行くぞ", "行こう", "いくぞ" -> "Let's go!"
                "やめろ", "やめて" -> "Stop it!"
                "ごめんなさい", "ごめん" -> "I'm sorry."
                "ありがとう", "ありがとうございます" -> "Thank you."
                "よろしく", "よろしくお願いします" -> "Nice to meet you."
                else -> null
            }
            if (animeIdiom != null) return finalize(animeIdiom)
        }

        // Method 1: Google clients5 dict-chrome-ex with explicit Japanese source language
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$effectiveSource&tl=$targetLangCode&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .build()
            val resp = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (resp.isNotEmpty() && resp.startsWith("[")) {
                val arr = JSONArray(resp)
                if (arr.length() > 0) {
                    val first = arr.opt(0)
                    if (first is JSONArray && first.length() > 0) {
                        val trans = first.optString(0, "")
                        if (trans.isNotBlank()) return finalize(trans)
                    } else if (first is String && first.isNotBlank()) {
                        return finalize(first)
                    }
                }
            }
        } catch (_: Exception) {}

        // Method 2: Google clients4 dict-chrome-ex
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://clients4.google.com/translate_a/t?client=dict-chrome-ex&sl=$effectiveSource&tl=$targetLangCode&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .build()
            val resp = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (resp.isNotEmpty() && resp.startsWith("[")) {
                val arr = JSONArray(resp)
                if (arr.length() > 0) {
                    val first = arr.opt(0)
                    if (first is JSONArray && first.length() > 0) {
                        val trans = first.optString(0, "")
                        if (trans.isNotBlank()) return finalize(trans)
                    } else if (first is String && first.isNotBlank()) {
                        return finalize(first)
                    }
                }
            }
        } catch (_: Exception) {}

        // Method 3: Google Translate GET (translate_a/single)
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$effectiveSource&tl=$targetLangCode&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()
            val responseStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (responseStr.isNotEmpty() && responseStr.startsWith("[")) {
                val jsonArr = JSONArray(responseStr)
                val sentencesArr = jsonArr.optJSONArray(0)
                if (sentencesArr != null) {
                    val sb = StringBuilder()
                    for (k in 0 until sentencesArr.length()) {
                        val item = sentencesArr.optJSONArray(k)
                        if (item != null) {
                            sb.append(item.optString(0, ""))
                        }
                    }
                    val res = sb.toString().trim()
                    if (res.isNotBlank()) return finalize(res)
                }
            }
        } catch (_: Exception) {}

        // Method 4: Lingva Translation Mirror
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://lingva.ml/api/v1/$effectiveSource/$targetLangCode/$encoded"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resStr = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (resStr.isNotEmpty()) {
                val json = JSONObject(resStr)
                val trans = json.optString("translation", "")
                if (trans.isNotBlank()) return finalize(trans)
            }
        } catch (_: Exception) {}

        // Method 5: Google Translate mobile m.translate.googleapis.com endpoint
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://translate.google.com/m?sl=$effectiveSource&tl=$targetLangCode&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .build()
            val html = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (html.isNotEmpty() && html.contains("class=\"result-container\"")) {
                val match = Regex("""<div[^>]*class="result-container"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)
                if (match != null) {
                    val translated = android.text.Html.fromHtml(match.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    if (translated.isNotBlank()) return finalize(translated)
                }
            }
        } catch (_: Exception) {}

        // Method 6: MyMemory Translation API
        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$effectiveSource|$targetLangCode"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resStr = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() ?: "" else "" }
            if (resStr.isNotEmpty()) {
                val json = org.json.JSONObject(resStr)
                val respData = json.optJSONObject("responseData")
                val trans = respData?.optString("translatedText", "") ?: ""
                if (trans.isNotBlank() && !trans.contains("MYMEMORY WARNING")) return finalize(trans)
            }
        } catch (_: Exception) {}

        return normalizePunctuationSpacing(cleanText)
    }
}
