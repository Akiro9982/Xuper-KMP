package com.xuperclean.tv.net

import com.xuperclean.core.net.HttpEngine
import com.xuperclean.core.net.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Motor HTTP para el core basado en OkHttp (con DNS resiliente DoH).
 * Reutiliza el cliente compartido de NetProvider (mismo pool de conexiones).
 */
class OkHttpEngine(
    private val client: OkHttpClient = NetProvider.okHttpClient,
) : HttpEngine {

    override suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResult =
        request("POST", url, headers, body.toRequestBody("application/json;charset=utf-8".toMediaTypeOrNull()))

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        request("GET", url, headers, null)

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: okhttp3.RequestBody?,
    ): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) builder.post(body) else builder.get()
        android.util.Log.d(
            "XuperNet",
            "$method $url headers=${headers.entries.joinToString { "${it.key}=${it.value.take(30)}" }}",
        )
        client.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            android.util.Log.d("XuperNet", "resp ${resp.code}: ${bytes.decodeToString().take(240)}")
            HttpResult(
                status = resp.code,
                headers = resp.headers.toMap(),
                body = bytes,
            )
        }
    }
}
