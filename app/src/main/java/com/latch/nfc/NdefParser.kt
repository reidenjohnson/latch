package com.latch.nfc

import android.content.Context
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord

/** Turns raw NDEF records into plain-English [ParsedRecord]s. Never throws; unreadable records become Unknown. */
object NdefParser {

    fun parse(message: NdefMessage?, context: Context): List<ParsedRecord> =
        message?.records?.map { parse(it, context) } ?: emptyList()

    fun parse(record: NdefRecord, context: Context): ParsedRecord =
        runCatching { parseRecord(record, context) }.getOrElse { unknown(record) }.copy(raw = raw(record))

    private fun raw(r: NdefRecord) = RawRecord(
        tnf = r.tnf.toInt(),
        type = String(r.type, Charsets.US_ASCII).ifEmpty { "(none)" },
        payloadSize = r.payload.size,
        payloadHex = r.payload.take(512).joinToString(" ") { "%02X".format(it) } + if (r.payload.size > 512) " …" else "",
    )

    /** One-line description of a message, e.g. "Website · example.com". */
    fun summary(records: List<ParsedRecord>): String {
        val real = records.filter { it.kind != RecordKind.Empty }
        if (real.isEmpty()) return "Blank tag"
        val first = real.first()
        val more = if (real.size > 1) " (+${real.size - 1} more)" else ""
        return "${first.label} · ${first.value.lineSequence().first().take(60)}$more"
    }

    fun summary(message: NdefMessage, context: Context): String = summary(parse(message, context))

    private fun parseRecord(r: NdefRecord, context: Context): ParsedRecord = when (r.tnf) {
        NdefRecord.TNF_EMPTY -> ParsedRecord(RecordKind.Empty, "Empty", "Nothing stored")
        NdefRecord.TNF_WELL_KNOWN -> when {
            r.type.contentEquals(NdefRecord.RTD_TEXT) -> text(r)
            r.type.contentEquals(NdefRecord.RTD_URI) -> r.toUri()?.let(::uri) ?: unknown(r)
            r.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> smartPoster(r, context)
            else -> unknown(r)
        }
        NdefRecord.TNF_ABSOLUTE_URI -> r.toUri()?.let(::uri) ?: unknown(r)
        NdefRecord.TNF_MIME_MEDIA -> mime(r)
        NdefRecord.TNF_EXTERNAL_TYPE -> external(r, context)
        else -> unknown(r)
    }

    /** NFC Forum Text RTD: status byte (bit 7 = UTF-16, bits 0-5 = language length), language, then text. */
    private fun text(r: NdefRecord): ParsedRecord {
        val p = r.payload
        val status = p[0].toInt()
        val langLen = status and 0x3F
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        val lang = String(p, 1, langLen, Charsets.US_ASCII)
        val body = String(p, 1 + langLen, p.size - 1 - langLen, charset)
        val details = if (lang.isNotEmpty() && !lang.startsWith("en")) listOf("Language" to lang) else emptyList()
        return ParsedRecord(RecordKind.Text, "Text", body, details, copyText = body)
    }

    private fun uri(uri: Uri): ParsedRecord {
        val full = uri.toString()
        // Use the *encoded* part so a '&' or '?' inside a message body can't break the query split.
        val (target, query) = splitQuery(uri.encodedSchemeSpecificPart.orEmpty())
        return when (uri.scheme?.lowercase()) {
            "tel" -> ParsedRecord(RecordKind.Phone, "Phone call", target, openUri = uri, copyText = target)
            "sms", "smsto" -> ParsedRecord(
                RecordKind.Sms, "Text message", target,
                details = listOfNotNull(query["body"]?.let { "Message" to it }),
                openUri = uri, copyText = target,
            )
            "mailto" -> ParsedRecord(
                RecordKind.Email, "Email", target,
                details = listOfNotNull(query["subject"]?.let { "Subject" to it }, query["body"]?.let { "Message" to it }),
                openUri = uri, copyText = target,
            )
            "geo" -> {
                val place = query["q"] ?: target
                ParsedRecord(RecordKind.Location, "Place", place, openUri = uri, copyText = place)
            }
            "http", "https" -> ParsedRecord(
                RecordKind.Link, "Website", full,
                details = listOfNotNull(uri.host?.let { "Site" to it.removePrefix("www.") }),
                openUri = uri, copyText = full,
            )
            else -> ParsedRecord(RecordKind.Link, "Link", full, openUri = uri, copyText = full)
        }
    }

    private fun splitQuery(encodedSsp: String): Pair<String, Map<String, String>> {
        val target = Uri.decode(encodedSsp.substringBefore('?'))
        val query = encodedSsp.substringAfter('?', "")
            .split('&')
            .filter { '=' in it }
            .associate { Uri.decode(it.substringBefore('=')).lowercase() to Uri.decode(it.substringAfter('=')) }
        return target to query
    }

    private fun smartPoster(r: NdefRecord, context: Context): ParsedRecord {
        val inner = NdefMessage(r.payload).records.map { parse(it, context) }
        val link = inner.firstOrNull { it.openUri != null } ?: return unknown(r)
        val title = inner.firstOrNull { it.kind == RecordKind.Text }?.value
        return if (title != null) link.copy(details = listOf("Title" to title) + link.details) else link
    }

    private fun mime(r: NdefRecord): ParsedRecord {
        val type = String(r.type, Charsets.US_ASCII).lowercase()
        return when {
            type == WifiTlv.MIME_TYPE -> wifi(r.payload)
            type == Bluetooth.MIME_TYPE -> bluetooth(r.payload)
            type == "text/vcard" || type == "text/x-vcard" -> vcard(String(r.payload, Charsets.UTF_8))
            type.startsWith("text/") -> {
                val body = String(r.payload, Charsets.UTF_8)
                ParsedRecord(RecordKind.Text, "Text", body, listOf("Type" to type), copyText = body)
            }
            else -> ParsedRecord(RecordKind.Data, "Data", type, listOf("Size" to "${r.payload.size} bytes"))
        }
    }

    private fun wifi(payload: ByteArray): ParsedRecord {
        val top = WifiTlv.decode(payload)
        val cred = top[WifiTlv.CREDENTIAL]?.let(WifiTlv::decode) ?: top
        val ssid = cred[WifiTlv.SSID]?.toString(Charsets.UTF_8) ?: "(no name)"
        val key = cred[WifiTlv.NETWORK_KEY]?.toString(Charsets.UTF_8).orEmpty()
        val auth = cred[WifiTlv.AUTH_TYPE]?.takeIf { it.size == 2 }
            ?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
        val security = when {
            auth == null -> "Not specified"
            auth and 0x0020 != 0 -> "WPA2"
            auth and 0x0002 != 0 -> "WPA"
            auth and 0x0018 != 0 -> "Enterprise (EAP)"
            auth == 0x0001 -> "None (open)"
            else -> "Unknown (0x%04X)".format(auth)
        }
        return ParsedRecord(
            RecordKind.WiFi, "Wi-Fi network", ssid,
            details = listOf("Security" to security),
            copyText = key.ifEmpty { ssid },
            secret = key.ifEmpty { null },
        )
    }

    private fun vcard(text: String): ParsedRecord {
        // Unfold continuation lines (RFC 6350 §3.2), then read the simple properties we show.
        val lines = text.replace(Regex("\r?\n[ \t]"), "").lines()
        fun prop(name: String) = lines.firstOrNull { it.substringBefore(':').substringBefore(';').equals(name, true) }
            ?.substringAfter(':')?.replace("\\,", ",")?.replace("\\;", ";")?.replace("\\n", "\n")?.trim()
            ?.takeIf { it.isNotEmpty() }
        val name = prop("FN") ?: prop("N")?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ")
        return ParsedRecord(
            RecordKind.Contact, "Contact", name ?: "(no name)",
            details = listOfNotNull(
                prop("TEL")?.let { "Phone" to it },
                prop("EMAIL")?.let { "Email" to it },
                prop("ORG")?.let { "Company" to it },
                prop("URL")?.let { "Website" to it },
            ),
            copyText = text,
        )
    }

    private fun bluetooth(payload: ByteArray): ParsedRecord {
        val bt = Bluetooth.decode(payload)
        return ParsedRecord(
            RecordKind.Bluetooth, "Bluetooth device", bt.name ?: bt.mac,
            details = listOfNotNull("Address" to bt.mac),
            copyText = bt.mac,
        )
    }

    private fun external(r: NdefRecord, context: Context): ParsedRecord {
        val type = String(r.type, Charsets.US_ASCII)
        if (type == SecretBox.TYPE) {
            return ParsedRecord(
                RecordKind.Secret, "Locked note", "Encrypted with a password",
                details = listOf("Protection" to "AES-256-GCM"), sealed = r.payload,
            )
        }
        if (type == Payloads.FOCUS_TYPE) {
            return ParsedRecord(RecordKind.App, "Latch Focus tag", "Tap to start or end Focus", details = listOf("Record" to type))
        }
        if (type == "android.com:pkg") {
            val pkg = String(r.payload, Charsets.US_ASCII)
            val pm = context.packageManager
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            return ParsedRecord(
                RecordKind.App, "Opens app", label ?: pkg,
                details = listOfNotNull("Package" to pkg, if (label == null) "On this phone" to "Not installed" else null),
                appPackage = pkg, copyText = pkg,
            )
        }
        return ParsedRecord(RecordKind.Data, "Custom data", type, listOf("Size" to "${r.payload.size} bytes"))
    }

    private fun unknown(r: NdefRecord): ParsedRecord {
        val preview = r.payload.take(24).joinToString(" ") { "%02X".format(it) } + if (r.payload.size > 24) " …" else ""
        return ParsedRecord(
            RecordKind.Unknown, "Unrecognized record", "Type ${r.tnf}",
            details = listOf("Size" to "${r.payload.size} bytes", "Bytes" to preview),
        )
    }
}
