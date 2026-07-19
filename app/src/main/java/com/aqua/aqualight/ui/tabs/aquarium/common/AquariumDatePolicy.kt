package com.aqua.aqualight.ui.tabs.aquarium.common

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    val setupDateLocale: Locale
        get() = Locale.getDefault()

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR) + MAX_YEAR_OFFSET
    }

    fun formatSetupDate(
        millis: Long?,
        emptyText: String
    ): String {
        if (millis == null) {
            return emptyText
        }

        return DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            setupDateLocale
        ).format(Date(millis))
    }
}
