package com.aqua.aqualight.application.notifications

/**
 * Process-wide foreground truth shared by central notification delivery policies.
 *
 * Presentation code never reads or mutates this state. The process lifecycle adapter is the only
 * writer, while notification coordinators use it to avoid posting non-essential availability
 * alerts over an already visible application experience.
 */
object AppProcessForegroundState {

    @Volatile
    private var foreground: Boolean = false

    fun isForeground(): Boolean = foreground

    fun update(isForeground: Boolean) {
        foreground = isForeground
    }
}
