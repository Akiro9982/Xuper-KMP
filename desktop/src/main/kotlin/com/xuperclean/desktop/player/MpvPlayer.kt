package com.xuperclean.desktop.player

import com.sun.jna.Native
import java.io.File
import java.io.FileOutputStream

/** Modos de encuadre del vídeo, de los habituales en un reproductor. */
enum class AjusteImagen(val etiqueta: String) {
    AJUSTAR("Ajustar"),
    PANTALLA_COMPLETA("Pantalla completa"),
    ESTIRAR("Estirar"),
    DIECISEIS_NUEVE("16:9"),
    CUATRO_TRES("4:3");

    fun siguiente(): AjusteImagen = entries[(ordinal + 1) % entries.size]
}

/**
 * Reproductor de escritorio basado en mpv embebido.
 *
 * Render: mpv dibuja dentro del panel AWT (`--wid=<hwnd>`); Compose pinta los controles encima.
 *
 * Estado: un script Lua propio (embebido aquí, escrito a %TEMP% al arrancar) publica
 * `XUPER pos/dur/pause/loaded/end` por stdout. Se eligió esto en lugar de leer el pipe IPC
 * porque Windows bloquea el handle si hay un ReadFile pendiente mientras se escribe
 * (de ahí el deadlock y el crash con I/O overlapped en ntdll).
 *
 * Comandos: conexión de escritura al pipe de `--input-ipc-server`, línea JSON + '\n'
 * (sin el '\n' mpv descarta el comando), y cierre inmediato.
 */
class MpvPlayer(
    private val mpvPath: String,
    private val hostWindow: HostWindow,
) {
    /** Interfaz para recibir el HWND del panel contenedor. */
    interface HostWindow {
        fun hwnd(): Long
    }

    private var process: Process? = null
    private var pipeName: String = ""
    @Volatile private var running = false

    private var duracion = 0.0
    /** Última posición conocida (se reenvía al llegar la duración). */
    private var ultimaPos = 0.0

    /** Fin de reproducción (EOF) o cierre del proceso. */
    var onEnd: (() -> Unit)? = null
    /** Error de reproducción reportado por mpv. */
    var onError: (() -> Unit)? = null
    /** Progreso VOD: (posición s, duración s). Duración 0 mientras no se conozca (live). */
    var onProgress: ((Double, Double) -> Unit)? = null
    /** Cambio de pausa. */
    var onPausedChange: ((Boolean) -> Unit)? = null
    /** Metadatos cargados (primer frame listo). */
    var onLoaded: (() -> Unit)? = null
    /** Búfer VOD: segundo del stream hasta donde hay datos descargados. */
    var onBuffer: ((Double) -> Unit)? = null

    /** Si se define, mpv escribe su log aquí (diagnóstico). */
    var logFile: File? = null

    fun start(
        url: String,
        headers: Map<String, String>,
        title: String,
        inicio: Double = 0.0,
        modo: AjusteImagen = AjusteImagen.AJUSTAR,
        audioLanguages: List<String> = emptyList(),
    ) {
        stop()
        val hwnd = hostWindow.hwnd()
        require(hwnd != 0L) { "panel sin HWND (aún no renderizado)" }

        pipeName = "xuper-mpv-${System.currentTimeMillis()}"
        duracion = 0.0
        ultimaPos = 0.0
        val headerArg = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
        val script = escribirScript()

        val cmd = buildList {
            add(mpvPath)
            add("--wid=$hwnd")
            add("--input-ipc-server=$pipeName")
            add("--script=$script")
            add("--no-border")
            add("--no-terminal")
            add("--osc=no")
            // Punto de reanudación aplicado por mpv al cargar (evita depender del IPC
            // en el primer instante, que puede no estar listo en Windows).
            if (inicio > 0.0) add("--start=" + String.format(java.util.Locale.US, "%.2f", inicio))
            add("--input-default-bindings=no")
            add("--input-vo-keyboard=no")
            add("--keep-open=no")
            add("--force-window=yes")
            add("--hwdec=auto-safe")
            add("--vo=gpu-next")
            add("--gpu-context=d3d11")
            add("--cache=yes")
            // 120 s de antelación: la barra puede mostrar el tramo ya descargado y
            // el reproductor aguanta mejor los cortes del CDN.
            add("--cache-secs=120")
            add("--demuxer-readahead-secs=120")
            add("--demuxer-max-bytes=64MiB")
            add("--network-timeout=20")
            add("--title=$title")
            // Encuadre elegido: se pasa al arrancar para no depender del IPC en el
            // primer instante (el pipe puede tardar en aceptar órdenes).
            when (modo) {
                AjusteImagen.AJUSTAR -> Unit
                AjusteImagen.PANTALLA_COMPLETA -> add("--panscan=1.0")
                AjusteImagen.ESTIRAR -> add("--keepaspect=no")
                AjusteImagen.DIECISEIS_NUEVE -> add("--video-aspect-override=16:9")
                AjusteImagen.CUATRO_TRES -> add("--video-aspect-override=4:3")
            }
            if (headers.isNotEmpty()) add("--http-header-fields=$headerArg")
            // Pista de audio: el VOD trae varios idiomas dentro del mismo stream y el
            // contenedor no marca ninguna como predeterminada (iptv-api.md §15.9).
            if (audioLanguages.isNotEmpty()) add("--alang=" + audioLanguages.joinToString(","))
            logFile?.let {
                add("--log-file=${it.absolutePath}")
                add("--msg-level=all=v")
            }
            add(url)
        }

        process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        running = true

        val p = process
        Thread {
            try {
                p?.inputStream?.bufferedReader()?.use { lector ->
                    while (true) {
                        val linea = lector.readLine() ?: break
                        // Tras stop() la instancia vieja no debe tocar el estado compartido.
                        if (!running) break
                        val idx = linea.indexOf(PREFIJO)
                        if (idx >= 0) procesarEstado(linea.substring(idx + PREFIJO.length).trim())
                    }
                }
            } catch (_: Exception) {
                // proceso cerrado
            }
        }.apply { isDaemon = true; name = "mpv-stdout" }.start()

        Thread {
            try { p?.waitFor() } catch (_: Exception) { }
            if (running) {
                running = false
                onEnd?.invoke()
            }
        }.apply { isDaemon = true; name = "mpv-watch" }.start()
    }

    // ---------------------------------------------------------------- estado

    private fun procesarEstado(mensaje: String) {
        val espacio = mensaje.indexOf(' ')
        val clave = if (espacio < 0) mensaje else mensaje.substring(0, espacio)
        val valor = if (espacio < 0) "" else mensaje.substring(espacio + 1)

        when (clave) {
            "loaded" -> onLoaded?.invoke()
            "dur" -> {
                duracion = valor.toDoubleOrNull() ?: 0.0
                // Un `dur 0` (metadato aún desconocido al cambiar de fuente) no debe
                // reiniciar la posición mostrada.
                if (duracion > 0.0) onProgress?.invoke(ultimaPos, duracion)
            }
            "pos" -> {
                val pos = valor.toDoubleOrNull() ?: return
                ultimaPos = pos
                onProgress?.invoke(pos, duracion)
            }
            "pause" -> onPausedChange?.invoke(valor == "1")
            "cache" -> valor.toDoubleOrNull()?.let { onBuffer?.invoke(it) }
            "end" -> when (valor) {
                "eof" -> {
                    running = false
                    onEnd?.invoke()
                }
                "error" -> {
                    running = false
                    onError?.invoke()
                }
            }
        }
    }

    // ---------------------------------------------------------------- control

    /** Envía un comando al pipe IPC de mpv. */
    fun comando(vararg args: String): Boolean {
        if (!running || pipeName.isEmpty()) return false
        val cuerpo = args.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
        val linea = """{"command":[$cuerpo]}""" + "\n"
        // El pipe de mpv puede estar ocupado un instante (Windows, un cliente a la vez):
        // reintentar brevemente evita perder órdenes justo tras cargar el archivo.
        repeat(4) { intento ->
            try {
                FileOutputStream(File("\\\\.\\pipe\\$pipeName")).use { salida ->
                    salida.write(linea.toByteArray(Charsets.UTF_8))
                    salida.flush()
                }
                return true
            } catch (e: Exception) {
                if (intento == 3) return false
                try { Thread.sleep(30) } catch (_: InterruptedException) { }
            }
        }
        return false
    }

    fun togglePause(): Boolean = comando("cycle", "pause")

    /** Salto relativo en segundos (live no lo admite). */
    fun seekBy(segundos: Int): Boolean = comando("seek", segundos.toString(), "relative")

    /** Salto absoluto en segundos. */
    fun seekTo(segundos: Double): Boolean =
        comando("seek", String.format(java.util.Locale.US, "%.2f", segundos), "absolute")

    fun setVolume(volumen: Int): Boolean = comando("set", "volume", volumen.toString())

    /**
     * Cambia el encuadre en caliente: «ajustar» respeta el formato, «pantalla
     * completa» recorta para llenar (panscan), «estirar» deforma y 16:9/4:3
     * fuerzan el formato. Se limpian siempre los tres ajustes para que no se
     * acumulen entre modos.
     */
    fun aplicarImagen(modo: AjusteImagen): Boolean {
        comando("set", "video-aspect-override", "no")
        comando("set", "keepaspect", "yes")
        comando("set", "panscan", "0")
        return when (modo) {
            AjusteImagen.AJUSTAR -> true
            AjusteImagen.PANTALLA_COMPLETA -> comando("set", "panscan", "1.0")
            AjusteImagen.ESTIRAR -> comando("set", "keepaspect", "no")
            AjusteImagen.DIECISEIS_NUEVE -> comando("set", "video-aspect-override", "16:9")
            AjusteImagen.CUATRO_TRES -> comando("set", "video-aspect-override", "4:3")
        }
    }

    /** Añade y selecciona un subtítulo remoto (mpv descarga por HTTP). */
    fun setSubtitle(url: String, language: String?, label: String?): Boolean =
        comando("sub-add", url, "select", label ?: "Subtítulo", language ?: "")

    fun disableSubtitles(): Boolean = comando("set", "sid", "no")

    /** Escala del texto de subtítulos (1.0 = tamaño base de mpv). */
    fun setSubtitleScale(escala: Float): Boolean =
        comando("set", "sub-scale", String.format(java.util.Locale.US, "%.2f", escala))

    /**
     * Cambia el idioma de audio en caliente: fija `alang` y deja que mpv re-elija la
     * pista (`aid=auto`). Si el idioma pedido no existe, mpv cae a la primera pista.
     */
    fun setAudioLanguage(codes: List<String>): Boolean {
        if (codes.isEmpty()) return false
        comando("set", "alang", codes.joinToString(","))
        return comando("set", "aid", "auto")
    }

    fun stop() {
        running = false
        process?.let { p -> try { p.destroy() } catch (_: Exception) { } }
        process = null
    }

    val isRunning: Boolean get() = running

    // ---------------------------------------------------------------- lua

    /** Escribe el script de estado una sola vez en %TEMP% y devuelve su ruta. */
    private fun escribirScript(): String {
        val dir = File(System.getProperty("java.io.tmpdir"), "xuper-mpv")
        dir.mkdirs()
        val archivo = File(dir, "xuper-state.lua")
        if (!archivo.exists() || archivo.length() != SCRIPT_LUA.toByteArray(Charsets.UTF_8).size.toLong()) {
            archivo.writeText(SCRIPT_LUA, Charsets.UTF_8)
        }
        return archivo.absolutePath
    }

    companion object {
        private const val PREFIJO = "XUPER "

        /**
         * Localiza `mpv.exe`. Como el binario no se versiona (pesa >100 MB), se buscan
         * también las rutas habituales donde el usuario puede dejarlo.
         *
         * Orden: ruta explícita (propiedad o variable), recursos empaquetados, desarrollo,
         * junto a la app, instalación por usuario, Program Files y PATH.
         */
        fun locate(): String? {
            val recursos = System.getProperty("compose.application.resources.dir")
            val appPath = System.getProperty("jpackage.app-path")
            val appDir = appPath?.let { File(it).parentFile }
            val localAppData = System.getenv("LOCALAPPDATA")
            val programFiles = System.getenv("ProgramFiles")
            val home = System.getProperty("user.home")

            val candidatos = buildList {
                (System.getProperty("xuper.mpv") ?: System.getenv("XUPER_MPV"))
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }

                if (!recursos.isNullOrEmpty()) {
                    add(File(recursos, "mpv/mpv.exe").absolutePath)
                    add(File(recursos, "mpv.exe").absolutePath)
                }

                add("desktop/resources/windows/mpv/mpv.exe") // desarrollo desde la raíz
                add("resources/windows/mpv/mpv.exe")         // desarrollo desde desktop/
                add("mpv/mpv.exe")                           // junto al .exe
                add("mpv.exe")                               // en el directorio actual

                appDir?.let {
                    add(File(it, "mpv/mpv.exe").absolutePath)
                    add(File(it, "resources/mpv/mpv.exe").absolutePath)
                    add(File(it, "mpv.exe").absolutePath)
                }

                // Instalación manual recomendada (sin permisos de administrador).
                if (!localAppData.isNullOrEmpty()) {
                    add(File(localAppData, "Xuper/mpv/mpv.exe").absolutePath)
                    add(File(localAppData, "Programs/mpv/mpv.exe").absolutePath)
                }
                if (!home.isNullOrEmpty()) add(File(home, ".xuper/mpv/mpv.exe").absolutePath)
                if (!programFiles.isNullOrEmpty()) add(File(programFiles, "mpv/mpv.exe").absolutePath)

                // Una instalación de mpv añadida al PATH.
                System.getenv("PATH")
                    ?.split(File.pathSeparatorChar)
                    ?.filter { it.isNotBlank() }
                    ?.forEach { add(File(it, "mpv.exe").absolutePath) }
            }

            return candidatos
                .distinct()
                .firstOrNull { File(it).isFile }
        }

        /** Ruta sugerida para dejar mpv.exe si se descarga aparte. */
        fun rutaSugerida(): String {
            val base = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return File(base, "Xuper/mpv/mpv.exe").absolutePath
        }

        /** HWND de un java.awt.Component (Canvas dentro de Compose). */
        fun hwndOf(component: java.awt.Component): Long {
            val ptr = Native.getComponentPointer(component) ?: return 0L
            return com.sun.jna.Pointer.nativeValue(ptr)
        }

        /**
         * Publica el estado por stdout. Los `pos` se limitan a ~4 por segundo
         * (o ante un salto hacia atrás) para no saturar la UI.
         */
        private val SCRIPT_LUA = """
            -- Xuper: estado de reproducción por stdout (lineas "XUPER ...").
            local ultimo = -1

            local function enviar(t)
                io.stdout:write("XUPER " .. t .. "\n")
                io.stdout:flush()
            end

            local function numero(v)
                if v == nil then return "0" end
                return string.format("%.3f", v)
            end

            mp.observe_property("duration", "number", function(_, v)
                enviar("dur " .. numero(v))
            end)

            mp.observe_property("time-pos", "number", function(_, v)
                if v == nil then return end
                if math.abs(v - ultimo) >= 0.25 or v < ultimo then
                    ultimo = v
                    enviar("pos " .. numero(v))
                end
            end)

            mp.observe_property("pause", "bool", function(_, v)
                enviar("pause " .. (v and "1" or "0"))
            end)

            -- Final del tramo descargado: pinta el búfer en la barra de avance.
            local ultimoCache = -1
            mp.observe_property("demuxer-cache-time", "number", function(_, v)
                if v == nil then return end
                if math.abs(v - ultimoCache) >= 1 or v < ultimoCache then
                    ultimoCache = v
                    enviar("cache " .. numero(v))
                end
            end)

            mp.register_event("file-loaded", function() enviar("loaded") end)
            mp.register_event("end-file", function(e) enviar("end " .. tostring(e.reason)) end)
        """.trimIndent()
    }
}
