package com.latch.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiTlvTest {

    private fun u16(b: ByteArray) = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)

    @Test
    fun credentialIsFirstTopLevelAttribute() {
        // Older Android parsers only work if Credential (0x100E) is the first attribute.
        val bytes = WifiTlv.encode("Home", WifiSecurity.Wpa2, "hunter22")
        assertEquals(0x10, bytes[0].toInt() and 0xFF)
        assertEquals(0x0E, bytes[1].toInt() and 0xFF)
        assertEquals(bytes.size - 4, u16(bytes.copyOfRange(2, 4)))
    }

    @Test
    fun wpa2RoundTrips() {
        val top = WifiTlv.decode(WifiTlv.encode("Home Wi-Fi", WifiSecurity.Wpa2, "correct horse"))
        val cred = WifiTlv.decode(top.getValue(WifiTlv.CREDENTIAL))
        assertEquals("Home Wi-Fi", cred.getValue(WifiTlv.SSID).toString(Charsets.UTF_8))
        assertEquals("correct horse", cred.getValue(WifiTlv.NETWORK_KEY).toString(Charsets.UTF_8))
        assertEquals(0x0020, u16(cred.getValue(WifiTlv.AUTH_TYPE)))
        assertEquals(0x0008, u16(cred.getValue(WifiTlv.ENCRYPTION_TYPE)))
    }

    @Test
    fun openNetworkHasEmptyKey() {
        // Android accepts an open network only when no pre-shared key is present (zero-length key field).
        val cred = WifiTlv.decode(WifiTlv.decode(WifiTlv.encode("Cafe", WifiSecurity.Open, "ignored")).getValue(WifiTlv.CREDENTIAL))
        assertArrayEquals(ByteArray(0), cred.getValue(WifiTlv.NETWORK_KEY))
        assertEquals(0x0001, u16(cred.getValue(WifiTlv.AUTH_TYPE)))
    }

    @Test
    fun decodeStopsOnTruncatedData() {
        val full = WifiTlv.encode("Home", WifiSecurity.Wpa2, "hunter22")
        val cut = full.copyOf(full.size - 3)
        assertEquals(emptyMap<Int, ByteArray>(), WifiTlv.decode(cut)) // credential length now exceeds the data
    }
}

class TagSizesTest {
    @Test
    fun fitsUsesDatasheetDataAreaWithTlvOverhead() {
        // NTAG213 data area = 144 bytes. A 141-byte message needs 141 + 2 (TLV) + 1 (terminator) = 144.
        assertEquals("NTAG213", TagSizes.smallestFit(141)?.first)
        assertEquals("NTAG215", TagSizes.smallestFit(142)?.first)
        // 255+ byte messages use the 3-byte length form: 492 + 4 + 1 = 497 > 496, so the next size up.
        assertEquals("NTAG215", TagSizes.smallestFit(491)?.first)
        assertEquals("NTAG216", TagSizes.smallestFit(492)?.first)
        assertEquals(null, TagSizes.smallestFit(868))
    }
}
