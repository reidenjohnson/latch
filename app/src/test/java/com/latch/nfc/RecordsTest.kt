package com.latch.nfc

import com.latch.data.Csv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    @Test fun substituteFillsNumberAndColumns() {
        val spec = RecordSpec(RecordType.Text, mapOf("text" to "Table {n} · {Name}"))
        val out = Records.substitute(spec, mapOf("n" to "7", "Name" to "Ava"))
        assertEquals("Table 7 · Ava", out.values["text"])
    }

    @Test fun requiredFieldsFlagged() {
        assertEquals(setOf("ssid", "password"), Records.validate(RecordSpec(RecordType.WiFi)).keys)
        // Open networks don't need a password (the field is hidden).
        assertEquals(setOf("ssid"), Records.validate(RecordSpec(RecordType.WiFi, mapOf("security" to "Open"))).keys)
    }

    @Test fun wifiPasswordLength() {
        val e = Records.validate(RecordSpec(RecordType.WiFi, mapOf("ssid" to "Home", "password" to "short")))
        assertTrue(e["password"]!!.isNotEmpty())
    }

    @Test fun bluetoothMacValidated() {
        assertTrue("mac" in Records.validate(RecordSpec(RecordType.Bluetooth, mapOf("mac" to "nope"))))
        assertFalse("mac" in Records.validate(RecordSpec(RecordType.Bluetooth, mapOf("mac" to "00:1A:7D:DA:71:13"))))
    }

    @Test fun hexParsing() {
        assertArrayEquals(byteArrayOf(0x30, 0x04), Records.hexOrNull("30 04"))
        assertEquals(null, Records.hexOrNull("3"))
        assertEquals(null, Records.hexOrNull("zz"))
    }
}

class BluetoothTest {
    @Test fun addressIsReversedAndNameIsEir09() {
        val p = Bluetooth.encode("00:1A:7D:DA:71:13", "Speaker")
        // total length (LE) = 2 + 6 + (1 + 1 + 7) = 17
        assertEquals(17, (p[0].toInt() and 0xFF) or ((p[1].toInt() and 0xFF) shl 8))
        assertArrayEquals(byteArrayOf(0x13, 0x71, 0xDA.toByte(), 0x7D, 0x1A, 0x00), p.copyOfRange(2, 8))
        assertEquals(8, p[8].toInt())      // EIR length = 1 (type) + 7 (name)
        assertEquals(0x09, p[9].toInt())   // Complete Local Name
        val d = Bluetooth.decode(p)
        assertEquals("00:1A:7D:DA:71:13", d.mac)
        assertEquals("Speaker", d.name)
    }
}

class CsvTest {
    @Test fun quotedFieldsAndEscapes() {
        val rows = Csv.parse("name,url\r\n\"Smith, Ava\",https://a.co\n\"He said \"\"hi\"\"\",x\n\n")
        assertEquals(listOf(listOf("name", "url"), listOf("Smith, Ava", "https://a.co"), listOf("He said \"hi\"", "x")), rows)
    }
}

class PasswordKeyTest {
    @Test fun rawHexUsedAsIs() {
        val k = PasswordKey.from("1A2B3C4D")
        assertArrayEquals(byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D), k.pwd)
    }

    @Test fun textIsHashedDeterministically() {
        assertArrayEquals(PasswordKey.from("hunter22").pwd, PasswordKey.from("hunter22").pwd)
        assertFalse(PasswordKey.from("hunter22").pwd.contentEquals(PasswordKey.from("hunter23").pwd))
    }
}
