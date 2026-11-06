package com.lagradost.cloudstream3.ui.animebox.extractors

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 1:1 port of Anilili's AppDns (F4.C0361i4 / F4.C0389k4 / F4.W4 / D7.o.W)
 * and JS Unpacker (F4.E / A4.b case 19 / C4.C0138g case 1 / D.E).
 */
object AnililiHttpClient {

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val cloudflareDoh: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .includeIPv6(false)
            .build()
    }

    private val googleDoh: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("8.8.4.4")
            )
            .includeIPv6(false)
            .build()
    }

    private val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private const val DNS_CACHE_TTL_MS = 10 * 60 * 1000L

    /**
     * Exact port of D7.o.W(List<InetAddress>) from Anilili-project:
     * Detects ISP sinkhole / bogon / loopback / CGNAT responses.
     */
    private fun isSinkholed(addresses: List<InetAddress>): Boolean {
        if (addresses.isEmpty()) return false
        for (addr in addresses) {
            if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
                return true
            }
            val raw = addr.address
            if (addr is Inet4Address && raw.size == 4) {
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                if (b0 == 0 || (b0 == 100 && (b1 and 192) == 64)) {
                    return true
                }
            } else if (addr is Inet6Address && raw.isNotEmpty()) {
                val b0 = raw[0].toInt() and 0xFF
                if ((b0 and 254) == 252) {
                    return true
                }
            }
        }
        return false
    }

    private fun lookupDoh(hostname: String): List<InetAddress>? {
        for (resolver in listOf(cloudflareDoh, googleDoh)) {
            try {
                val res = resolver.lookup(hostname)
                if (res.isNotEmpty() && !isSinkholed(res)) {
                    return res.sortedBy { if (it is Inet4Address) 0 else 1 }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Automatic DNS with DoH fallback (Cloudflare 1.1.1.1 -> Google 8.8.8.8)
     * whenever system DNS fails or returns a sinkholed IP for anime domains.
     */
    val appDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lower = hostname.lowercase()
            if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1") {
                return Dns.SYSTEM.lookup(hostname)
            }

            val now = System.currentTimeMillis()
            dnsCache[lower]?.let { (ts, cached) ->
                if (now - ts < DNS_CACHE_TTL_MS && cached.isNotEmpty()) {
                    return cached
                }
            }

            // For domains frequently blocked/sinkholed by ISPs, try DoH first then system DNS
            val preferDohFirst = lower.endsWith("animepahe.pw") ||
                lower.endsWith("animepahe.ru") ||
                lower.endsWith("animepahe.com") ||
                lower.contains("kwik.") ||
                lower.endsWith("ani.pm") ||
                lower.contains("settlar.") ||
                lower.contains("rareanimes.") ||
                lower.contains("codedew.") ||
                lower.contains("miruro.") ||
                lower.contains("razorshell.") ||
                lower.contains("groovy.monster") ||
                lower.contains("2dhive.") ||
                lower.contains("babastream.") ||
                lower.contains("megaplay.") ||
                lower.contains("kaa.lt") ||
                lower.contains("krussdomi.")

            if (preferDohFirst) {
                lookupDoh(hostname)?.let { resolved ->
                    dnsCache[lower] = now to resolved
                    return resolved
                }
            }

            var sysError: UnknownHostException? = null
            val sysResult = try {
                Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
            } catch (e: UnknownHostException) {
                sysError = e
                null
            } catch (e: Exception) {
                null
            }

            if (sysResult != null && sysResult.isNotEmpty() && !isSinkholed(sysResult)) {
                dnsCache[lower] = now to sysResult
                return sysResult
            }

            lookupDoh(hostname)?.let { resolved ->
                dnsCache[lower] = now to resolved
                return resolved
            }

            if (sysResult != null && sysResult.isNotEmpty()) {
                return sysResult
            }
            throw sysError ?: UnknownHostException(hostname)
        }
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

    val sharedCookieJar: okhttp3.CookieJar = object : okhttp3.CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val host = url.host.lowercase()
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(list) {
                for (cookie in cookies) {
                    list.removeAll { it.name == cookie.name }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            val host = url.host.lowercase()
            val result = mutableListOf<okhttp3.Cookie>()
            for ((storedHost, cookies) in cookieStore) {
                if (host == storedHost || host.endsWith(".$storedHost") || storedHost.endsWith(".$host")) {
                    synchronized(cookies) {
                        result.addAll(cookies)
                    }
                }
            }
            return result
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(appDns)
            .cookieJar(sharedCookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // 1:1 port of F4.E + A4.b (case 19) + C4.C0138g (case 1) + D.E JS Unpacker
    // -------------------------------------------------------------------------
    private val PACKED_HEADER_REGEX = Regex(
        """eval\(function\(p,a,c,k,e,[rd]?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val PACKED_ARGS_REGEX = Regex(
        """\}\s*\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val WORD_TOKEN_REGEX = Regex("""\b\w+\b""")

    fun isPackedJs(script: String): Boolean = PACKED_HEADER_REGEX.containsMatchIn(script)

    fun unpackJs(script: String): String? {
        val matches = PACKED_ARGS_REGEX.findAll(script).mapNotNull { match ->
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val count = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val symtab = match.groupValues[4].split('|')
            if (symtab.size != count && symtab.isEmpty()) return@mapNotNull null

            val alphabet = when {
                radix > 62 -> " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
                radix > 54 -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                radix > 52 -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQR"
                else -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP"
            }
            val charMap = HashMap<Char, Int>(alphabet.length)
            for (i in alphabet.indices) {
                charMap[alphabet[i]] = i
            }

            val replaced = WORD_TOKEN_REGEX.replace(payload) { tokenMatch ->
                val token = tokenMatch.value
                val index = if (radix in 2..36) {
                    token.toIntOrNull(radix) ?: 0
                } else {
                    val rev = token.reversed()
                    var acc = 0
                    for (pos in rev.indices) {
                        val digit = charMap[rev[pos]] ?: 0
                        acc += (Math.pow(radix.toDouble(), pos.toDouble()) * digit.toDouble()).toInt()
                    }
                    acc
                }
                val word = symtab.getOrNull(index)
                if (!word.isNullOrEmpty()) word else token
            }

            // Unescape sequence from A4.b case 19
            val sb = StringBuilder(replaced.length)
            var i = 0
            while (i < replaced.length) {
                val c = replaced[i]
                if (c != '\\' || i == replaced.lastIndex) {
                    sb.append(c)
                    i++
                } else {
                    when (val next = replaced[i + 1]) {
                        '"', '\'', '\\' -> sb.append(next)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> {
                            sb.append(c)
                            sb.append(next)
                        }
                    }
                    i += 2
                }
            }
            sb.toString()
        }.joinToString(" ")

        return matches.takeIf { it.isNotBlank() }
    }
}
