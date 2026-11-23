package com.xuperclean.core

import com.xuperclean.core.model.ActiveData
import com.xuperclean.core.model.SlbInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Estado de sesión completo: cuenta + CDNs.
 */
public class Session(
    public val account: ActiveData,
    public val slb: SlbInfo,
    public val refreshedAtMs: Long,
)

/**
 * Gestión de sesión con refresco preventivo (30 min) y recuperación de kick
 * (iptv-api.md §9.1 y §14.3): si el portal/CDN rechaza por sesión sustituida
 * o token caducado, refresca y reintenta una vez.
 */
public class SessionManager(
    private val client: XuperPortalClient,
    private val config: XuperConfig,
    private val nowMs: () -> Long,
    /** Se invoca tras cada refresh (persistencia de la app, métricas...). */
    private val onRefreshed: (Session) -> Unit = {},
    /**
     * Prepara el terminal antes de cada refresh (registro de identidad, perfil…).
     * Se ejecuta dentro del mutex y ANTES de `active`, para que `sn` nunca vaya vacío.
     */
    private val preparar: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private var session: Session? = null

    public val current: Session?
        get() = session

    /** Sesión válida, refrescando si es antigua o no existe. */
    public suspend fun ensureValid(): Session = mutex.withLock {
        val s = session
        if (s == null || isStale(s)) {
            refreshLocked()
        } else {
            s
        }
    }

    /** Fuerza un refresco (nueva llamada active + getSlbInfo). */
    public suspend fun refresh(): Session = mutex.withLock { refreshLocked() }

    /**
     * Ejecuta [block] con la sesión válida; si el portal lanza [XuperException.Kick],
     * refresca y reintenta una vez (debounce: máximo un kick cada [KICK_DEBOUNCE_MS]).
     */
    public suspend fun <T> withKickRetry(block: suspend (Session) -> T): T {
        var s = ensureValid()
        try {
            return block(s)
        } catch (e: XuperException.Kick) {
            val now = nowMs()
            require(now - lastKickAt >= KICK_DEBOUNCE_MS || lastKickAt == 0L) {
                "kick reiterado: posible problema de sesión subyacente"
            }
            lastKickAt = now
            s = refresh()
            return block(s)
        }
    }

    private var lastKickAt: Long = 0

    private fun isStale(s: Session): Boolean =
        nowMs() - s.refreshedAtMs >= config.refreshMinutes * 60_000

    private suspend fun refreshLocked(): Session {
        preparar()
        client.refreshProfileIfNeeded()
        val account = client.active()
        require(account.portalCode.isNotEmpty()) { "active no devolvió portalCode" }
        val slb = client.slbInfo(account)
        val s = Session(account, slb, nowMs())
        session = s
        onRefreshed(s)
        return s
    }

    public companion object {
        public const val KICK_DEBOUNCE_MS: Long = 60_000
    }
}
