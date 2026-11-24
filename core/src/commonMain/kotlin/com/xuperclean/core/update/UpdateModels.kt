package com.xuperclean.core.update

/**
 * Canales de actualización. Cada uno tiene su propio prefijo de etiqueta en GitHub
 * (`mobile-v0.2.0`, `tv-v0.2.0`, `desktop-v0.2.0`), de modo que una versión publicada
 * para una plataforma no aparece como actualización en las demás.
 */
public enum class UpdateChannel(
    public val id: String,
    public val tagPrefix: String,
    public val label: String,
    public val assetExtension: String,
) {
    DESKTOP("desktop", "desktop-v", "Escritorio", ".msi"),
    MOBILE("mobile", "mobile-v", "Android", ".apk"),
    TV("tv", "tv-v", "Android TV", ".apk"),
    ;

    public companion object {
        public fun fromId(id: String): UpdateChannel? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** Versión semántica estable (major.minor.patch). */
public data class AppVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
) : Comparable<AppVersion> {

    public val name: String get() = "$major.$minor.$patch"

    /** Debe coincidir con la fórmula de app-mobile/build.gradle.kts y app-tv/build.gradle.kts. */
    public val versionCode: Int
        get() = major * 1_000_000 + minor * 1_000 + patch

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = name

    public companion object {
        private val STABLE = Regex("""^(\d{1,4})\.(\d{1,4})\.(\d{1,4})$""")

        /** Acepta `0.2.0` y `v0.2.0`; las pre-versiones no se usan en los canales estables. */
        public fun parse(text: String): AppVersion? {
            val limpio = text.trim().removePrefix("v")
            val m = STABLE.matchEntire(limpio) ?: return null
            return AppVersion(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = m.groupValues[3].toInt(),
            )
        }

        /** Extrae la versión de una etiqueta con prefijo de canal: `mobile-v0.2.0`. */
        public fun fromTag(tag: String, prefix: String): AppVersion? =
            if (tag.startsWith(prefix)) parse(tag.removePrefix(prefix)) else null
    }
}

/** Archivo descargable de un release (APK o MSI). */
public data class UpdateAsset(
    public val name: String,
    public val downloadUrl: String,
    public val size: Long,
    /** SHA-256 en minúsculas, o null si el release no lo publica. */
    public val sha256: String?,
) {
    public fun sizeLabel(): String = formatBytes(size)
}

/** Release más reciente de un canal, ya normalizado para la app. */
public data class AppUpdate(
    public val channel: UpdateChannel,
    public val version: AppVersion,
    public val tag: String,
    public val title: String,
    public val notes: String,
    public val publishedAt: String,
    public val releaseUrl: String,
    public val asset: UpdateAsset,
) {
    public fun publishedDateLabel(): String {
        val fecha = publishedAt.take(10)
        val partes = fecha.split('-')
        return if (partes.size == 3 && partes.all { it.isNotEmpty() }) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else {
            publishedAt
        }
    }
}

/** Resultado de consultar GitHub Releases para un canal. */
public data class UpdateCheck(
    public val channel: UpdateChannel,
    public val current: AppVersion,
    public val latest: AppUpdate?,
    public val isNewer: Boolean,
)

public fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) {
        val decena = (mb * 10).toLong()
        val entero = decena / 10
        val decimal = decena % 10
        return if (decimal == 0L) "$entero MB" else "$entero,$decimal MB"
    }
    val kb = (bytes / 1024L).coerceAtLeast(1L)
    return "$kb KB"
}
