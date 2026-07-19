package com.aqua.aqualight.i18n

import android.content.Context
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Locale-aware, per-call formatters. NumberFormat and DateFormat are not shared across threads. */
object LocaleFormatter {

    /**
     * Returns a context whose resources follow the AndroidX per-app language selection.
     * This is required for framework dialogs and non-AppCompat contexts on API 32 and lower.
     */
    fun localizedContext(context: Context): Context {
        return ContextCompat.getContextForLanguage(context)
    }

    fun appLocale(context: Context): Locale {
        val locales = localizedContext(context).resources.configuration.locales
        val configuredLocale = if (locales.isEmpty) {
            Locale.forLanguageTag(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)
        } else {
            locales[0]
        }
        return resolveSupportedLocale(configuredLocale)
    }

    internal fun resolveSupportedLocale(configuredLocale: Locale): Locale {
        return Locale.forLanguageTag(
            SupportedLocaleRegistry.resolve(configuredLocale.toLanguageTag())
        )
    }

    fun formatInteger(context: Context, value: Number): String {
        return formatInteger(value, appLocale(context))
    }

    fun formatDecimal(
        context: Context,
        value: Number,
        maximumFractionDigits: Int = 2
    ): String {
        return formatDecimal(value, appLocale(context), maximumFractionDigits)
    }

    fun formatPercent(
        context: Context,
        fraction: Number,
        maximumFractionDigits: Int = 0
    ): String {
        return formatPercent(fraction, appLocale(context), maximumFractionDigits)
    }

    fun formatDate(context: Context, timeMillis: Long): String {
        return formatDate(timeMillis, appLocale(context))
    }

    fun formatTime(context: Context, timeMillis: Long): String {
        return formatTime(timeMillis, appLocale(context))
    }

    fun formatDateTime(context: Context, timeMillis: Long): String {
        return formatDateTime(timeMillis, appLocale(context))
    }

    internal fun formatInteger(value: Number, locale: Locale): String {
        return NumberFormat.getIntegerInstance(locale).format(value)
    }

    internal fun formatDecimal(
        value: Number,
        locale: Locale,
        maximumFractionDigits: Int = 2
    ): String {
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits.coerceAtLeast(0)
        }.format(value)
    }

    internal fun formatPercent(
        fraction: Number,
        locale: Locale,
        maximumFractionDigits: Int = 0
    ): String {
        return NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits.coerceAtLeast(0)
        }.format(fraction)
    }

    internal fun formatDate(timeMillis: Long, locale: Locale): String {
        return DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            locale
        ).format(Date(timeMillis))
    }

    internal fun formatTime(timeMillis: Long, locale: Locale): String {
        return DateFormat.getTimeInstance(
            DateFormat.SHORT,
            locale
        ).format(Date(timeMillis))
    }

    internal fun formatDateTime(timeMillis: Long, locale: Locale): String {
        return DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            locale
        ).format(Date(timeMillis))
    }
}
