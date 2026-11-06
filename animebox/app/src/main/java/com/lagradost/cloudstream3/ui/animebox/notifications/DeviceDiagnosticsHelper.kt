package com.lagradost.cloudstream3.ui.animebox.notifications

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

object DeviceDiagnosticsHelper {

    data class VideoPlaybackDiagnostics(
        val serverName: String = "",
        val streamUrl: String = "",
        val resolution: String = "",
        val audioTrack: String = "",
        val subtitleTrack: String = "",
        val playbackPositionMs: Long = 0L,
        val errorMessage: String = "",
        val errorStackTrace: String = ""
    )

    private var currentVideoDiagnostics: VideoPlaybackDiagnostics? = null

    fun setVideoContext(
        server: String? = null,
        streamUrl: String? = null,
        resolution: String? = null,
        subMode: String? = null,
        audioTrack: String? = null,
        playbackPositionMs: Long? = null,
        errorMessage: String? = null,
        errorStackTrace: String? = null
    ) {
        val prev = currentVideoDiagnostics
        currentVideoDiagnostics = VideoPlaybackDiagnostics(
            serverName = server?.ifBlank { null } ?: prev?.serverName ?: "",
            streamUrl = streamUrl?.ifBlank { null } ?: prev?.streamUrl ?: "",
            resolution = resolution?.ifBlank { null } ?: prev?.resolution ?: "",
            audioTrack = audioTrack?.ifBlank { null } ?: prev?.audioTrack ?: "",
            subtitleTrack = subMode?.ifBlank { null } ?: prev?.subtitleTrack ?: "",
            playbackPositionMs = if ((playbackPositionMs ?: 0L) > 0L) playbackPositionMs!! else (prev?.playbackPositionMs ?: 0L),
            errorMessage = errorMessage?.ifBlank { null } ?: prev?.errorMessage ?: "",
            errorStackTrace = errorStackTrace?.ifBlank { null } ?: prev?.errorStackTrace ?: ""
        )
    }

    fun getCurrentVideoDiagnostics(): VideoPlaybackDiagnostics? = currentVideoDiagnostics

    /**
     * Collects privacy-safe system, hardware, network, and video playback diagnostics.
     * Deliberately excludes sensitive user location and IP identifiers:
     * public_ip, ip, postal_code, city, region, resolved_city, resolved_region,
     * isp_gateway_city, isp_gateway_region, location_source, and GPS coordinates.
     */
    suspend fun collectDiagnostics(
        context: Context,
        videoDiag: VideoPlaybackDiagnostics? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val root = JSONObject()

        try {
            // 1. Android & Device Info
            val deviceObj = JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER ?: "Unknown")
                put("brand", Build.BRAND ?: "Unknown")
                put("model", Build.MODEL ?: "Unknown")
                put("device", Build.DEVICE ?: "Unknown")
                put("product", Build.PRODUCT ?: "Unknown")
                put("hardware", Build.HARDWARE ?: "Unknown")
                put("android_version", Build.VERSION.RELEASE ?: "Unknown")
                put("sdk_int", Build.VERSION.SDK_INT)
            }
            root.put("device", deviceObj)

            // 2. Hardware: Processor & Memory & Storage
            val hardwareObj = JSONObject().apply {
                val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
                put("cpu_abi", primaryAbi)
                put("cpu_abis", Build.SUPPORTED_ABIS.joinToString(", "))
                put("cpu_cores", Runtime.getRuntime().availableProcessors())

                // Storage
                try {
                    val stat = StatFs(Environment.getDataDirectory().path)
                    val availableBytes = stat.availableBytes
                    val totalBytes = stat.totalBytes
                    val freeMb = availableBytes / (1024 * 1024)
                    val totalMb = totalBytes / (1024 * 1024)
                    put("free_storage_mb", freeMb)
                    put("total_storage_mb", totalMb)
                    put("storage_free", String.format(Locale.US, "%.2f GB", availableBytes.toDouble() / (1024 * 1024 * 1024)))
                    put("storage_total", String.format(Locale.US, "%.2f GB", totalBytes.toDouble() / (1024 * 1024 * 1024)))
                } catch (_: Throwable) {
                    put("storage_free", "N/A")
                    put("storage_total", "N/A")
                }

                // RAM
                try {
                    val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    actMgr?.getMemoryInfo(memInfo)
                    val availMb = memInfo.availMem / (1024 * 1024)
                    val totalMb = memInfo.totalMem / (1024 * 1024)
                    put("avail_ram_mb", availMb)
                    put("total_ram_mb", totalMb)
                    put("ram_available", String.format(Locale.US, "%.2f GB", memInfo.availMem.toDouble() / (1024 * 1024 * 1024)))
                    put("ram_total", String.format(Locale.US, "%.2f GB", memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)))
                    put("low_memory", memInfo.lowMemory)
                } catch (_: Throwable) {
                    put("ram_available", "N/A")
                    put("ram_total", "N/A")
                }
            }
            root.put("hardware", hardwareObj)

            // 3. Privacy-Safe Locale & Carrier Context (Country/Timezone only — no IP, city, region, or postal code)
            val deviceLocale = Locale.getDefault()
            val deviceCountry = deviceLocale.displayCountry.ifBlank { deviceLocale.country }.ifBlank { "Unknown" }
            val deviceCountryCode = deviceLocale.country.orEmpty().uppercase()
            val deviceLang = deviceLocale.displayLanguage.ifBlank { "Unknown" }
            val deviceTimezone = TimeZone.getDefault().id ?: "Unknown"

            var simCountry = ""
            var simOperator = ""
            var networkOperator = ""
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (tm != null) {
                    simCountry = tm.simCountryIso.orEmpty().uppercase()
                    simOperator = tm.simOperatorName.orEmpty()
                    networkOperator = tm.networkOperatorName.orEmpty()
                }
            } catch (_: Throwable) {}

            // 4. Network & Connection Speed (Strictly without IP, city, region, or postal_code)
            val networkObj = JSONObject()
            var downlinkKbps = 0
            try {
                val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connMgr?.activeNetwork
                val caps = connMgr?.getNetworkCapabilities(activeNetwork)
                val netType = when {
                    caps == null -> "Disconnected"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Mobile Data)"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Other"
                }
                networkObj.put("connection_type", netType)

                downlinkKbps = caps?.linkDownstreamBandwidthKbps ?: 0
                networkObj.put("downlink_kbps", downlinkKbps)
                if (downlinkKbps > 0) {
                    val downMbps = String.format(Locale.US, "%.2f Mbps", downlinkKbps.toDouble() / 1000)
                    networkObj.put("downstream_speed", downMbps)
                } else {
                    networkObj.put("downstream_speed", "Unknown")
                }
            } catch (_: Throwable) {
                networkObj.put("connection_type", "Unknown")
                networkObj.put("downlink_kbps", 0)
                networkObj.put("downstream_speed", "Unknown")
            }

            networkObj.put("country", deviceCountry)
            networkObj.put("country_code", deviceCountryCode)
            networkObj.put("timezone", deviceTimezone)

            val localeSummaryObj = JSONObject().apply {
                put("resolved_country", deviceCountry)
                put("device_timezone", deviceTimezone)
                put("device_locale_country", deviceCountry)
                put("device_language", deviceLang)
                if (simCountry.isNotBlank()) put("sim_country", simCountry)
                if (simOperator.isNotBlank()) put("sim_operator", simOperator)
                if (networkOperator.isNotBlank()) put("network_operator", networkOperator)
            }
            root.put("network", networkObj)
            root.put("location", localeSummaryObj)

            // 5. Video Playback & Error Context
            val current = currentVideoDiagnostics
            val effServer = videoDiag?.serverName?.ifBlank { null } ?: current?.serverName ?: ""
            val effStream = videoDiag?.streamUrl?.ifBlank { null } ?: current?.streamUrl ?: ""
            val effRes = videoDiag?.resolution?.ifBlank { null } ?: current?.resolution ?: ""
            val effAudio = videoDiag?.audioTrack?.ifBlank { null } ?: current?.audioTrack ?: ""
            val effSub = videoDiag?.subtitleTrack?.ifBlank { null } ?: current?.subtitleTrack ?: ""
            val effPos = if ((videoDiag?.playbackPositionMs ?: 0L) > 0L) videoDiag!!.playbackPositionMs else (current?.playbackPositionMs ?: 0L)
            val effErr = videoDiag?.errorMessage?.ifBlank { null } ?: current?.errorMessage ?: ""
            val effTrace = videoDiag?.errorStackTrace?.ifBlank { null } ?: current?.errorStackTrace ?: ""

            if (effServer.isNotBlank() || effStream.isNotBlank() || effPos > 0L || effErr.isNotBlank() || effRes.isNotBlank() || effSub.isNotBlank() || effAudio.isNotBlank()) {
                val videoObj = JSONObject().apply {
                    put("server", effServer.ifBlank { "Auto / Default Server" })
                    put("stream_url", effStream)
                    put("resolution", effRes.ifBlank { "Auto" })
                    put("audio_track", effAudio.ifBlank { "Default Audio" })
                    put("sub_mode", effSub.ifBlank { "Default Subtitle" })
                    put("subtitle_track", effSub.ifBlank { "Default Subtitle" })
                    put("position_ms", effPos)
                    put("playback_position_ms", effPos)
                    if (effErr.isNotBlank()) {
                        put("error_message", effErr)
                    }
                    if (effTrace.isNotBlank()) {
                        put("error_trace", effTrace)
                        put("error_stack_trace", effTrace)
                    }
                }
                root.put("video", videoObj)
                root.put("video_playback", videoObj)
            }

            root.put("timestamp", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date()))
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        root
    }
}
