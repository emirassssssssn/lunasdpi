package com.lunasdev.lunasdpi.vpn

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * RFC 8484 DNS-over-HTTPS. Runs in the app process, which is excluded from the TUN,
 * so queries do not loop and are not sent as hijackable UDP/53.
 */
object DnsOverHttps {
    private val endpoints = listOf(
        "https://dns.google/dns-query",
        "https://cloudflare-dns.com/dns-query",
    )

    fun resolve(query: ByteArray): ByteArray? {
        if (query.size < 12 || query.size > 4096) {
            return null
        }
        for (endpoint in endpoints) {
            post(endpoint, query)?.let { return it }
        }
        return get(endpoints.first(), query)
    }

    private fun post(endpoint: String, query: ByteArray): ByteArray? {
        return runCatching {
            val conn = open(endpoint)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.outputStream.use { stream -> stream.write(query) }
            readBody(conn)
        }.getOrNull()
    }

    private fun get(endpoint: String, query: ByteArray): ByteArray? {
        val encoded = Base64.encodeToString(
            query,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val url = "$endpoint?dns=$encoded"
        return runCatching {
            val conn = open(url)
            conn.requestMethod = "GET"
            readBody(conn)
        }.getOrNull()
    }

    private fun open(spec: String): HttpURLConnection {
        val conn = URL(spec).openConnection() as HttpURLConnection
        conn.connectTimeout = 1800
        conn.readTimeout = 1800
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/dns-message")
        conn.setRequestProperty("User-Agent", "LunaDPI/1")
        return conn
    }

    private fun readBody(conn: HttpURLConnection): ByteArray? {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        if (stream == null || code !in 200..299) {
            conn.disconnect()
            return null
        }
        val bytes = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) {
                    break
                }
                total += n
                if (total > 4096) {
                    return@use null
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        conn.disconnect()
        if (bytes == null || bytes.size < 12) {
            return null
        }
        return bytes
    }
}
