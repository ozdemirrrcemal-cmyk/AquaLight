package com.aqua.aqualight.ui.common.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentUriPolicyTest {

    @Test
    fun `document asset follows supported runtime language`() {
        assertEquals(
            "privacy_policy_tr.html",
            LegalDocumentAsset.PRIVACY_POLICY.fileName("tr-TR")
        )
        assertEquals(
            "terms_of_use_en.html",
            LegalDocumentAsset.TERMS_OF_USE.fileName("en-US")
        )
    }

    @Test
    fun `asset url accepts only simple bundled html file names`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/privacy_policy_en.html",
            LegalDocumentUriPolicy.assetUrl("privacy_policy_en.html")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `asset url rejects path traversal`() {
        LegalDocumentUriPolicy.assetUrl("../privacy_policy_en.html")
    }

    @Test
    fun `only appassets asset origin is treated as bundled`() {
        assertTrue(
            LegalDocumentUriPolicy.isBundledAsset(
                "https://appassets.androidplatform.net/assets/terms_of_use_en.html"
            )
        )
        assertFalse(
            LegalDocumentUriPolicy.isBundledAsset(
                "https://example.com/assets/terms_of_use_en.html"
            )
        )
        assertFalse(
            LegalDocumentUriPolicy.isBundledAsset(
                "file:///android_asset/terms_of_use_en.html"
            )
        )
    }

    @Test
    fun `external navigation is limited to https and mailto`() {
        assertTrue(LegalDocumentUriPolicy.mayOpenExternally("https://example.com/privacy"))
        assertTrue(LegalDocumentUriPolicy.mayOpenExternally("mailto:support@myaqualight.com"))
        assertFalse(LegalDocumentUriPolicy.mayOpenExternally("http://example.com"))
        assertFalse(LegalDocumentUriPolicy.mayOpenExternally("javascript:alert(1)"))
        assertFalse(LegalDocumentUriPolicy.mayOpenExternally("intent://example"))
    }
}
