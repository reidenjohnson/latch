package com.latch

import android.app.Application
import android.content.Context
import com.latch.data.DraftHandoff
import com.latch.data.HistoryStore
import com.latch.data.TemplateStore
import com.latch.focus.FocusManager
import com.latch.nfc.NfcController

class LatchApplication : Application() {
    lateinit var history: HistoryStore
        private set
    lateinit var templates: TemplateStore
        private set
    lateinit var nfc: NfcController
        private set
    lateinit var focus: FocusManager
        private set
    val handoff = DraftHandoff()

    override fun onCreate() {
        super.onCreate()
        history = HistoryStore(this)
        templates = TemplateStore(this)
        focus = FocusManager(this)
        nfc = NfcController(this, history, focus)
    }
}

val Context.latch: LatchApplication get() = applicationContext as LatchApplication
