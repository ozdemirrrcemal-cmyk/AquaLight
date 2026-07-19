package com.aqua.aqualight.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocaleRegistryTest {

    @Test
    fun `only reviewed English locale is production supported`() {
        assertEquals(
            listOf(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG),
            SupportedLocaleRegistry.supportedLocales.map { locale -> locale.languageTag }
        )
    }

    @Test
    fun `unsupported saved locale is normalized to English`() {
        listOf("tr", "de", "fr", "ru", "zh", "zh-CN", "invalid", "").forEach { code ->
            assertEquals(
                SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
                SupportedLocaleRegistry.normalizeLanguageTag(code)
            )
        }
    }

    @Test
    fun `canonical and case-varied English resolve to supported locale`() {
        assertEquals("en", SupportedLocaleRegistry.normalizeLanguageTag("en"))
        assertEquals("en", SupportedLocaleRegistry.normalizeLanguageTag("EN"))
        assertTrue(SupportedLocaleRegistry.isSupported("en"))
        assertTrue(SupportedLocaleRegistry.isSupported("EN"))
        assertFalse(SupportedLocaleRegistry.isSupported("zh"))
    }
}
