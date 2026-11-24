package com.xuperclean.mobile.ui.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

/**
 * Envuelve la fuente de datos del reproductor para contar los bytes que llegan de verdad.
 *
 * Media3 sólo publica sus estadísticas de carga al cerrar la conexión, y en un MPEG-TS
 * progresivo la conexión dura toda la película: sin este contador no se puede saber si un
 * corte es de red (no llegan bytes) o del decodificador (llegan y no se pintan).
 */
class ContadorDatos(
    private val base: DataSource,
    val bytes: AtomicLong,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) {
        base.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long = base.open(dataSpec)

    override fun getUri(): Uri? = base.uri

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val leidos = base.read(buffer, offset, length)
        if (leidos > 0) bytes.addAndGet(leidos.toLong())
        return leidos
    }

    override fun close() = base.close()
}