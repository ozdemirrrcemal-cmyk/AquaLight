package com.aqua.aqualight.localization

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import java.util.Locale

data class SupportedLocale(
    val languageTag: String,
    @StringRes val displayNameRes: Int
)

/**
 * Production source of truth for AquaLight application locales.
 *
 * A locale may be added only after its complete Android string catalog, placeholder validation and
 * linguistic review are finished. The phone locale is never treated as an implicitly supported app
 * locale.
 */
object SupportedLocaleRegistry {

    const val DEFAULT_LANGUAGE_TAG = "en"

    val supportedLocales: List<SupportedLocale> = listOf(
        SupportedLocale(
            languageTag = DEFAULT_LANGUAGE_TAG,
            displayNameRes = R.string.language_english
        )
    )

    private val byCanonicalTag: Map<String, SupportedLocale> =
        supportedLocales.associateBy { supported -> canonicalize(supported.languageTag) }

    fun normalize(rawLanguageTag: String?): String {
        val canonical = canonicalize(rawLanguageTag)
        return byCanonicalTag[canonical]?.languageTag ?: DEFAULT_LANGUAGE_TAG
    }

    fun resolve(rawLanguageTag: String?): SupportedLocale =
        byCanonicalTag.getValue(normalize(rawLanguageTag))

    fun isSupported(rawLanguageTag: String?): Boolean =
        canonicalize(rawLanguageTag) in byCanonicalTag

    fun asJavaLocale(rawLanguageTag: String?): Locale =
        Locale.forLanguageTag(normalize(rawLanguageTag))

    private fun canonicalize(rawLanguageTag: String?): String {
        val normalizedSeparators = rawLanguageTag
            ?.trim()
            ?.replace('_', '-')
            .orEmpty()
        if (normalizedSeparators.isBlank()) return ""

        val locale = Locale.forLanguageTag(normalizedSeparators)
        if (locale.language.isBlank()) return ""

        return locale.toLanguageTag().lowercase(Locale.ROOT)
    }
}
