package com.aqua.aqualight.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocaleRegistryTest {

    @Test
    fun defaultLocaleIsTheOnlyProductionLocaleUntilTranslationsAreComplete() {
        assertEquals(listOf("en"), SupportedLocaleRegistry.supportedLocales.map { it.languageTag })
        assertEquals("en", SupportedLocaleRegistry.languageTags())
    }

    @Test
    fun unsupportedAndLegacySelectionsFallBackToEnglish() {
        listOf(null, "", "tr", "de-DE", "fr", "ru", "zh-CN", "invalid").forEach { tag ->
            assertEquals("en", SupportedLocaleRegistry.normalize(tag))
        }
    }

    @Test
    fun supportCheckDoesNotPromoteTranslationStagingLocales() {
        assertTrue(SupportedLocaleRegistry.isSupported("en"))
        assertTrue(SupportedLocaleRegistry.isSupported("en-US"))
        assertFalse(SupportedLocaleRegistry.isSupported("tr"))
        assertFalse(SupportedLocaleRegistry.isSupported("de"))
    }
}
