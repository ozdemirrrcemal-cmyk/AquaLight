package com.aqua.aqualight.platform.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedImagePolicyTest {

    @Test
    fun rejectsExcessivePixelCountBeforeDecode() {
        val result = BoundedImagePolicy.validateSource(
            width = 12_000,
            height = 8_000,
            sourceBytes = 2L * 1024L * 1024L
        )
        assertEquals(BoundedImagePolicyResult.TooManyPixels, result)
    }

    @Test
    fun rejectsSourceByteLimitBeforeDecode() {
        val result = BoundedImagePolicy.validateSource(
            width = 1_000,
            height = 1_000,
            sourceBytes = BoundedImagePolicy.MAX_SOURCE_BYTES + 1L
        )
        assertEquals(BoundedImagePolicyResult.SourceTooLarge, result)
    }

    @Test
    fun samplingBoundsDecodedBitmapBudget() {
        val sample = BoundedImagePolicy.calculateInSampleSize(8_000, 6_000)
        assertTrue(sample >= 4)
        assertTrue(maxOf(8_000 / sample, 6_000 / sample) <= BoundedImagePolicy.MAX_OUTPUT_EDGE_PX * 2)
    }

    @Test
    fun targetSizeDoesNotUpscaleSmallImages() {
        assertEquals(800 to 600, BoundedImagePolicy.targetSize(800, 600))
    }
}
