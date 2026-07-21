package com.aqua.aqualight.platform.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMediaPolicyTest {

    @Test
    fun rejectsExcessivePixelCountBeforeDecode() {
        val result = ImageMediaPolicy.validateSource(
            width = 12_000,
            height = 8_000,
            sourceBytes = 2L * 1024L * 1024L
        )
        assertEquals(ImageMediaPolicyResult.TooManyPixels, result)
    }

    @Test
    fun rejectsSourceByteLimitBeforeDecode() {
        val result = ImageMediaPolicy.validateSource(
            width = 1_000,
            height = 1_000,
            sourceBytes = ImageMediaPolicy.MAX_SOURCE_BYTES + 1L
        )
        assertEquals(ImageMediaPolicyResult.SourceTooLarge, result)
    }

    @Test
    fun samplingBoundsDecodedBitmapBudget() {
        val sample = ImageMediaPolicy.calculateInSampleSize(8_000, 6_000)
        assertTrue(sample >= 4)
        assertTrue(maxOf(8_000 / sample, 6_000 / sample) <= ImageMediaPolicy.MAX_OUTPUT_EDGE_PX * 2)
    }

    @Test
    fun targetSizeDoesNotUpscaleSmallImages() {
        assertEquals(800 to 600, ImageMediaPolicy.targetSize(800, 600))
    }
}
