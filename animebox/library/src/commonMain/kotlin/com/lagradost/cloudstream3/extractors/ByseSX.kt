package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.http.Url
import io.ktor.http.decodeURLPart

class Bysezejataos : ByseSX() {
    override var name = "Bysezejataos"
    override var mainUrl = "https://bysezejataos.com"
}

class ByseBuho : ByseSX() {
    override var name = "ByseBuho"
    override var mainUrl = "https://bysebuho.com"
}

class ByseVepoin : ByseSX() {
    override var name = "ByseVepoin"
    override var mainUrl = "https://bysevepoin.com"
}

class ByseQekaho : ByseSX() {
    override var name = "ByseQekaho"
    override var mainUrl = "https://byseqekaho.com"
}

class ByseFujedu : ByseSX() {
    override var name = "ByseFujedu"
    override var mainUrl = "https://bysefujedu.com"
}

open class ByseSX : ExtractorApi() {
    override var name = "Byse"
    override var mainUrl = "https://byse.sx"
    override val requiresReferer = true

    private val aesGcm = CryptographyProvider.Default.get(AES.GCM)

    private fun b64UrlDecode(s: String): ByteArray {
        val fixed = s.replace('-', '+').replace('_', '/')
        val pad = (4 - fixed.length % 4) % 4
        return base64DecodeArray(fixed + "=".repeat(pad))
    }

    private fun getBaseUrl(url: String): String {
        return Url(url).let { "${it.protocol.name}://${it.host}" }
    }

    private fun getCodeFromUrl(url: String): String {
        val path = Url(url).encodedPath.decodeURLPart()
        return path.trimEnd('/').substringAfterLast('/')
    }

    private suspend fun getDetails(mainUrl: String): DetailsRoot? {
        val base = getBaseUrl(mainUrl)
        val code = getCodeFromUrl(mainUrl)
        val url = "$base/api/videos/$code/embed/details"
        return app.get(url).parsedSafe<DetailsRoot>()
    }

    private suspend fun getPlayback(mainUrl: String): PlaybackRoot? {
        val details = getDetails(mainUrl) ?: return null
        val embedFrameUrl = details.embedFrameUrl
        val embedBase = getBaseUrl(embedFrameUrl)
        val code = getCodeFromUrl(embedFrameUrl)
        val playbackUrl = "$embedBase/api/videos/$code/embed/playback"
        val headers = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "priority" to "u=1, i",
            "referer" to embedFrameUrl,
            "x-embed-parent" to mainUrl,
        )
        return app.get(playbackUrl, headers = headers).parsedSafe<PlaybackRoot>()
    }

    private fun buildAesKey(playback: Playback): ByteArray {
        val p1 = b64UrlDecode(playback.keyParts[0])
        val p2 = b64UrlDecode(playback.keyParts[1])
        return p1 + p2
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun decryptPlaybackFull(playback: Playback): PlaybackDecrypt? {
        val keyBytes = buildAesKey(playback)
        val ivBytes = b64UrlDecode(playback.iv)
        val cipherBytes = b64UrlDecode(playback.payload)

        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        val cipher = aesKey.cipher()
        val plainBytes = cipher.decryptWithIv(ivBytes, cipherBytes)

        var jsonStr = plainBytes.decodeToString()
        if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)

        return try {
            tryParseJson<PlaybackDecrypt>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun decryptPlayback(playback: Playback): String? {
        return decryptPlaybackFull(playback)?.sources?.firstOrNull()?.url
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return
        val decrypted = decryptPlaybackFull(playbackRoot.playback) ?: return
        val streamUrl = decrypted.sources.firstOrNull()?.url ?: return

        fun isValidSubTrack(sub: PlaybackDecryptSubtitle, url: String): Boolean {
            val k = sub.kind?.lowercase() ?: ""
            val l = (sub.label ?: sub.lang ?: sub.language ?: "").lowercase()
            val u = url.lowercase()
            return !k.contains("thumbnail") && !l.contains("thumbnail") && !l.contains("sprite") &&
                   !u.contains("thumbnail") && !u.contains("sprite") && !u.contains("xywh=")
        }

        decrypted.subtitles?.forEach { sub ->
            val subUrl = sub.url ?: sub.file ?: sub.src ?: return@forEach
            if (!isValidSubTrack(sub, subUrl)) return@forEach
            subtitleCallback(
                com.lagradost.cloudstream3.newSubtitleFile(
                    sub.label ?: sub.lang ?: sub.language ?: "English",
                    subUrl
                )
            )
        }
        decrypted.tracks?.forEach { sub ->
            val subUrl = sub.url ?: sub.file ?: sub.src ?: return@forEach
            if (!isValidSubTrack(sub, subUrl)) return@forEach
            subtitleCallback(
                com.lagradost.cloudstream3.newSubtitleFile(
                    sub.label ?: sub.lang ?: sub.language ?: "English",
                    subUrl
                )
            )
        }
        decrypted.captions?.forEach { sub ->
            val subUrl = sub.url ?: sub.file ?: sub.src ?: return@forEach
            if (!isValidSubTrack(sub, subUrl)) return@forEach
            subtitleCallback(
                com.lagradost.cloudstream3.newSubtitleFile(
                    sub.label ?: sub.lang ?: sub.language ?: "English",
                    subUrl
                )
            )
        }

        val headers = mapOf("Referer" to refererUrl)
        M3u8Helper.generateM3u8(
            name,
            streamUrl,
            mainUrl,
            headers = headers,
            subtitleCallback = subtitleCallback
        ).forEach(callback)
    }
}

data class DetailsRoot(
    val id: Long,
    val code: String,
    val title: String,
    @JsonProperty("poster_url")
    val posterUrl: String,
    val description: String,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("owner_private")
    val ownerPrivate: Boolean,
    @JsonProperty("embed_frame_url")
    val embedFrameUrl: String,
    val subtitles: List<PlaybackDecryptSubtitle>? = null,
    val tracks: List<PlaybackDecryptSubtitle>? = null,
    val captions: List<PlaybackDecryptSubtitle>? = null,
)

data class PlaybackRoot(
    val playback: Playback,
)

data class Playback(
    val algorithm: String,
    val iv: String,
    val payload: String,
    @JsonProperty("key_parts")
    val keyParts: List<String>,
    @JsonProperty("expires_at")
    val expiresAt: String,
    @JsonProperty("decrypt_keys")
    val decryptKeys: DecryptKeys,
    val iv2: String,
    val payload2: String,
)

data class DecryptKeys(
    @JsonProperty("edge_1")
    val edge1: String,
    @JsonProperty("edge_2")
    val edge2: String,
    @JsonProperty("legacy_fallback")
    val legacyFallback: String,
)

data class PlaybackDecryptSubtitle(
    val file: String? = null,
    val url: String? = null,
    val src: String? = null,
    val label: String? = null,
    val lang: String? = null,
    val language: String? = null,
    val kind: String? = null,
)

data class PlaybackDecrypt(
    val sources: List<PlaybackDecryptSource> = emptyList(),
    val subtitles: List<PlaybackDecryptSubtitle>? = null,
    val tracks: List<PlaybackDecryptSubtitle>? = null,
    val captions: List<PlaybackDecryptSubtitle>? = null,
)

data class PlaybackDecryptSource(
    val quality: String,
    val label: String,
    @JsonProperty("mime_type")
    val mimeType: String,
    val url: String,
    @JsonProperty("bitrate_kbps")
    val bitrateKbps: Long,
    val height: Any?,
)
