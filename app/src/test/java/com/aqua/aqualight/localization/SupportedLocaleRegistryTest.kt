package com.aqua.aqualight.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocaleRegistryTest {

    @Test
    fun onlyEnglishIsPublishedUntilReviewedTranslationPacksExist() {
        assertEquals(
            listOf("en"),
            SupportedLocaleRegistry.publishedLocales.map { locale -> locale.languageTag }
        )
        assertEquals(
            listOf("tr", "de", "fr"),
            SupportedLocaleRegistry.plannedLocales.map { locale -> locale.languageTag }
        )
    }

    @Test
    fun removedLocalesAreNotPartOfTheProductContract() {
        assertNull(SupportedLocaleRegistry.find("ru"))
        assertNull(SupportedLocaleRegistry.find("zh"))
        assertFalse(SupportedLocaleRegistry.isPublished("ru"))
        assertFalse(SupportedLocaleRegistry.isPublished("zh"))
    }

    @Test
    fun onlyPublishedTagsCanResolveForSelection() {
        assertTrue(SupportedLocaleRegistry.isPublished("en"))
        assertFalse(SupportedLocaleRegistry.isPublished("tr"))
        assertEquals(
            SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
            SupportedLocaleRegistry.normalizePublishedTag("tr")
        )
        assertEquals(
            SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
            SupportedLocaleRegistry.normalizePublishedTag("unknown")
        )
    }
}
