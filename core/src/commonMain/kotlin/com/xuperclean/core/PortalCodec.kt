package com.xuperclean.core

import com.xuperclean.core.crypto.XuperCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Errores del portal. */
public sealed class XuperException(public val returnCode: String, message: String) :
    Exception(message) {

    /** Sesión sustituida / token caducado en el portal: refrescar y reintentar. */
    public class Kick(returnCode: String, message: String) : XuperException(returnCode, message)

    /** Otro error del portal (parámetros, columna inexistente...). */
    public class Portal(returnCode: String, message: String) : XuperException(returnCode, message)

    /** Respuesta inesperada del portal (falta un campo obligatorio). */
    public class Protocol(message: String) : XuperException("protocolo", message)

    /** El portal retiró el build del cliente y el sondeo no encontró uno aceptado. */
    public class VersionRetired(returnCode: String) :
        XuperException(returnCode, "El portal retiró esta versión del cliente; hace falta una app nueva")

    public companion object {
        public fun of(returnCode: String, errorMessage: String?): XuperException =
            if (returnCode in KICK_CODES) Kick(returnCode, errorMessage ?: "sesión expulsada")
            else Portal(returnCode, errorMessage ?: "error $returnCode")

        /** Códigos observados que implican sesión/token expirado o sustituido. */
        public val KICK_CODES: Set<String> = setOf(
            "aaa100080", // snToken caducado
            "portal200004",
            "portal200003",
            "401", "403", "409",
        )
    }
}

/**
 * Codec del portal: arma el body cifrado con los campos comunes del interceptor
 * (iptv-api.md §2.4) y descifra respuestas.
 */
public class PortalCodec(private val config: XuperConfig) {

    public val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Campos comunes que el interceptor añade a TODO body. */
    public fun commonFields(portalCode: String): JsonObject = buildJsonObject {
        put("loginType", "2")
        put("appLanguage", config.language)
        put("apkVersion", config.apkVersion)
        put("sysVersion", config.sysVersion)
        put("appId", config.appId)
        put("hardwareInfo", config.hardwareInfo)
        put("model", config.model)
        put("product", config.product)
        put("cpu", config.cpu)
        put("B29", "")
        put("portalCode", portalCode)
        put("deviceToken", "")
        put("sn", config.sn)
        put("drmId", "")
        put("sdkVer", config.sdkVer)
    }

    /** Cifra un bean (ya serializado) + campos comunes → body hex listo para POST. */
    public fun encryptBody(beanJson: JsonObject, portalCode: String): String {
        val merged = JsonObject(beanJson + commonFields(portalCode))
        return XuperCrypto.encryptRequest(merged.toString(), config.portalKey)
    }

    public fun encryptBody(beanJson: String, portalCode: String): String =
        XuperCrypto.encryptRequest(beanJson, config.portalKey)

    /**
     * Descifra la respuesta HTTP: el `data` puede venir como string hex cifrado o como
     * JSON plano (device/updateOrInsert). Devuelve el objeto JSON de nivel superior con
     * `data` ya descifrado.
     */
    public fun decodeResponse(bodyText: String): JsonObject {
        val root = json.parseToJsonElement(bodyText).jsonObjectSafe()
        val returnCode = root["returnCode"]?.toString()?.trim('"') ?: "0"
        val errorMessage = root["errorMessage"]?.toString()?.trim('"')
        if (returnCode != "0" && returnCode.isNotEmpty()) {
            throw XuperException.of(returnCode, errorMessage)
        }
        val data = root["data"]
        val rawData = data?.toString()?.trim('"') ?: return root
        if (rawData.length > 20 && rawData.matches(Regex("[0-9a-fA-F]+"))) {
            try {
                val decrypted = XuperCrypto.decryptResponse(rawData, config.portalKey)
                val newData = json.parseToJsonElement(decrypted)
                return JsonObject(root.toMutableMap().apply { put("data", newData) })
            } catch (_: Exception) {
                // si no se pudo descifrar, devolver el root tal cual
            }
        }
        return root
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectSafe(): JsonObject =
        this as? JsonObject ?: JsonObject(emptyMap())
}
