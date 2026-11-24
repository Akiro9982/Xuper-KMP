package com.xuperclean.mobile.update

import android.content.Context
import com.xuperclean.core.update.AndroidUpdateDownloader
import com.xuperclean.core.update.AppVersion
import com.xuperclean.core.update.UpdateChannel
import com.xuperclean.core.update.UpdateClient
import com.xuperclean.core.update.UpdateController
import com.xuperclean.mobile.BuildConfig
import com.xuperclean.mobile.net.NetProvider

/**
 * Instancia única del actualizador móvil. Vive en la Application para sobrevivir a
 * la navegación: si el usuario sale de Ajustes mientras descarga, la descarga sigue.
 */
object AppUpdates {

    @Volatile
    private var instancia: UpdateController? = null

    fun init(context: Context) {
        if (instancia != null) return
        instancia = UpdateController(
            client = UpdateClient(NetProvider.httpEngine),
            channel = UpdateChannel.MOBILE,
            currentVersion = versionInstalada(),
            downloader = AndroidUpdateDownloader(context.applicationContext),
        )
    }

    val current: UpdateController
        get() = instancia ?: error("AppUpdates.init() no fue llamado")

    private fun versionInstalada(): AppVersion =
        AppVersion.parse(BuildConfig.VERSION_NAME)
            ?: AppVersion(
                major = BuildConfig.VERSION_CODE / 1_000_000,
                minor = (BuildConfig.VERSION_CODE / 1_000) % 1_000,
                patch = BuildConfig.VERSION_CODE % 1_000,
            )
}
