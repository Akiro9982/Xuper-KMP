package com.xuperclean.core.crypto

/**
 * Cripto del portal Xuper (verificada byte a byte contra la app, iptv-api.md §2).
 *
 * - Clave: 24 bytes derivados con base64-sun de la cadena de clave.
 * - Request  = hex( ascii( base64( 3DES-ECB-PKCS5(json) ) ) )
 * - Response = inverso.
 */
public object XuperCrypto {

    /** Deriva la clave de 24 bytes a partir de la cadena de clave.
     *
     * Acepta dos formatos, ambos usados por el cliente oficial:
     *  - 48 caracteres hex = clave 3DES cruda (formato actual, p. ej. `e7af1ed7…`).
     *  - cadena normal = base64-sun (formato heredado, p. ej. un UUID).
     */
    public fun deriveKey(keyString: String): ByteArray {
        if (keyString.length == 48 && keyString.all { it.isHexDigit() }) {
            return hexToBytes(keyString)
        }
        val decoded = SunBase64.decode(keyString)
        return decoded.copyOf(24)
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    public fun encryptRequest(json: String, keyString: String): String {
        val key = deriveKey(keyString)
        val plain = json.encodeToByteArrayUtf8()
        val padded = padPkcs5(plain, 8)
        val ct = Des3Engine.encrypt(key, padded)
        // hex del ascii del base64 estándar de los bytes cifrados
        val b64 = SunBase64.encode(ct)
        val sb = StringBuilder(b64.length * 2)
        for (c in b64) {
            sb.append(HEX[(c.code shr 4) and 0xF])
            sb.append(HEX[c.code and 0xF])
        }
        return sb.toString()
    }

    public fun decryptResponse(hex: String, keyString: String): String {
        val key = deriveKey(keyString)
        val ascii = hexToBytes(hex).decodeToString() // ascii del base64
        val raw = SunBase64.decode(ascii)
        val decrypted = Des3Engine.decrypt(key, raw)
        return unpadPkcs5(decrypted).decodeToString()
    }

    // ---- helpers sin dependencias ----

    private const val HEX = "0123456789abcdef"

    public fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexVal(hex[i * 2].code)
            val lo = hexVal(hex[i * 2 + 1].code)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexVal(c: Int): Int = when (c) {
        in 48..57 -> c - 48        // 0-9
        in 97..102 -> c - 87       // a-f
        in 65..70 -> c - 55        // A-F
        else -> throw IllegalArgumentException("hex inválido: $c")
    }

    public fun padPkcs5(data: ByteArray, block: Int): ByteArray {
        val padLen = block - (data.size % block)
        val out = data.copyOf(data.size + padLen)
        for (i in data.size until out.size) out[i] = padLen.toByte()
        return out
    }

    public fun unpadPkcs5(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val padLen = data[data.size - 1].toInt() and 0xFF
        require(padLen in 1..data.size) { "padding PKCS5 inválido" }
        return data.copyOf(data.size - padLen)
    }
}

/** UTF-8 sin dependencias de plataforma. */
public fun String.encodeToByteArrayUtf8(): ByteArray = encodeToByteArray()
