package com.aqua.aqualight.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocaleRegistryTest {

    @Test
    fun onlyCompleteEnglishResourcesAreCommerciallyEnabled() {
        assertEquals(setOf("en"), SupportedLocaleRegistry.all)
        assertTrue(SupportedLocaleRegistry.isSupported("en"))
        assertFalse(SupportedLocaleRegistry.isSupported("tr"))
    }

    @Test
    fun previouslyAdvertisedUntranslatedLanguagesMigrateToEnglish() {
        listOf("tr", "de", "fr", "ru", "zh").forEach { languageTag ->
            assertEquals(
                SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
                SupportedLocaleRegistry.normalizeStoredLanguageTag(languageTag)
            )
        }
    }

    @Test
    fun arbitraryAndNonCanonicalTagsAreNotAcceptedForPersistence() {
        assertNull(SupportedLocaleRegistry.normalizeStoredLanguageTag("es"))
        assertNull(SupportedLocaleRegistry.normalizeStoredLanguageTag("EN"))
        assertNull(SupportedLocaleRegistry.normalizeStoredLanguageTag(" en "))
        assertNull(SupportedLocaleRegistry.normalizeStoredLanguageTag(""))
    }

    @Test
    fun runtimeResolutionAlwaysReturnsASupportedLanguage() {
        assertEquals("en", SupportedLocaleRegistry.resolve("en"))
        assertEquals("en", SupportedLocaleRegistry.resolve("tr"))
        assertEquals("en", SupportedLocaleRegistry.resolve("es"))
        assertEquals("en", SupportedLocaleRegistry.resolve(null))
    }
}
