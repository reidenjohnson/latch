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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.latch.nfc.NfcAvailability
import com.latch.ui.LatchRoot
import com.latch.ui.theme.LatchTheme

class MainActivity : ComponentActivity() {

    private var adapter: NfcAdapter? = null
    /** Bumped when something (the Focus notification, the tap card) asks to open the Focus tab. */
    private var focusRequest by mutableIntStateOf(0)

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
        if (intent.getBooleanExtra(EXTRA_FOCUS, false)) focusRequest++
        setContent {
            LatchTheme { LatchRoot(latch, focusRequest) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FOCUS, false)) focusRequest++
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

    companion object {
        const val EXTRA_FOCUS = "open_focus"
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
