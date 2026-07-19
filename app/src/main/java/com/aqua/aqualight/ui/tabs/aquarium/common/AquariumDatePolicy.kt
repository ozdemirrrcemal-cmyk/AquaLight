package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
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
     * Temporary source-compatible bridge for existing setup-date callers.
     * It deliberately uses the supported commercial default and never the device locale.
     */
    @Deprecated(
        message = "Pass a Context so the per-app locale is used.",
        replaceWith = ReplaceWith("setupDateLocale(context)")
    )
    val setupDateLocale: Locale
        get() = Locale.forLanguageTag(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)

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

    /**
     * Temporary source-compatible bridge for existing display callers.
     * It prevents unsupported device locales from leaking before those callers migrate.
     */
    @Deprecated(
        message = "Pass a Context so the per-app locale is used.",
        replaceWith = ReplaceWith("formatSetupDate(context, millis, emptyText)")
    )
    fun formatSetupDate(
        millis: Long?,
        emptyText: String
    ): String {
        if (millis == null) {
            return emptyText
        }

        return LocaleFormatter.formatDate(
            millis,
            Locale.forLanguageTag(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)
        )
    }
}
