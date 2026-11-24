package com.xuperclean.mobile.net

import com.xuperclean.core.net.HttpEngine
import com.xuperclean.core.net.LegacyTls
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
        val tls = LegacyTls.config
        OkHttpClient.Builder()
            .dns(dns)
            .sslSocketFactory(tls.socketFactory, tls.trustManager)
            .connectTimeout(10, TimeUnit.SECONDS)
            // Un socket muerto se detecta antes y el reintento automático del reproductor
            // reabre la descarga en la posición actual (antes se esperaban 25 s).
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    /** Motor para el core (portal). */
    val httpEngine: HttpEngine by lazy { OkHttpEngine() }
}
