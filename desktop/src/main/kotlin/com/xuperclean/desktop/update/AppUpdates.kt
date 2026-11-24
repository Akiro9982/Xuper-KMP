package com.xuperclean.desktop.update

import com.xuperclean.core.update.AppVersion
import com.xuperclean.core.update.UpdateChannel
import com.xuperclean.core.update.UpdateClient
import com.xuperclean.core.update.UpdateController
import com.xuperclean.desktop.net.NetProvider

/** Actualizador de escritorio: misma lógica de versiones/changelog que móvil y TV. */
object AppUpdates {

    /** En desarrollo (`gradlew run`) no hay `jpackage.app-path` y no se puede instalar el MSI. */
    val puedeInstalar: Boolean =
        System.getProperty("jpackage.app-path")?.isNotBlank() == true

    val current: UpdateController by lazy {
        val version = AppVersion.parse(System.getProperty("xuper.version") ?: "") ?: AppVersion(0, 0, 0)
        UpdateController(
            client = UpdateClient(NetProvider.httpEngine),
            channel = UpdateChannel.DESKTOP,
            currentVersion = version,
            downloader = DesktopUpdateDownloader(),
        )
    }
}
