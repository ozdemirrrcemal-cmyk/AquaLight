package com.aqua.aqualight.data.user

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSummaryPreferencesTest {

    @Test
    fun withoutUsageSummaryClearsOnlySessionSummaryFields() {
        val preferences = UserPreferences.newBuilder()
            .setThemeMode("light")
            .setLanguageCode("tr")
            .setAutoUpdateEnabled(true)
            .setWeeklyAutomationCount(12)
            .setWeeklyAlertCount(3)
            .setTodayAutomationCount(4)
            .setTodayManualActionCount(2)
            .setLastEventTimeMillis(123456789L)
            .setLastEventDescription("Lighting schedule started")
            .setLastUsageDayKey("2026-07-21")
            .setLastUsageWeekKey("2026-W30")
            .build()

        val cleared = preferences.withoutUsageSummary()

        assertEquals(0, cleared.weeklyAutomationCount)
        assertEquals(0, cleared.weeklyAlertCount)
        assertEquals(0, cleared.todayAutomationCount)
        assertEquals(0, cleared.todayManualActionCount)
        assertEquals(0L, cleared.lastEventTimeMillis)
        assertEquals("", cleared.lastEventDescription)
        assertEquals("", cleared.lastUsageDayKey)
        assertEquals("", cleared.lastUsageWeekKey)

        assertEquals("light", cleared.themeMode)
        assertEquals("tr", cleared.languageCode)
        assertEquals(true, cleared.autoUpdateEnabled)
    }
}
