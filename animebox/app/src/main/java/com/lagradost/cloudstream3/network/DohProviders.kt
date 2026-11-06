package com.lagradost.cloudstream3.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Real, high-availability DNS over HTTPS (DoH) providers with verified RFC 8484 endpoints
 * and fast Anycast IP bootstrapping.
 */

fun OkHttpClient.Builder.addGenericDns(url: String, ips: List<String> = emptyList()) = dns(
    DnsOverHttps
        .Builder()
        .client(build())
        .url(url.toHttpUrl())
        .apply {
            if (ips.isNotEmpty()) {
                val resolved = ips.mapNotNull { ip ->
                    runCatching { InetAddress.getByName(ip) }.getOrNull()
                }
                if (resolved.isNotEmpty()) {
                    bootstrapDnsHosts(resolved)
                }
            }
        }
        .build()
)

fun OkHttpClient.Builder.addGoogleDns() = (
    addGenericDns(
        "https://dns.google/dns-query",
        listOf(
            "8.8.8.8",
            "8.8.4.4",
            "2001:4860:4860::8888",
            "2001:4860:4860::8844"
        )
    ))

fun OkHttpClient.Builder.addCloudFlareDns() = (
    addGenericDns(
        "https://cloudflare-dns.com/dns-query",
        listOf(
            "1.1.1.1",
            "1.0.0.1",
            "2606:4700:4700::1111",
            "2606:4700:4700::1001"
        )
    ))

fun OkHttpClient.Builder.addAdGuardDns() = (
    addGenericDns(
        "https://dns.adguard-dns.com/dns-query",
        listOf(
            "94.140.14.14",
            "94.140.15.15",
            "2a10:50c0::ad1:ff",
            "2a10:50c0::ad2:ff"
        )
    ))

fun OkHttpClient.Builder.addQuad9Dns() = (
    addGenericDns(
        "https://dns.quad9.net/dns-query",
        listOf(
            "9.9.9.9",
            "149.112.112.112",
            "2620:fe::fe",
            "2620:fe::9"
        )
    ))

fun OkHttpClient.Builder.addControlDDns() = (
    addGenericDns(
        "https://freedns.controld.com/p0",
        listOf(
            "76.76.2.0",
            "76.76.10.0",
            "2606:1a40::",
            "2606:1a40:1::"
        )
    ))

fun OkHttpClient.Builder.addDnsSbDns() = (
    addGenericDns(
        "https://doh.dns.sb/dns-query",
        listOf(
            "185.222.222.222",
            "45.11.45.11",
            "2a09::",
            "2a11::"
        )
    ))
