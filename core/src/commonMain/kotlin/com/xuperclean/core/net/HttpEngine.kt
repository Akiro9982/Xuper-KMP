package com.xuperclean.core.net

/**
 * Motor HTTP mínimo multiplataforma. JVM (Android + Desktop) usa HttpURLConnection;
 * otras plataformas pueden aportar su implementación sin tocar el resto del core.
 */
public interface HttpEngine {
    public suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResult

    public suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult
}

public class HttpResult(
    public val status: Int,
    public val headers: Map<String, String>,
    public val body: ByteArray,
) {
    public val bodyText: String get() = body.decodeToString()

    public fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

public class HttpException(
    public val status: Int,
    message: String,
) : Exception("HTTP $status: $message")
