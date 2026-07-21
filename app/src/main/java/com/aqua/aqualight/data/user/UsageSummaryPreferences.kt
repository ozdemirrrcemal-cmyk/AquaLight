package com.aqua.aqualight.data.user

/**
 * Removes the device-local usage summary without touching appearance or other app settings.
 *
 * The summary belongs to the active signed-in session and must not cross an account boundary
 * on a shared device.
 */
internal fun UserPreferences.withoutUsageSummary(): UserPreferences {
    return toBuilder()
        .setWeeklyAutomationCount(0)
        .setWeeklyAlertCount(0)
        .setTodayAutomationCount(0)
        .setTodayManualActionCount(0)
        .setLastEventTimeMillis(0L)
        .setLastEventDescription("")
        .setLastUsageDayKey("")
        .setLastUsageWeekKey("")
        .build()
}

internal suspend fun UserPreferencesManager.clearUsageSummary() {
    update { preferences -> preferences.withoutUsageSummary() }
}
