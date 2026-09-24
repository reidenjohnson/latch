package com.latch.nfc

import java.math.BigInteger

/**
 * Verifies an NXP originality signature (READ_SIG, command 3Ch): ECDSA on curve secp128r1 over the raw 7-byte UID,
 * with signature = r (16 bytes) || s (16 bytes).
 *
 * Curve parameters and public keys match node-nxp-originality-verifier (which implements NXP AN11350):
 * https://github.com/alexbatalov/node-nxp-originality-verifier
 * The NTAG/Ultralight key is also the one quoted on the NXP community forum:
 * https://community.nxp.com/t5/NFC/Using-Easy-Ecc-to-Verify-Originality-Signature-for-NTAG213/m-p/1065325
 */
object Originality {
    private val TWO = BigInteger.valueOf(2)
    private val P = BigInteger("fffffffdffffffffffffffffffffffff", 16)
    private val A = BigInteger("fffffffdfffffffffffffffffffffffc", 16)
    private val N = BigInteger("fffffffe0000000075a30d1b9038a115", 16)
    private val G = Point(BigInteger("161ff7528b899b2d0c28607ca52c5b86", 16), BigInteger("cf5ac8395bafeb13c02da292dded7a83", 16))

    /** NXP keys: "MIFARE UL and NTAG" and "MIFARE UL EV1" (from the repo above). */
    private val KEYS = listOf(
        "04494e1a386d3d3cfe3dc10e5de68a499b1c202db5b132393e89ed19fe5be8bc61",
        "0490933bdcd6e99b4e255e3da55389a827564e11718e017292faf23226a96614b8",
    ).map(::decodeKey)

    fun isGenuine(uid: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 32 || signature.all { it == 0.toByte() }) return false
        val r = BigInteger(1, signature.copyOfRange(0, 16))
        val s = BigInteger(1, signature.copyOfRange(16, 32))
        val e = BigInteger(1, uid) // 56-bit message is shorter than n, so no truncation is needed.
        return KEYS.any { verify(e, r, s, it) }
    }

    private fun verify(e: BigInteger, r: BigInteger, s: BigInteger, q: Point): Boolean {
        if (r.signum() <= 0 || r >= N || s.signum() <= 0 || s >= N) return false
        val w = s.modInverse(N)
        val u1 = e.multiply(w).mod(N)
        val u2 = r.multiply(w).mod(N)
        val x = add(mul(G, u1), mul(q, u2)) ?: return false
        return x.x.mod(N) == r
    }

    private data class Point(val x: BigInteger, val y: BigInteger)

    private fun decodeKey(hex: String): Point {
        val b = BigInteger(hex, 16).toByteArray().takeLast(32).toByteArray()
        return Point(BigInteger(1, b.copyOfRange(0, 16)), BigInteger(1, b.copyOfRange(16, 32)))
    }

    // Affine arithmetic; null represents the point at infinity.
    private fun add(p: Point?, q: Point?): Point? {
        if (p == null) return q
        if (q == null) return p
        if (p.x == q.x) {
            if ((p.y + q.y).mod(P).signum() == 0) return null
            return double(p)
        }
        val l = (q.y - p.y).multiply((q.x - p.x).modInverse(P)).mod(P)
        val x = (l * l - p.x - q.x).mod(P)
        return Point(x, (l * (p.x - x) - p.y).mod(P))
    }

    private fun double(p: Point): Point? {
        if (p.y.signum() == 0) return null
        val l = (BigInteger.valueOf(3) * p.x * p.x + A).multiply((TWO * p.y).modInverse(P)).mod(P)
        val x = (l * l - TWO * p.x).mod(P)
        return Point(x, (l * (p.x - x) - p.y).mod(P))
    }

    private fun mul(p: Point, k: BigInteger): Point? {
        var result: Point? = null
        var addend: Point? = p
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) result = add(result, addend)
            addend = addend?.let(::double)
        }
        return result
    }
}
