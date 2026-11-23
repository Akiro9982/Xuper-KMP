package com.xuperclean.core

import kotlin.random.Random

/**
 * Identidad de terminal por instalación.
 *
 * Hallazgo del análisis del cliente NetXus (ver skill `references/nexus-protocol.md`):
 * cada instalación genera su propio `snToken` y deriva de él un SN distinto, así que
 * varias instalaciones coexisten sin desalojarse (el portal asigna un `userId`/`userToken`
 * a cada SN). El SN NO es aleatorio: es `md5(snToken + SALT)`.
 */
public data class PortalIdentity(
    public val sn: String,
    public val macAddr: String,
    public val androidId: String,
    public val snToken: String,
    /** Contador de sesiones/huecos de este terminal. Crece en cada renovación. */
    public val openNum: Int = 0,
    public val userId: String = "",
    public val userToken: String = "",
) {
    public val registered: Boolean get() = snToken.isNotEmpty()
}

/** Persistencia de la identidad (la app la guarda en sus prefs). */
public interface IdentityStore {
    public fun load(): PortalIdentity?
    public fun save(identity: PortalIdentity)
}

public object IdentityFactory {

    /** Sal del algoritmo de derivación de SN del cliente oficial. */
    public const val SN_SALT: String = "ntFT65w6itH!lHCPw7D=@qnsFC5adD28"

    /** Identidad nueva (aún sin registrar): SN derivado en cuanto llega el snToken. */
    public fun fresh(random: Random = Random.Default): PortalIdentity = PortalIdentity(
        sn = "",
        macAddr = (0 until 6).joinToString(":") { hex(random, 2).uppercase() },
        androidId = hex(random, 16),
        snToken = "",
    )

    /** SN del servidor para un `snToken`: `md5(snToken + SN_SALT)`. */
    public fun snFromToken(snToken: String): String = md5((snToken + SN_SALT).encodeToByteArray()).hex()

    public fun hex(random: Random, chars: Int): String =
        (0 until chars).joinToString("") { HEX[random.nextInt(HEX.length)].toString() }

    public fun randomHex(chars: Int, random: Random = Random.Default): String = hex(random, chars)

    private const val HEX = "0123456789abcdef"
}

public fun ByteArray.hex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append("0123456789abcdef"[v shr 4])
        sb.append("0123456789abcdef"[v and 0xF])
    }
    return sb.toString()
}

/** MD5 en Kotlin común (solo se usa para derivar el SN, no para criptografía de red). */
public fun md5(input: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val k = IntArray(64) { kotlin.math.floor(kotlin.math.abs(kotlin.math.sin(it + 1.0)) * 4294967296.0).toLong().toInt() }

    val bitLen = input.size.toLong() * 8
    val paddedSize = ((input.size + 8) / 64 + 1) * 64
    val msg = ByteArray(paddedSize)
    input.copyInto(msg)
    msg[input.size] = 0x80.toByte()
    for (i in 0 until 8) msg[paddedSize - 8 + i] = ((bitLen ushr (8 * i)) and 0xFF).toByte()

    var a0 = 0x67452301
    var b0 = 0xEFCDAB89.toInt()
    var c0 = 0x98BADCFE.toInt()
    var d0 = 0x10325476

    val m = IntArray(16)
    var off = 0
    while (off < paddedSize) {
        for (i in 0 until 16) {
            val base = off + i * 4
            m[i] = (msg[base].toInt() and 0xFF) or
                ((msg[base + 1].toInt() and 0xFF) shl 8) or
                ((msg[base + 2].toInt() and 0xFF) shl 16) or
                ((msg[base + 3].toInt() and 0xFF) shl 24)
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0
        for (i in 0 until 64) {
            var f: Int
            var g: Int
            when (i / 16) {
                0 -> { f = (b and c) or (b.inv() and d); g = i }
                1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }
            val tmp = d
            d = c
            c = b
            val x = a + f + k[i] + m[g]
            b = b + ((x shl s[i]) or (x ushr (32 - s[i])))
            a = tmp
        }
        a0 += a; b0 += b; c0 += c; d0 += d
        off += 64
    }

    val out = ByteArray(16)
    val words = intArrayOf(a0, b0, c0, d0)
    for (i in 0 until 4) {
        out[i * 4] = (words[i] and 0xFF).toByte()
        out[i * 4 + 1] = ((words[i] ushr 8) and 0xFF).toByte()
        out[i * 4 + 2] = ((words[i] ushr 16) and 0xFF).toByte()
        out[i * 4 + 3] = ((words[i] ushr 24) and 0xFF).toByte()
    }
    return out
}
