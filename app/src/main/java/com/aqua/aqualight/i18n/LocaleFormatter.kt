@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.i18n

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat as AndroidDateFormat
import androidx.core.content.ContextCompat
import java.text.DateFormat as JavaDateFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Locale-aware, per-call formatters. NumberFormat and DateFormat are not shared across threads. */
object LocaleFormatter {

    private val unsignedIntegerInputPattern = Regex("^\\d+$")
    private val unsignedDecimalInputPattern = Regex("^(\\d*)([.,])(\\d{1,2})$")

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

    /**
     * Parses an unsigned product decimal using the active app locale.
     *
     * Turkish comma and English point are the primary separators. The alternate separator remains
     * accepted for IME and paste compatibility only when it unambiguously contains one or two
     * fractional digits. Grouping, mixed separators, signs, partial values, three-or-more
     * fractional digits and non-finite values are rejected instead of being silently reinterpreted.
     */
    fun parseDecimal(context: Context, value: CharSequence): Double? {
        return parseDecimal(value.toString(), appLocale(context))
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

    /** Formats a calendar-only value without converting it through a timezone-dependent instant. */
    fun formatDateEpochDay(context: Context, epochDay: Long): String {
        return formatDateEpochDay(epochDay, appLocale(context))
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

    /** Formats a wall-clock schedule value without converting it through a timezone. */
    fun formatTimeOfDay24Hour(context: Context, minutesOfDay: Int): String {
        return formatTimeOfDay24Hour(minutesOfDay, appLocale(context))
    }

    internal fun formatTimeOfDay24Hour(minutesOfDay: Int, locale: Locale): String {
        require(minutesOfDay in 0 until MINUTES_PER_DAY) {
            "minutesOfDay must be inside one day."
        }
        return DateTimeFormatter.ofPattern(TIME_OF_DAY_24_HOUR_PATTERN, locale)
            .format(LocalTime.of(minutesOfDay / MINUTES_PER_HOUR, minutesOfDay % MINUTES_PER_HOUR))
    }

    /** Product integers follow the active app locale and never use grouping separators. */
    internal fun formatInteger(value: Number, locale: Locale): String {
        return NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
        }.format(value)
    }

    /** Product decimals follow the active app locale and never use grouping separators. */
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

    internal fun parseDecimal(value: String, locale: Locale): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        val normalized = when {
            unsignedIntegerInputPattern.matches(trimmed) -> trimmed
            else -> {
                val match = unsignedDecimalInputPattern.matchEntire(trimmed) ?: return null
                val primarySeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
                val alternateSeparator = if (primarySeparator == ',') '.' else ','
                val suppliedSeparator = match.groupValues[2].single()
                if (suppliedSeparator != primarySeparator && suppliedSeparator != alternateSeparator) {
                    return null
                }

                val integerPart = match.groupValues[1].ifEmpty { "0" }
                val fractionalPart = match.groupValues[3]
                "$integerPart.$fractionalPart"
            }
        }

        return normalized.toBigDecimalOrNull()
            ?.toDouble()
            ?.takeIf { it.isFinite() }
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

    internal fun formatDateEpochDay(epochDay: Long, locale: Locale): String {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(DateOnly.toLocalDate(epochDay))
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

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val TIME_OF_DAY_24_HOUR_PATTERN = "HH:mm"
}
