package com.xuperclean.core

import com.xuperclean.core.crypto.SunBase64
import com.xuperclean.core.model.ActiveData
import com.xuperclean.core.model.AssetData
import com.xuperclean.core.model.CdnEntry
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.ColumnContents
import com.xuperclean.core.model.NextColumnsData
import com.xuperclean.core.model.PlayTicket
import com.xuperclean.core.model.SearchData
import com.xuperclean.core.model.SlbInfo
import com.xuperclean.core.net.HttpEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cliente del portal Xuper: todos los endpoints verificados (live §3-§6, VOD §14).
 * Los bodies van cifrados (3DES + base64-sun + hex); las respuestas se descifran igual.
 */
public class XuperPortalClient(
    public val config: XuperConfig,
    private val http: HttpEngine,
) {
    private val codec = PortalCodec(config)

    /** Se invoca cuando la recuperación cambia valores de conexión, para persistirlos. */
    public var onProfileChanged: ((PortalProfile) -> Unit)? = null

    private fun portalHeaders(portalCode: String? = null): Map<String, String> = buildMap {
        put("apk", config.appId)
        put("apkVer", config.apkVerHeader)
        put("User-Agent", "okhttp/4.9.3")
        put("Content-Type", "application/json;charset=utf-8")
        put("Cache-Control", "no-store")
        portalCode?.let { put("portalCode", it) }
    }

    /** POST cifrado a un endpoint del portal con fallback por hosts rotativos. */
    private suspend fun post(path: String, bean: JsonObject, portalCode: String): JsonObject =
        postOnce(path, bean, portalCode, reintentarVersion = true)

    private suspend fun postOnce(
        path: String,
        bean: JsonObject,
        portalCode: String,
        reintentarVersion: Boolean,
    ): JsonObject {
        val body = codec.encryptBody(bean, portalCode)
        var last: Exception? = null
        for (host in config.portalHosts) {
            try {
                val r = http.post("https://$host$path", portalHeaders(), body.encodeToByteArray())
                if (r.status !in 200..299) {
                    last = XuperException.Portal(r.status.toString(), "HTTP ${r.status} en $host")
                    continue
                }
                try {
                    return codec.decodeResponse(r.bodyText)
                } catch (e: XuperException) {
                    if (reintentarVersion && e.returnCode == VERSION_RETIRED_CODE) {
                        if (recoverConnection()) {
                            return postOnce(path, bean, portalCode, reintentarVersion = false)
                        }
                        throw XuperException.VersionRetired(e.returnCode)
                    }
                    throw XuperException.of(e.returnCode, "$path: ${e.message}")
                }
            } catch (e: XuperException) {
                throw e // error del portal (no de red): no reintentar en otro host
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("POST $path falló sin excepción")
    }

    /**
     * Trae el perfil remoto de conexión ([XuperConfig.profileUrl]) una vez por ejecución.
     * Sirve para que el proveedor pueda publicar el build aceptado o una clave nueva sin
     * recompilar la app. Nunca lanza: si falla se sigue con los valores locales.
     */
    public suspend fun refreshProfileIfNeeded() {
        if (perfilConsultado || config.profileUrl.isBlank()) return
        perfilConsultado = true
        val remoto = PortalProfile.fetch(http, config.profileUrl) ?: return
        if (!remoto.updatesAnything()) return
        remoto.applyTo(config)
        val combinado = remoto.mergedWith(perfilAplicado)
        perfilAplicado = combinado
        onProfileChanged?.invoke(combinado)
    }

    private var perfilConsultado: Boolean = false
    private var perfilAplicado: PortalProfile? = null

    /** Serializa la recuperación: varias peticiones en paralelo no repiten barridos. */
    private val recuperacionMutex = Mutex()

    /** Si la última recuperación falló hace menos de esto, se aborta en seco. */
    private var ultimoFalloRecuperacion: TimeMark? = null

    /**
     * Recuperación completa ante `portal200001` (Vía C). El servidor devuelve el mismo
     * código para la puerta de versión y para un body cifrado con una clave rotada
     * (iptv-api.md §2.2), así que se intenta en orden de coste:
     *
     * 1. **Re-sondeo** del par build×clave actual: otra petición pudo haberlo resuelto
     *    mientras esperábamos el mutex (y si sigue fallando, confirma que toca barrer).
     * 2. **Sondeo corto** de builds ([XuperConfig.versionRecoveryShortProbes]): la puerta
     *    de versión normal (build retirado cerca) se resuelve en pocas peticiones.
     * 3. **Perfil remoto forzado**: trae build y/o `portalKey` candidatas; se sonda cada
     *    combinación build×clave con un `active` real y **solo se adopta la que el
     *    servidor acepta** (una clave publicada sin verificar no puede romper la
     *    conexión). Si el perfil trae una clave, los barridos siguientes la usan.
     * 4. **Barrido largo** ([recoverVersion], tope ×5): build lejano sin perfil.
     *
     * Un fallo reciente ([RECOVERY_COOLDOWN]) aborta rápido para no disparar barridos
     * duplicados cuando varias peticiones chocan con la misma puerta. Devuelve true si
     * algún valor quedó adoptado y verificado: entonces el caller reintenta la
     * petición original una vez.
     */
    public suspend fun recoverConnection(): Boolean = recuperacionMutex.withLock {
        // Si otra petición falló hace poco, ni siquiera se sondea: aborta en seco.
        ultimoFalloRecuperacion?.let { if (it.elapsedNow() < RECOVERY_COOLDOWN) return@withLock false }
        val buildActual = config.apkVerHeader.toIntOrNull()
        if (buildActual != null && aceptaBuild(buildActual)) {
            ultimoFalloRecuperacion = null
            return@withLock true // ya lo resolvió otra petición
        }
        val claveOriginal = config.portalKey
        val ok = sondeoCorto() || recuperarPorPerfil() || recoverVersion()
        // Nada quedó verificado: no se deja en la config una clave que solo era una pista.
        if (!ok) config.portalKey = claveOriginal
        ultimoFalloRecuperacion = if (ok) null else TimeSource.Monotonic.markNow()
        ok
    }

    /** Barrido corto en ambos sentidos: la puerta de versión normal, en pocas sondas. */
    private suspend fun sondeoCorto(): Boolean {
        val actual = config.apkVerHeader.toIntOrNull() ?: return false
        val encontrado = buscarEnAmbasDirecciones(actual, intArrayOf(config.versionRecoveryShortProbes))
            ?: return false
        adoptarBuild(encontrado, "build recuperado (sondeo corto)")
        return true
    }

    /**
     * Refetch forzado del perfil remoto ([XuperConfig.profileUrl]) y sonda de sus
     * candidatas: solo adopta una combinación build×clave que el servidor acepta.
     * Si el perfil trae una clave y ninguna combinación pasa, esa clave (más reciente
     * que la local) queda fijada para los barridos siguientes.
     */
    private suspend fun recuperarPorPerfil(): Boolean {
        if (config.profileUrl.isBlank()) return false
        val remoto = PortalProfile.fetch(http, config.profileUrl) ?: return false
        perfilConsultado = true // ya se consultó en esta ejecución
        if (!remoto.updatesAnything()) return false
        val buildOriginal = config.apkVerHeader
        val claveOriginal = config.portalKey
        val claves = (remoto.keyCandidates() + claveOriginal).distinct()
        val builds = (listOfNotNull(remoto.apkVer) + buildOriginal).distinct()
        for (clave in claves) {
            for (buildTexto in builds) {
                val build = buildTexto.toIntOrNull() ?: continue
                // El par original ya falló en la petición que dispara la recuperación.
                if (buildTexto == buildOriginal && clave == claveOriginal) continue
                config.portalKey = clave
                if (aceptaBuild(build)) {
                    remoto.applyTo(config)
                    config.apkVerHeader = buildTexto // la combinación verificada gana
                    config.portalKey = clave
                    val adoptado = remoto.copy(apkVer = buildTexto, portalKey = clave)
                        .mergedWith(perfilAplicado)
                    perfilAplicado = adoptado
                    perfilConsultado = true
                    onProfileChanged?.invoke(adoptado)
                    return true
                }
            }
        }
        config.portalKey = remoto.keyCandidates().firstOrNull() ?: claveOriginal
        config.apkVerHeader = buildOriginal
        return false
    }

    /**
     * El portal retiró el build de la cabecera `apkVer` (`portal200001`). Sondea builds
     * contra `active` y adopta el primero que acepte (barrido largo: última red de
     * [recoverConnection]; también usable por sí solo, p. ej. en tests).
     *
     * El build retirado puede haber quedado por encima o por debajo del actual, así que se
     * barre en ambos sentidos en bloques paralelos. El tope es de sondas (determinista):
     * [XuperConfig.versionRecoveryProbes] y una segunda pasada ×[RECOVERY_SEGUNDA_PASADA]
     * (pedido del usuario). Si el proveedor rota además la clave, el sondeo no basta
     * (ver PortalProfile).
     */
    public suspend fun recoverVersion(): Boolean {
        val actual = config.apkVerHeader.toIntOrNull() ?: return false
        val topes = listOf(config.versionRecoveryProbes, config.versionRecoveryProbes * RECOVERY_SEGUNDA_PASADA)
        for (tope in topes) {
            val encontrado = buscarEnAmbasDirecciones(actual, intArrayOf(tope))
            if (encontrado != null) {
                adoptarBuild(encontrado, "build recuperado")
                return true
            }
        }
        return false
    }

    /** Adopta un build verificado, lo deja en la config y lo persiste. */
    private fun adoptarBuild(build: Int, nota: String) {
        config.apkVerHeader = build.toString()
        val actualizado = PortalProfile(
            apkVer = config.apkVerHeader,
            portalKey = config.portalKey,
            note = nota,
        ).mergedWith(perfilAplicado)
        perfilAplicado = actualizado
        onProfileChanged?.invoke(actualizado)
    }

    /**
     * Busca a la vez hacia arriba y hacia abajo y se queda con la primera dirección que
     * acierta: el build actual puede haber quedado por encima o por debajo del retirado.
     */
    private suspend fun buscarEnAmbasDirecciones(actual: Int, restantes: IntArray): Int? =
        coroutineScope {
            val mitad = (restantes[0] / 2).coerceAtLeast(BLOQUE_VERSION)
            val arriba = async {
                buscarBuild(actual + 1, actual + RECOVERY_SALTO_MAXIMO, intArrayOf(mitad))
            }
            val abajo = async {
                buscarBuildDescendente(
                    (actual - RECOVERY_SALTO_MAXIMO).coerceAtLeast(1),
                    actual - 1,
                    intArrayOf(restantes[0] - mitad),
                )
            }
            val primero = select<Int?> {
                arriba.onAwait { it }
                abajo.onAwait { it }
            }
            if (primero != null) {
                arriba.cancel()
                abajo.cancel()
                return@coroutineScope primero
            }
            // La primera en agotar su mitad no acertó: esperar a la otra.
            runCatching { arriba.await() }.getOrNull() ?: runCatching { abajo.await() }.getOrNull()
        }

    /** Barrido paralelo hacia arriba; devuelve el primer build aceptado. */
    private suspend fun buscarBuild(desde: Int, hasta: Int, restantes: IntArray): Int? {
        if (hasta < desde) return null
        var inicio = desde
        while (inicio <= hasta && restantes[0] > 0) {
            val tamano = minOf(BLOQUE_VERSION, restantes[0], hasta - inicio + 1)
            val fin = inicio + tamano - 1
            restantes[0] -= tamano
            val hallados = coroutineScope {
                (inicio..fin).map { build -> async { if (aceptaBuild(build)) build else null } }.awaitAll()
            }.filterNotNull()
            if (hallados.isNotEmpty()) return hallados.minOrNull()
            inicio = fin + 1
        }
        return null
    }

    /** Igual que [buscarBuild] pero empezando por el bloque más cercano a [hasta]. */
    private suspend fun buscarBuildDescendente(desde: Int, hasta: Int, restantes: IntArray): Int? {
        if (hasta < desde) return null
        var fin = hasta
        while (fin >= desde && restantes[0] > 0) {
            val tamano = minOf(BLOQUE_VERSION, restantes[0], fin - desde + 1)
            val inicio = fin - tamano + 1
            restantes[0] -= tamano
            val hallados = coroutineScope {
                (inicio..fin).map { build -> async { if (aceptaBuild(build)) build else null } }.awaitAll()
            }.filterNotNull()
            if (hallados.isNotEmpty()) return hallados.maxOrNull()
            fin = inicio - 1
        }
        return null
    }

    /** Comprueba un build con una petición `active` sin sesión. */
    private suspend fun aceptaBuild(build: Int): Boolean {
        val bean = buildJsonObject {
            put("authCode", "")
            put("authVersion", "")
            put("channel", "default")
            put("macAddr", config.macAddr)
            put("matadata", "")
            put("openNum", 20)
            put("reserve1", config.reserve1)
            put("signdata", "")
            put("snToken", "")
            put("sn", config.sn)
        }
        val cuerpo = codec.encryptBody(bean, "")
        val cabeceras = portalHeaders().toMutableMap().apply { put("apkVer", build.toString()) }
        val host = config.portalHosts.firstOrNull() ?: return false
        return runCatching {
            val r = http.post("https://$host/api/portalCore/v8/active", cabeceras, cuerpo.encodeToByteArray())
            if (r.status !in 200..299) return@runCatching false
            val raiz = codec.json.parseToJsonElement(r.bodyText) as? JsonObject ?: return@runCatching false
            (raiz.str("returnCode") ?: "0") == "0"
        }.getOrDefault(false)
    }

    private fun dataOf(root: JsonObject): JsonObject =
        (root["data"] as? JsonObject) ?: JsonObject(emptyMap())

    // ---------------------------------------------------------- live (iptv-api.md §3-§6)

    /** POST /api/portalCore/v8/active — autenticación por sn (sin contraseña). */
    public suspend fun active(): ActiveData {
        val bean = buildJsonObject {
            put("authCode", "")
            put("authVersion", "")
            put("channel", "default")
            put("macAddr", config.macAddr)
            put("matadata", "")
            put("openNum", 20)
            put("reserve1", config.reserve1)
            put("signdata", "")
            put("snToken", "")
            put("sn", config.sn)
        }
        return codec.json.decodeFromJsonElement(ActiveData.serializer(), dataOf(post("/api/portalCore/v8/active", bean, "")))
    }

    /** POST /api/portalCore/v15/getSlbInfo — CDNs (live/vod/record) y play_params. */
    public suspend fun slbInfo(session: ActiveData): SlbInfo {
        val bean = buildJsonObject {
            put("appParams", "")
            put("appVer", config.apkVersion)
            put("encMediaSupported", 1)
            put("hasPay", session.hasPay ?: "0")
            put("lang", config.language)
            put("liveCodeList", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(session.portalCode + "_live"))))
            put("pipFlag", "0")
            put("reserve1", config.reserve1)
            put("type", "merge")
            put("userId", session.userId)
            put("userIdentity", session.userIdentity ?: "1")
            put("userToken", session.userToken)
        }
        return codec.json.decodeFromJsonElement(SlbInfo.serializer(), dataOf(post("/api/portalCore/v15/getSlbInfo", bean, session.portalCode)))
    }

    /** POST /api/portalCore/v6/getLiveData — catálogo de canales en vivo. */
    public suspend fun liveData(
        session: ActiveData,
        pageNum: Int = 1,
        pageSize: Int = 3000,
        columnId: Long = config.liveColumnId,
    ): List<Channel> {
        val bean = buildJsonObject {
            put("columnId", columnId)
            put("dataVersion", "")
            put("expireTimeStr", "")
            put("pageNum", pageNum)
            put("pageSize", pageSize)
            put("userId", session.userId)
            put("userToken", session.userToken)
        }
        val data = dataOf(post("/api/portalCore/v6/getLiveData", bean, session.portalCode))
        val channels = (data["channelList"] as? JsonArray) ?: return emptyList()
        return channels.map { codec.json.decodeFromJsonElement(Channel.serializer(), it) }
    }

    // ---------------------------------------------------------- VOD (iptv-api.md §14)

    /** POST /v3/getRecommends — banners del home VOD. */
    public suspend fun recommends(session: ActiveData, codes: List<String> = DEFAULT_RECOMMEND_CODES): JsonObject {
        val bean = buildJsonObject {
            put("portalCode", session.portalCode)
            put("recommendCodeArr", buildJsonArray { codes.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("userId", session.userId)
            put("userToken", session.userToken)
        }
        return dataOf(post("/api/portalCore/v3/getRecommends", bean, session.portalCode))
    }

    /**
     * POST /getNextColumns — filas del home VOD con contenido embebido.
     * Bean real de la app: columnCode "masnew_featured", pageNum/pageSize, version (caché).
     */
    public suspend fun nextColumns(
        session: ActiveData,
        columnCode: String = "masnew_featured",
        pageNum: Int = 1,
        pageSize: Int = 10,
        version: String = "",
    ): NextColumnsData {
        val bean = buildJsonObject {
            put("columnCode", columnCode)
            put("pageNum", pageNum)
            put("pageSize", pageSize)
            put("portalCode", session.portalCode)
            put("userId", session.userId)
            put("userToken", session.userToken)
            if (version.isNotEmpty()) put("version", version)
        }
        return codec.json.decodeFromJsonElement(NextColumnsData.serializer(), dataOf(post("/api/portalCore/getNextColumns", bean, session.portalCode)))
    }

    /** POST /v3/getColumnContents — árbol de columnas o contenidos de una columna. */
    public suspend fun columnContents(session: ActiveData, columnId: Long, pageNum: Int = 1, pageSize: Int = 1000): ColumnContents {
        val bean = buildJsonObject {
            put("columnId", columnId)
            put("isAv1", "0")
            put("pageNum", pageNum)
            put("pageSize", pageSize)
            put("portalCode", session.portalCode)
            put("specialFlag", "0")
            put("userId", session.userId)
            put("userToken", session.userToken)
            put("reserve1", config.reserve1)
        }
        return codec.json.decodeFromJsonElement(ColumnContents.serializer(), dataOf(post("/api/portalCore/v3/getColumnContents", bean, session.portalCode)))
    }

    /**
     * POST /v4/getItemData — detalle. `type`: "1" película, "0" serie/teleplay.
     * En series, `assetData.simpleProgramList` trae los episodios.
     */
    public suspend fun itemData(session: ActiveData, contentId: String, type: String, sortType: String = "0", language: String = "es"): AssetData {
        val bean = buildJsonObject {
            put("userToken", session.userToken)
            put("userId", session.userId)
            put("portalCode", session.portalCode)
            put("contentId", contentId)
            put("type", type)
            put("sortType", sortType)
            put("language", language)
            put("macAddr", config.macAddr)
        }
        val data = dataOf(post("/api/portalCore/v4/getItemData", bean, session.portalCode))
        val asset = data["assetData"] ?: JsonObject(emptyMap())
        return codec.json.decodeFromJsonElement(AssetData.serializer(), asset)
    }

    /**
     * POST /v10/startPlayVOD — ticket de reproducción.
     * Película: contentId=<peli>, type="1", columnId=0, seriesContentId="", episodeNumberList=[].
     * Serie:    contentId=<episodio>, seriesContentId=<temporada>, type="0", columnId=vodRoot (87026),
     *           episodeNumberList=[n].
     */
    public suspend fun startPlayVod(
        session: ActiveData,
        contentId: String,
        type: String,
        columnId: Long = 0,
        seriesContentId: String = "",
        startTime: Int = 0,
        authType: String = "",
        episodeNumbers: List<Int> = emptyList(),
    ): PlayTicket {
        val bean = buildJsonObject {
            put("authType", authType)
            put("columnId", columnId)
            put("contentId", contentId)
            put("seriesContentId", seriesContentId)
            put("startTime", startTime)
            put("type", type)
            put("userId", session.userId)
            put("userToken", session.userToken)
            if (episodeNumbers.isNotEmpty()) {
                put("episodeNumberList", buildJsonArray { episodeNumbers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }
        return codec.json.decodeFromJsonElement(PlayTicket.serializer(), dataOf(post("/api/portalCore/v10/startPlayVOD", bean, session.portalCode)))
    }

    /** POST /v3/searchByName — buscador global. */
    public suspend fun searchByName(session: ActiveData, value: String, pageNum: Int = 1, pageSize: Int = 20): SearchData {
        val bean = buildJsonObject {
            put("userToken", session.userToken)
            put("userId", session.userId)
            put("portalCode", session.portalCode)
            put("columnId", "")
            put("filter", "")
            put("pageNum", pageNum)
            put("pageSize", pageSize)
            put("type", "1")
            put("value", value)
        }
        return codec.json.decodeFromJsonElement(SearchData.serializer(), dataOf(post("/api/portalCore/v3/searchByName", bean, session.portalCode)))
    }

    /** POST /v3/searchByContent — contenidos relacionados ("Quizás te guste"). */
    public suspend fun searchByContent(session: ActiveData, contentId: String, rows: Int = 10): SearchData {
        val bean = buildJsonObject {
            put("contentId", contentId)
            put("start", 0)
            put("end", rows * 5)
            put("rows", rows)
            put("type", "1")
            put("userId", session.userId)
            put("userToken", session.userToken)
            put("portalCode", session.portalCode)
        }
        return codec.json.decodeFromJsonElement(SearchData.serializer(), dataOf(post("/api/portalCore/v3/searchByContent", bean, session.portalCode)))
    }

    public companion object {
        /** Tamaño del bloque de builds sondeados en paralelo al recuperar la versión. */
        private const val BLOQUE_VERSION = 16

        /** Hasta dónde se barre por encima del build actual (sin pista del salto real). */
        private const val RECOVERY_SALTO_MAXIMO = 50_000

        /** Multiplicador de tiempo de la segunda pasada de recuperación. */
        private const val RECOVERY_SEGUNDA_PASADA = 5

        /** Tasa mínima entre recuperaciones fallidas (evita barridos duplicados). */
        private val RECOVERY_COOLDOWN = 30.seconds

        public val DEFAULT_RECOMMEND_CODES: List<String> =
            (1..6).map { "masnew_featured%02d".format(it) }

        /** Clave por defecto hardcodeada en fc.b (fallback si el backend rota PORTAL_KEY). */
        public val DEFAULT_FC_B_KEY: ByteArray = SunBase64.decode(
            "2b494e53756c664c2f44465245733572"
        ).copyOf(24)
    }
}

