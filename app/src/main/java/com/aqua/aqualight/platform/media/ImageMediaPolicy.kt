package com.aqua.aqualight.platform.media

object ImageMediaPolicy {
    const val MAX_SOURCE_BYTES: Long = 12L * 1024L * 1024L
    const val MAX_SOURCE_PIXELS: Long = 60_000_000L
    const val MAX_OUTPUT_BYTES: Long = 3L * 1024L * 1024L
    const val MAX_OUTPUT_EDGE_PX: Int = 1_600
    const val MAX_OUTPUT_PIXELS: Long = 3_000_000L

    fun validateSource(
        width: Int,
        height: Int,
        sourceBytes: Long?
    ): ImageMediaPolicyResult {
        if (width <= 0 || height <= 0) return ImageMediaPolicyResult.InvalidDimensions
        if (sourceBytes != null && sourceBytes > MAX_SOURCE_BYTES) {
            return ImageMediaPolicyResult.SourceTooLarge
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels <= 0L || pixels > MAX_SOURCE_PIXELS) {
            return ImageMediaPolicyResult.TooManyPixels
        }
        return ImageMediaPolicyResult.Accepted
    }

    fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (true) {
            val sampledWidth = width / sample
            val sampledHeight = height / sample
            val sampledPixels = sampledWidth.toLong() * sampledHeight.toLong()
            val edgeWithinBudget = maxOf(sampledWidth, sampledHeight) <= MAX_OUTPUT_EDGE_PX * 2
            val pixelsWithinBudget = sampledPixels <= MAX_OUTPUT_PIXELS * 4
            if (edgeWithinBudget && pixelsWithinBudget) return sample
            if (sample >= 128) return sample
            sample *= 2
        }
    }

    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val edgeScale = MAX_OUTPUT_EDGE_PX.toFloat() / maxOf(width, height).toFloat()
        val pixelScale = kotlin.math.sqrt(
            MAX_OUTPUT_PIXELS.toDouble() / (width.toLong() * height.toLong()).toDouble()
        ).toFloat()
        val scale = minOf(1f, edgeScale, pixelScale)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}

sealed interface ImageMediaPolicyResult {
    data object Accepted : ImageMediaPolicyResult
    data object InvalidDimensions : ImageMediaPolicyResult
    data object SourceTooLarge : ImageMediaPolicyResult
    data object TooManyPixels : ImageMediaPolicyResult
}
