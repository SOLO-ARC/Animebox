package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ByseResult(
    val hlsUrls: List<String>,
    val referer: String,
    val subtitles: List<Map<String, String>> = emptyList()
)

object ByseExtractor {

    private val client: OkHttpClient by lazy {
        AnililiHttpClient.client
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

    private const val BLOCKS = 512
    private const val MASK = BLOCKS - 1
    private const val ROUNDS = 2
    private const val MUL_A = 2654435761L
    private const val MUL_B = 2246822519L

    fun canExtract(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return Regex("""(?:byse[a-z0-9-]*\.[a-z]+|gn1r5n\.org|mfw\d+\.org)/(?:e|v|d|embed)/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
               (lower.contains("byse") || lower.contains("gn1r5n") || lower.contains("mfw")) && (Regex("""/(?:e|v|d|embed)/([a-zA-Z0-9]+)""").containsMatchIn(url) || Regex("""/([a-zA-Z0-9]{6,})""").containsMatchIn(url))
    }

    private fun b64uDec(str: String): ByteArray {
        return Base64.decode(str, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun b64uEnc(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
    }

    private fun rot(v: Long, shift: Int): Long {
        val u32 = v and 0xFFFFFFFFL
        return ((u32 shl shift) or (u32 ushr (32 - shift))) and 0xFFFFFFFFL
    }

    private fun mul(v: Long, factor: Long): Long {
        val a = (v and 0xFFFFFFFFL).toInt()
        val b = (factor and 0xFFFFFFFFL).toInt()
        return ((a * b).toLong()) and 0xFFFFFFFFL
    }

    private fun mix(state: LongArray) {
        state[0] = (state[0] + state[1]) and 0xFFFFFFFFL
        state[3] = rot(state[3] xor state[0], 16)
        state[2] = (state[2] + state[3]) and 0xFFFFFFFFL
        state[1] = rot(state[1] xor state[2], 12)
        state[0] = (state[0] + state[1]) and 0xFFFFFFFFL
        state[3] = rot(state[3] xor state[0], 8)
        state[2] = (state[2] + state[3]) and 0xFFFFFFFFL
        state[1] = rot(state[1] xor state[2], 7)
    }

    private fun byseHash(bytes: ByteArray): LongArray {
        val state = longArrayOf(1779033703L, 3144134277L, 1013904242L, 2773480762L)
        for (b in bytes) {
            val u8 = (b.toInt() and 0xFF).toLong()
            state[0] = (state[0] + u8) and 0xFFFFFFFFL
            state[0] = rot(state[0], 7)
            mix(state)
        }
        for (i in 0 until 8) mix(state)
        val table = LongArray(BLOCKS)
        for (i in 0 until BLOCKS) {
            mix(state)
            table[i] = (state[0] xor state[2]) and 0xFFFFFFFFL
        }
        for (i in 0 until ROUNDS) {
            for (index in 0 until BLOCKS) {
                val tableIndex = (table[index] and MASK.toLong()).toInt()
                var value = (table[index] + table[tableIndex]) and 0xFFFFFFFFL
                value = rot(value, 13)
                value = (value xor mul(table[(index + 1) and MASK], MUL_A)) and 0xFFFFFFFFL
                table[index] = value
                state[0] = (state[0] xor value) and 0xFFFFFFFFL
                mix(state)
            }
        }
        val out = LongArray(8)
        val width = BLOCKS / 8
        for (i in 0 until 8) {
            mix(state)
            var value = state[0]
            val offset = i * width
            for (index in 0 until width) {
                val tableValue = table[offset + index]
                value = (value + tableValue) and 0xFFFFFFFFL
                value = rot(value, 5)
                value = (value xor mul(tableValue, MUL_B)) and 0xFFFFFFFFL
            }
            out[i] = (value xor state[2]) and 0xFFFFFFFFL
        }
        return out
    }

    private fun leadingZeros(values: LongArray): Int {
        var total = 0
        for (item in values) {
            val u32 = (item and 0xFFFFFFFFL).toInt()
            if (u32 == 0) {
                total += 32
                continue
            }
            return total + Integer.numberOfLeadingZeros(u32)
        }
        return total
    }

    private fun solvePoW(nonce: String, difficulty: Int): String {
        val prefix = "$nonce:"
        var counter = 0L
        while (true) {
            val candidate = (prefix + counter).toByteArray(Charsets.ISO_8859_1)
            if (leadingZeros(byseHash(candidate)) >= difficulty) {
                return counter.toString()
            }
            counter++
        }
    }

    private fun derToRawP1363(der: ByteArray): ByteArray {
        var offset = 0
        if (der[offset++].toInt() != 0x30) throw IllegalArgumentException("Invalid DER")
        var len = der[offset++].toInt() and 0xFF
        if (len and 0x80 != 0) {
            val nBytes = len and 0x7F
            offset += nBytes
        }
        // r
        if (der[offset++].toInt() != 0x02) throw IllegalArgumentException("Invalid DER r")
        val rLen = der[offset++].toInt() and 0xFF
        var rStart = offset
        var rCount = rLen
        offset += rLen
        while (rCount > 32 && der[rStart].toInt() == 0) {
            rStart++
            rCount--
        }

        // s
        if (der[offset++].toInt() != 0x02) throw IllegalArgumentException("Invalid DER s")
        val sLen = der[offset++].toInt() and 0xFF
        var sStart = offset
        var sCount = sLen
        while (sCount > 32 && der[sStart].toInt() == 0) {
            sStart++
            sCount--
        }

        val raw = ByteArray(64)
        System.arraycopy(der, rStart, raw, 32 - rCount, rCount)
        System.arraycopy(der, sStart, raw, 64 - sCount, sCount)
        return raw
    }

    suspend fun extract(
        embedUrl: String,
        referer: String = "https://aniwaves.ru/"
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val code = Regex("""/(?:e|v|d|embed)/([a-zA-Z0-9]+)""").find(embedUrl)?.groupValues?.get(1)
                ?: Regex("""/([a-zA-Z0-9]{6,})""").find(embedUrl)?.groupValues?.get(1)
                ?: return@withContext emptyList()

            val embedUri = URL(embedUrl)
            val embedOrigin = "${embedUri.protocol}://${embedUri.host}${if (embedUri.port > 0 && embedUri.port != 80 && embedUri.port != 443) ":${embedUri.port}" else ""}"
            val parentUrl = if (referer.isNotEmpty()) referer else embedUrl
            val parentHost = try {
                val u = if (parentUrl.startsWith("http")) URL(parentUrl) else URL("https://$parentUrl")
                u.host
            } catch (_: Exception) { embedUri.host }

            val embedHeaders = mapOf(
                "X-Embed-Origin" to parentHost,
                "X-Embed-Referer" to parentUrl,
                "X-Embed-Parent" to embedUrl
            )

            // 1. Details
            val detailsReq = Request.Builder()
                .url("$embedOrigin/api/videos/$code/embed/details")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val detailsJsonStr = client.newCall(detailsReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (detailsJsonStr.isEmpty()) return@withContext emptyList()

            val details = JSONObject(detailsJsonStr)
            val frameUrl = details.optString("embed_frame_url", embedUrl)
        val frameUri = URL(frameUrl)
        val frameBase = "${frameUri.protocol}://${frameUri.host}${if (frameUri.port > 0 && frameUri.port != 80 && frameUri.port != 443) ":${frameUri.port}" else ""}"

        // 2. Challenge
        val challengeReq = Request.Builder()
            .url("$frameBase/api/videos/access/challenge")
            .post("".toRequestBody(null))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Origin", frameBase)
            .addHeader("Referer", frameUrl)
            .build()

        val challengeJsonStr = client.newCall(challengeReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
        if (challengeJsonStr.isEmpty()) return@withContext emptyList()

        val challenge = JSONObject(challengeJsonStr)
        val nonce = challenge.optString("nonce")
        val challengeId = challenge.optString("challenge_id")

        // 3. ECDSA KeyPair & Sign
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val ecPub = keyPair.public as ECPublicKey

        fun toPadded32(b: ByteArray): ByteArray {
            var start = 0
            while (start < b.size && b[start].toInt() == 0) start++
            val unpadded = b.copyOfRange(start, b.size)
            if (unpadded.size >= 32) return unpadded.copyOfRange(unpadded.size - 32, unpadded.size)
            val padded = ByteArray(32)
            System.arraycopy(unpadded, 0, padded, 32 - unpadded.size, unpadded.size)
            return padded
        }

        val xBytes = toPadded32(ecPub.w.affineX.toByteArray())
        val yBytes = toPadded32(ecPub.w.affineY.toByteArray())

        val jwk = JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", b64uEnc(xBytes))
            put("y", b64uEnc(yBytes))
        }

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonce.toByteArray(Charsets.UTF_8))
        val derSig = signer.sign()
        val rawSig = derToRawP1363(derSig)
        val sigB64 = b64uEnc(rawSig)

        // 4. Attest
        val attestBody = JSONObject().apply {
            put("nonce", nonce)
            put("challenge_id", challengeId)
            put("public_key", jwk)
            put("signature", sigB64)
        }.toString()

        val attestReq = Request.Builder()
            .url("$frameBase/api/videos/access/attest")
            .post(attestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Origin", frameBase)
            .addHeader("Referer", frameUrl)
            .build()

        val attestJsonStr = client.newCall(attestReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
        if (attestJsonStr.isEmpty()) return@withContext emptyList()

        val attest = JSONObject(attestJsonStr)
        val viewerId = attest.optString("viewer_id")
        val deviceId = attest.optString("device_id")
        val cookie = "byse_viewer_id=$viewerId; byse_device_id=$deviceId"

        val fingerprint = JSONObject().apply {
            put("token", attest.optString("token"))
            put("viewer_id", viewerId)
            put("device_id", deviceId)
            put("confidence", attest.optDouble("confidence", 0.0))
        }

        // 5. Captcha
        val captchaBody = JSONObject().apply {
            put("fingerprint", fingerprint)
        }.toString()

        val captchaReq = Request.Builder()
            .url("$frameBase/api/videos/$code/embed/captcha")
            .post(captchaBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Origin", frameBase)
            .addHeader("Referer", frameUrl)
            .addHeader("Cookie", cookie)
            .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val captchaJsonStr = client.newCall(captchaReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
        if (captchaJsonStr.isEmpty()) return@withContext emptyList()

        val captcha = JSONObject(captchaJsonStr)
        val powToken = captcha.optString("pow_token")
        val powNonce = captcha.optString("pow_nonce")
        val powDifficulty = captcha.optInt("pow_difficulty", 0)

        // 6. Solve PoW
        val solution = solvePoW(powNonce, powDifficulty)

        // 7. Verify
        val verifyBody = JSONObject().apply {
            put("pow_token", powToken)
            put("solution", solution)
            put("fingerprint", fingerprint)
        }.toString()

        val verifyReq = Request.Builder()
            .url("$frameBase/api/videos/$code/embed/captcha/verify")
            .post(verifyBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Origin", frameBase)
            .addHeader("Referer", frameUrl)
            .addHeader("Cookie", cookie)
            .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val verifyJsonStr = client.newCall(verifyReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
        if (verifyJsonStr.isEmpty()) return@withContext emptyList()

        val verification = JSONObject(verifyJsonStr)
        val verifyToken = verification.optString("token")

        // 8. Playback
        val playbackBody = JSONObject().apply {
            put("fingerprint", fingerprint)
        }.toString()

        val playbackReq = Request.Builder()
            .url("$frameBase/api/videos/$code/embed/playback")
            .post(playbackBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Origin", frameBase)
            .addHeader("Referer", frameUrl)
            .addHeader("Cookie", cookie)
            .addHeader("X-Captcha-Token", verifyToken)
            .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val playbackJsonStr = client.newCall(playbackReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
        if (playbackJsonStr.isEmpty()) return@withContext emptyList()

        val playbackData = JSONObject(playbackJsonStr)
        val playback = playbackData.optJSONObject("playback") ?: return@withContext emptyList()

        val keyPartsArr = playback.optJSONArray("key_parts") ?: return@withContext emptyList()
        val keyPartsBytes = ByteArrayOutputStream()
        for (i in 0 until keyPartsArr.length()) {
            val part = keyPartsArr.optString(i)
            val dec = b64uDec(part)
            if (dec.size == 16) {
                keyPartsBytes.write(dec)
            }
        }
        val keyBytes = keyPartsBytes.toByteArray()
        val ivBytes = b64uDec(playback.optString("iv"))
        val payloadBytes = b64uDec(playback.optString("payload"))

        // 9. AES-GCM Decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = cipher.doFinal(payloadBytes)

        val decryptedJson = JSONObject(String(decrypted, Charsets.UTF_8))
        val sourcesArr = decryptedJson.optJSONArray("sources") ?: return@withContext emptyList()

        val streamUrls = mutableListOf<String>()
        for (i in 0 until sourcesArr.length()) {
            val srcObj = sourcesArr.optJSONObject(i) ?: continue
            val u = srcObj.optString("url")
            if (u.isNotEmpty()) {
                streamUrls.add(u)
            }
        }

        streamUrls
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun extractFull(
        embedUrl: String,
        referer: String = ""
    ): ByseResult = withContext(Dispatchers.IO) {
        val effectiveRef = if (referer.isNotEmpty()) referer else embedUrl
        try {
            val code = Regex("""/(?:e|v|d|embed)/([a-zA-Z0-9]+)""").find(embedUrl)?.groupValues?.get(1)
                ?: Regex("""/([a-zA-Z0-9]{6,})""").find(embedUrl)?.groupValues?.get(1)
                ?: return@withContext ByseResult(emptyList(), effectiveRef)

            val embedUri = URL(embedUrl)
            val embedOrigin = "${embedUri.protocol}://${embedUri.host}${if (embedUri.port > 0 && embedUri.port != 80 && embedUri.port != 443) ":${embedUri.port}" else ""}"
            val parentHost = try {
                val u = if (effectiveRef.startsWith("http")) URL(effectiveRef) else URL("https://$effectiveRef")
                u.host
            } catch (_: Exception) { embedUri.host }

            val embedHeaders = mapOf(
                "X-Embed-Origin" to parentHost,
                "X-Embed-Referer" to effectiveRef,
                "X-Embed-Parent" to embedUrl
            )

            // 1. Details
            val detailsReq = Request.Builder()
                .url("$embedOrigin/api/videos/$code/embed/details")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val detailsJsonStr = client.newCall(detailsReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (detailsJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), embedOrigin)

            val details = JSONObject(detailsJsonStr)
            val frameUrl = details.optString("embed_frame_url", embedUrl)
            val frameUri = URL(frameUrl)
            val frameBase = "${frameUri.protocol}://${frameUri.host}${if (frameUri.port > 0 && frameUri.port != 80 && frameUri.port != 443) ":${frameUri.port}" else ""}"

            // 2. Challenge
            val challengeReq = Request.Builder()
                .url("$frameBase/api/videos/access/challenge")
                .post("".toRequestBody(null))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", frameBase)
                .addHeader("Referer", frameUrl)
                .build()

            val challengeJsonStr = client.newCall(challengeReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (challengeJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), frameBase)

            val challenge = JSONObject(challengeJsonStr)
            val nonce = challenge.optString("nonce")
            val challengeId = challenge.optString("challenge_id")

            // 3. ECDSA KeyPair & Sign
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = kpg.generateKeyPair()
            val ecPub = keyPair.public as ECPublicKey

            fun toPadded32(b: ByteArray): ByteArray {
                var start = 0
                while (start < b.size && b[start].toInt() == 0) start++
                val unpadded = b.copyOfRange(start, b.size)
                if (unpadded.size >= 32) return unpadded.copyOfRange(unpadded.size - 32, unpadded.size)
                val padded = ByteArray(32)
                System.arraycopy(unpadded, 0, padded, 32 - unpadded.size, unpadded.size)
                return padded
            }

            val xBytes = toPadded32(ecPub.w.affineX.toByteArray())
            val yBytes = toPadded32(ecPub.w.affineY.toByteArray())

            val jwk = JSONObject().apply {
                put("kty", "EC")
                put("crv", "P-256")
                put("x", b64uEnc(xBytes))
                put("y", b64uEnc(yBytes))
            }

            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(keyPair.private)
            signer.update(nonce.toByteArray(Charsets.UTF_8))
            val derSig = signer.sign()
            val rawSig = derToRawP1363(derSig)
            val sigB64 = b64uEnc(rawSig)

            // 4. Attest
            val attestBody = JSONObject().apply {
                put("nonce", nonce)
                put("challenge_id", challengeId)
                put("public_key", jwk)
                put("signature", sigB64)
            }.toString()

            val attestReq = Request.Builder()
                .url("$frameBase/api/videos/access/attest")
                .post(attestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", frameBase)
                .addHeader("Referer", frameUrl)
                .build()

            var cookie = ""
            val attestJsonStr = client.newCall(attestReq).execute().use { resp ->
                val setCookie = resp.header("Set-Cookie", "") ?: ""
                if (setCookie.contains("=")) {
                    cookie = setCookie.substringBefore(";")
                }
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (attestJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), frameBase)

            val attest = JSONObject(attestJsonStr)
            val viewerId = attest.optString("viewer_id")
            val deviceId = attest.optString("device_id")
            if (cookie.isEmpty()) {
                cookie = "byse_viewer_id=$viewerId; byse_device_id=$deviceId"
            }

            val fingerprint = JSONObject().apply {
                put("token", attest.optString("token"))
                put("viewer_id", viewerId)
                put("device_id", deviceId)
                put("confidence", attest.optDouble("confidence", 0.0))
            }

            // 5. Captcha
            val captchaBody = JSONObject().apply {
                put("fingerprint", fingerprint)
            }.toString()

            val captchaReq = Request.Builder()
                .url("$frameBase/api/videos/$code/embed/captcha")
                .post(captchaBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", frameBase)
                .addHeader("Referer", frameUrl)
                .addHeader("Cookie", cookie)
                .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val captchaJsonStr = client.newCall(captchaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (captchaJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), frameBase)

            val captcha = JSONObject(captchaJsonStr)
            val powToken = captcha.optString("pow_token")
            val powNonce = captcha.optString("pow_nonce")
            val powDifficulty = captcha.optInt("pow_difficulty", 0)

            // 6. Solve PoW
            val solution = solvePoW(powNonce, powDifficulty)

            // 7. Verify
            val verifyBody = JSONObject().apply {
                put("pow_token", powToken)
                put("solution", solution)
                put("fingerprint", fingerprint)
            }.toString()

            val verifyReq = Request.Builder()
                .url("$frameBase/api/videos/$code/embed/captcha/verify")
                .post(verifyBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", frameBase)
                .addHeader("Referer", frameUrl)
                .addHeader("Cookie", cookie)
                .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val verifyJsonStr = client.newCall(verifyReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (verifyJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), frameBase)

            val verification = JSONObject(verifyJsonStr)
            val verifyToken = verification.optString("token")

            // 8. Playback
            val playbackBody = JSONObject().apply {
                put("fingerprint", fingerprint)
            }.toString()

            val playbackReq = Request.Builder()
                .url("$frameBase/api/videos/$code/embed/playback")
                .post(playbackBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", frameBase)
                .addHeader("Referer", frameUrl)
                .addHeader("Cookie", cookie)
                .addHeader("X-Captcha-Token", verifyToken)
                .apply { embedHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val playbackJsonStr = client.newCall(playbackReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (playbackJsonStr.isEmpty()) return@withContext ByseResult(emptyList(), frameBase)

            val playbackData = JSONObject(playbackJsonStr)
            val playback = playbackData.optJSONObject("playback") ?: return@withContext ByseResult(emptyList(), frameBase)

            val keyPartsArr = playback.optJSONArray("key_parts") ?: return@withContext ByseResult(emptyList(), frameBase)
            val keyPartsBytes = ByteArrayOutputStream()
            for (i in 0 until keyPartsArr.length()) {
                val part = keyPartsArr.optString(i)
                val dec = b64uDec(part)
                if (dec.size == 16) {
                    keyPartsBytes.write(dec)
                }
            }
            val keyBytes = keyPartsBytes.toByteArray()
            val ivBytes = b64uDec(playback.optString("iv"))
            val payloadBytes = b64uDec(playback.optString("payload"))

            // 9. AES-GCM Decrypt
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(payloadBytes)

            val decryptedJson = JSONObject(String(decrypted, Charsets.UTF_8))
            val sourcesArr = decryptedJson.optJSONArray("sources") ?: return@withContext ByseResult(emptyList(), frameBase)

            val streamUrls = mutableListOf<String>()
            for (i in 0 until sourcesArr.length()) {
                val srcObj = sourcesArr.optJSONObject(i) ?: continue
                val u = srcObj.optString("url")
                if (u.isNotEmpty()) {
                    streamUrls.add(u)
                }
            }

            val subsList = mutableListOf<Map<String, String>>()
            val seenSubUrls = HashSet<String>()

            fun addSubTrack(tObj: JSONObject, baseOrigin: String) {
                val tKind = tObj.optString("kind", "").lowercase()
                var tFile = tObj.optString("file", tObj.optString("url", tObj.optString("src", ""))).trim()
                val tLabel = tObj.optString("label", tObj.optString("name", tObj.optString("lang", tObj.optString("language", "")))).trim()
                if (tFile.isEmpty()) return
                val lowerFile = tFile.lowercase()
                val lowerLabel = tLabel.lowercase()
                if (tKind.contains("thumbnail") || lowerFile.contains("thumbnail") || lowerFile.contains("sprite") || lowerFile.contains("xywh=") || lowerLabel.contains("thumbnail") || lowerLabel.contains("sprite")) {
                    return // Skip preview thumbnails / storyboard tracks
                }
                if (!tFile.startsWith("http://") && !tFile.startsWith("https://")) {
                    tFile = if (tFile.startsWith("/")) "$baseOrigin$tFile" else "$baseOrigin/$tFile"
                }
                if (seenSubUrls.add(tFile)) {
                    val cleanLabel = if (tLabel.isNotBlank()) tLabel else "English"
                    subsList.add(mapOf("url" to tFile, "lang" to cleanLabel, "label" to cleanLabel))
                }
            }

            // Parse subtitles from details endpoint
            listOf("tracks", "subtitles", "captions").forEach { key ->
                val arr = details.optJSONArray(key)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { addSubTrack(it, frameBase.ifEmpty { embedOrigin }) }
                    }
                }
            }

            // Parse subtitles from decrypted playback payload
            listOf("tracks", "subtitles", "captions").forEach { key ->
                val arr = decryptedJson.optJSONArray(key)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { addSubTrack(it, frameBase.ifEmpty { embedOrigin }) }
                    }
                }
            }

            // If still empty and we have an m3u8 playlist, parse HLS subtitle playlists
            if (subsList.isEmpty() && streamUrls.isNotEmpty() && streamUrls[0].contains(".m3u8", ignoreCase = true)) {
                try {
                    val masterReq = Request.Builder()
                        .url(streamUrls[0])
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", frameUrl.ifEmpty { effectiveRef })
                        .build()
                    val masterContent = client.newCall(masterReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }
                    if (masterContent.isNotEmpty()) {
                        val subRegex = Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES.*?NAME="([^"]+)".*?URI="([^"]+)"""")
                        val baseM3u8 = streamUrls[0].substringBeforeLast('/')
                        subRegex.findAll(masterContent).forEach { match ->
                            val sName = match.groupValues[1].ifEmpty { "English" }
                            var sUri = match.groupValues[2]
                            if (!sUri.startsWith("http://") && !sUri.startsWith("https://")) {
                                sUri = if (sUri.startsWith("/")) {
                                    val u = URL(streamUrls[0])
                                    "${u.protocol}://${u.host}$sUri"
                                } else {
                                    "$baseM3u8/$sUri"
                                }
                            }
                            if (seenSubUrls.add(sUri)) {
                                subsList.add(mapOf("url" to sUri, "lang" to sName, "label" to sName))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalRef = frameUrl.ifEmpty { effectiveRef }
            ByseResult(streamUrls, finalRef, subsList)
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackStreams = extract(embedUrl, effectiveRef)
            ByseResult(fallbackStreams, effectiveRef, emptyList())
        }
    }
}
