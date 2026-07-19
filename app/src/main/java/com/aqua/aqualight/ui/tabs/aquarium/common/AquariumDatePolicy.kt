package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.localization.LocaleFormatters
import java.util.Calendar

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int = Calendar.getInstance().get(Calendar.YEAR) + MAX_YEAR_OFFSET

    fun formatSetupDate(
        context: Context,
        millis: Long?,
        emptyText: String
    ): String = millis?.let { LocaleFormatters.formatDate(context, it) } ?: emptyText
}
