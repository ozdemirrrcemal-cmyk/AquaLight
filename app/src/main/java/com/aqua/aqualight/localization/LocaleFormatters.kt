package com.aqua.aqualight.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-facing formatting boundary.
 *
 * The active AquaLight application locale is authoritative. `Locale.getDefault()` is deliberately
 * not used because the phone locale may differ from the language selected inside AquaLight.
 */
object LocaleFormatters {

    fun currentLocale(context: Context): Locale {
        val appLocaleTag = AppCompatDelegate.getApplicationLocales()
            .get(0)
            ?.toLanguageTag()
        val contextLocaleTag = context.resources.configuration.locales
            .get(0)
            ?.toLanguageTag()

        return SupportedLocaleRegistry.javaLocale(appLocaleTag ?: contextLocaleTag)
    }

    fun formatDate(
        context: Context,
        millis: Long,
        style: Int = DateFormat.MEDIUM
    ): String = formatDate(
        millis = millis,
        locale = currentLocale(context),
        style = style
    )

    fun formatDate(
        millis: Long,
        locale: Locale,
        style: Int = DateFormat.MEDIUM
    ): String = DateFormat.getDateInstance(style, locale).format(Date(millis))

    fun formatTime(
        context: Context,
        millis: Long,
        style: Int = DateFormat.SHORT
    ): String = formatTime(
        millis = millis,
        locale = currentLocale(context),
        style = style
    )

    fun formatTime(
        millis: Long,
        locale: Locale,
        style: Int = DateFormat.SHORT
    ): String = DateFormat.getTimeInstance(style, locale).format(Date(millis))

    fun formatDateTime(
        context: Context,
        millis: Long,
        dateStyle: Int = DateFormat.MEDIUM,
        timeStyle: Int = DateFormat.SHORT
    ): String = DateFormat.getDateTimeInstance(
        dateStyle,
        timeStyle,
        currentLocale(context)
    ).format(Date(millis))

    /**
     * Preserves an established AquaLight English pattern while applying the app locale for month
     * names, digit shaping and symbols. Patterns that become locale-specific must move atomically
     * with a newly enabled locale catalog.
     */
    fun formatPattern(
        context: Context,
        millis: Long,
        pattern: String
    ): String = formatPattern(
        millis = millis,
        pattern = pattern,
        locale = currentLocale(context)
    )

    fun formatPattern(
        millis: Long,
        pattern: String,
        locale: Locale
    ): String = SimpleDateFormat(pattern, locale).format(Date(millis))

    fun formatInteger(context: Context, value: Long): String =
        formatInteger(value, currentLocale(context))

    fun formatInteger(value: Long, locale: Locale): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    fun formatNumber(
        context: Context,
        value: Number,
        maximumFractionDigits: Int = 2,
        minimumFractionDigits: Int = 0,
        groupingUsed: Boolean = false
    ): String = formatNumber(
        value = value,
        locale = currentLocale(context),
        maximumFractionDigits = maximumFractionDigits,
        minimumFractionDigits = minimumFractionDigits,
        groupingUsed = groupingUsed
    )

    fun formatNumber(
        value: Number,
        locale: Locale,
        maximumFractionDigits: Int = 2,
        minimumFractionDigits: Int = 0,
        groupingUsed: Boolean = false
    ): String = NumberFormat.getNumberInstance(locale).apply {
        this.maximumFractionDigits = maximumFractionDigits.coerceAtLeast(0)
        this.minimumFractionDigits = minimumFractionDigits
            .coerceAtLeast(0)
            .coerceAtMost(this.maximumFractionDigits)
        isGroupingUsed = groupingUsed
    }.format(value)

    fun formatPercent(context: Context, percent: Number): String =
        formatPercent(percent, currentLocale(context))

    fun formatPercent(percent: Number, locale: Locale): String =
        NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }.format(percent.toDouble() / 100.0)

    fun parseNumber(context: Context, value: CharSequence?): Number? =
        parseNumber(value, currentLocale(context))

    fun parseNumber(value: CharSequence?, locale: Locale): Number? {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null

        val position = ParsePosition(0)
        val parsed = NumberFormat.getNumberInstance(locale).parse(text, position)
        return parsed?.takeIf { position.index == text.length }
    }
}
