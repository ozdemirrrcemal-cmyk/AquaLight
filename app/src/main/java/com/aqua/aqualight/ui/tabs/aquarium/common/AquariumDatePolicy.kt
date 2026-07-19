package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import java.util.Calendar
import java.util.Locale

object AquariumDatePolicy {
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun setupDateLocale(context: Context): Locale {
        return LocaleFormatter.appLocale(context)
    }

    /**
     * Source-compatible locale boundary for picker APIs that accept a Locale argument.
     * It follows the AppCompat per-app language and never falls back to an unsupported
     * device locale.
     */
    val setupDateLocale: Locale
        get() = currentApplicationLocale()

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

    /** Source-compatible display boundary for callers that do not own a Context. */
    fun formatSetupDate(
        millis: Long?,
        emptyText: String
    ): String {
        if (millis == null) {
            return emptyText
        }

        return LocaleFormatter.formatDate(
            millis,
            currentApplicationLocale()
        )
    }

    private fun currentApplicationLocale(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        val configuredLocale = if (locales.isEmpty) {
            Locale.forLanguageTag(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)
        } else {
            locales[0] ?: Locale.forLanguageTag(
                SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG
            )
        }
        return LocaleFormatter.resolveSupportedLocale(configuredLocale)
    }
}
