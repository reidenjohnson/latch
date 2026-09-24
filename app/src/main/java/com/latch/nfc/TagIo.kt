package com.latch.nfc

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import java.io.IOException

data class WriteReport(val size: Int, val capacity: Int?, val verified: Boolean)

/** Blocking tag I/O. Call from the reader-mode callback thread, never the main thread. */
object TagIo {

    fun uid(tag: Tag): String = tag.id.joinToString(":") { "%02X".format(it) }

    fun read(tag: Tag, context: Context): TagSnapshot {
        val ndef = Ndef.get(tag)
        val formatable = NdefFormatable.get(tag) != null
        // Reader mode's NDEF check already read the message, so the cached copy is instant and needs no RF.
        var message = ndef?.cachedNdefMessage
        if (ndef != null && message == null) {
            message = runCatching { ndef.use { it.connect(); it.ndefMessage } }.getOrNull()
        }
        val records = NdefParser.parse(message, context)
        return TagSnapshot(
            uid = uid(tag),
            typeLabel = typeLabel(tag, ndef),
            chip = identifyChip(tag),
            techs = tag.techList.map { it.substringAfterLast('.') },
            capacity = ndef?.maxSize,
            used = message?.byteArrayLength ?: 0,
            writable = ndef?.isWritable,
            canLock = ndef?.let { it.isWritable && it.canMakeReadOnly() } ?: false,
            supported = ndef != null || formatable,
            message = message,
            records = records,
            summary = NdefParser.summary(records),
            time = System.currentTimeMillis(),
        )
    }

    /** Writes [message], then reads it back and compares byte-for-byte. Throws [NfcProblem] with a clear reason. */
    fun write(tag: Tag, message: NdefMessage): WriteReport {
        val bytes = message.toByteArray()
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                if (!it.isWritable) throw NfcProblem(Problems.locked)
                if (bytes.size > it.maxSize) throw NfcProblem(Problems.tooBig(bytes.size, it.maxSize))
                it.writeNdefMessage(message)
                val back = runCatching { it.ndefMessage?.toByteArray() }.getOrNull()
                if (back != null && !back.contentEquals(bytes)) throw NfcProblem(Problems.verifyFailed)
                return WriteReport(bytes.size, it.maxSize, verified = back != null)
            }
        }
        val formatable = NdefFormatable.get(tag) ?: throw NfcProblem(Problems.unsupported)
        formatable.use { it.connect(); it.format(message) }
        // The first write to a factory-blank tag formats it. Verify through a fresh Ndef connection.
        val back = runCatching { Ndef.get(tag)?.use { n -> n.connect(); n.ndefMessage?.toByteArray() } }.getOrNull()
        if (back != null && !back.contentEquals(bytes)) throw NfcProblem(Problems.verifyFailed)
        return WriteReport(bytes.size, null, verified = back != null)
    }

    /** Permanently makes the tag read-only. Returns the tag's type label. */
    fun lock(tag: Tag): String {
        val ndef = Ndef.get(tag) ?: throw NfcProblem(
            Problem("Can't lock this tag", "Only tags that already have data on them can be locked. Write something first."),
        )
        ndef.use {
            if (!it.isWritable) throw NfcProblem(Problem("Already locked", "This tag was already read-only."))
            if (!it.canMakeReadOnly()) throw NfcProblem(Problem("This tag can't be locked", "Its chip doesn't support read-only mode."))
            it.connect()
            if (!it.makeReadOnly()) throw NfcProblem(Problem("Lock didn't take", "The tag refused. Hold still and tap again."))
        }
        return typeLabel(tag, ndef)
    }

    fun explain(e: Throwable): Problem = when (e) {
        is NfcProblem -> e.problem
        is TagLostException -> Problems.moved
        // Android throws this when the tag object is stale because the tag left the field mid-operation.
        is SecurityException -> Problems.moved
        is FormatException -> Problem("The tag's data is scrambled", "It can't be read, but you can still write over it.")
        is IOException, is IllegalStateException -> Problem(
            "Couldn't talk to the tag",
            "Hold the middle of your phone's back flat on the tag and try again. Thick cases and metal block NFC.",
        )
        else -> Problem("Something went wrong", e.message ?: e.javaClass.simpleName)
    }

    private fun typeLabel(tag: Tag, ndef: Ndef?): String {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        return when {
            ndef?.type == Ndef.NFC_FORUM_TYPE_1 -> "NFC Forum Type 1"
            ndef?.type == Ndef.NFC_FORUM_TYPE_2 -> "NFC Forum Type 2"
            ndef?.type == Ndef.NFC_FORUM_TYPE_3 -> "NFC Forum Type 3"
            ndef?.type == Ndef.NFC_FORUM_TYPE_4 -> "NFC Forum Type 4"
            ndef?.type == Ndef.MIFARE_CLASSIC || "MifareClassic" in techs -> "MIFARE Classic"
            "MifareUltralight" in techs -> "MIFARE Ultralight family"
            "IsoDep" in techs -> "Smart card (ISO-DEP)"
            "NfcV" in techs -> "NFC-V (ISO 15693)"
            "NfcF" in techs -> "FeliCa"
            else -> "NFC tag"
        }
    }

    /**
     * Asks a Type 2 tag for its exact chip with GET_VERSION (command 60h). Response bytes and storage-size codes
     * come from the NTAG213/215/216 datasheet Rev. 3.2, Table 26 and Table 28:
     * vendor 04h (NXP), product type 04h (NTAG), storage size 0Fh/11h/13h = NTAG213/215/216.
     * https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf
     * Anything else returns null. The UI then shows the generic type instead of guessing.
     */
    private fun identifyChip(tag: Tag): String? {
        val nfcA = NfcA.get(tag) ?: return null
        if (nfcA.sak.toInt() != 0) return null // SAK 00h = Type 2 tag; only those understand GET_VERSION.
        val v = runCatching { nfcA.use { it.connect(); it.transceive(byteArrayOf(0x60)) } }.getOrNull() ?: return null
        if (v.size < 8 || v[1] != 0x04.toByte() || v[2] != 0x04.toByte()) return null
        return when (v[6].toInt()) {
            0x0F -> "NTAG213"
            0x11 -> "NTAG215"
            0x13 -> "NTAG216"
            else -> null
        }
    }
}

object Problems {
    val moved = Problem("Tag moved away too soon", "Hold your phone still on the tag until you feel the buzz.")
    val locked = Problem("This tag is locked", "It was made read-only, so it can't be changed. Use a different tag.")
    val unsupported = Problem(
        "Latch can't write to this one",
        "It looks like a bank, transit or access card. Latch works with NFC stickers, cards and key fobs.",
    )
    val verifyFailed = Problem("Didn't write correctly", "The tag read back different data. Tap again to rewrite it.")
    fun tooBig(size: Int, max: Int) = Problem(
        "Too much for this tag",
        "This needs $size bytes, but the tag holds $max. Shorten it, or use a bigger tag like an NTAG216.",
    )
}
