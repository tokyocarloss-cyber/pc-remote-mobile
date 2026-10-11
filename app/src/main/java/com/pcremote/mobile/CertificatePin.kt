package com.pcremote.mobile

import java.security.MessageDigest

object CertificatePin {
    fun digest(encoded: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02X".format(it) }
    fun matches(encoded: ByteArray, expected: String): Boolean = expected.length == 64 &&
        MessageDigest.isEqual(digest(encoded).toByteArray(Charsets.US_ASCII), expected.toByteArray(Charsets.US_ASCII))
}
