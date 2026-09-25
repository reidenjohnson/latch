package com.latch.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.latch.latch
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
 * Watches which app comes to the front and, during Focus, covers blocked apps with [BlockedActivity].
 *
 * It only listens for "window state changed" events and never reads screen content (canRetrieveWindowContent is
 * false in res/xml/focus_blocker.xml). Accessibility services may start activities from the background:
 * https://developer.android.com/guide/components/activities/background-starts#exceptions
 */
class FocusBlocker : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** The last app in front that isn't Latch or the system UI. */
    private var front: String? = null
    private var lastShown: String? = null
    private var lastShownAt = 0L

    override fun onServiceConnected() {
        _running.value = true
        // Focus turned on while a blocked app was open (tag tapped over it): cover it right away.
        scope.launch {
            latch.focus.state.distinctUntilChangedBy { it.active }.collect { s ->
                if (s.active != null) front?.let { check(it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        front = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        val active = latch.focus.state.value.active ?: return
        if (pkg !in active.blocked) return
        // One app can fire several events as it opens. Don't stack block screens.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastShown && now - lastShownAt < 800) return
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
        /** True while Android has the service turned on and connected. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
