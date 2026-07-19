package com.aqua.aqualight.localization

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import java.util.Locale

/**
 * Single source of truth for locales that are complete enough to be exposed in production.
 *
 * Translation staging folders may exist before a locale is enabled. A locale must only be added
 * here after every translatable resource passes placeholder parity and product review.
 */
data class SupportedLocale(
    val languageTag: String,
    @StringRes val displayNameRes: Int
)

object SupportedLocaleRegistry {
    const val DEFAULT_LANGUAGE_TAG = "en"

    val supportedLocales: List<SupportedLocale> = listOf(
        SupportedLocale(
            languageTag = DEFAULT_LANGUAGE_TAG,
            displayNameRes = R.string.language_english
        )
    )

    private val supportedByTag = supportedLocales.associateBy { it.languageTag }

    fun normalize(languageTag: String?): String {
        val normalized = languageTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(Locale::forLanguageTag)
            ?.language
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return normalized.takeIf(supportedByTag::containsKey) ?: DEFAULT_LANGUAGE_TAG
    }

    fun isSupported(languageTag: String?): Boolean {
        val normalized = languageTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(Locale::forLanguageTag)
            ?.language
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return supportedByTag.containsKey(normalized)
    }

    fun locale(languageTag: String?): SupportedLocale {
        return supportedByTag.getValue(normalize(languageTag))
    }

    fun languageTags(): String = supportedLocales.joinToString(",") { it.languageTag }
}
