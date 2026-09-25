package com.latch.focus.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("Match phone"), Light("Light"), Dark("Dark") }

/**
 * Device preferences that aren't part of Latch's data: theme and haptics. Kept in SharedPreferences, separate from
 * latch.json, so "Reset Latch" starts your data over without also flipping your theme.
 */
object Prefs {
    private const val FILE = "latch_prefs"
    private val _theme = MutableStateFlow(ThemeMode.System)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    fun load(context: Context) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _theme.value = ThemeMode.entries.firstOrNull { it.name == sp.getString("theme", null) } ?: ThemeMode.System
        _haptics.value = sp.getBoolean("haptics", true)
    }

    fun setTheme(context: Context, mode: ThemeMode) {
        _theme.value = mode
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme", mode.name).apply()
    }

    fun setHaptics(context: Context, on: Boolean) {
        _haptics.value = on
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("haptics", on).apply()
    }
}
