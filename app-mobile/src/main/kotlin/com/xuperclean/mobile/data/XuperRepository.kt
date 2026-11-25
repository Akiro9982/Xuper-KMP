package com.xuperclean.mobile.data

import com.xuperclean.core.NexusPortal
import com.xuperclean.core.PortalProfile
import com.xuperclean.core.TextIdentityStore
import com.xuperclean.core.XuperException
import com.xuperclean.core.crypto.XuperCrypto
import com.xuperclean.core.SessionManager
import com.xuperclean.core.SeasonCopy
import com.xuperclean.core.SeasonEntry
import com.xuperclean.core.SeasonEpisode
import com.xuperclean.core.SeasonMerge
import com.xuperclean.core.SeriesGrouping
import com.xuperclean.core.StreamBuilder
import com.xuperclean.core.SubtitleTrack
import com.xuperclean.core.SubtitleTracks
import com.xuperclean.core.XuperConfig
import com.xuperclean.core.XuperPortalClient
import com.xuperclean.core.model.ActiveData
import com.xuperclean.core.model.AssetData
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.ColumnInfo
import com.xuperclean.core.model.MovieVariant
import com.xuperclean.core.model.NextColumnsData
import com.xuperclean.core.model.PlayTicket
import com.xuperclean.core.model.SearchData
import com.xuperclean.core.model.SlbInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.core.net.JvmHttpEngine
import com.xuperclean.mobile.net.NetProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Repositorio de la app: una sola sesión compartida por todas las pantallas.
 * Envuelve el core; las llamadas al portal van protegidas con recuperación de kick.
 */
object XuperRepository {

    /**
     * SN del dispositivo. En la app final se lee de las preferencias del dispositivo real;
     * para el arranque se usa el sn del dispositivo registrado (misma cuenta personal).
     */
    var sn: String = "4c3b596ade128c770e86d19424c6f38a"

    private val config: XuperConfig by lazy {
        XuperConfig(sn = "").apply {
            macAddr = identityStore.load()?.macAddr ?: macAddr
            // Valores de conexión aprendidos en ejecuciones anteriores (build aceptado…).
            WatchStore.portalProfile()?.let { texto -> PortalProfile.parse(texto)?.applyTo(this) }
        }
    }

    private val identityStore = TextIdentityStore(
        read = { WatchStore.identity() },
        write = { WatchStore.saveIdentity(it) },
    )

    /** Registro del terminal (una sola vez) y sincronización del SN con la identidad. */
    private val nexus by lazy {
        NexusPortal(config, identityStore) { host, ruta, cuerpo, clave, apk, version, spkg ->
            portalPost(host, ruta, cuerpo, clave, apk, version, spkg)
        }
    }

    private suspend fun asegurarIdentidad() {
        // Perfil remoto: si el proveedor rotó build/clave, se aplica antes de tocar el portal.
        runCatching { client.refreshProfileIfNeeded() }
        val ident = nexus.ensureRegistered()
        if (config.sn != ident.sn) config.sn = ident.sn
        if (config.macAddr != ident.macAddr) config.macAddr = ident.macAddr
    }
    private val client: XuperPortalClient by lazy {
        XuperPortalClient(config, com.xuperclean.mobile.net.NetProvider.httpEngine).apply {
            onProfileChanged = { nuevo ->
                val previo = WatchStore.portalProfile()?.let(PortalProfile::parse)
                WatchStore.savePortalProfile(PortalProfile.encode(nuevo.mergedWith(previo)))
            }
        }
    }
    private val sessionManager: SessionManager by lazy {
        SessionManager(
        client, config, nowMs = { System.currentTimeMillis() },
        preparar = { asegurarIdentidad() },
    )
    }

    private val stateMutex = Mutex()
    private var slbCache: SlbInfo? = null
    private var liveChannelsCache: List<Channel>? = null
    private var homeCache: NextColumnsData? = null

    suspend fun session(): ActiveData {
        asegurarIdentidad()
        return sessionManager.ensureValid().account
    }

    /**
     * POST cifrado con la clave/identidad indicadas (registro y renovación de sesión).
     * Devuelve el `data` ya descifrado.
     */
    private suspend fun portalPost(
        host: String, ruta: String, cuerpo: String, clave: String,
        apk: String, version: String, spkgVer: String,
    ): String {
        val resultado = NetProvider.httpEngine.post(
            "https://$host$ruta",
            mapOf(
                "user-agent" to "okhttp/3.12.12",
                "apkver" to version,
                "apk" to apk,
                "spkgver" to spkgVer,
                "content-type" to "application/json;charset=utf-8",
                "accept-encoding" to "identity",
                "cache-control" to "no-store;Content-type:application/json;charset=utf-8",
            ),
            cuerpo.encodeToByteArray(),
        )
        val texto = resultado.bodyText
        val codigo = Regex("\"returnCode\"\\s*:\\s*\"([^\"]*)\"").find(texto)?.groupValues?.get(1)
        if (codigo != null && codigo != "0") {
            val mensaje = Regex("\"errorMessage\"\\s*:\\s*\"([^\"]*)\"").find(texto)?.groupValues?.get(1)
            throw XuperException.of(codigo, mensaje)
        }
        val datos = Regex("\"data\"\\s*:\\s*\"([0-9a-fA-F]+)\"").find(texto)?.groupValues?.get(1)
            ?: return "{}"
        return XuperCrypto.decryptResponse(datos, clave)
    }

    /** Canales en vivo (cacheados en memoria, con recuperación de kick). */
    suspend fun liveChannels(): List<Channel> = stateMutex.withLock {
        liveChannelsCache ?: sessionManager.withKickRetry { s ->
            client.liveData(s.account).also { liveChannelsCache = it }
        }
    }

    /**
     * Categorías reales del árbol LIVE del portal (#3): 38 nodos, de los que se
     * descarta `ChannelList` (76182), que es la lista plana ya mostrada en «Todos».
     */
    suspend fun liveCategories(): List<ColumnInfo> =
        sessionManager.withKickRetry { s ->
            client.columnContents(s.account, config.liveTreeColumnId).childColumnList
                .filterNot { it.id == config.liveColumnId }
        }

    /** Canales de una categoría concreta del árbol LIVE (#3). */
    suspend fun liveChannelsPorColumna(columnId: Long): List<Channel> =
        sessionManager.withKickRetry { s -> client.liveData(s.account, columnId = columnId) }

    /** Filas del home VOD (cacheadas en memoria, con recuperación de kick). */
    suspend fun homeRows(): NextColumnsData = stateMutex.withLock {
        homeCache ?: sessionManager.withKickRetry { s ->
            client.nextColumns(s.account).also { homeCache = it }
        }
    }

    /** Filtra el pool del home por tipo de programa (movie / teleplay). */
    suspend fun assetsByProgramType(vararg types: String): List<VodAsset> {
        val rows = homeRows()
        return rows.recommendList
            .asSequence()
            .flatMap { it.assetList.asSequence() }
            .filter { asset -> types.any { t -> asset.programType.equals(t, ignoreCase = true) } }
            .distinctBy { it.contentId }
            .toList()
    }

    /** Todos los títulos del home, sin duplicados (base del catálogo free). */
    suspend fun homeAssets(): List<VodAsset> = homeRows().recommendList
        .asSequence()
        .flatMap { it.assetList.asSequence() }
        .distinctBy { it.contentId }
        .toList()

    /**
     * Temporadas hermanas de una serie (el portal las devuelve como assets sueltos).
     *
     * Se busca por el título visible **y por el alias**: el portal reparte la misma
     * serie entre dos nombres (`Un Show Más` T1/T2 y `Historias corrientes` T3–T8),
     * unidos por el alias (`Regular Show S1` / `Regular Show Season3`). Buscar solo el
     * título deja la mitad de las temporadas fuera.
     */
    suspend fun seasonsFor(asset: VodAsset): List<SeasonEntry> {
        if (!asset.isSeries) return emptyList()
        val terminos = buildList {
            add(SeriesGrouping.searchTitle(asset.name))
            asset.alias?.takeIf { it.isNotBlank() }?.let { add(SeriesGrouping.searchTitle(it)) }
        }.filter { it.length >= 3 }.distinct()

        val candidatos = ArrayList<VodAsset>()
        terminos.forEach { termino ->
            val encontrados = runCatching {
                search(termino, pageSize = 50).searchItemList
                    .flatMap { it.itemList }
                    .map { it.toVodAsset() }
                    .filter { it.isSeries }
            }.getOrDefault(emptyList())
            candidatos += encontrados
        }
        return SeriesGrouping.seasonsOf(candidatos.distinctBy { it.contentId }, asset)
    }

/**
 * Episodios de una temporada **fusionando todas sus copias**.
 *
 * El portal publica la misma temporada dos veces: la original (más idiomas y las dos
 * pistas en español) y la doblada (más episodios). La ficha muestra una sola lista: un
 * episodio por número, tomado de la copia con mejor audio, y conserva los números que
 * solo publica una de ellas. [principal] ya está cargada por la ficha.
 */
suspend fun episodiosDeTemporada(
    principal: AssetData,
    copias: List<VodAsset>,
): List<SeasonEpisode> {
    val otras = copias.distinctBy { it.contentId }
        .filter { it.contentId != principal.contentId }
    val detalles = coroutineScope {
        otras.map { copia ->
            async {
                copia to runCatching { itemData(copia.contentId, "0") }.getOrNull()
            }
        }.awaitAll()
    }
    val todas = buildList {
        add(SeasonCopy(principal.toVodAsset(), principal.audioInfo, principal.simpleProgramList))
        detalles.forEach { (asset, detalle) ->
            detalle?.let { add(SeasonCopy(asset, it.audioInfo, it.simpleProgramList)) }
        }
    }
    return SeasonMerge.merge(todas)
}


    suspend fun itemData(contentId: String, type: String) =
        sessionManager.withKickRetry { s -> client.itemData(s.account, contentId, type) }

    suspend fun startPlayVod(
        contentId: String,
        type: String,
        columnId: Long = 0,
        seriesContentId: String = "",
        episodeNumbers: List<Int> = emptyList(),
    ): PlayTicket = sessionManager.withKickRetry { s ->
        client.startPlayVod(
            session = s.account,
            contentId = contentId,
            type = type,
            columnId = columnId,
            seriesContentId = seriesContentId,
            episodeNumbers = episodeNumbers,
        )
    }

    suspend fun search(value: String, pageNum: Int = 1, pageSize: Int = 20): SearchData =
        sessionManager.withKickRetry { s -> client.searchByName(s.account, value, pageNum, pageSize) }

    suspend fun related(contentId: String): SearchData =
        sessionManager.withKickRetry { s -> client.searchByContent(s.account, contentId) }

    // ---------------------------------------------------------------- streams

    private suspend fun slb(): SlbInfo = stateMutex.withLock {
        slbCache ?: sessionManager.withKickRetry { s ->
            client.slbInfo(s.account).also { slbCache = it }
        }
    }

    /** Streams de un canal en vivo, el primero es el preferido (fallback de CDN). */
    suspend fun liveStreams(channel: Channel): List<LiveStream> {
        val addr = StreamBuilder.pickLiveAddress(channel) ?: return emptyList()
        val license = addr.license ?: return emptyList()
        val tag = addr.tag ?: "free"
        return StreamBuilder.liveCdnCandidates(slb().cdnList, tag).mapNotNull { cdn ->
            val auth = cdn.urlList.firstOrNull { it.tag == tag }?.url
                ?: cdn.urlList.firstOrNull()?.url
                ?: return@mapNotNull null
            LiveStream(
                title = channel.alias ?: channel.name,
                url = StreamBuilder.liveM3u8Url(cdn, addr.playCode),
                headers = StreamBuilder.liveHeaders(auth, license),
                isLive = true,
            )
        }.distinctBy { it.url.substringAfter("//").substringBefore('/') }
    }

    suspend fun liveStream(channel: Channel): LiveStream? = liveStreams(channel).firstOrNull()

    /** Stream progresivo VOD para una variante. */
    suspend fun vodStream(variant: MovieVariant, title: String): VodStream? {
        val cdn = StreamBuilder.pickVodCdn(slb().cdnList) ?: return null
        return vodStream(variant, cdn, title)
    }

    private fun vodStream(variant: MovieVariant, cdn: com.xuperclean.core.model.CdnEntry, title: String): VodStream {
        val license = StreamBuilder.pickLicense(variant) ?: ""
        val url = StreamBuilder.vodMediaTsUrl(cdn, variant.contentId, transId(), "")
        val headers = buildMap {
            put("User-Agent", "Ranger/4.9.4-17294ac0")
            put("App", "com.android.msandroid")
            put("App-Version", "49902")
            if (license.isNotEmpty()) put("Content-License", license)
        }
        return VodStream(title = title, url = url, headers = headers, variant = variant, label = etiqueta(variant))
    }

    /** Fuentes VOD (variantes × CDN) con la primera que responde delante. */
    suspend fun vodStreams(ticket: PlayTicket, title: String): List<VodStream> {
        val cdns = StreamBuilder.vodCdnCandidates(slb().cdnList)
        if (cdns.isEmpty()) return emptyList()
        val variantes = ticket.episodeList.flatMap { it.variants }.distinctBy { it.contentId }
        val opciones = variantes.flatMap { v -> cdns.map { cdn -> vodStream(v, cdn, title) } }
        val primero = opciones.indexOfFirst { sano(it) }
        return if (primero <= 0) opciones else opciones.drop(primero) + opciones.take(primero)
    }

    /** Pistas de subtítulo del ticket (todos los episodios, sin duplicados). */
    fun subtitles(ticket: PlayTicket): List<SubtitleTrack> =
        SubtitleTracks.from(ticket.episodeList.flatMap { it.subtitleList })

    /** Idiomas de audio del ticket: pistas dentro del mismo stream, no fuentes distintas. */
    fun audio(ticket: PlayTicket): List<com.xuperclean.core.AudioTrack> =
        com.xuperclean.core.AudioTracks.fromVariants(ticket.episodeList.flatMap { it.variants })

    /** Etiqueta humana de la variante: calidad · codec · audio. */
    private fun etiqueta(variant: MovieVariant): String =
        listOfNotNull(
            variant.quality,
            variant.encodeFormat?.uppercase(),
            variant.audioInfo,
        ).joinToString(" · ").ifEmpty { "Fuente" }

    private suspend fun sano(stream: VodStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cliente = NetProvider.okHttpClient.newBuilder()
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val req = okhttp3.Request.Builder()
            .url(stream.url)
            .header("Range", "bytes=0-32767")
            .apply { stream.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        runCatching {
            cliente.newCall(req).execute().use { r ->
                val bytes = r.body?.bytes() ?: ByteArray(0)
                r.isSuccessful && (bytes.size > 1000) && bytes.firstOrNull()?.toInt()?.and(0xFF) == 0x47
            }
        }.getOrDefault(false)
    }

    private fun transId(): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        fun part() = buildString(12) { repeat(12) { append(allowed[Random.nextInt(allowed.length)]) } }
        return "${part()}_${part()}"
    }
}

/** Stream listo para el player. */
data class LiveStream(
    val title: String,
    val url: String,
    val headers: Map<String, String>,
    val isLive: Boolean,
)

data class VodStream(
    val title: String,
    val url: String,
    val headers: Map<String, String>,
    val variant: MovieVariant,
    val label: String = ""
)
