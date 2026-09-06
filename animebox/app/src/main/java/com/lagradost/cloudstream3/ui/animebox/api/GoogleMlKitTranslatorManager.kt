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
        return when (langCode.lowercase()) {
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
            else -> null
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
            onProgress(10)
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            val modelManager = RemoteModelManager.getInstance()
            val conditions = DownloadConditions.Builder().build()
            onProgress(30)
            modelManager.download(model, conditions).awaitTask()
            onProgress(100)
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
        val mlKitTargetLang = mapLanguageCodeToMlKit(targetLangCode) ?: return@withContext null

        val rawVtt = fetchVttContent(context, vttUrlOrPath, hlsUrl, referer)
        if (rawVtt.isBlank() || rawVtt.length < 50) return@withContext null

        val originalCues = AiSubtitleModelManager.parseVttToCues(rawVtt)
        if (originalCues.isEmpty()) return@withContext null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(mlKitTargetLang)
            .build()

        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()

            val translatedCues = mutableListOf<LiveSubtitleCue>()
            val total = originalCues.size

            val outFile = AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, episodeNum, targetLangCode)
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
                            transText = translator.translate(origText).awaitTask().trim()
                            transText = transText.replace(Regex("""^[A-Za-z0-9_\s]{2,20}:\s*"""), "")
                        } catch (_: Throwable) {
                            transText = origText
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
                    onProgress?.invoke(i + 1, total, tempProgressFile.absolutePath, ArrayList(translatedCues))
                }
            }

            translator.close()
            try { tempProgressFile.delete() } catch (_: Throwable) {}

            if (translatedCues.size >= 5) {
                outFile.parentFile?.mkdirs()
                outFile.writeText(sb.toString())

                AiSubtitleModelManager.saveSubtitleMetadata(
                    context = context,
                    info = SavedSubtitleInfo(
                        filePath = outFile.absolutePath,
                        anilistId = anilistId,
                        episodeNum = episodeNum,
                        animeTitle = animeTitle,
                        langCode = targetLangCode,
                        langName = AiSubtitleModelManager.getLanguageName(targetLangCode),
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
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder().url(urlOrPath).build()
                    client.newCall(req).execute().use { it.body?.string() ?: "" }
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
