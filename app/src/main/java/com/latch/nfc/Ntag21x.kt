package com.latch.nfc

import android.nfc.NdefMessage
import android.nfc.tech.NfcA
import java.io.IOException
import java.security.MessageDigest

/**
 * Raw NTAG213/215/216 commands over NfcA. Every command code, page address and bit position below comes from the
 * NXP NTAG213/215/216 datasheet Rev. 3.2: https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf
 *   GET_VERSION 60h (Table 26/28) · READ 30h (Table 29) · WRITE A2h · READ_CNT 39h 02h (Table 37)
 *   PWD_AUTH 1Bh (Table 39) · READ_SIG 3Ch 00h (Table 41)
 *   Config pages (Table 8): CFG0 = MIRROR | RFUI | MIRROR_PAGE | AUTH0, CFG1 = ACCESS | RFUI…, then PWD, PACK.
 *   MIRROR byte (Table 9): bits 7-6 MIRROR_CONF, 5-4 MIRROR_BYTE, 2 STRG_MOD_EN.
 *   ACCESS byte (Table 10): bit 7 PROT, 6 CFGLCK, 4 NFC_CNT_EN, 3 NFC_CNT_PWD_PROT, 2-0 AUTHLIM.
 */
enum class NtagChip(val label: String, val cfgPage: Int, val ccBytes: Int) {
    // cfgPage: 29h/83h/E3h (§8.5.7). ccBytes: NDEF area from the CC (Table 4).
    NTAG213("NTAG213", 0x29, 144),
    NTAG215("NTAG215", 0x83, 496),
    NTAG216("NTAG216", 0xE3, 872);

    val lastPage get() = cfgPage + 3            // 2Ch / 86h / E6h: last READ address (§10.2)
    val lastUserPage get() = cfgPage - 2        // page before the dynamic lock bytes
    val accessPage get() = cfgPage + 1
    val pwdPage get() = cfgPage + 2
    val packPage get() = cfgPage + 3

    companion object {
        fun fromVersion(v: ByteArray): NtagChip? {
            if (v.size < 8 || v[1] != 0x04.toByte() || v[2] != 0x04.toByte()) return null
            return when (v[6].toInt()) { 0x0F -> NTAG213; 0x11 -> NTAG215; 0x13 -> NTAG216; else -> null }
        }
    }
}

data class NtagConfig(val mirror: Int, val mirrorPage: Int, val auth0: Int, val access: Int, val lastPage: Int) {
    /** §8.5.7: an AUTH0 above the last page means protection is effectively off. */
    val passwordProtected get() = auth0 <= lastPage
    val readProtected get() = passwordProtected && (access and 0x80) != 0
    val mirrorEnabled get() = mirrorPage > 3 && (mirror shr 6) != 0
    val counterEnabled get() = (access and 0x10) != 0
    val counterProtected get() = (access and 0x08) != 0
    val configLocked get() = (access and 0x40) != 0
}

data class NtagInfo(
    val chip: NtagChip,
    val config: NtagConfig?,
    val counter: Int?,
    val genuine: Boolean?,
)

enum class Mirror(val label: String, val conf: Int, val length: Int) {
    Id("Tag ID", 0b01, 14),
    Count("Scan count", 0b10, 6),
    Both("Tag ID + scan count", 0b11, 21); // UID(14) + 'x' + counter(6), §8.7.3

    val placeholder: String get() = "0".repeat(length)
}

class Ntag21x(private val a: NfcA) {

    fun version(): ByteArray = a.transceive(byteArrayOf(0x60))

    /** READ 30h: 16 bytes = 4 pages starting at [page]. */
    fun read(page: Int): ByteArray = a.transceive(byteArrayOf(0x30, page.toByte()))

    /** WRITE A2h. The tag answers a 4-bit ACK (Ah) or NAK. A NAK surfaces as an IOException or a non-ACK byte. */
    fun write(page: Int, data: ByteArray) {
        require(data.size == 4)
        val r = a.transceive(byteArrayOf(0xA2.toByte(), page.toByte()) + data)
        if (r.size == 1 && (r[0].toInt() and 0x0F) != 0x0A) throw IOException("Tag refused write to page $page")
    }

    fun pwdAuth(pwd: ByteArray): ByteArray = a.transceive(byteArrayOf(0x1B) + pwd)

    /** READ_CNT 39h 02h → 24-bit counter. The byte order isn't stated in the datasheet table, so LSB-first is taken from
     *  §8.8.1 ("written LSByte first") by analogy. It's checked on the device against the mirrored counter. */
    fun readCounter(): Int {
        val r = a.transceive(byteArrayOf(0x39, 0x02))
        require(r.size >= 3)
        return (r[0].toInt() and 0xFF) or ((r[1].toInt() and 0xFF) shl 8) or ((r[2].toInt() and 0xFF) shl 16)
    }

    fun readSignature(): ByteArray = a.transceive(byteArrayOf(0x3C, 0x00))

    fun config(chip: NtagChip): NtagConfig {
        val r = read(chip.cfgPage)
        return NtagConfig(
            mirror = r[0].toInt() and 0xFF, mirrorPage = r[2].toInt() and 0xFF,
            auth0 = r[3].toInt() and 0xFF, access = r[4].toInt() and 0xFF, lastPage = chip.lastPage,
        )
    }

    /** Reads every page (0 → last). Stops at the first page the tag refuses (e.g. read-protected memory). */
    fun dump(chip: NtagChip): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()
        var page = 0
        while (page <= chip.lastPage) {
            val block = runCatching { read(page) }.getOrNull() ?: break
            if (block.size < 16) break
            for (i in 0 until 4) if (page + i <= chip.lastPage) pages += block.copyOfRange(i * 4, i * 4 + 4)
            page += 4
        }
        return pages
    }

    /**
     * Writes an NDEF message as a Type 2 TLV straight into user memory (pages 04h+). Used for password-protected tags,
     * because Android's Ndef class can't send PWD_AUTH. Returns the TLV bytes so the caller can verify them.
     */
    fun writeNdef(chip: NtagChip, message: NdefMessage): ByteArray {
        val tlv = ndefTlv(message)
        if (tlv.size > chip.ccBytes) throw NfcProblem(Problems.tooBig(message.byteArrayLength, chip.ccBytes))
        val padded = tlv.copyOf((tlv.size + 3) / 4 * 4)
        for (i in padded.indices step 4) write(4 + i / 4, padded.copyOfRange(i, i + 4))
        val back = ByteArray(padded.size)
        var off = 0
        while (off < padded.size) {
            val block = read(4 + off / 4)
            System.arraycopy(block, 0, back, off, minOf(16, padded.size - off))
            off += 16
        }
        if (!back.copyOf(tlv.size).contentEquals(tlv)) throw NfcProblem(Problems.verifyFailed)
        return tlv
    }

    /** Protects writes from page 04h on. Reads stay open (PROT=0) so any phone can still read the tag.
     *  AUTHLIM stays 000b, so wrong guesses can never permanently brick the tag (§8.8.2). */
    fun setPassword(chip: NtagChip, key: PasswordKey) {
        val cfg = config(chip)
        write(chip.pwdPage, key.pwd)
        write(chip.packPage, byteArrayOf(key.pack[0], key.pack[1], 0, 0))
        val access = cfg.access and 0x80.inv() and 0x07.inv() // PROT=0 (write-only protection), AUTHLIM=000b
        write(chip.accessPage, byteArrayOf(access.toByte(), 0, 0, 0))
        // AUTH0 is written last, because after it any further config write needs PWD_AUTH first.
        write(chip.cfgPage, byteArrayOf(cfg.mirror.toByte(), 0, cfg.mirrorPage.toByte(), 0x04))
    }

    fun removePassword(chip: NtagChip, key: PasswordKey) {
        auth(key)
        val cfg = config(chip)
        write(chip.cfgPage, byteArrayOf(cfg.mirror.toByte(), 0, cfg.mirrorPage.toByte(), 0xFF.toByte()))
        write(chip.pwdPage, byteArrayOf(-1, -1, -1, -1)) // back to the factory password FFFFFFFFh
        write(chip.packPage, byteArrayOf(0, 0, 0, 0))
    }

    fun auth(key: PasswordKey) {
        val pack = try { pwdAuth(key.pwd) } catch (e: IOException) { throw NfcProblem(Problems.wrongPassword) }
        if (pack.size < 2) throw NfcProblem(Problems.wrongPassword)
    }

    fun setCounter(chip: NtagChip, enabled: Boolean) {
        val cfg = config(chip)
        val access = if (enabled) cfg.access or 0x10 else cfg.access and 0x10.inv()
        write(chip.accessPage, byteArrayOf(access.toByte(), 0, 0, 0))
    }

    /** Points the ASCII mirror at [placeholderOffset] (byte offset inside the TLV written at page 04h). */
    fun setMirror(chip: NtagChip, mirror: Mirror, placeholderOffset: Int) {
        val abs = 16 + placeholderOffset
        val page = abs / 4
        val byte = abs % 4
        // Table 13: the mirror must start between page 04h and (last user page - 3).
        if (page < 4 || page > chip.lastUserPage - 3) throw NfcProblem(Problems.tooBig(abs, chip.ccBytes))
        if (mirror != Mirror.Id) setCounter(chip, true)
        val cfg = config(chip)
        val mirrorByte = (mirror.conf shl 6) or (byte shl 4) or (cfg.mirror and 0x04) // keep STRG_MOD_EN
        write(chip.cfgPage, byteArrayOf(mirrorByte.toByte(), 0, page.toByte(), cfg.auth0.toByte()))
    }

    companion object {
        fun ndefTlv(message: NdefMessage): ByteArray {
            val msg = message.toByteArray()
            val header = if (msg.size < 0xFF) byteArrayOf(0x03, msg.size.toByte())
            else byteArrayOf(0x03, 0xFF.toByte(), (msg.size shr 8).toByte(), msg.size.toByte())
            return header + msg + byteArrayOf(0xFE.toByte())
        }
    }
}

/**
 * The 4-byte PWD and 2-byte PACK. A text password is hashed (SHA-256) into those bytes, so only Latch can unlock
 * it. Exactly 8 hex digits are used as-is (PACK 0000h), which lets other NFC apps that take a raw hex PWD unlock it too.
 */
class PasswordKey private constructor(val pwd: ByteArray, val pack: ByteArray) {
    companion object {
        private val HEX8 = Regex("^[0-9A-Fa-f]{8}$")
        fun from(password: String): PasswordKey {
            if (HEX8.matches(password)) {
                return PasswordKey(password.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), byteArrayOf(0, 0))
            }
            val h = MessageDigest.getInstance("SHA-256").digest("latch:$password".toByteArray(Charsets.UTF_8))
            return PasswordKey(h.copyOfRange(0, 4), h.copyOfRange(4, 6))
        }
        fun isRawHex(password: String) = HEX8.matches(password)
    }
}
