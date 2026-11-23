package com.xuperclean.core

import com.xuperclean.core.crypto.XuperCrypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registro y sesión por terminal — sistema replicado del cliente NetXus.
 *
 * Diferencias con el flujo "clásico" (XuperPortalClient):
 *  - Registro: `v3/snToken` en el host de registro con su propia app/clave; el SN se
 *    deriva del token (`md5(snToken + SALT)`) y se registra con `active` + `snToken`.
 *  - Sesión: `active` en el host del portal con `openNum` incremental; el servidor
 *    devuelve un `userId`/`userToken` NUEVO en cada llamada.
 *  - Cada instalación tiene su SN, así que varias no se desalojan.
 *
 * Todas las peticiones van con la clave fijada del build aceptado (43404), sin depender
 * de descargar APKs ni de barridos de versión.
 */
public class NexusPortal(
    private val config: XuperConfig,
    private val store: IdentityStore,
    private val post: suspend (host: String, path: String, body: String, key: String,
                              apk: String, apkVer: String, spkgVer: String) -> String,
) {
    private val mutex = Mutex()
    private var identity: PortalIdentity? = null

    public suspend fun identity(): PortalIdentity = mutex.withLock {
        identity ?: (store.load() ?: IdentityFactory.fresh()).also {
            identity = it
            store.save(it)
        }
    }

    /** Registra el terminal si hace falta. Devuelve la identidad con `sn`/`snToken`. */
    public suspend fun ensureRegistered(): PortalIdentity = mutex.withLock {
        val actual = identity ?: (store.load() ?: IdentityFactory.fresh()).also { identity = it }
        if (actual.registered) return actual

        val sello = sello(actual)
        val respuestaToken = post(
            config.registrationHost, "/api/portalCore/v3/snToken",
            XuperCrypto.encryptRequest(sello, config.registrationKey),
            config.registrationKey, config.registrationAppId,
            config.registrationApkVersion, config.registrationSpkgVer,
        )
        val snToken = extractJson(respuestaToken, "snToken")
            ?: throw XuperException.Protocol("respuesta sin snToken")
        val sn = IdentityFactory.snFromToken(snToken)

        val registro = buildString {
            append("{\"channel\":\"default\"")
            append(",\"macAddr\":\"").append(actual.macAddr).append('"')
            append(",\"snToken\":\"").append(snToken).append('"')
            append(",\"loginType\":\"3\",\"appLanguage\":\"").append(config.language).append('"')
            append(",\"apkVersion\":\"").append(config.registrationApkVersion).append('"')
            append(",\"sysVersion\":\"").append(config.registrationSysVersion).append('"')
            append(",\"appId\":\"").append(config.registrationAppId).append('"')
            append(",\"hardwareInfo\":\"").append(config.hardwareInfo).append('"')
            append(",\"model\":\"").append(config.model).append('"')
            append(",\"product\":\"").append(config.product).append('"')
            append(",\"cpu\":\"").append(config.cpu).append('"')
            append(",\"deviceToken\":\"\",\"reserve1\":\"").append(config.reserve1).append('"')
            append(",\"sn\":\"").append(sn).append('"')
            append(",\"drmId\":\"\"}")
        }
        val respuesta = post(
            config.registrationHost, "/api/portalCore/v8/active",
            XuperCrypto.encryptRequest(registro, config.registrationKey),
            config.registrationKey, config.registrationAppId,
            config.registrationApkVersion, config.registrationSpkgVer,
        )
        val userId = extractJson(respuesta, "userId").orEmpty()
        val userToken = extractJson(respuesta, "userToken").orEmpty()

        val registrada = actual.copy(
            sn = sn, snToken = snToken, userId = userId, userToken = userToken,
            openNum = config.openNumInicial,
        )
        identity = registrada
        store.save(registrada)
        return registrada
    }

    /** Renueva la sesión: `openNum` debe crecer para obtener credenciales nuevas. */
    public suspend fun renewSession(): PortalIdentity = mutex.withLock {
        val actual = ensureRegistered()
        val siguiente = actual.copy(openNum = actual.openNum + 1)

        val cuerpo = buildString {
            append("{\"authCode\":\"\",\"authVersion\":\"\",\"channel\":\"default\"")
            append(",\"macAddr\":\"").append(actual.macAddr).append('"')
            append(",\"matadata\":\"\",\"openNum\":").append(siguiente.openNum)
            append(",\"reserve1\":\"").append(config.reserve1).append('"')
            append(",\"signdata\":\"\",\"snToken\":\"\"")
            append(",\"loginType\":\"2\",\"appLanguage\":\"").append(config.language).append('"')
            append(",\"apkVersion\":\"").append(config.apkVersion).append('"')
            append(",\"sysVersion\":\"").append(config.sysVersion).append('"')
            append(",\"appId\":\"").append(config.appId).append('"')
            append(",\"hardwareInfo\":\"").append(config.hardwareInfo).append('"')
            append(",\"model\":\"").append(config.model).append('"')
            append(",\"product\":\"").append(config.product).append('"')
            append(",\"cpu\":\"").append(config.cpu).append('"')
            append(",\"B29\":\"\",\"deviceToken\":\"\"")
            append(",\"sn\":\"").append(actual.sn).append('"')
            append(",\"drmId\":\"\",\"sdkVer\":").append(config.sdkVer)
            append(",\"portalCode\":\"\"}")
        }
        val respuesta = post(
            config.portalHosts.first(), "/api/portalCore/v8/active",
            XuperCrypto.encryptRequest(cuerpo, config.portalKey),
            config.portalKey, config.appId, config.apkVerHeader, config.spkgVer,
        )
        val userId = extractJson(respuesta, "userId").orEmpty()
        val userToken = extractJson(respuesta, "userToken").orEmpty()
        if (userId.isEmpty() || userToken.isEmpty()) throw XuperException.Protocol("respuesta sin sesión")

        val renovada = siguiente.copy(userId = userId, userToken = userToken)
        identity = renovada
        store.save(renovada)
        return renovada
    }

    private fun sello(id: PortalIdentity): String = buildString {
        append("{\"androidId\":\"").append(id.androidId).append('"')
        append(",\"board\":\"SM8850\u2011AC\",\"brand\":\"Nubia\",\"cpuAbi\":\"").append(config.cpu).append('"')
        append(",\"cpuId\":\"\",\"device\":\"").append(config.model).append('"')
        append(",\"diskInfo\":\"\",\"display\":\"Nubia-user 9.0.0 20171130.276299 release-keys\"")
        append(",\"etheMac\":\"02:00:00:00:00:00\"")
        append(",\"fingerprint\":\"Nubia/Nubia/").append(config.model).append(":9/dev03041042:user/release-keys\"")
        append(",\"gatewayMac\":\"02:00:00:00:01:00\",\"hardware\":\"").append(config.hardwareInfo).append('"')
        append(",\"host\":\"dev\",\"manufacturer\":\"Nubia\",\"ramSize\":\"6,24 GB\",\"romSize\":\"34,57 GB\"")
        append(",\"se\":\"").append(IdentityFactory.hex(kotlin.random.Random, 20).chunked(2).joinToString(":").uppercase()).append('"')
        append(",\"serialNumber\":\"unknown\",\"tags\":\"release-keys\",\"verId\":\"PI\"")
        append(",\"wifiMac\":\"").append(id.macAddr).append('"')
        append(",\"loginType\":\"3\",\"appLanguage\":\"").append(config.language).append('"')
        append(",\"apkVersion\":\"").append(config.registrationApkVersion).append('"')
        append(",\"sysVersion\":\"").append(config.registrationSysVersion).append('"')
        append(",\"appId\":\"").append(config.registrationAppId).append('"')
        append(",\"hardwareInfo\":\"").append(config.hardwareInfo).append('"')
        append(",\"model\":\"").append(config.model).append('"')
        append(",\"product\":\"").append(config.product).append('"')
        append(",\"cpu\":\"").append(config.cpu).append('"')
        append(",\"deviceToken\":\"\",\"reserve1\":\"").append(config.reserve1).append('"')
        append(",\"sn\":\"\",\"drmId\":\"\"}")
    }

    private fun extractJson(json: String, campo: String): String? {
        val m = Regex("\"" + Regex.escape(campo) + "\"\\s*:\\s*\"([^\"]*)\"").find(json)
        return m?.groupValues?.get(1)
    }
}
