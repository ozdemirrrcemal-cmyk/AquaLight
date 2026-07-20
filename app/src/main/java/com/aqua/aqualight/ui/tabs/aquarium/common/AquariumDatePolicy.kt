package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.aqua.aqualight.i18n.DateOnly
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import java.time.LocalDate
import java.util.Locale

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun setupDateLocale(context: Context): Locale {
        return LocaleFormatter.appLocale(context)
    }

    /** Picker locale follows the AppCompat per-app language. */
    val setupDateLocale: Locale
        get() = currentApplicationLocale()

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int = LocalDate.now().year + MAX_YEAR_OFFSET

    fun pickerMillis(epochDay: Long?): Long? = epochDay?.let(DateOnly::toPickerMillis)

    fun epochDayFromPickerMillis(timeMillis: Long): Long = DateOnly.fromPickerMillis(timeMillis)

    fun formatSetupDate(
        context: Context,
        epochDay: Long?,
        emptyText: String
    ): String {
        if (epochDay == null) {
            return emptyText
        }

        return LocaleFormatter.formatDateEpochDay(context, epochDay)
    }

    /** Display boundary for callers that do not own a Context. */
    fun formatSetupDate(
        epochDay: Long?,
        emptyText: String
    ): String {
        if (epochDay == null) {
            return emptyText
        }

        return LocaleFormatter.formatDateEpochDay(
            epochDay,
            currentApplicationLocale()
        )
    }

    private fun currentApplicationLocale(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        val deviceDefault = Locale.forLanguageTag(
            SupportedLocaleRegistry.deviceDefault()
        )
        val configuredLocale = if (locales.isEmpty) {
            deviceDefault
        } else {
            locales[0] ?: deviceDefault
        }
        return LocaleFormatter.resolveSupportedLocale(configuredLocale)
    }
}
