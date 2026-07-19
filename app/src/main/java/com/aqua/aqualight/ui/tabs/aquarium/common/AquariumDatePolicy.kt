package com.aqua.aqualight.ui.tabs.aquarium.common

import com.aqua.aqualight.localization.LocaleFormatters
import java.util.Calendar
import java.util.Locale

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR) + MAX_YEAR_OFFSET
    }

    fun formatSetupDate(
        millis: Long?,
        emptyText: String,
        locale: Locale = Locale.getDefault()
    ): String {
        if (millis == null) {
            return emptyText
        }
        return LocaleFormatters.formatDate(millis, locale)
    }
}
