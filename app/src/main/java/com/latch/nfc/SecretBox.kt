package com.latch.nfc

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted notes stored on a tag. AES-256-GCM, key from PBKDF2-HMAC-SHA256.
 * Iterations follow the OWASP Password Storage Cheat Sheet (600,000 for PBKDF2-HMAC-SHA256):
 * https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
 *
 * Payload layout: version(1) | iterations(4, big-endian) | salt(16) | iv(12) | ciphertext+tag.
 */
object SecretBox {
    const val TYPE = "latch.app:secret"
    const val DEFAULT_ITERATIONS = 600_000
    private const val VERSION: Byte = 1

    class WrongPassword : Exception("Wrong password")

    fun seal(plain: String, password: String, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return byteArrayOf(VERSION) + intBytes(iterations) + salt + iv + ct
    }

    fun open(payload: ByteArray, password: String): String {
        require(payload.size > 1 + 4 + 16 + 12 + 16 && payload[0] == VERSION) { "Not a Latch secret" }
        val iterations = ((payload[1].toInt() and 0xFF) shl 24) or ((payload[2].toInt() and 0xFF) shl 16) or
            ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        val salt = payload.copyOfRange(5, 21)
        val iv = payload.copyOfRange(21, 33)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        return try {
            String(cipher.doFinal(payload.copyOfRange(33, payload.size)), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
