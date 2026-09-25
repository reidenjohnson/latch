package com.latch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import com.latch.ui.theme.isDark
import androidx.core.content.ContextCompat
import com.latch.nfc.NfcAvailability
import com.latch.ui.LatchRoot
import com.latch.ui.theme.LatchTheme

class MainActivity : ComponentActivity() {

    private var adapter: NfcAdapter? = null

    /** Re-checks NFC when the user flips it on/off in quick settings while Latch is open. */
    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshAvailability()
            startReaderMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            // Status and nav bar icons follow the in-app theme choice, not just the phone's setting.
            val dark = isDark()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LatchTheme { LatchRoot(latch) }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, nfcStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshAvailability()
        startReaderMode()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(nfcStateReceiver)
        runCatching { adapter?.disableReaderMode(this) }
    }

    /**
     * Reader mode sends every tag straight to Latch while it's in the foreground. The system's
     * "open with..." dispatch is bypassed, which is what makes taps consistent.
     */
    private fun startReaderMode() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        android.util.Log.d("Latch", "reader mode on")
        a.enableReaderMode(this, { tag -> latch.nfc.onTag(tag) }, flags, null)
    }

    private fun refreshAvailability() {
        val a = adapter
        latch.nfc.setAvailability(
            when {
                a == null -> NfcAvailability.Unsupported
                !a.isEnabled -> NfcAvailability.Off
                else -> NfcAvailability.On
            },
        )
    }
}
