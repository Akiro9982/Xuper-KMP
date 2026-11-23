package com.xuperclean.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Motor HTTP para JVM (Android + Desktop) con HttpURLConnection: cero dependencias,
 * ideal para dispositivos de gama baja.
 */
public class JvmHttpEngine(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 25_000,
) : HttpEngine {

    override suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResult =
        request(url, "POST", headers, body)

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        request(url, "GET", headers, null)

    private suspend fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val respHeaders = conn.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key as String }
                .mapValues { it.value.joinToString(", ") }
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val respBody = stream?.use { it.readBytes() } ?: ByteArray(0)
            HttpResult(status, respHeaders, respBody)
        } finally {
            conn.disconnect()
        }
    }
}
