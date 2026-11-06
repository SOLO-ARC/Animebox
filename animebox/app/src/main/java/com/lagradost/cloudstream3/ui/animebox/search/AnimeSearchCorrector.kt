package com.lagradost.cloudstream3.ui.animebox.search

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Algorithmic Search Corrector modeled after YouTube/search engine autocorrection:
 * 1. Physical QWERTY keyboard spatial distance (fat-finger modeling on mobile screens).
 * 2. Automated compound word splitting (e.g., "rezero" -> "re zero", "sololeveling" -> "solo leveling").
 * 3. Weighted Damerau-Levenshtein edit distance accounting for spatial proximity and transposition.
 * 4. Canonical vocabulary of anime terms, titles, and search modifiers without hardcoded typo pairs.
 */
object AnimeSearchCorrector {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Physical key coordinates on standard QWERTY layout for spatial keyboard modeling
    private val QWERTY_COORDS = mapOf(
        'q' to (0.0 to 0.0), 'w' to (1.0 to 0.0), 'e' to (2.0 to 0.0), 'r' to (3.0 to 0.0), 't' to (4.0 to 0.0),
        'y' to (5.0 to 0.0), 'u' to (6.0 to 0.0), 'i' to (7.0 to 0.0), 'o' to (8.0 to 0.0), 'p' to (9.0 to 0.0),
        'a' to (0.5 to 1.0), 's' to (1.5 to 1.0), 'd' to (2.5 to 1.0), 'f' to (3.5 to 1.0), 'g' to (4.5 to 1.0),
        'h' to (5.5 to 1.0), 'j' to (6.5 to 1.0), 'k' to (7.5 to 1.0), 'l' to (8.5 to 1.0),
        'z' to (1.5 to 2.0), 'x' to (2.5 to 2.0), 'c' to (3.5 to 2.0), 'v' to (4.5 to 2.0), 'b' to (5.5 to 2.0),
        'n' to (6.5 to 2.0), 'm' to (7.5 to 2.0)
    )

    // Common anime search acronym expansions
    private val acronymExpansions = mapOf(
        "jjk" to "jujutsu kaisen",
        "mha" to "my hero academia",
        "bnha" to "my hero academia",
        "hxh" to "hunter x hunter",
        "aot" to "attack on titan",
        "sao" to "sword art online",
        "fmab" to "fullmetal alchemist brotherhood",
        "fma" to "fullmetal alchemist"
    )

    // Canonical vocabulary of legitimate anime keywords, search terms, and media titles
    private val canonicalVocabulary: Set<String> = setOf(
        // Modifiers & Structure
        "season", "episode", "movie", "part", "cour", "ova", "special", "film",
        "remake", "final", "chapter", "arc", "dub", "sub", "prologue", "uncensored",
        // Common connectors & short words
        "re", "zero", "no", "x", "on", "the", "of", "in", "to", "for", "with", "and", "vs", "a", "my", "hero", "man",
        // Canonical anime titles and keywords
        "naruto", "shippuden", "boruto", "bleach", "piece", "one", "dragon", "ball", "super",
        "attack", "titan", "shingeki", "kyojin", "demon", "slayer", "kimetsu", "yaiba",
        "jujutsu", "kaisen", "hunter", "academia", "boku", "clover", "black", "blue", "lock",
        "leveling", "solo", "chainsaw", "frieren", "doraemon", "pokemon", "shinchan",
        "ghoul", "tokyo", "death", "note", "fullmetal", "alchemist", "brotherhood",
        "vinland", "saga", "steins", "gate", "evangelion", "code", "geass", "conan",
        "detective", "mushoku", "tensei", "bocchi", "rock", "overlord", "horimiya",
        "inuyasha", "gintama", "haikyuu", "slam", "dunk", "sword", "art", "online",
        "fairy", "tail", "clannad", "toradora", "violet", "evergarden", "monster",
        "mob", "psycho", "stone", "classroom", "elite", "fate", "stay", "night",
        "berserk", "cyberpunk", "edgerunners", "spy", "family", "oshi", "ko",
        "apothecary", "diaries", "dungeon", "meshi", "delicious", "mashle", "hells",
        "paradise", "kaiju", "wind", "breaker", "dandadan", "ranma", "parasyte",
        "noragami", "akame", "kill", "charlotte", "erased", "assassination", "another",
        "durarara", "bungo", "stray", "dogs", "soul", "eater", "fire", "force",
        "seraph", "monogatari", "kaguya", "sama", "love", "war", "quintuplets",
        "komi", "nagatoro", "uzaki", "darling", "saiki", "kusuo", "danmachi",
        "slime", "tensura", "shield", "eminence", "shadow",
        "rezero", "sololeveling", "demonslayer", "onepiece", "tokyoghoul", "chainsawman",
        "blackclover", "bluelock", "jujutsukaisen", "myheroacademia"
    )

    /**
     * Calculates spatial key distance on a QWERTY keyboard (0.0 = identical, 0.35 = adjacent, 1.0 = distant)
     */
    private fun getKeyboardDistance(c1: Char, c2: Char): Double {
        if (c1 == c2) return 0.0
        val p1 = QWERTY_COORDS[c1]
        val p2 = QWERTY_COORDS[c2]
        if (p1 == null || p2 == null) return 1.0
        val dx = p1.first - p2.first
        val dy = p1.second - p2.second
        val dist = sqrt(dx * dx + dy * dy)
        return when {
            dist <= 1.1 -> 0.35 // Immediate horizontal or vertical neighbor (e.g. s-a, n-j, e-w)
            dist <= 1.6 -> 0.55 // Diagonal neighbor (e.g. n-h, s-e, w-a)
            dist <= 2.2 -> 0.80 // Nearby key on mobile
            else -> 1.0
        }
    }

    /**
     * Weighted Damerau-Levenshtein edit distance considering spatial keyboard layout and transpositions
     */
    private fun calculateWeightedDistance(s1: String, s2: String): Double {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n.toDouble()
        if (n == 0) return m.toDouble()

        val dp = Array(m + 1) { DoubleArray(n + 1) }
        for (i in 0..m) dp[i][0] = i.toDouble()
        for (j in 0..n) dp[0][j] = j.toDouble()

        for (i in 1..m) {
            val c1 = s1[i - 1]
            for (j in 1..n) {
                val c2 = s2[j - 1]
                val subCost = getKeyboardDistance(c1, c2)

                var cost = dp[i - 1][j - 1] + subCost

                // Insertion cost
                val insCost = dp[i][j - 1] + 1.0
                if (insCost < cost) cost = insCost

                // Deletion cost (lower if adjacent to previously typed char, e.g. fat-finger double tap)
                val delCost = dp[i - 1][j] + (if (i > 1 && getKeyboardDistance(s1[i - 2], c1) <= 1.6) 0.5 else 1.0)
                if (delCost < cost) cost = delCost

                // Transposition cost (swapped adjacent letters, e.g. epsiode -> episode)
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    val transCost = dp[i - 2][j - 2] + 0.6
                    if (transCost < cost) cost = transCost
                }

                dp[i][j] = cost
            }
        }
        return dp[m][n]
    }

    /**
     * Normalized similarity score between 0.0 and 1.0
     */
    private fun calculateSimilarity(word: String, candidate: String): Double {
        val dist = calculateWeightedDistance(word, candidate)
        val maxLen = max(word.length, candidate.length)
        if (maxLen == 0) return 1.0
        return (1.0 - (dist / maxLen)).coerceIn(0.0, 1.0)
    }

    /**
     * Finds the closest matching word in the canonical vocabulary using spatial keyboard distance
     */
    private fun findClosestWord(word: String, minSimilarity: Double = 0.62): String? {
        if (canonicalVocabulary.contains(word)) return word

        var bestMatch: String? = null
        var bestSimilarity = 0.0

        for (candidate in canonicalVocabulary) {
            val similarity = calculateSimilarity(word, candidate)
            if (similarity > bestSimilarity && similarity >= minSimilarity) {
                bestSimilarity = similarity
                bestMatch = candidate
            }
        }
        return bestMatch
    }

    /**
     * Automated compound word segmentation (e.g., "rezero" -> "re zero", "chainsawman" -> "chainsaw man")
     */
    private fun tryCompoundSplit(token: String): String? {
        if (token.length < 5) return null

        // 1. Try 2-word split
        for (i in 2..(token.length - 2)) {
            val left = token.substring(0, i)
            val right = token.substring(i)

            // Direct dictionary match on both halves
            if (canonicalVocabulary.contains(left) && canonicalVocabulary.contains(right)) {
                return "$left $right"
            }

            // Left exact, right fuzzy (e.g. "rezro" -> "re" + "zro" -> "re zero")
            if (canonicalVocabulary.contains(left) && right.length >= 3) {
                val fixedRight = findClosestWord(right, minSimilarity = 0.68)
                if (fixedRight != null) {
                    return "$left $fixedRight"
                }
            }

            // Left fuzzy, right exact (e.g. "sololevelng" -> "solo" + "levelng" -> "solo leveling")
            if (canonicalVocabulary.contains(right) && left.length >= 3) {
                val fixedLeft = findClosestWord(left, minSimilarity = 0.68)
                if (fixedLeft != null) {
                    return "$fixedLeft $right"
                }
            }
        }

        // 2. Try 3-word split (e.g. "oshinoko" -> "oshi no ko", "attackontitan" -> "attack on titan")
        if (token.length >= 7) {
            for (i in 2..(token.length - 4)) {
                val left = token.substring(0, i)
                if (canonicalVocabulary.contains(left)) {
                    for (j in (i + 1)..(token.length - 2)) {
                        val mid = token.substring(i, j)
                        val right = token.substring(j)
                        if (canonicalVocabulary.contains(mid) && canonicalVocabulary.contains(right)) {
                            return "$left $mid $right"
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Local algorithmic search correction:
     * - Tokenizes and normalizes punctuation/separators
     * - Expands standard acronyms
     * - Automatically segments compound words
     * - Employs spatial keyboard Damerau-Levenshtein matching against canonical anime vocabulary
     */
    fun findLocalCorrection(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null

        // Normalize punctuation separators like '-', ':', '.', '_' into spaces
        val normalizedSeparators = trimmed.replace(Regex("[-:_./+]"), " ")
        val tokens = normalizedSeparators.split(Regex("\\s+")).filter { it.isNotBlank() }

        var modified = false
        val correctedTokens = mutableListOf<String>()

        for (rawToken in tokens) {
            val lower = rawToken.lowercase()

            // 1. Acronym expansion
            if (acronymExpansions.containsKey(lower)) {
                correctedTokens.add(acronymExpansions[lower]!!)
                modified = true
                continue
            }

            // 2. Skip numeric tokens or very short single characters
            if (lower.all { it.isDigit() } || (lower.length == 1 && !canonicalVocabulary.contains(lower))) {
                correctedTokens.add(rawToken)
                continue
            }

            // 3. Exact canonical match
            if (canonicalVocabulary.contains(lower)) {
                correctedTokens.add(lower)
                continue
            }

            // 4. Automated compound word segmentation (e.g. "rezero" -> "re zero")
            val compoundSplit = tryCompoundSplit(lower)
            if (compoundSplit != null) {
                correctedTokens.add(compoundSplit)
                modified = true
                continue
            }

            // 5. Spatial keyboard fuzzy matching (e.g. "sesiojh" -> "season")
            if (lower.length >= 3) {
                val closest = findClosestWord(lower, minSimilarity = 0.62)
                if (closest != null && closest != lower) {
                    correctedTokens.add(closest)
                    modified = true
                    continue
                }
            }

            // Unchanged fallback
            correctedTokens.add(rawToken)
        }

        val result = correctedTokens.joinToString(" ")
        return if (modified && !result.equals(trimmed, ignoreCase = true)) {
            result
        } else {
            null
        }
    }

    /**
     * AI-powered suggested correction fallback using OpenRouter or Groq API (when user has configured API keys)
     */
    suspend fun getAiSuggestedCorrection(context: Context, query: String): String? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 3) return@withContext null

        try {
            val openRouterKey = AnimeBoxSettings.getOpenRouterApiKey(context).trim()
            val groqKey = AnimeBoxSettings.getGroqApiKey(context).trim()

            val prompt = """
                You are an anime search query typo-corrector.
                Fix any typos or misspelled anime titles in the following query: "$trimmed"
                Example: "reasezero seasosn 4" -> "rezero season 4"
                Example: "attck on titan" -> "attack on titan"
                Example: "narutoo shippuden" -> "naruto shippuden"
                Respond with ONLY the corrected anime search query.
                If the query cannot be understood or is already correct, respond with "NO_CORRECTION".
                Do not include explanations or extra text.
            """.trimIndent()

            if (openRouterKey.isNotBlank()) {
                val model = AnimeBoxSettings.getOpenRouterModel(context).ifBlank { "google/gemini-3.8-flash" }
                val jsonPayload = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an anime search title autocorrect utility.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.1)
                    put("max_tokens", 50)
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $openRouterKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use null
                        val json = JSONObject(body)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .optJSONObject("message")
                                ?.optString("content")
                                ?.trim()
                                ?.replace("\"", "")
                                ?: ""
                            if (content.isNotBlank() &&
                                !content.contains("NO_CORRECTION", ignoreCase = true) &&
                                !content.equals(trimmed, ignoreCase = true)
                            ) {
                                return@withContext content
                            }
                        }
                    }
                }
            } else if (groqKey.isNotBlank()) {
                val jsonPayload = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.1)
                    put("max_tokens", 50)
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $groqKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use null
                        val json = JSONObject(body)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .optJSONObject("message")
                                ?.optString("content")
                                ?.trim()
                                ?.replace("\"", "")
                                ?: ""
                            if (content.isNotBlank() &&
                                !content.contains("NO_CORRECTION", ignoreCase = true) &&
                                !content.equals(trimmed, ignoreCase = true)
                            ) {
                                return@withContext content
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore AI fallback network errors safely
        }

        null
    }
}
