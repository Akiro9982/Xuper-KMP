package com.xuperclean.core.crypto

/** Primitiva 3DES/ECB sin padding (el padding PKCS5 se aplica en XuperCrypto). */
public expect object Des3Engine {
    public fun encrypt(key: ByteArray, data: ByteArray): ByteArray
    public fun decrypt(key: ByteArray, data: ByteArray): ByteArray
}
