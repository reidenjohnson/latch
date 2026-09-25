package com.latch.focus.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.latch.focus.latch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Watches which app comes to the front and, during a session, covers blocked apps with [BlockedActivity].
 * It only receives "window state changed" events and can't read screen content (res/xml/blocker.xml sets
 * canRetrieveWindowContent="false"). Accessibility services may start activities from the background:
 * https://developer.android.com/guide/components/activities/background-starts#exceptions
 */
class Blocker : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** The last app in front that isn't Latch or the system UI. */
    private var front: String? = null
    private var lastShown: String? = null
    private var lastShownAt = 0L

    override fun onServiceConnected() {
        _running.value = true
        // A session just started while a blocked app was open (say, the tag was tapped over it): cover it now.
        scope.launch {
            latch.state.distinctUntilChangedBy { it.active?.start }.collect { s -> if (s.active != null) front?.let(::check) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        front = pkg
        latch.tick() // a timer or schedule may have ended or started since the last alarm
        check(pkg)
    }

    private fun check(pkg: String) {
        val active = latch.state.value.active ?: return
        if (pkg !in active.blocked) return
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastShown && now - lastShownAt < 800) return // one app can fire several events as it opens
        lastShown = pkg
        lastShownAt = now
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _running = MutableStateFlow(false)
        /** True while Android has the service on and connected. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
