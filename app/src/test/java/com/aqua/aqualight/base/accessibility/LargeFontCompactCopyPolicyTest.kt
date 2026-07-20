package com.aqua.aqualight.base.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFontCompactCopyPolicyTest {

    @Test
    fun normalFontScalePreservesOriginalPrimaryActionCopy() {
        assertEquals(
            "+ Add",
            LargeFontCompactCopyPolicy.compactPrimaryActionText(
                originalText = "+ Add",
                plusText = "+",
                fontScale = 1.0f
            )
        )
        assertFalse(LargeFontCompactCopyPolicy.shouldUseCompactCopy(1.79f))
    }

    @Test
    fun nearTwoHundredPercentCompactsLeadingPlusActionOnly() {
        assertEquals(
            "+",
            LargeFontCompactCopyPolicy.compactPrimaryActionText(
                originalText = "+ Add",
                plusText = "+",
                fontScale = 2.0f
            )
        )
        assertEquals(
            "+",
            LargeFontCompactCopyPolicy.compactPrimaryActionText(
                originalText = "  + Ekle",
                plusText = "+",
                fontScale = 1.8f
            )
        )
        assertTrue(LargeFontCompactCopyPolicy.shouldUseCompactCopy(1.8f))
    }

    @Test
    fun nonAddHeaderActionsRemainUnchangedAtLargeFontScale() {
        assertEquals(
            "Delete",
            LargeFontCompactCopyPolicy.compactPrimaryActionText(
                originalText = "Delete",
                plusText = "+",
                fontScale = 2.0f
            )
        )
    }
}
