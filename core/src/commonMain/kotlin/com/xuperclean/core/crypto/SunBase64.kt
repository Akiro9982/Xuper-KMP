package com.xuperclean.core.crypto

/**
 * Base64 "sun" de la app Xuper (clases fc.b / cc.j / dc.a).
 *
 * NO es el base64 estándar: recorre átomos de 4 y, para caracteres fuera del alfabeto,
 * usa el valor 63 en vez de descartarlos. Es imprescindible para la clave runtime,
 * que contiene `-` (vale 63).
 */
public object SunBase64 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    public fun decode(s: String): ByteArray {
        val table = HashMap<Char, Int>(ALPHABET.length)
        ALPHABET.forEachIndexed { i, c -> table[c] = i }
        val out = ArrayList<Byte>(s.length * 3 / 4 + 3)
        var i = 0
        while (i < s.length) {
            val end = minOf(i + 4, s.length)
            val chunk = s.substring(i, end)
            i = end
            var rem = chunk.length
            if (rem == 4 && chunk[3] == '=') rem = 3
            if (rem >= 3 && chunk[2] == '=') rem = 2
            val v = IntArray(4)
            for (k in 0 until 4) {
                v[k] = if (k < rem) (table[chunk[k]] ?: 63) else 0
            }
            out.add((((v[0] shl 2) or (v[1] shr 4)) and 0xFF).toByte())
            if (rem >= 3) out.add(((((v[1] and 0xF) shl 4) or (v[2] shr 2)) and 0xFF).toByte())
            if (rem >= 4) out.add(((((v[2] and 3) shl 6) or (v[3] and 0x3F)) and 0xFF).toByte())
        }
        return out.toByteArray()
    }

    public fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    (data[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[(n shr 18) and 0x3F])
            sb.append(ALPHABET[(n shr 12) and 0x3F])
            sb.append(ALPHABET[(n shr 6) and 0x3F])
            sb.append(ALPHABET[n and 0x3F])
            i += 3
        }
        val rest = data.size - i
        if (rest == 1) {
            val n = (data[i].toInt() and 0xFF) shl 16
            sb.append(ALPHABET[(n shr 18) and 0x3F])
            sb.append(ALPHABET[(n shr 12) and 0x3F])
            sb.append("==")
        } else if (rest == 2) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
            sb.append(ALPHABET[(n shr 18) and 0x3F])
            sb.append(ALPHABET[(n shr 12) and 0x3F])
            sb.append(ALPHABET[(n shr 6) and 0x3F])
            sb.append("=")
        }
        return sb.toString()
    }
}
