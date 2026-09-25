package com.latch.focus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Little rotating ways of saying "locked", so the lock screen doesn't read the same every time.
 * A new one is picked each time a screen appears, and it stays put while that screen is showing.
 */
object Words {
    /** Follows an app name: "Pinterest is on ice". */
    val locked = listOf(
        "is locked down", "is on ice", "is bolted shut", "is under lock and key", "is sealed up",
        "is snoozing", "is on lockdown", "is off-limits", "is benched", "is in the vault",
        "is padlocked", "is shelved", "is grounded", "is on a timeout", "is taking a nap",
        "is out of reach", "is behind glass", "is boarded up", "is shut tight", "is latched",
    )

    /** A one-word state for the home disc and the tap card: "Latched", "Locked down". */
    val state = listOf(
        "Latched", "Locked down", "Bolted", "Sealed", "On ice", "Padlocked", "Shut tight", "Vaulted",
        "Grounded", "Benched", "Buttoned up", "On lockdown",
    )

    fun pick(list: List<String>): String = list.random()
}

/** A random word that holds still until this screen leaves. */
@Composable
fun rememberWord(list: List<String>, key: Any? = null): String = remember(key) { Words.pick(list) }
