package com.aqua.aqualight.application.devices.light.manual

/**
 * Product-neutral built-in Manual scenes documented by the Light firmware contract.
 *
 * This application model is intentionally independent of Android resources and runtime payloads.
 * Presentation supplies localized labels; a later data adapter will translate the selected scene
 * to the exact product-specific command shape.
 */
enum class DeviceLightBuiltInManualPreset(
    val scene: DeviceLightManualScenePercentages
) {
    RED(
        scene(
            PresetPercent.RED_RED,
            PresetPercent.RED_GREEN,
            PresetPercent.RED_BLUE,
            PresetPercent.RED_WHITE
        )
    ),
    GREEN(
        scene(
            PresetPercent.GREEN_RED,
            PresetPercent.GREEN_GREEN,
            PresetPercent.GREEN_BLUE,
            PresetPercent.GREEN_WHITE
        )
    ),
    BLUE(
        scene(
            PresetPercent.BLUE_RED,
            PresetPercent.BLUE_GREEN,
            PresetPercent.BLUE_BLUE,
            PresetPercent.BLUE_WHITE
        )
    ),
    FISH(
        scene(
            PresetPercent.FISH_RED,
            PresetPercent.FISH_GREEN,
            PresetPercent.FISH_BLUE,
            PresetPercent.FISH_WHITE
        )
    ),
    SHRIMP(
        scene(
            PresetPercent.SHRIMP_RED,
            PresetPercent.SHRIMP_GREEN,
            PresetPercent.SHRIMP_BLUE,
            PresetPercent.SHRIMP_WHITE
        )
    ),
    ALL(
        scene(
            PresetPercent.ALL_RED,
            PresetPercent.ALL_GREEN,
            PresetPercent.ALL_BLUE,
            PresetPercent.ALL_WHITE
        )
    )
}

data class DeviceLightManualScenePercentages(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    init {
        require(listOf(red, green, blue, white).all { percent -> percent in PERCENT_RANGE })
    }
}

private fun scene(
    red: Int,
    green: Int,
    blue: Int,
    white: Int
) = DeviceLightManualScenePercentages(red, green, blue, white)

private object PresetPercent {
    const val RED_RED = 85
    const val RED_GREEN = 50
    const val RED_BLUE = 55
    const val RED_WHITE = 35

    const val GREEN_RED = 60
    const val GREEN_GREEN = 85
    const val GREEN_BLUE = 65
    const val GREEN_WHITE = 40

    const val BLUE_RED = 50
    const val BLUE_GREEN = 60
    const val BLUE_BLUE = 85
    const val BLUE_WHITE = 35

    const val FISH_RED = 80
    const val FISH_GREEN = 45
    const val FISH_BLUE = 70
    const val FISH_WHITE = 45

    const val SHRIMP_RED = 85
    const val SHRIMP_GREEN = 70
    const val SHRIMP_BLUE = 65
    const val SHRIMP_WHITE = 50

    const val ALL_RED = 70
    const val ALL_GREEN = 70
    const val ALL_BLUE = 70
    const val ALL_WHITE = 70
}

private val PERCENT_RANGE = MINIMUM_PERCENT..MAXIMUM_PERCENT
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
