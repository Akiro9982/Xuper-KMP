package com.xuperclean.desktop.net

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS resiliente para gama baja: primero el del sistema; si falla (routers baratos,
 * DNS de operador caído), resuelve por DNS-over-HTTPS contra 1.1.1.1 (IP directa,
 * no necesita DNS) y cachea el resultado.
 *
 * Nació de un diagnóstico real: el DNS virtual del emulador resolvía el portal pero no
 * el CDN de pósters de forma intermitente.
 */
class DohDns(
    private val dohUrl: String = "https://1.1.1.1/dns-query?name=%s&type=A",
) : Dns {

    private val cache = HashMap<String, List<InetAddress>>()
    private val lock = Any()

    override fun lookup(hostname: String): List<InetAddress> {
        // 1) caché exitosa previa
        synchronized(lock) { cache[hostname] }?.let { return it }

        // 2) DNS del sistema (validando: MuMu llegó a devolver "[::]" basura)
        try {
            val addrs = InetAddress.getAllByName(hostname).filter { isValid(it) }
            if (addrs.isNotEmpty()) {
                synchronized(lock) { cache[hostname] = addrs }
                return addrs
            }
        } catch (_: UnknownHostException) {
            // continuar con DoH
        }

        // 3) DoH por IP directa (no requiere DNS): 1.1.1.1 y 8.8.8.8
        for (resolver in listOf("1.1.1.1", "8.8.8.8")) {
            try {
                val ips = dohLookup(hostname, resolver).filter { isValid(it) }
                if (ips.isNotEmpty()) {
                    synchronized(lock) { cache[hostname] = ips }
                    return ips
                }
            } catch (_: Exception) {
                // siguiente resolver
            }
        }
        throw UnknownHostException("No se pudo resolver $hostname (sistema ni DoH)")
    }

    /** Descarta direcciones inútiles: any-local (:: / 0.0.0.0), loopback y vacías. */
    private fun isValid(addr: InetAddress): Boolean {
        val s = addr.hostAddress ?: return false
        if (s.isEmpty() || s == "::" || s == "0.0.0.0") return false
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress) return false
        return true
    }

    private fun dohLookup(host: String, resolver: String): List<InetAddress> {
        // cliente mínimo SIN DNS (la URL es una IP literal)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(5_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        val url = dohUrl.replace("%s", java.net.URLEncoder.encode(host, "UTF-8"))
            .replace("https://1.1.1.1", "https://$resolver")
            .replace("https://8.8.8.8", "https://$resolver")
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("accept", "application/dns-json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            // parseo JSON mínimo sin dependencias: "data":"1.2.3.4"
            val regex = Regex(""""data"\s*:\s*"([0-9a-fA-F.:]+)"""")
            return regex.findAll(body)
                .map { it.groupValues[1] }
                .mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                .toList()
        }
    }
}
