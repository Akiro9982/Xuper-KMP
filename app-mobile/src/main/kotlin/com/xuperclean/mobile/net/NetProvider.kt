package com.xuperclean.mobile.net

import com.xuperclean.core.net.HttpEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Proveedor único de red de la app: un solo OkHttpClient con DNS resiliente (DoH),
 * compartido por el portal (core), las imágenes (Coil) y el reproductor (media3).
 * Un solo pool de conexiones = menos memoria y sockets.
 */
object NetProvider {

    val dns: DohDns by lazy { DohDns() }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Motor para el core (portal). */
    val httpEngine: HttpEngine by lazy { OkHttpEngine() }
}
