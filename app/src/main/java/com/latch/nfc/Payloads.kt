package com.latch.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Builds the NDEF messages Latch writes. Each one uses a standard record type any phone understands. */
object Payloads {

    fun link(url: String): NdefMessage {
        val trimmed = url.trim()
        val full = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return NdefMessage(NdefRecord.createUri(full))
    }

    fun text(text: String): NdefMessage = NdefMessage(NdefRecord.createTextRecord("en", text))

    fun phone(number: String): NdefMessage = uri("tel:${cleanNumber(number)}")

    fun sms(number: String, body: String): NdefMessage {
        val query = if (body.isBlank()) "" else "?body=${Uri.encode(body)}"
        return uri("sms:${cleanNumber(number)}$query")
    }

    fun email(to: String, subject: String, body: String): NdefMessage {
        val params = buildList {
            if (subject.isNotBlank()) add("subject=${Uri.encode(subject)}")
            if (body.isNotBlank()) add("body=${Uri.encode(body)}")
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return uri("mailto:${to.trim()}$query")
    }

    /** Coordinates ("40.78, -73.97") become a pin. Anything else becomes a map search. */
    fun location(place: String): NdefMessage {
        val coords = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""").find(place)
        return if (coords != null) {
            val (lat, lng) = coords.destructured
            uri("geo:$lat,$lng?q=$lat,$lng")
        } else {
            uri("geo:0,0?q=${Uri.encode(place.trim())}")
        }
    }

    /** Android Application Record: opens the app, or its Play Store page if it isn't installed. */
    fun app(packageName: String): NdefMessage = NdefMessage(NdefRecord.createApplicationRecord(packageName))

    fun contact(name: String, phone: String, email: String, org: String, website: String): NdefMessage {
        val parts = name.trim().split(Regex("\\s+"))
        val family = if (parts.size > 1) parts.last() else ""
        val given = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.first()
        val card = buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            append("N:${vEsc(family)};${vEsc(given)};;;\r\n")
            append("FN:${vEsc(name.trim())}\r\n")
            if (org.isNotBlank()) append("ORG:${vEsc(org.trim())}\r\n")
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:${cleanNumber(phone)}\r\n")
            if (email.isNotBlank()) append("EMAIL:${vEsc(email.trim())}\r\n")
            if (website.isNotBlank()) append("URL:${vEsc(website.trim())}\r\n")
            append("END:VCARD\r\n")
        }
        return NdefMessage(NdefRecord.createMime("text/vcard", card.toByteArray(Charsets.UTF_8)))
    }

    fun wifi(ssid: String, security: WifiSecurity, password: String): NdefMessage =
        NdefMessage(NdefRecord.createMime(WifiTlv.MIME_TYPE, WifiTlv.encode(ssid, security, password)))

    val EMPTY: NdefMessage get() = NdefMessage(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null))

    private fun uri(value: String) = NdefMessage(NdefRecord.createUri(value))

    private fun cleanNumber(n: String) = n.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private fun vEsc(s: String) =
        s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
}

/**
 * Wi-Fi security options Android's tag handler can actually connect with. Android maps WPA and WPA2 PSK
 * to the same key management, and has no WPA3-only (SAE) option, so WPA3-only networks aren't offered.
 * Source: packages/modules/Nfc NfcWifiProtectedSetup.populateAllowedKeyManagement
 * https://android.googlesource.com/platform/packages/modules/Nfc/+/refs/heads/main/NfcNci/src/com/android/nfc/NfcWifiProtectedSetup.java
 */
enum class WifiSecurity(val label: String, val authType: Int, val encType: Int) {
    Wpa2("WPA2 / WPA3", 0x0020, 0x0008),
    Open("None", 0x0001, 0x0001),
}

/**
 * Wi-Fi Simple Configuration (WSC) token, the format Android reads to offer "Connect to network?".
 * Attribute IDs and auth values match Android's parser (link on [WifiSecurity]), which cites the
 * WFA Wi-Fi Simple Configuration Technical Specification v2.0.2.1.
 *
 * The Credential attribute is written FIRST, on purpose. Older Android versions don't skip unknown top-level
 * attributes (see commit 8172b1e: https://android.googlesource.com/platform/packages/apps/Nfc/+/8172b1e),
 * so a leading Version attribute would make them misread the tag.
 */
object WifiTlv {
    const val MIME_TYPE = "application/vnd.wfa.wsc"
    const val CREDENTIAL = 0x100E
    const val NETWORK_INDEX = 0x1026
    const val SSID = 0x1045
    const val AUTH_TYPE = 0x1003
    const val ENCRYPTION_TYPE = 0x100F
    const val NETWORK_KEY = 0x1027
    const val MAC_ADDRESS = 0x1020
    /** Android rejects keys longer than this (MAX_NETWORK_KEY_SIZE_BYTES). */
    const val MAX_KEY_BYTES = 64

    fun encode(ssid: String, security: WifiSecurity, password: String): ByteArray {
        val key = if (security == WifiSecurity.Open) ByteArray(0) else password.toByteArray(Charsets.UTF_8)
        val credential = ByteArrayOutputStream().apply {
            write(attr(NETWORK_INDEX, byteArrayOf(1)))
            write(attr(SSID, ssid.toByteArray(Charsets.UTF_8)))
            write(attr(AUTH_TYPE, u16(security.authType)))
            write(attr(ENCRYPTION_TYPE, u16(security.encType)))
            write(attr(NETWORK_KEY, key))
            write(attr(MAC_ADDRESS, ByteArray(6) { 0xFF.toByte() }))
        }.toByteArray()
        return attr(CREDENTIAL, credential)
    }

    /** Parses TLV attributes into id → value. Stops cleanly on truncated data. */
    fun decode(bytes: ByteArray): Map<Int, ByteArray> {
        val out = LinkedHashMap<Int, ByteArray>()
        val buf = ByteBuffer.wrap(bytes)
        while (buf.remaining() >= 4) {
            val id = buf.short.toInt() and 0xFFFF
            val len = buf.short.toInt() and 0xFFFF
            if (len > buf.remaining()) break
            val value = ByteArray(len).also { buf.get(it) }
            out.putIfAbsent(id, value)
        }
        return out
    }

    private fun attr(id: Int, value: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + value.size).putShort(id.toShort()).putShort(value.size.toShort()).put(value).array()

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
}

/**
 * NDEF data-area sizes of the common NXP tags, from the Capability Container (NTAG213/215/216 datasheet
 * Rev. 3.2, Table 4 "NDEF memory size": 144 / 496 / 872 bytes).
 * https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf
 * The NDEF message sits inside a TLV (1 type byte + 1 or 3 length bytes) followed by a 1-byte terminator.
 */
object TagSizes {
    val common = listOf("NTAG213" to 144, "NTAG215" to 496, "NTAG216" to 872)

    fun bytesNeeded(messageSize: Int): Int = messageSize + (if (messageSize < 0xFF) 2 else 4) + 1

    /** The smallest common tag this message fits on, or null if it's too big for all of them. */
    fun smallestFit(messageSize: Int): Pair<String, Int>? =
        common.firstOrNull { bytesNeeded(messageSize) <= it.second }
}
