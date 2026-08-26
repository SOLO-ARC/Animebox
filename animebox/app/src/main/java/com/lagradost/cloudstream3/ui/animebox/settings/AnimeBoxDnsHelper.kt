package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context
import com.lagradost.cloudstream3.network.addAdGuardDns
import com.lagradost.cloudstream3.network.addCloudFlareDns
import com.lagradost.cloudstream3.network.addGoogleDns
import com.lagradost.cloudstream3.network.addQuad9Dns
import com.lagradost.cloudstream3.network.addGenericDns
import okhttp3.OkHttpClient

object AnimeBoxDnsHelper {
    fun applyDns(builder: OkHttpClient.Builder, context: Context?): OkHttpClient.Builder {
        if (context == null) return builder
        val mode = AnimeBoxSettings.getDnsMode(context)
        return try {
            when (mode) {
                "cloudflare" -> builder.addCloudFlareDns()
                "google" -> builder.addGoogleDns()
                "adguard" -> builder.addAdGuardDns()
                "quad9" -> builder.addQuad9Dns()
                "custom" -> {
                    val url = AnimeBoxSettings.getCustomDnsUrl(context)
                    if (url.isNotEmpty()) {
                        builder.addGenericDns(url, emptyList())
                    } else builder
                }
                else -> builder
            }
        } catch (e: Exception) {
            builder
        }
    }
}
