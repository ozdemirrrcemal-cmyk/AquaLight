package com.aqua.aqualight.ui.tabs.aquarium.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AquariumDatePolicy {
    private const val SETUP_DATE_PATTERN = "dd MMM yyyy"
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    val setupDateLocale: Locale = Locale.ENGLISH

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

        return SimpleDateFormat(
            SETUP_DATE_PATTERN,
            setupDateLocale
        ).format(Date(millis))
    }
}
