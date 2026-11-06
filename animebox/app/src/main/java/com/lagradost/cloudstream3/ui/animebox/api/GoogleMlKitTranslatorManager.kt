package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resumeWithException

object GoogleMlKitTranslatorManager {

    private const val TAG = "MlKitTranslator"

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result, null)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
    }

    /**
     * Map ISO language code to Google ML Kit TranslateLanguage string.
     */
    fun mapLanguageCodeToMlKit(langCode: String): String? {
        val clean = langCode.trim().lowercase()
            .removeSuffix("_pro")
            .substringBefore("-")
            .substringBefore("_")
        return when (clean) {
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "hi" -> TranslateLanguage.HINDI
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "ja" -> TranslateLanguage.JAPANESE
            "pt" -> TranslateLanguage.PORTUGUESE
            "ar" -> TranslateLanguage.ARABIC
            "it" -> TranslateLanguage.ITALIAN
            "ru" -> TranslateLanguage.RUSSIAN
            "id" -> TranslateLanguage.INDONESIAN
            "tr" -> TranslateLanguage.TURKISH
            "ko" -> TranslateLanguage.KOREAN
            "vi" -> TranslateLanguage.VIETNAMESE
            "zh" -> TranslateLanguage.CHINESE
            "pl" -> TranslateLanguage.POLISH
            "nl" -> TranslateLanguage.DUTCH
            "th" -> TranslateLanguage.THAI
            "bn" -> TranslateLanguage.BENGALI
            "ta" -> TranslateLanguage.TAMIL
            "te" -> TranslateLanguage.TELUGU
            "mr" -> TranslateLanguage.MARATHI
            "gu" -> TranslateLanguage.GUJARATI
            "kn" -> TranslateLanguage.KANNADA
            "ur" -> TranslateLanguage.URDU
            "uk" -> TranslateLanguage.UKRAINIAN
            "fa" -> TranslateLanguage.PERSIAN
            "ms" -> TranslateLanguage.MALAY
            "tl", "fil" -> TranslateLanguage.TAGALOG
            else -> TranslateLanguage.fromLanguageTag(clean)
        }
    }

    /**
     * Translates a single text string from source language to target language.
     */
    suspend fun translateText(
        text: String,
        sourceLangCode: String = "ja",
        targetLangCode: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        val src = mapLanguageCodeToMlKit(sourceLangCode) ?: TranslateLanguage.JAPANESE
        val tgt = mapLanguageCodeToMlKit(targetLangCode) ?: TranslateLanguage.ENGLISH
        if (src == tgt) return@withContext text
        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()
            val res = translator.translate(text).awaitTask().trim()
            translator.close()
            if (res.isNotBlank()) res else AiSubtitleModelManager.translateSingleText(text, targetLangCode.removeSuffix("_pro"), sourceLangCode.removeSuffix("_pro"))
        } catch (_: Throwable) {
            try {
                AiSubtitleModelManager.translateSingleText(text, targetLangCode.removeSuffix("_pro"), sourceLangCode.removeSuffix("_pro"))
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Check if a Google ML Kit language model is already downloaded on device.
     */
    suspend fun isModelDownloaded(langCode: String): Boolean = withContext(Dispatchers.IO) {
        val mlKitLang = mapLanguageCodeToMlKit(langCode) ?: return@withContext false
        try {
            val modelManager = RemoteModelManager.getInstance()
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            modelManager.isModelDownloaded(model).awaitTask()
        } catch (e: Exception) {
            Log.e(TAG, "isModelDownloaded check error for $langCode", e)
            false
        }
    }

    /**
     * Download a Google ML Kit translation model on device.
     */
    suspend fun downloadModel(
        langCode: String,
        onProgress: (percent: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val mlKitLang = mapLanguageCodeToMlKit(langCode) ?: return@withContext false
        try {
            withContext(Dispatchers.Main) { onProgress(10) }
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            val enModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
            val modelManager = RemoteModelManager.getInstance()
            val conditions = DownloadConditions.Builder().build()
            withContext(Dispatchers.Main) { onProgress(30) }
            modelManager.download(enModel, conditions).awaitTask()
            withContext(Dispatchers.Main) { onProgress(65) }
            modelManager.download(model, conditions).awaitTask()
            withContext(Dispatchers.Main) { onProgress(100) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadModel error for $langCode", e)
            false
        }
    }

    /**
     * Translate an existing VTT subtitle file to target language using Google ML Kit.
     */
    suspend fun translateVttWithMlKit(
        context: Context,
        vttUrlOrPath: String,
        targetLangCode: String,
        hlsUrl: String = "",
        referer: String = "",
        anilistId: Int = 0,
        episodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        onProgress: ((cur: Int, total: Int, tempPath: String, cues: List<LiveSubtitleCue>) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanLangCode = targetLangCode.trim().lowercase().removeSuffix("_pro")
        val mlKitTargetLang = mapLanguageCodeToMlKit(cleanLangCode) ?: return@withContext null

        var rawVtt = fetchVttContent(context, vttUrlOrPath, hlsUrl, referer)
        if (rawVtt.isBlank() || rawVtt.length < 50) {
            rawVtt = SenseVoiceAudioTranscriber.fetchEpisodeTranscript(
                context,
                hlsUrl,
                referer,
                anilistId,
                episodeNum,
                animeTitle,
                vttUrlOrPath
            ) ?: ""
        }
        if (rawVtt.isBlank() || rawVtt.length < 20) return@withContext null

        val normalizedVtt = rawVtt
            .replace("\r\n", "\n")
            .replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")
            .replace(Regex("""(\d{2}:\d{2}),(\d{3})"""), "$1.$2")

        val originalCues = AiSubtitleModelManager.parseVttToCues(normalizedVtt)
        if (originalCues.isEmpty()) return@withContext null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(mlKitTargetLang)
            .build()

        val translator = Translation.getClient(options)
        try {
            var mlKitReady = false
            try {
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).awaitTask()
                mlKitReady = true
            } catch (dlErr: Throwable) {
                Log.w(TAG, "ML Kit model download/init fallback to cloud neural translator", dlErr)
            }

            val translatedCues = mutableListOf<LiveSubtitleCue>()
            val total = originalCues.size

            val outFile = AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, episodeNum, cleanLangCode)
            val tempProgressFile = File(context.cacheDir, "mlkit_trans_tmp_${System.currentTimeMillis()}.vtt")
            val sb = StringBuilder("WEBVTT\n\n")

            for (i in originalCues.indices) {
                val cue = originalCues[i]
                val origText = cue.originalText.trim()
                var transText = ""

                if (origText.isNotBlank()) {
                    val pureLetters = origText.replace(Regex("""[.\s\dots…～・,;:!?_\-—ー。、\s]"""), "")
                    if (pureLetters.isNotBlank()) {
                        try {
                            transText = if (mlKitReady) {
                                translator.translate(origText).awaitTask().trim()
                            } else {
                                AiSubtitleModelManager.translateSingleText(origText, cleanLangCode, "en").trim()
                            }
                            if (transText.isBlank() || (transText == origText && cleanLangCode != "en")) {
                                val fallback = AiSubtitleModelManager.translateSingleText(origText, cleanLangCode, "en").trim()
                                if (fallback.isNotBlank()) transText = fallback
                            }
                            transText = transText.replace(Regex("""^[A-Za-z0-9_\s]{2,20}:\s*"""), "")
                        } catch (_: Throwable) {
                            transText = try {
                                AiSubtitleModelManager.translateSingleText(origText, cleanLangCode, "en").trim()
                            } catch (_: Throwable) {
                                origText
                            }
                        }
                    }
                }

                val finalCue = LiveSubtitleCue(
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    originalText = cue.originalText,
                    translatedText = if (transText.isNotBlank()) transText else origText
                )
                translatedCues.add(finalCue)

                val startStr = formatMsToVttTime(cue.startMs)
                val endStr = formatMsToVttTime(cue.endMs)
                val cleanLine = SenseVoiceAudioTranscriber.formatToSubtitleLines(finalCue.translatedText)
                if (cleanLine.isNotBlank()) {
                    sb.append("$startStr --> $endStr\n$cleanLine\n\n")
                }

                if (i % 5 == 0 || i == total - 1) {
                    try {
                        tempProgressFile.writeText(sb.toString())
                    } catch (_: Throwable) {}
                    val snapshot = ArrayList(translatedCues)
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(i + 1, total, tempProgressFile.absolutePath, snapshot)
                    }
                }
            }

            try { translator.close() } catch (_: Throwable) {}
            try { tempProgressFile.delete() } catch (_: Throwable) {}

            if (translatedCues.isNotEmpty()) {
                outFile.parentFile?.mkdirs()
                outFile.writeText(sb.toString())

                AiSubtitleModelManager.saveSubtitleMetadata(
                    context = context,
                    info = SavedSubtitleInfo(
                        filePath = outFile.absolutePath,
                        anilistId = anilistId,
                        episodeNum = episodeNum,
                        animeTitle = animeTitle,
                        langCode = cleanLangCode,
                        langName = AiSubtitleModelManager.getLanguageName(cleanLangCode),
                        fileSizeBytes = outFile.length(),
                        lastModified = System.currentTimeMillis(),
                        coverUrl = coverUrl
                    )
                )

                outFile.absolutePath
            } else {
                if (outFile.exists()) outFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "translateVttWithMlKit error", e)
            try { translator.close() } catch (_: Exception) {}
            null
        }
    }

    private fun fetchVttContent(context: Context, urlOrPath: String, hlsUrl: String, referer: String): String {
        if (urlOrPath.isEmpty()) return ""
        return try {
            when {
                urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://") -> {
                    val client = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper.applyDns(
                        okhttp3.OkHttpClient.Builder()
                            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    ).build()
                    val reqBuilder = okhttp3.Request.Builder()
                        .url(urlOrPath)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    if (referer.isNotBlank()) {
                        reqBuilder.header("Referer", referer)
                    }
                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }
                }
                urlOrPath.startsWith("content://") -> {
                    context.contentResolver.openInputStream(android.net.Uri.parse(urlOrPath))?.bufferedReader()?.use { it.readText() } ?: ""
                }
                else -> {
                    val f = File(urlOrPath.removePrefix("file://"))
                    if (f.exists()) f.readText() else ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatMsToVttTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
