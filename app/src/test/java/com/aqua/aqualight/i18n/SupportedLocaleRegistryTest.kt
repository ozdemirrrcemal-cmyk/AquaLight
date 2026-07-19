package com.aqua.aqualight.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocaleRegistryTest {

    @Test
    fun onlyCompleteTurkishAndEnglishResourcesAreCommerciallyEnabled() {
        assertEquals(setOf("en", "tr"), SupportedLocaleRegistry.all)
        assertTrue(SupportedLocaleRegistry.isSupported("en"))
        assertTrue(SupportedLocaleRegistry.isSupported("tr"))
        assertFalse(SupportedLocaleRegistry.isSupported("de"))
        assertFalse(SupportedLocaleRegistry.isSupported("fr"))
        assertFalse(SupportedLocaleRegistry.isSupported("ru"))
        assertFalse(SupportedLocaleRegistry.isSupported("zh"))
    }

    @Test
    fun persistedChoiceAcceptsOnlyExactCanonicalSupportedTags() {
        assertEquals("en", SupportedLocaleRegistry.normalizeStoredLanguageTag("en"))
        assertEquals("tr", SupportedLocaleRegistry.normalizeStoredLanguageTag("tr"))
        listOf("de", "fr", "ru", "zh", "es", "EN", "TR", " en ", "tr-TR", "")
            .forEach { languageTag ->
                assertNull(SupportedLocaleRegistry.normalizeStoredLanguageTag(languageTag))
            }
    }

    @Test
    fun firstRunDefaultUsesTurkishOnlyForTurkishDevices() {
        assertEquals("tr", SupportedLocaleRegistry.deviceDefault(Locale("tr", "TR")))
        assertEquals("en", SupportedLocaleRegistry.deviceDefault(Locale.US))
        assertEquals("en", SupportedLocaleRegistry.deviceDefault(Locale.GERMANY))
    }

    @Test
    fun runtimeResolutionSupportsRegionalVariantsWithoutAddingLanguages() {
        assertEquals("en", SupportedLocaleRegistry.resolve("en"))
        assertEquals("en", SupportedLocaleRegistry.resolve("en-US"))
        assertEquals("tr", SupportedLocaleRegistry.resolve("tr"))
        assertEquals("tr", SupportedLocaleRegistry.resolve("tr-TR"))
    }
}
