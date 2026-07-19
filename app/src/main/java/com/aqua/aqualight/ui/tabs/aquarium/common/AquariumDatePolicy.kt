package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.i18n.LocaleFormatter
import java.util.Calendar
import java.util.Locale

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun setupDateLocale(context: Context): Locale {
        return LocaleFormatter.appLocale(context)
    }

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR) + MAX_YEAR_OFFSET
    }

    fun formatSetupDate(
        context: Context,
        millis: Long?,
        emptyText: String
    ): String {
        if (millis == null) {
            return emptyText
        }

        return LocaleFormatter.formatDate(context, millis)
    }
}
