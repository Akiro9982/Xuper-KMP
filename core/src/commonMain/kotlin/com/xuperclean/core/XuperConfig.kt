package com.xuperclean.core

/**
 * ConfiguraciÃ³n del cliente portal. Valores por defecto = los observados y verificados
 * (config.json del bridge / iptv-api.md Â§2).
 */
public data class XuperConfig(
    /** Serial number del dispositivo: LA credencial (la fija la identidad registrada). */
    var sn: String,
    /** Hosts del portal (rotan por DCS; el cliente prueba en orden). */
    var portalHosts: List<String> = listOf(
        "eajmnp.hcgv1dt8.com",
        "krdwvd.swcaw7qx.com",
    ),
    /** build interno del APK en la cabecera apkVer (NO el versionCode). */
    var apkVerHeader: String = "43404",
    /** Versión APK declarada en los bodies (la que usa el build aceptado). */
    var apkVersion: String = "43665",
    val appId: String = "com.android.msandroid",
    val language: String = "es",
    /** Columna del live (getLiveData: todos los canales). */
    val liveColumnId: Long = 76182,
    /** RaÃ­z del Ã¡rbol LIVE; sus hijos son las categorÃ­as (iptv-api.md Â§14). */
    val liveTreeColumnId: Long = 76175,
    /** Columna raÃ­z VOD (necesaria en startPlayVOD de series). */
    val vodRootColumnId: Long = 87026,
    var reserve1: String = "415a7a38593161756952496a4c774b665541477051513d3d",
    var macAddr: String = "02:00:00:00:00:00",
    var sysVersion: String = "2022-05-25 14:15:32_28_9_4.19.71+",
    val hardwareInfo: String = "Samsung",
    val model: String = "SM-S908N",
    val product: String = "b0qksx",
    val cpu: String = "arm64-v8a",
    var sdkVer: Int = 28,
    /** Minutos entre refrescos preventivos de sesiÃ³n (los tokens viven ~4 h). */
    val refreshMinutes: Long = 30,

    /** JSON remoto con valores de conexiÃ³n si el portal rota algo (vacÃ­o = desactivado). */
    /** Perfil remoto (GitHub Pages): permite corregir build/clave sin actualizar la app. */
    var profileUrl: String = "https://akiro9982.github.io/xuper-profile/profile.json",

    // ---- Identidad/sesión por terminal (sistema del cliente NetXus) ----
    /** Clave del build aceptado en hex crudo (24 bytes): no exige descargar APKs. */
    var portalKey: String = "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8",
    /** Registro de terminal: otro host/app/clave del propio proveedor. */
    var registrationHost: String = "eaeylu.bvsvfb8c.com",
    var registrationKey: String = "e9c7f6e7cf5bfdddb57bfe1f6dfffc6dfdbf777e3adfae1b",
    var registrationAppId: String = "com.msandroid.mobile",
    var registrationApkVersion: String = "60291",
    var registrationSpkgVer: String = "9",
    var registrationSysVersion: String = "9",
    /** Cabecera `spkgVer` de las peticiones al portal. */
    var spkgVer: String = "2022-05-25 14:15:32_28_9_4.19.71+",
    /** `openNum` inicial del terminal (el cliente oficial arranca en 600). */
    var openNumInicial: Int = 600,

    /** Sondas mÃ¡ximas de la primera pasada (la segunda multiplica por RECOVERY_SEGUNDA_PASADA). */
    var versionRecoveryProbes: Int = 1200,

    /**
     * Sondas de la pasada CORTA de recuperación (Vía C): se intenta antes de tocar el
     * perfil remoto y antes del barrido largo, para resolver en pocas peticiones la
     * puerta de versión normal (build retirado cerca del actual).
     */
    var versionRecoveryShortProbes: Int = 64,
) {
    public companion object {
        /** Sign key del manifest usada para derivar PORTAL_KEY (referencia). */
        public const val SIGN_KEY: String = "*&@!6d5d-c483-4720-bb29-785b8f321c^%"
    }
}


