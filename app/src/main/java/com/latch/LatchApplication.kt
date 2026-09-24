package com.latch

import android.app.Application
import android.content.Context
import com.latch.data.HistoryStore
import com.latch.nfc.NfcController

class LatchApplication : Application() {
    lateinit var history: HistoryStore
        private set
    lateinit var nfc: NfcController
        private set

    override fun onCreate() {
        super.onCreate()
        history = HistoryStore(this)
        nfc = NfcController(this, history)
    }
}

val Context.latch: LatchApplication get() = applicationContext as LatchApplication
