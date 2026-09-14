package com.aqua.aqualight.application.devices.light.manual

/**
 * Product-neutral AquaLight-curated Manual scenes.
 *
 * Percentages are conservative channel-drive baselines rather than PAR targets. Optical output
 * still depends on the product, installation height, aquarium depth, plants, CO2 and nutrients.
 * Presentation keeps only channels supported by the connected Light product.
 */
enum class DeviceLightBuiltInManualPreset(
    val scene: DeviceLightManualScenePercentages
) {
    NATURAL(
        scene(
            PresetPercent.NATURAL_RED,
            PresetPercent.NATURAL_GREEN,
            PresetPercent.NATURAL_BLUE,
            PresetPercent.NATURAL_WHITE
        )
    ),
    GROWTH(
        scene(
            PresetPercent.GROWTH_RED,
            PresetPercent.GROWTH_GREEN,
            PresetPercent.GROWTH_BLUE,
            PresetPercent.GROWTH_WHITE
        )
    ),
    RED_FLORA(
        scene(
            PresetPercent.RED_FLORA_RED,
            PresetPercent.RED_FLORA_GREEN,
            PresetPercent.RED_FLORA_BLUE,
            PresetPercent.RED_FLORA_WHITE
        )
    ),
    COLOR_BOOST(
        scene(
            PresetPercent.COLOR_BOOST_RED,
            PresetPercent.COLOR_BOOST_GREEN,
            PresetPercent.COLOR_BOOST_BLUE,
            PresetPercent.COLOR_BOOST_WHITE
        )
    ),
    DETAIL(
        scene(
            PresetPercent.DETAIL_RED,
            PresetPercent.DETAIL_GREEN,
            PresetPercent.DETAIL_BLUE,
            PresetPercent.DETAIL_WHITE
        )
    ),
    LOW_LIGHT(
        scene(
            PresetPercent.LOW_LIGHT_RED,
            PresetPercent.LOW_LIGHT_GREEN,
            PresetPercent.LOW_LIGHT_BLUE,
            PresetPercent.LOW_LIGHT_WHITE
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
    const val NATURAL_RED = 50
    const val NATURAL_GREEN = 50
    const val NATURAL_BLUE = 45
    const val NATURAL_WHITE = 60

    const val GROWTH_RED = 60
    const val GROWTH_GREEN = 55
    const val GROWTH_BLUE = 60
    const val GROWTH_WHITE = 50

    const val RED_FLORA_RED = 70
    const val RED_FLORA_GREEN = 45
    const val RED_FLORA_BLUE = 65
    const val RED_FLORA_WHITE = 45

    const val COLOR_BOOST_RED = 65
    const val COLOR_BOOST_GREEN = 50
    const val COLOR_BOOST_BLUE = 60
    const val COLOR_BOOST_WHITE = 55

    const val DETAIL_RED = 50
    const val DETAIL_GREEN = 55
    const val DETAIL_BLUE = 45
    const val DETAIL_WHITE = 65

    const val LOW_LIGHT_RED = 35
    const val LOW_LIGHT_GREEN = 35
    const val LOW_LIGHT_BLUE = 30
    const val LOW_LIGHT_WHITE = 40
}

private val PERCENT_RANGE = MINIMUM_PERCENT..MAXIMUM_PERCENT
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
