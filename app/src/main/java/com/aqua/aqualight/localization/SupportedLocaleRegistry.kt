package com.aqua.aqualight.localization

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import java.util.Locale

data class SupportedLocale(
    val languageTag: String,
    @StringRes val displayNameRes: Int
)

/**
 * Single production source of truth for application locales.
 *
 * A locale belongs here only after its complete string catalog, placeholder compatibility and
 * linguistic review have all passed. Device/system locales are never treated as implicitly
 * supported.
 */
object SupportedLocaleRegistry {

    const val DEFAULT_LANGUAGE_TAG = "en"

    val supportedLocales: List<SupportedLocale> = listOf(
        SupportedLocale(
            languageTag = DEFAULT_LANGUAGE_TAG,
            displayNameRes = R.string.language_english
        )
    )

    private val localesByNormalizedTag: Map<String, SupportedLocale> =
        supportedLocales.associateBy { locale -> locale.languageTag.lowercase(Locale.ROOT) }

    fun normalizeLanguageTag(rawLanguageTag: String?): String {
        val canonicalTag = rawLanguageTag
            ?.trim()
            ?.replace('_', '-')
            ?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?.toLanguageTag()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return localesByNormalizedTag[canonicalTag]?.languageTag ?: DEFAULT_LANGUAGE_TAG
    }

    fun locale(rawLanguageTag: String?): SupportedLocale =
        localesByNormalizedTag.getValue(normalizeLanguageTag(rawLanguageTag))

    fun isSupported(rawLanguageTag: String?): Boolean {
        val normalizedInput = rawLanguageTag
            ?.trim()
            ?.replace('_', '-')
            ?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?.toLanguageTag()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return normalizedInput in localesByNormalizedTag
    }

    fun javaLocale(rawLanguageTag: String?): Locale =
        Locale.forLanguageTag(normalizeLanguageTag(rawLanguageTag))
}
