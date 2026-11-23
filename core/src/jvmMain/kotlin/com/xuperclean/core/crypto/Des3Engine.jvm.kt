package com.xuperclean.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 3DES/ECB sobre JVM (Android incluido). Sin padding: el PKCS5 lo aplica XuperCrypto.
 * DESede está disponible en todos los Android API 19+.
 */
public actual object Des3Engine {
    private const val TRANSFORMATION = "DESede/ECB/NoPadding"

    public actual fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DESede"))
        return cipher.doFinal(data)
    }

    public actual fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "DESede"))
        return cipher.doFinal(data)
    }
}
