package com.xuperclean.core.update

/**
 * Convierte el Markdown del release de GitHub en texto legible para Compose.
 *
 * La app no incluye un motor Markdown: mostrar el cuerpo tal cual llenaría la pantalla
 * de `**`, `###` y enlaces crudos. Aquí se conserva la estructura (títulos, viñetas y
 * saltos de línea) y se eliminan los marcadores, sin dependencias nuevas.
 */
public object ReleaseNotes {

    private val COMENTARIO_HTML = Regex("""<!--[\s\S]*?-->""")
    private val ETIQUETA_HTML = Regex("""<[^>]+>""")
    private val IMAGEN = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
    private val ENLACE = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val TITULO = Regex("""(?m)^[ \t]*#{1,6}[ \t]*(.+)$""")
    private val VINETA = Regex("""(?m)^[ \t]*[-*+][ \t]+(?=\S)""")
    private val REGLA = Regex("""(?m)^[ \t]*([-*_])\1{2,}[ \t]*$""")
    private val CITA = Regex("""(?m)^[ \t]*>[ \t]?""")
    private val CERCA_CODIGO = Regex("""(?m)^[ \t]*```[^\n]*$""")
    private val CODIGO = Regex("""`([^`\n]*)`""")
    private val NEGRITA = Regex("""\*\*([^*\n]+)\*\*""")
    private val NEGRITA_BAJA = Regex("""__([^_\n]+)__""")
    private val TACHADO = Regex("""~~([^~\n]+)~~""")
    private val CURSIVA = Regex("""(?<![\w*])\*([^*\n]+)\*(?![\w*])""")
    private val CURSIVA_BAJA = Regex("""(?<!\w)_([^_\n]+)_(?!\w)""")
    private val SALTOS = Regex("""\n{3,}""")

    public fun clean(markdown: String): String {
        var texto = markdown.replace("\r\n", "\n").replace('\r', '\n')
        texto = texto.replace(COMENTARIO_HTML, "")
        texto = texto.replace(ETIQUETA_HTML, "")
        texto = texto.replace(IMAGEN, "$1")
        texto = texto.replace(ENLACE, "$1 ($2)")
        texto = texto.replace(TITULO, "$1")
        texto = texto.replace(VINETA, "• ")
        texto = texto.replace(REGLA, "")
        texto = texto.replace(CITA, "")
        texto = texto.replace(CERCA_CODIGO, "")
        texto = texto.replace(CODIGO, "$1")
        texto = texto.replace(NEGRITA, "$1")
        texto = texto.replace(NEGRITA_BAJA, "$1")
        texto = texto.replace(TACHADO, "$1")
        texto = texto.replace(CURSIVA, "$1")
        texto = texto.replace(CURSIVA_BAJA, "$1")
        texto = SALTOS.replace(texto, "\n\n")
        return texto.trim()
    }
}
