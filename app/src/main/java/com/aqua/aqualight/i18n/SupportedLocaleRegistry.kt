package com.aqua.aqualight.i18n

import java.util.Locale

/**
 * Single commercial source of truth for complete, reviewed application languages.
 *
 * Persisted user choices are intentionally strict: only the canonical language tags declared
 * here are accepted. Runtime locale variants such as tr-TR and en-US are reduced to their
 * supported language without creating hidden compatibility or migration paths.
 */
object SupportedLocaleRegistry {

    const val ENGLISH_LANGUAGE_TAG = "en"
    const val TURKISH_LANGUAGE_TAG = "tr"
    const val DEFAULT_LANGUAGE_TAG = ENGLISH_LANGUAGE_TAG

    private val supportedLanguageTags = linkedSetOf(
        ENGLISH_LANGUAGE_TAG,
        TURKISH_LANGUAGE_TAG
    )

    val all: Set<String>
        get() = supportedLanguageTags.toSet()

    fun isSupported(languageTag: String): Boolean {
        return supportedCanonicalOrNull(languageTag) != null
    }

    /** Accepts only exact canonical values that may be persisted as an explicit user choice. */
    fun supportedCanonicalOrNull(languageTag: String): String? {
        val canonical = canonicalize(languageTag) ?: return null
        return canonical.takeIf {
            canonical == languageTag && canonical in supportedLanguageTags
        }
    }

    /**
     * Validates persisted settings without migrating removed or arbitrary languages.
     * The application has not shipped, so unsupported historical values are rejected cleanly.
     */
    fun normalizeStoredLanguageTag(languageTag: String): String? {
        return supportedCanonicalOrNull(languageTag)
    }

    /** Uses Turkish on Turkish devices and English for every other device language. */
    fun deviceDefault(deviceLocale: Locale = Locale.getDefault()): String {
        val language = deviceLocale.language.lowercase(Locale.ROOT)
        return language.takeIf(supportedLanguageTags::contains)
            ?: DEFAULT_LANGUAGE_TAG
    }

    /**
     * Resolves framework/runtime locale variants to one of the two supported application
     * languages. Missing or unsupported runtime input follows the supported device default.
     */
    fun resolve(languageTag: String?): String {
        val language = canonicalize(languageTag.orEmpty())
            ?.substringBefore('-')
            ?.takeIf(supportedLanguageTags::contains)

        return language ?: deviceDefault()
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
