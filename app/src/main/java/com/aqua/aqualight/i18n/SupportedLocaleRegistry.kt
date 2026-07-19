package com.aqua.aqualight.i18n

import java.util.Locale

/**
 * Single commercial source of truth for languages that have complete, reviewed resources.
 *
 * A language must not be added here until its values-xx resources are complete and the
 * localization guard passes. Previously advertised but untranslated choices are migrated
 * safely to English instead of leaving the application in a falsely supported locale.
 */
object SupportedLocaleRegistry {

    const val DEFAULT_LANGUAGE_TAG = "en"

    private val supportedLanguageTags = linkedSetOf(
        DEFAULT_LANGUAGE_TAG
    )

    private val legacyAdvertisedLanguageTags = setOf(
        "tr",
        "de",
        "fr",
        "ru",
        "zh"
    )

    val all: Set<String>
        get() = supportedLanguageTags.toSet()

    fun isSupported(languageTag: String): Boolean {
        return supportedCanonicalOrNull(languageTag) != null
    }

    fun supportedCanonicalOrNull(languageTag: String): String? {
        val canonical = canonicalize(languageTag) ?: return null
        return canonical.takeIf {
            canonical == languageTag && canonical in supportedLanguageTags
        }
    }

    /**
     * Normalizes persisted settings without silently accepting arbitrary unsupported locales.
     * Canonical legacy choices from the old language screen migrate to English.
     */
    fun normalizeStoredLanguageTag(languageTag: String): String? {
        val canonical = canonicalize(languageTag) ?: return null
        if (canonical != languageTag) return null

        return when (canonical) {
            in supportedLanguageTags -> canonical
            in legacyAdvertisedLanguageTags -> DEFAULT_LANGUAGE_TAG
            else -> null
        }
    }

    /** Resolves process/runtime input fail-safely to a genuinely supported language. */
    fun resolve(languageTag: String?): String {
        val canonical = canonicalize(languageTag.orEmpty())
        return canonical
            ?.takeIf(supportedLanguageTags::contains)
            ?: DEFAULT_LANGUAGE_TAG
    }

    private fun canonicalize(languageTag: String): String? {
        val trimmed = languageTag.trim()
        if (trimmed.isEmpty()) return null

        val locale = Locale.forLanguageTag(trimmed.replace('_', '-'))
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isBlank() || language == "und") return null

        val country = locale.country
        return if (country.isBlank()) {
            language
        } else {
            "$language-${country.uppercase(Locale.ROOT)}"
        }
    }
}
