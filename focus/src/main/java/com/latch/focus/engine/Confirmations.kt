package com.latch.focus.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Makes sure each latch/unlatch is confirmed exactly once, by whichever Latch screen is on top. Three screens can
 * show it (the app, the tap screen over another app, and the lock screen), and without this they stacked up.
 * [owner] is the event stamp from [Engine.events] and the screen that's showing it.
 */
object Confirmations {
    enum class Screen { App, Tap, Lock }

    private val _owner = MutableStateFlow<Pair<Long, Screen>?>(null)
    val owner: StateFlow<Pair<Long, Screen>?> = _owner.asStateFlow()

    /** Show it only if nobody has yet (the app coming back to the front mustn't repeat an old one). */
    @Synchronized
    fun claim(stamp: Long, screen: Screen): Boolean {
        if (_owner.value?.first == stamp) return false
        _owner.value = stamp to screen
        return true
    }

    /** Show it here, taking over from a screen that's now underneath (that screen closes its copy). */
    @Synchronized
    fun take(stamp: Long, screen: Screen) {
        _owner.value = stamp to screen
    }
}
