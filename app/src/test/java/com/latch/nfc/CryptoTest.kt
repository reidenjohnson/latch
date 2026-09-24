package com.latch.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalityTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // Test vector from node-nxp-originality-verifier's README (a real NTAG UID + signature).
    // https://github.com/alexbatalov/node-nxp-originality-verifier
    private val uid = hex("04ee45daa34084")
    private val sig = hex("ebb6102bff74b087d18a57a54bc375159a04ea9bc61080b7f4a85afe1587d73b")

    @Test fun genuineSignatureVerifies() = assertTrue(Originality.isGenuine(uid, sig))

    @Test fun tamperedUidFails() {
        val other = uid.copyOf().also { it[6] = (it[6] + 1).toByte() }
        assertFalse(Originality.isGenuine(other, sig))
    }

    @Test fun tamperedSignatureFails() {
        val bad = sig.copyOf().also { it[31] = (it[31].toInt() xor 1).toByte() }
        assertFalse(Originality.isGenuine(uid, bad))
    }

    @Test fun zeroSignatureFails() = assertFalse(Originality.isGenuine(uid, ByteArray(32)))
}

class SecretBoxTest {
    @Test fun roundTrip() {
        val sealed = SecretBox.seal("garage code 4471", "hunter22", iterations = 1000)
        assertEquals("garage code 4471", SecretBox.open(sealed, "hunter22"))
    }

    @Test(expected = SecretBox.WrongPassword::class)
    fun wrongPasswordRejected() {
        SecretBox.open(SecretBox.seal("x", "right", iterations = 1000), "wrong")
    }

    @Test fun defaultUsesOwaspIterations() {
        val sealed = SecretBox.seal("x", "pw", iterations = SecretBox.DEFAULT_ITERATIONS)
        val stored = ((sealed[1].toInt() and 0xFF) shl 24) or ((sealed[2].toInt() and 0xFF) shl 16) or
            ((sealed[3].toInt() and 0xFF) shl 8) or (sealed[4].toInt() and 0xFF)
        assertEquals(600_000, stored)
    }
}
