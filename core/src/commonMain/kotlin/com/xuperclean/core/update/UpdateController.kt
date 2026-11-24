package com.xuperclean.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado observable de la sección de actualizaciones. */
public sealed interface UpdateState {
    public data object Idle : UpdateState
    public data object Checking : UpdateState
    public data class UpToDate(public val latest: AppUpdate?) : UpdateState
    public data class Available(public val update: AppUpdate) : UpdateState
    public data class Downloading(
        public val update: AppUpdate,
        public val downloaded: Long,
        public val total: Long,
    ) : UpdateState
    public data class Ready(public val update: AppUpdate, public val path: String) : UpdateState
    public data class Installing(public val update: AppUpdate) : UpdateState
    public data class InstallLaunched(public val update: AppUpdate) : UpdateState
    public data class PermissionRequired(
        public val update: AppUpdate,
        public val message: String,
    ) : UpdateState
    public data class Failed(
        public val message: String,
        public val update: AppUpdate? = null,
    ) : UpdateState
}

/** Descarga/instalación concreta de cada plataforma (APK o MSI). */
public interface UpdateDownloader {
    /** Descarga el archivo y devuelve su ruta local. Debe verificar el SHA-256. */
    public suspend fun download(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String

    /** Lanza el instalador del sistema. */
    public suspend fun install(update: AppUpdate, path: String): InstallResult
}

public sealed interface InstallResult {
    public data object Started : InstallResult
    public data class NeedsPermission(public val message: String) : InstallResult
    public data class Failed(public val message: String) : InstallResult
}

/**
 * Orquestador común: consulta, descarga e instala. La UI de cada plataforma sólo
 * observa [state] y delega en los métodos; así móvil, TV y escritorio comparten
 * exactamente la misma lógica de versiones y changelog.
 */
public class UpdateController(
    private val client: UpdateClient,
    public val channel: UpdateChannel,
    public val currentVersion: AppVersion,
    private val downloader: UpdateDownloader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    public val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var downloadedPath: String? = null
    private var pendingUpdate: AppUpdate? = null

    /** Comprueba si hay una versión más nueva. No interrumpe una descarga en curso. */
    public fun check(force: Boolean = false) {
        when (_state.value) {
            is UpdateState.Checking,
            is UpdateState.Downloading,
            is UpdateState.Installing,
            -> return
            is UpdateState.Available -> if (!force) return
            else -> Unit
        }
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Checking
            try {
                val result = client.check(channel, currentVersion)
                if (result.isNewer && result.latest != null) {
                    pendingUpdate = result.latest
                    _state.value = UpdateState.Available(result.latest)
                } else {
                    _state.value = UpdateState.UpToDate(result.latest)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(mensaje(e))
            }
        }
    }

    /** Comprobación silenciosa al abrir la app: no repite si ya hay estado. */
    public fun checkIfIdle() {
        if (_state.value is UpdateState.Idle) check()
    }

    /** Descarga la actualización disponible y abre el instalador. */
    public fun downloadAndInstall() {
        val update = when (val actual = _state.value) {
            is UpdateState.Available -> actual.update
            // Un fallo de descarga conserva la actualización en el estado: reintentar
            // debe volver a descargarla, no quedarse en un no-op.
            is UpdateState.Failed -> actual.update ?: pendingUpdate ?: return
            else -> pendingUpdate ?: return
        }
        if (_state.value is UpdateState.Downloading) return

        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(update, 0L, update.asset.size)
            try {
                val path = downloader.download(update) { descargado, total ->
                    _state.value = UpdateState.Downloading(update, descargado, total)
                }
                downloadedPath = path
                pendingUpdate = update
                _state.value = UpdateState.Ready(update, path)
                launchInstall(update, path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(mensaje(e), update)
            }
        }
    }

    /** Reintenta instalar el archivo ya descargado (por ejemplo, tras conceder permisos). */
    public fun installDownloaded() {
        val update = pendingUpdate ?: return
        val path = downloadedPath ?: return
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Installing(update)
            launchInstall(update, path)
        }
    }

    private suspend fun launchInstall(update: AppUpdate, path: String) {
        _state.value = UpdateState.Installing(update)
        val result = try {
            downloader.install(update, path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InstallResult.Failed(mensaje(e))
        }
        when (result) {
            is InstallResult.Started -> {
                _state.value = UpdateState.InstallLaunched(update)
            }
            is InstallResult.NeedsPermission -> {
                _state.value = UpdateState.PermissionRequired(update, result.message)
            }
            is InstallResult.Failed -> {
                _state.value = UpdateState.Failed(result.message, update)
            }
        }
    }

    private fun mensaje(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la actualización."
}