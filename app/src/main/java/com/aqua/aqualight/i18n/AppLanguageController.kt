package com.aqua.aqualight.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative boundary for the language that Android/AppCompat is actually applying.
 *
 * Persistent stores mirror this value for startup and settings durability; they must not report a
 * different selected language from the locale currently rendering the application.
 */
object AppLanguageController {

    private val mutableLanguageChanges = MutableStateFlow(current())

    /** Emits whenever AquaLight applies a supported application language in this process. */
    val languageChanges: StateFlow<String> = mutableLanguageChanges.asStateFlow()

    fun currentOrNull(): String? {
        val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (languageTags.isBlank()) return null

        return SupportedLocaleRegistry.runtimeSupportedOrNull(
            languageTags.substringBefore(',')
        )
    }

    /** Follows the Turkish-device/English-everywhere-else first-run product rule. */
    fun current(): String {
        return currentOrNull() ?: SupportedLocaleRegistry.deviceDefault()
    }

    fun apply(languageTag: String) {
        val supportedLanguage = requireNotNull(
            SupportedLocaleRegistry.supportedCanonicalOrNull(languageTag)
        ) {
            "Application language must be an explicit supported locale."
        }

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(supportedLanguage)
        )
        mutableLanguageChanges.value = supportedLanguage
    }
}
