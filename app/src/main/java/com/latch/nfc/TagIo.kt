package com.latch.nfc

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import java.io.IOException

data class WriteReport(val size: Int, val capacity: Int?, val verified: Boolean)

/** Blocking tag I/O. Call from the reader-mode callback thread, never the main thread. */
object TagIo {

    fun uid(tag: Tag): String = hex(tag.id, ":")

    fun hex(bytes: ByteArray, sep: String = " "): String = bytes.joinToString(sep) { "%02X".format(it) }

    fun read(tag: Tag, context: Context): TagSnapshot {
        val ndef = Ndef.get(tag)
        val nfcA = NfcA.get(tag)
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
            ntag = inspectNtag(tag),
            techs = tag.techList.map { it.substringAfterLast('.') },
            atqa = nfcA?.atqa?.let { hex(it) },
            sak = nfcA?.sak?.let { "%02X".format(it) },
            maxTransceive = runCatching { nfcA?.maxTransceiveLength ?: IsoDep.get(tag)?.maxTransceiveLength }.getOrNull(),
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

    /**
     * Writes [message] and verifies it by reading it back. With a [password] or [mirror], it writes raw NTAG pages
     * (the Ndef class can't authenticate or configure mirrors). Throws [NfcProblem] with a clear reason.
     */
    fun write(tag: Tag, message: NdefMessage, password: String? = null, mirror: Mirror? = null): WriteReport {
        if (password != null || mirror != null) return writeRaw(tag, message, password, mirror)
        val bytes = message.toByteArray()
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.use {
                    it.connect()
                    if (!it.isWritable) throw NfcProblem(Problems.locked)
                    if (bytes.size > it.maxSize) throw NfcProblem(Problems.tooBig(bytes.size, it.maxSize))
                    it.writeNdefMessage(message)
                    val back = runCatching { it.ndefMessage?.toByteArray() }.getOrNull()
                    if (back != null && !back.contentEquals(bytes)) throw NfcProblem(Problems.verifyFailed)
                    return WriteReport(bytes.size, it.maxSize, verified = back != null)
                }
            } catch (e: IOException) {
                // A plain write fails on a password-protected NTAG. Tell the user that instead of "couldn't talk".
                if (e !is TagLostException && isPasswordProtected(tag)) throw NfcProblem(Problems.needsPassword)
                throw e
            }
        }
        val formatable = NdefFormatable.get(tag) ?: throw NfcProblem(Problems.unsupported)
        formatable.use { it.connect(); it.format(message) }
        // The first write to a factory-blank tag formats it. Verify through a fresh Ndef connection.
        val back = runCatching { Ndef.get(tag)?.use { n -> n.connect(); n.ndefMessage?.toByteArray() } }.getOrNull()
        if (back != null && !back.contentEquals(bytes)) throw NfcProblem(Problems.verifyFailed)
        return WriteReport(bytes.size, null, verified = back != null)
    }

    private fun writeRaw(tag: Tag, message: NdefMessage, password: String?, mirror: Mirror?): WriteReport =
        withNtag(tag) { n, chip ->
            if (password != null) n.auth(PasswordKey.from(password))
            val tlv = n.writeNdef(chip, message)
            if (mirror != null) {
                val offset = lastIndexOf(tlv, mirror.placeholder.toByteArray(Charsets.US_ASCII))
                check(offset >= 0) { "Live link placeholder missing" }
                n.setMirror(chip, mirror, offset)
            }
            WriteReport(message.byteArrayLength, chip.ccBytes, verified = true)
        }

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

    fun setPassword(tag: Tag, newPassword: String, current: String?) = withNtag(tag) { n, chip ->
        val cfg = n.config(chip)
        if (cfg.passwordProtected) {
            if (current == null) throw NfcProblem(Problems.needsPassword)
            n.auth(PasswordKey.from(current))
        }
        n.setPassword(chip, PasswordKey.from(newPassword))
        chip
    }

    fun removePassword(tag: Tag, password: String?) = withNtag(tag) { n, chip ->
        if (!n.config(chip).passwordProtected) throw NfcProblem(Problem("No password on this tag", "It's already open."))
        if (password == null) throw NfcProblem(Problems.needsPassword)
        n.removePassword(chip, PasswordKey.from(password))
        chip
    }

    fun setCounter(tag: Tag, enable: Boolean, password: String?) = withNtag(tag) { n, chip ->
        val cfg = n.config(chip)
        if (cfg.passwordProtected) {
            if (password == null) throw NfcProblem(Problems.needsPassword)
            n.auth(PasswordKey.from(password))
        }
        n.setCounter(chip, enable)
        chip
    }

    fun dump(tag: Tag): Report {
        val pages: List<ByteArray>
        val label: String
        val ntag = NfcA.get(tag)?.takeIf { it.sak.toInt() == 0 }?.let { a ->
            runCatching { a.use { it.connect(); val n = Ntag21x(it); NtagChip.fromVersion(n.version()) } }.getOrNull()
        }
        if (ntag != null) {
            pages = withNtag(tag) { n, chip -> n.dump(chip) }
            label = ntag.label
        } else {
            val ul = MifareUltralight.get(tag) ?: throw NfcProblem(
                Problem("Memory dump isn't available for this tag", "It works with NFC Type 2 tags like NTAG and MIFARE Ultralight."),
            )
            pages = ul.use { u ->
                u.connect()
                val out = mutableListOf<ByteArray>()
                var p = 0
                while (p < 256) {
                    val block = runCatching { u.readPages(p) }.getOrNull() ?: break
                    for (i in 0 until 4) out += block.copyOfRange(i * 4, i * 4 + 4)
                    p += 4
                }
                out
            }
            label = "MIFARE Ultralight family"
        }
        val text = buildString {
            appendLine("Latch memory dump")
            appendLine("Chip: $label · UID ${uid(tag)} · ${pages.size} pages")
            appendLine()
            pages.forEachIndexed { i, p ->
                val ascii = p.joinToString("") { b -> val c = b.toInt() and 0xFF; if (c in 32..126) c.toChar().toString() else "." }
                appendLine("%03d  %02Xh   %s   %s".format(i, i, hex(p), ascii))
            }
        }
        return Report(text, "latch-dump-${uid(tag).replace(":", "")}.txt")
    }

    fun raw(tag: Tag, tech: RawTech, commands: List<ByteArray>): Report {
        val t: TagTechnology = when (tech) {
            RawTech.NfcA -> NfcA.get(tag)
            RawTech.NfcB -> NfcB.get(tag)
            RawTech.NfcF -> NfcF.get(tag)
            RawTech.NfcV -> NfcV.get(tag)
            RawTech.IsoDep -> IsoDep.get(tag)
        } ?: throw NfcProblem(Problem("This tag doesn't speak ${tech.name}", "It supports: ${tag.techList.joinToString { it.substringAfterLast('.') }}"))
        val text = buildString {
            appendLine("${tech.name} · UID ${uid(tag)}")
            t.use {
                it.connect()
                for (cmd in commands) {
                    appendLine("> ${hex(cmd)}")
                    val r = runCatching { transceive(it, cmd) }
                    appendLine(r.fold({ b -> "< ${hex(b)}" }, { e -> "! ${e.javaClass.simpleName}: ${e.message}" }))
                }
            }
        }
        return Report(text, "latch-commands.txt")
    }

    private fun transceive(t: TagTechnology, cmd: ByteArray): ByteArray = when (t) {
        is NfcA -> t.transceive(cmd)
        is NfcB -> t.transceive(cmd)
        is NfcF -> t.transceive(cmd)
        is NfcV -> t.transceive(cmd)
        is IsoDep -> t.transceive(cmd)
        else -> error("unsupported")
    }

    private fun <T> withNtag(tag: Tag, block: (Ntag21x, NtagChip) -> T): T {
        val a = NfcA.get(tag)?.takeIf { it.sak.toInt() == 0 } ?: throw NfcProblem(Problems.notNtag)
        return a.use {
            it.connect()
            val n = Ntag21x(it)
            val chip = NtagChip.fromVersion(n.version()) ?: throw NfcProblem(Problems.notNtag)
            block(n, chip)
        }
    }

    private fun isPasswordProtected(tag: Tag): Boolean = runCatching {
        withNtag(tag) { n, chip -> n.config(chip).passwordProtected }
    }.getOrDefault(false)

    /** GET_VERSION, then signature, config and counter. Any step the tag refuses is left null, never guessed. */
    private fun inspectNtag(tag: Tag): NtagInfo? {
        val a = NfcA.get(tag)?.takeIf { it.sak.toInt() == 0 } ?: return null // SAK 00h = Type 2 tag
        return runCatching {
            a.use {
                it.connect()
                val n = Ntag21x(it)
                val chip = NtagChip.fromVersion(n.version()) ?: return null
                val genuine = runCatching { Originality.isGenuine(tag.id, n.readSignature()) }.getOrNull()
                val cfg = runCatching { n.config(chip) }.getOrNull()
                val counter = if (cfg?.counterEnabled == true && !cfg.counterProtected) runCatching { n.readCounter() }.getOrNull() else null
                NtagInfo(chip, cfg, counter, genuine)
            }
        }.getOrNull()
    }

    private fun lastIndexOf(hay: ByteArray, needle: ByteArray): Int {
        for (i in hay.size - needle.size downTo 0) {
            if ((needle.indices).all { hay[i + it] == needle[it] }) return i
        }
        return -1
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
}

object Problems {
    val moved = Problem("Tag moved away too soon", "Hold your phone still on the tag until you feel the buzz.")
    val locked = Problem("This tag is locked", "It was made read-only, so it can't be changed. Use a different tag.")
    val unsupported = Problem(
        "Latch can't write to this one",
        "It looks like a bank, transit or access card. Latch works with NFC stickers, cards and key fobs.",
    )
    val verifyFailed = Problem("Didn't write correctly", "The tag read back different data. Tap again to rewrite it.")
    val needsPassword = Problem("This tag has a password", "Enter it below, then tap the tag again.", needsPassword = true)
    val wrongPassword = Problem("Wrong password", "The tag didn't accept that password. Check it and tap again.", needsPassword = true)
    val notNtag = Problem(
        "This needs an NTAG213/215/216 tag",
        "Passwords, scan counters, live links and some memory tools use NXP NTAG chip commands. This tag didn't identify as one.",
    )
    fun tooBig(size: Int, max: Int) = Problem(
        "Too much for this tag",
        "This needs $size bytes, but the tag holds $max. Shorten it, or use a bigger tag like an NTAG216.",
    )
}
