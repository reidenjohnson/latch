package com.latch.focus.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The optional unlock passcode, stored only as a salted PBKDF2-HMAC-SHA256 hash ("salt:hash", base64), never as
 * the digits themselves. PBKDF2 is in the standard Java crypto provider on Android and the JVM:
 * https://developer.android.com/reference/javax/crypto/SecretKeyFactory
 */
object Passcode {
    private const val ITERATIONS = 120_000
    private const val BITS = 256
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValid(code: String) = code.length in MIN_LENGTH..MAX_LENGTH && code.all { it.isDigit() }

    fun hash(code: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b64(salt) + ":" + b64(derive(code, salt))
    }

    fun matches(code: String, stored: String?): Boolean {
        val (salt, hash) = stored?.split(":")?.takeIf { it.size == 2 } ?: return false
        val expected = runCatching { Base64.getDecoder().decode(hash) }.getOrNull() ?: return false
        val actual = derive(code, runCatching { Base64.getDecoder().decode(salt) }.getOrNull() ?: return false)
        return MessageDigest.isEqual(expected, actual) // constant-time compare
    }

    private fun derive(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, BITS)).encoded

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}
