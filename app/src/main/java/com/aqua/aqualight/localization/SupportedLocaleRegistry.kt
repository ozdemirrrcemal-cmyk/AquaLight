package com.aqua.aqualight.localization

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import java.util.Locale

enum class LocaleAvailability {
    PUBLISHED,
    PLANNED
}

data class SupportedLocale(
    val languageTag: String,
    @StringRes val displayNameRes: Int,
    @DrawableRes val flagRes: Int,
    val availability: LocaleAvailability
) {
    val isPublished: Boolean
        get() = availability == LocaleAvailability.PUBLISHED
}

/**
 * Single source of truth for AquaLight application locales.
 *
 * A locale must remain PLANNED until its complete Android resource package is present and passes
 * the Stage 11 placeholder/completeness gates. Planned locales are intentionally hidden from the
 * language picker so Android never presents untranslated fallback content as a supported language.
 */
object SupportedLocaleRegistry {

    const val DEFAULT_LANGUAGE_TAG = "en"

    val locales: List<SupportedLocale> = listOf(
        SupportedLocale(
            languageTag = "tr",
            displayNameRes = R.string.language_turkish,
            flagRes = R.drawable.flag_tr,
            availability = LocaleAvailability.PLANNED
        ),
        SupportedLocale(
            languageTag = DEFAULT_LANGUAGE_TAG,
            displayNameRes = R.string.language_english,
            flagRes = R.drawable.flag_en,
            availability = LocaleAvailability.PUBLISHED
        ),
        SupportedLocale(
            languageTag = "de",
            displayNameRes = R.string.language_german,
            flagRes = R.drawable.flag_de,
            availability = LocaleAvailability.PLANNED
        ),
        SupportedLocale(
            languageTag = "fr",
            displayNameRes = R.string.language_french,
            flagRes = R.drawable.flag_fr,
            availability = LocaleAvailability.PLANNED
        )
    )

    val publishedLocales: List<SupportedLocale> = locales.filter(SupportedLocale::isPublished)
    val plannedLocales: List<SupportedLocale> = locales.filterNot(SupportedLocale::isPublished)

    init {
        require(locales.map { it.languageTag }.distinct().size == locales.size) {
            "Supported locale tags must be unique."
        }
        require(publishedLocales.any { it.languageTag == DEFAULT_LANGUAGE_TAG }) {
            "The default locale must always be published."
        }
    }

    fun find(languageTag: String?): SupportedLocale? {
        val canonical = canonicalize(languageTag) ?: return null
        return locales.firstOrNull { locale ->
            locale.languageTag.equals(canonical, ignoreCase = true)
        }
    }

    fun findPublished(languageTag: String?): SupportedLocale? {
        return find(languageTag)?.takeIf(SupportedLocale::isPublished)
    }

    fun normalizePublishedTag(languageTag: String?): String {
        return findPublished(languageTag)?.languageTag ?: DEFAULT_LANGUAGE_TAG
    }

    @StringRes
    fun displayNameRes(languageTag: String?): Int {
        return findPublished(languageTag)?.displayNameRes
            ?: requireNotNull(findPublished(DEFAULT_LANGUAGE_TAG)).displayNameRes
    }

    fun isPublished(languageTag: String?): Boolean = findPublished(languageTag) != null

    private fun canonicalize(languageTag: String?): String? {
        val candidate = languageTag.orEmpty().trim().replace('_', '-')
        if (candidate.isBlank()) return null

        val locale = Locale.forLanguageTag(candidate)
        return locale.toLanguageTag().takeUnless { it == "und" }
    }
}
