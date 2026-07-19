package com.aqua.aqualight.i18n

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat as AndroidDateFormat
import androidx.core.content.ContextCompat
import java.text.DateFormat as JavaDateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Locale-aware, per-call formatters. NumberFormat and DateFormat are not shared across threads. */
object LocaleFormatter {

    /**
     * Returns a context whose resources follow the AndroidX per-app language selection.
     * Before an explicit user choice is available, Turkish devices use Turkish and every other
     * device uses English. Unsupported framework locales can therefore never leak into formatting.
     *
     * This context is for resource and formatting work only. Window-owning UI such as Dialog must
     * always use its live Activity context.
     */
    fun localizedContext(context: Context): Context {
        val languageContext = ContextCompat.getContextForLanguage(context)
        val locales = languageContext.resources.configuration.locales
        val configuredLocale = if (locales.isEmpty) {
            Locale.forLanguageTag(SupportedLocaleRegistry.deviceDefault())
        } else {
            locales[0]
        }
        val supportedLocale = resolveSupportedLocale(configuredLocale)

        if (configuredLocale.toLanguageTag() == supportedLocale.toLanguageTag()) {
            return languageContext
        }

        val configuration = Configuration(languageContext.resources.configuration).apply {
            setLocale(supportedLocale)
            setLayoutDirection(supportedLocale)
        }
        return languageContext.createConfigurationContext(configuration)
    }

    fun appLocale(context: Context): Locale {
        val locales = localizedContext(context).resources.configuration.locales
        return if (locales.isEmpty) {
            Locale.forLanguageTag(SupportedLocaleRegistry.deviceDefault())
        } else {
            resolveSupportedLocale(locales[0])
        }
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
        val localizedContext = localizedContext(context)
        return formatTime(
            timeMillis = timeMillis,
            locale = appLocale(localizedContext),
            is24Hour = AndroidDateFormat.is24HourFormat(localizedContext)
        )
    }

    fun formatDateTime(context: Context, timeMillis: Long): String {
        val localizedContext = localizedContext(context)
        return formatDateTime(
            timeMillis = timeMillis,
            locale = appLocale(localizedContext),
            is24Hour = AndroidDateFormat.is24HourFormat(localizedContext)
        )
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
        return JavaDateFormat.getDateInstance(
            JavaDateFormat.MEDIUM,
            locale
        ).format(Date(timeMillis))
    }

    /** Locale-default overload retained for deterministic JVM formatter coverage. */
    internal fun formatTime(timeMillis: Long, locale: Locale): String {
        return JavaDateFormat.getTimeInstance(
            JavaDateFormat.SHORT,
            locale
        ).format(Date(timeMillis))
    }

    internal fun formatTime(
        timeMillis: Long,
        locale: Locale,
        is24Hour: Boolean
    ): String {
        val skeleton = if (is24Hour) "Hm" else "hm"
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).format(Date(timeMillis))
    }

    /** Locale-default overload retained for deterministic JVM formatter coverage. */
    internal fun formatDateTime(timeMillis: Long, locale: Locale): String {
        return JavaDateFormat.getDateTimeInstance(
            JavaDateFormat.MEDIUM,
            JavaDateFormat.SHORT,
            locale
        ).format(Date(timeMillis))
    }

    internal fun formatDateTime(
        timeMillis: Long,
        locale: Locale,
        is24Hour: Boolean
    ): String {
        val skeleton = if (is24Hour) "yMMMdHm" else "yMMMdhm"
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).format(Date(timeMillis))
    }
}
