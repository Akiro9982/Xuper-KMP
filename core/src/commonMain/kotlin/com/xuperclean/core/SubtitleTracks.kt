package com.xuperclean.core

import com.xuperclean.core.model.Subtitle

/**
 * Pista de subtítulo lista para el reproductor.
 *
 * El portal entrega `subtitleList` por episodio con uno o varios ficheros por idioma
 * (normalmente `vtt` y `srt`); aquí se elige el formato más compatible y se expone
 * el MIME que esperan Media3 y mpv.
 */
public data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String,
    val mimeType: String,
)

public object SubtitleTracks {

    private val Nombres = mapOf(
        "es" to "Español",
        "spa" to "Español",
        "en" to "Inglés",
        "eng" to "Inglés",
        "pt" to "Portugués",
        "por" to "Portugués",
        "fr" to "Francés",
        "fra" to "Francés",
        "de" to "Alemán",
        "deu" to "Alemán",
        "it" to "Italiano",
        "ita" to "Italiano",
        "ja" to "Japonés",
        "jpn" to "Japonés",
        "ko" to "Coreano",
        "kor" to "Coreano",
        "zh" to "Chino",
        "zho" to "Chino",
    )

    /** Nombre legible del idioma; el código se muestra tal cual si no se conoce. */
    public fun languageLabel(code: String?): String {
        val limpio = code?.trim()?.lowercase()?.substringBefore('-') ?: return "Desconocido"
        return Nombres[limpio] ?: code.trim().ifEmpty { "Desconocido" }
    }

    /** MIME a partir del tipo declarado o de la extensión de la URL. */
    public fun mimeOf(hint: String?): String? {
        val valor = hint?.lowercase()?.substringAfterLast('.') ?: return null
        return when {
            "vtt" in valor || valor == "webvtt" -> "text/vtt"
            "srt" in valor || "subrip" in valor -> "application/x-subrip"
            valor == "ass" || valor == "ssa" -> "text/x-ssa"
            "ttml" in valor || valor == "dfxp" -> "application/ttml+xml"
            else -> null
        }
    }

    /** Aplana la lista del portal, prefiere vtt > srt > ass y descarta duplicados. */
    public fun from(subtitles: List<Subtitle>): List<SubtitleTrack> {
        val preferencia = listOf("text/vtt", "application/x-subrip", "text/x-ssa", "application/ttml+xml")
        return subtitles.mapNotNull { sub ->
            val elegido = sub.file
                .mapNotNull { f ->
                    val mime = mimeOf(f.fileType) ?: mimeOf(f.url) ?: return@mapNotNull null
                    Triple(mime, f.url, preferencia.indexOf(mime).let { if (it < 0) 99 else it })
                }
                .minByOrNull { it.third }
                ?: return@mapNotNull null
            val code = sub.language?.trim().orEmpty()
            SubtitleTrack(
                language = code,
                label = languageLabel(code),
                url = elegido.second,
                mimeType = elegido.first,
            )
        }.distinctBy { it.language.lowercase() to it.url }
    }
}
