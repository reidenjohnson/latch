package com.latch.focus

import android.app.Application
import android.content.Context
import com.latch.focus.engine.Engine
import com.latch.focus.nfc.TagReader

class App : Application() {
    lateinit var engine: Engine
        private set
    lateinit var tags: TagReader
        private set

    override fun onCreate() {
        super.onCreate()
        com.latch.focus.engine.Prefs.load(this)
        engine = Engine(this)
        tags = TagReader(this, engine)
    }
}

val Context.latch: Engine get() = (applicationContext as App).engine
val Context.tagReader: TagReader get() = (applicationContext as App).tags
