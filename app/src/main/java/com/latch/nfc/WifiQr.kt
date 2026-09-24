package com.latch.nfc

/**
 * Parses the Wi-Fi QR format used by Android's "Share network" screen and most router stickers:
 * `WIFI:T:WPA;S:mynetwork;P:mypass;;`. Field order doesn't matter, and `\ ; , " :` are backslash-escaped.
 * Spec: https://github.com/zxing/zxing/wiki/Barcode-Contents#wi-fi-network-config-android-ios-11
 */
object WifiQr {
    data class Network(val ssid: String, val password: String, val type: String, val hidden: Boolean)

    fun parse(text: String): Network? {
        val t = text.trim()
        if (!t.startsWith("WIFI:", ignoreCase = true)) return null
        val fields = mutableMapOf<String, String>()
        val body = t.substring(5)
        var i = 0
        while (i < body.length) {
            val colon = body.indexOf(':', i).takeIf { it >= 0 } ?: break
            val key = body.substring(i, colon).uppercase()
            val value = StringBuilder()
            var j = colon + 1
            // Only *unescaped* surrounding quotes are ASCII-quoting. Escaped \" quotes are part of the value.
            val openQuote = j < body.length && body[j] == '"'
            var closeQuote = false
            while (j < body.length && body[j] != ';') {
                closeQuote = false
                if (body[j] == '\\' && j + 1 < body.length) { value.append(body[j + 1]); j += 2 }
                else { if (body[j] == '"') closeQuote = true; value.append(body[j]); j++ }
            }
            var v = value.toString()
            if (openQuote && closeQuote && v.length >= 2) v = v.substring(1, v.length - 1)
            if (key.isNotEmpty()) fields[key] = v
            i = j + 1
            if (i < body.length && body[i] == ';') break // ";;" ends the record
        }
        val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
        return Network(ssid, fields["P"].orEmpty(), fields["T"].orEmpty().ifEmpty { "nopass" }, fields["H"].equals("true", true))
    }

    /** Maps a QR auth type or scan capabilities to what Android's NFC Wi-Fi reader supports, with an honest note. */
    fun classify(authOrCaps: String): Pair<WifiSecurity?, String?> {
        val a = authOrCaps.uppercase()
        return when {
            "EAP" in a -> null to "Enterprise (802.1X) networks can't go on an NFC Wi-Fi tag. Android's tag reader doesn't support them."
            "WEP" in a -> null to "WEP networks aren't supported by Android's NFC Wi-Fi reader."
            ("SAE" in a || "WPA3" in a) && "PSK" !in a && a != "WPA" ->
                WifiSecurity.Wpa2 to "This network uses WPA3. Android's NFC Wi-Fi reader has no WPA3-only option, so the tag only " +
                    "works if the router also allows WPA2 (\"WPA2/WPA3\" or \"transition\" mode). Test it once after writing."
            "WPA" in a || "PSK" in a -> WifiSecurity.Wpa2 to null
            else -> WifiSecurity.Open to null
        }
    }
}
