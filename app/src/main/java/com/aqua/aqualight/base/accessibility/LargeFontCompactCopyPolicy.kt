package com.aqua.aqualight.base.accessibility

/** Pure policy for copy changes that are allowed only near the Android 200% font setting. */
internal object LargeFontCompactCopyPolicy {

    private const val COMPACT_FONT_SCALE_THRESHOLD = 1.8f

    fun shouldUseCompactCopy(fontScale: Float): Boolean {
        return fontScale >= COMPACT_FONT_SCALE_THRESHOLD
    }

    fun compactPrimaryActionText(
        originalText: CharSequence,
        plusText: CharSequence,
        fontScale: Float
    ): CharSequence {
        if (!shouldUseCompactCopy(fontScale)) return originalText

        val normalizedOriginal = originalText.toString().trimStart()
        val normalizedPlus = plusText.toString()
        return if (
            normalizedPlus.isNotEmpty() &&
            normalizedOriginal.startsWith(normalizedPlus)
        ) {
            plusText
        } else {
            originalText
        }
    }
}
