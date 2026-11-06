package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.network.*
import okhttp3.OkHttpClient

object AnimeBoxDnsHelper {

    fun applyDns(builder: OkHttpClient.Builder, context: Context? = null): OkHttpClient.Builder {
        val ctx = context ?: CloudStreamApp.context
        val mode = if (ctx != null) AnimeBoxSettings.getDnsMode(ctx) else "cloudflare"
        return try {
            when (mode) {
                "cloudflare" -> builder.addCloudFlareDns()
                "google" -> builder.addGoogleDns()
                "adguard" -> builder.addAdGuardDns()
                "quad9" -> builder.addQuad9Dns()
                "controld" -> builder.addControlDDns()
                "dnssb" -> builder.addDnsSbDns()
                "custom" -> {
                    val url = if (ctx != null) AnimeBoxSettings.getCustomDnsUrl(ctx).trim() else ""
                    if (url.startsWith("https://", ignoreCase = true)) {
                        builder.addGenericDns(url, emptyList())
                    } else builder.addCloudFlareDns()
                }
                "default", "system" -> builder
                else -> builder.addCloudFlareDns()
            }
        } catch (_: Throwable) {
            builder
        }
    }

    /**
     * Instantly refreshes all app network clients (app.baseClient, AniList, AniZip, Kitsu, scrapers)
     * so that DNS routing changes take effect immediately across video streaming and API queries.
     */
    fun refreshAllNetworkClients(context: Context) {
        try {
            com.lagradost.cloudstream3.app.initClient(context)
        } catch (_: Throwable) {}
        try {
            com.lagradost.cloudstream3.ui.animebox.api.AniListClient.refreshClient()
        } catch (_: Throwable) {}
        try {
            com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.refreshClient()
        } catch (_: Throwable) {}
        try {
            com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.refreshClient()
        } catch (_: Throwable) {}
    }
}
