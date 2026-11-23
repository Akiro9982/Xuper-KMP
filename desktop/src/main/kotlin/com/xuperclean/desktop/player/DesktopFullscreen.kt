package com.xuperclean.desktop.player

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Component
import java.awt.Window

/**
 * Pantalla completa real en Windows.
 *
 * `GraphicsDevice.fullScreenWindow` y `WindowPlacement.Fullscreen` dejan la barra de
 * título y la barra de tareas visibles con el motor de vídeo embebido (HWND hijo).
 * Aquí se cambia el estilo nativo a `WS_POPUP`, se cubre el monitor completo y se
 * restaura el estilo/posición exactos al salir. En otros sistemas se usa AWT.
 */
object DesktopFullscreen {

    private data class Snapshot(
        val hwnd: WinDef.HWND,
        val style: Int,
        val bounds: WinDef.RECT,
    )

    private var snapshot: Snapshot? = null

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** Entra en pantalla completa; devuelve false si no se pudo aplicar. */
    fun enter(window: Window, videoCanvas: Component?): Boolean {
        if (!isWindows) return enterAwt(window)
        val hwnd = rootHwnd(videoCanvas) ?: return enterAwt(window)
        val user32 = User32.INSTANCE
        val style = user32.GetWindowLong(hwnd, GWL_STYLE)
        val bounds = WinDef.RECT().also { user32.GetWindowRect(hwnd, it) }
        val monitor = user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return false
        val info = WinUser.MONITORINFO().also { it.cbSize = it.size() }
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false

        snapshot = Snapshot(hwnd, style, bounds)
        val chrome = WS_CAPTION or WS_THICKFRAME or WS_SYSMENU or
            WS_MINIMIZEBOX or WS_MAXIMIZEBOX
        user32.SetWindowLong(hwnd, GWL_STYLE, (style and chrome.inv()) or WS_POPUP)
        val area = info.rcMonitor
        val width = area.right - area.left
        val height = area.bottom - area.top
        val flags = WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
        user32.SetWindowPos(hwnd, TOPMOST, area.left, area.top, width, height, flags)
        return true
    }

    /** Restaura el estilo y los límites previos. */
    fun exit(window: Window) {
        val saved = snapshot
        if (saved == null) {
            exitAwt(window)
            return
        }
        snapshot = null
        val user32 = User32.INSTANCE
        user32.SetWindowLong(saved.hwnd, GWL_STYLE, saved.style)
        val b = saved.bounds
        user32.SetWindowPos(
            saved.hwnd,
            NOTOPMOST,
            b.left,
            b.top,
            b.right - b.left,
            b.bottom - b.top,
            WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW,
        )
    }

    /** Libera el estado si la ventana se cierra estando en pantalla completa. */
    fun release(window: Window) {
        if (snapshot != null) exit(window)
    }

    private fun enterAwt(window: Window): Boolean = runCatching {
        val device = window.graphicsConfiguration?.device ?: return false
        device.fullScreenWindow = window
        true
    }.getOrDefault(false)

    private fun exitAwt(window: Window) {
        runCatching {
            val device = window.graphicsConfiguration?.device ?: return
            if (device.fullScreenWindow === window) device.fullScreenWindow = null
        }
    }

    private fun rootHwnd(component: Component?): WinDef.HWND? {
        if (component == null || !component.isDisplayable) return null
        return runCatching {
            val pointer = Native.getComponentPointer(component) ?: return null
            User32.INSTANCE.GetAncestor(WinDef.HWND(pointer), WinUser.GA_ROOT)
        }.getOrNull()
    }

    private val TOPMOST = WinDef.HWND(Pointer.createConstant(-1))
    private val NOTOPMOST = WinDef.HWND(Pointer.createConstant(-2))

    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val WS_POPUP = 0x80000000.toInt()
    private const val MONITOR_DEFAULTTONEAREST = 2
}
