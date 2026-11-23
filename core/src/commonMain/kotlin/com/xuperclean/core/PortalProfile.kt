package com.xuperclean.core

import com.xuperclean.core.net.HttpEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Valores de conexión que el proveedor puede rotar (build aceptado, clave del portal,
 * `reserve1`, hosts). Se pueden sobreescribir sin recompilar: perfil remoto o almacén local.
 *
 * Cualquier campo nulo conserva el valor por defecto del cliente.
 */

@Serializable

public data class PortalProfile(
    val apkVer: String? = null,
    val apkVersion: String? = null,
    val portalKey: String? = null,
    /**
     * Claves candidatas (rotación de PORTAL_KEY): la primera que supere la sonda
     * `active` se adopta. Permite publicar la clave actual junto con las anteriores
     * para cubrir el caso en que el cliente todavía no recibió la última.
     */
    val portalKeys: List<String> = emptyList(),
    val reserve1: String? = null,
    /** Clave del host de registro, si también se movió. */
    val registrationKey: String? = null,
    val registrationHost: String? = null,
    val hosts: List<String> = emptyList(),
    val note: String? = null,
) {
    public fun applyTo(config: XuperConfig) {
        apkVer?.takeIf { it.isNotBlank() }?.let { config.apkVerHeader = it }
        apkVersion?.takeIf { it.isNotBlank() }?.let { config.apkVersion = it }
        (portalKey ?: portalKeys.firstOrNull())?.takeIf { it.isNotBlank() }?.let { config.portalKey = it }
        reserve1?.takeIf { it.isNotBlank() }?.let { config.reserve1 = it }
        registrationKey?.takeIf { it.isNotBlank() }?.let { config.registrationKey = it }
        registrationHost?.takeIf { it.isNotBlank() }?.let { config.registrationHost = it }
        if (hosts.isNotEmpty()) config.portalHosts = hosts
    }

    /** Claves candidatas en orden de prioridad (la principal primero), sin duplicados. */
    public fun keyCandidates(): List<String> =
        (listOfNotNull(portalKey?.takeIf { it.isNotBlank() }) + portalKeys)
            .filter { it.isNotBlank() }
            .distinct()

    /** Combina este perfil con el anterior: los campos nulos heredan del guardado. */
    public fun mergedWith(previous: PortalProfile?): PortalProfile = PortalProfile(
        apkVer = apkVer ?: previous?.apkVer,
        apkVersion = apkVersion ?: previous?.apkVersion,
        portalKey = portalKey ?: previous?.portalKey,
        portalKeys = (portalKeys + (previous?.portalKeys ?: emptyList())).distinct(),
        reserve1 = reserve1 ?: previous?.reserve1,
        registrationKey = registrationKey ?: previous?.registrationKey,
        registrationHost = registrationHost ?: previous?.registrationHost,
        hosts = hosts.ifEmpty { previous?.hosts ?: emptyList() },
        note = note ?: previous?.note,
    )

    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
        }

        /** Lee un perfil de un JSON; devuelve null si no es válido. */
        public fun parse(text: String): PortalProfile? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        /** Descarga un perfil remoto. Nunca lanza: si falla devuelve null. */
        public suspend fun fetch(http: HttpEngine, url: String): PortalProfile? {
            if (url.isBlank()) return null
            return runCatching {
                val r = http.get(url, mapOf("Cache-Control" to "no-cache"))
                if (r.status in 200..299) parse(r.bodyText) else null
            }.getOrNull()
        }

        public fun encode(profile: PortalProfile): String =
            json.encodeToString(serializer(), profile)

        public fun empty(): PortalProfile = PortalProfile()
    }
}

/** Código con el que el portal rechaza un build retirado. */
public const val VERSION_RETIRED_CODE: String = "portal200001"

/** Valores del build actual, para diagnósticos. */
public fun XuperConfig.currentBuild(): String = apkVerHeader

/** Extrae los hosts de un `getSlbInfo` si vinieran dentro del perfil (no usado aún). */
public fun PortalProfile.hostListOrNull(): List<String>? = hosts.takeIf { it.isNotEmpty() }

/** Comprueba si un JSON trae algo aplicable. */
public fun PortalProfile.updatesAnything(): Boolean =
    apkVer != null || apkVersion != null || portalKey != null || portalKeys.isNotEmpty() ||
        reserve1 != null || registrationKey != null || registrationHost != null || hosts.isNotEmpty()

/** Vista del perfil para depuración. */
public fun PortalProfile.describe(): String = buildString {
    apkVer?.let { append("build=").append(it).append(' ') }
    portalKey?.let { append("clave=").append(it.take(8)).append("… ") }
    reserve1?.let { append("reserve1=").append(it.take(6)).append("… ") }
    if (hosts.isNotEmpty()) append("hosts=").append(hosts.size)
}.trim().ifEmpty { "vacío" }

internal fun JsonObject.str(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
