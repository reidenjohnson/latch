package com.latch.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WifiQrTest {
    @Test fun basic() {
        val n = WifiQr.parse("WIFI:T:WPA;S:mynetwork;P:mypass;;")!!
        assertEquals("mynetwork", n.ssid); assertEquals("mypass", n.password); assertEquals("WPA", n.type)
    }

    @Test fun escapesAndOrder() {
        // Example straight from the ZXing spec: an SSID of "foo;bar\baz" including the quotes.
        val n = WifiQr.parse("WIFI:S:\\\"foo\\;bar\\\\baz\\\";;")!!
        assertEquals("\"foo;bar\\baz\"", n.ssid) // escaped quotes are part of the SSID
        // Unescaped quotes are ASCII-quoting (so "ABCD" isn't read as hex) and get stripped.
        assertEquals("ABCD", WifiQr.parse("WIFI:S:\"ABCD\";T:nopass;;")!!.ssid)
        val m = WifiQr.parse("WIFI:P:p\\:w\\;d;S:Home;T:SAE;H:true;;")!!
        assertEquals("p:w;d", m.password); assertEquals("Home", m.ssid); assertEquals(true, m.hidden)
    }

    @Test fun notWifi() {
        assertNull(WifiQr.parse("https://example.com"))
        assertNull(WifiQr.parse("WIFI:T:WPA;P:x;;")) // no SSID
    }

    @Test fun classify() {
        assertEquals(WifiSecurity.Wpa2, WifiQr.classify("WPA").first)
        assertNull(WifiQr.classify("WPA").second)
        assertEquals(WifiSecurity.Open, WifiQr.classify("nopass").first)
        assertNotNull(WifiQr.classify("SAE").second)                     // WPA3-only warns
        assertNull(WifiQr.classify("[WPA2-PSK+SAE-CCMP][ESS]").second)  // transition mode is fine
        assertNotNull(WifiQr.classify("[RSN-SAE-CCMP][ESS]").second)     // WPA3-only scan result warns
        assertNull(WifiQr.classify("[WPA2-EAP-CCMP]").first)             // enterprise unsupported
    }
}
