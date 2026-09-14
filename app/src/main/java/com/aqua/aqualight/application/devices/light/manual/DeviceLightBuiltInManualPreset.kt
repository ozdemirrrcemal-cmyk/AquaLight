package com.aqua.aqualight.application.devices.light.manual

/**
 * Product-neutral AquaLight-curated Manual scenes for common freshwater aquarium use cases.
 *
 * These percentages are research-informed channel-drive baselines, not universal PAR/PPFD targets.
 * Optical output still depends on the product, installation height, aquarium depth, plants, CO2,
 * nutrients and water clarity. Presentation keeps only channels supported by the connected product.
 */
enum class DeviceLightBuiltInManualPreset(
    val scene: DeviceLightManualScenePercentages
) {
    NATURAL_AQUARIUM(
        scene(
            PresetPercent.NATURAL_AQUARIUM_RED,
            PresetPercent.NATURAL_AQUARIUM_GREEN,
            PresetPercent.NATURAL_AQUARIUM_BLUE,
            PresetPercent.NATURAL_AQUARIUM_WHITE
        )
    ),
    PLANTED_AQUARIUM(
        scene(
            PresetPercent.PLANTED_AQUARIUM_RED,
            PresetPercent.PLANTED_AQUARIUM_GREEN,
            PresetPercent.PLANTED_AQUARIUM_BLUE,
            PresetPercent.PLANTED_AQUARIUM_WHITE
        )
    ),
    RED_PLANTS(
        scene(
            PresetPercent.RED_PLANTS_RED,
            PresetPercent.RED_PLANTS_GREEN,
            PresetPercent.RED_PLANTS_BLUE,
            PresetPercent.RED_PLANTS_WHITE
        )
    ),
    VIVID_COLORS(
        scene(
            PresetPercent.VIVID_COLORS_RED,
            PresetPercent.VIVID_COLORS_GREEN,
            PresetPercent.VIVID_COLORS_BLUE,
            PresetPercent.VIVID_COLORS_WHITE
        )
    ),
    LOW_TECH(
        scene(
            PresetPercent.LOW_TECH_RED,
            PresetPercent.LOW_TECH_GREEN,
            PresetPercent.LOW_TECH_BLUE,
            PresetPercent.LOW_TECH_WHITE
        )
    ),
    AQUASCAPE(
        scene(
            PresetPercent.AQUASCAPE_RED,
            PresetPercent.AQUASCAPE_GREEN,
            PresetPercent.AQUASCAPE_BLUE,
            PresetPercent.AQUASCAPE_WHITE
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
    const val NATURAL_AQUARIUM_RED = 45
    const val NATURAL_AQUARIUM_GREEN = 50
    const val NATURAL_AQUARIUM_BLUE = 50
    const val NATURAL_AQUARIUM_WHITE = 60

    const val PLANTED_AQUARIUM_RED = 60
    const val PLANTED_AQUARIUM_GREEN = 50
    const val PLANTED_AQUARIUM_BLUE = 65
    const val PLANTED_AQUARIUM_WHITE = 55

    const val RED_PLANTS_RED = 65
    const val RED_PLANTS_GREEN = 45
    const val RED_PLANTS_BLUE = 70
    const val RED_PLANTS_WHITE = 45

    const val VIVID_COLORS_RED = 65
    const val VIVID_COLORS_GREEN = 50
    const val VIVID_COLORS_BLUE = 65
    const val VIVID_COLORS_WHITE = 60

    const val LOW_TECH_RED = 30
    const val LOW_TECH_GREEN = 30
    const val LOW_TECH_BLUE = 30
    const val LOW_TECH_WHITE = 35

    const val AQUASCAPE_RED = 55
    const val AQUASCAPE_GREEN = 55
    const val AQUASCAPE_BLUE = 60
    const val AQUASCAPE_WHITE = 65
}

private val PERCENT_RANGE = MINIMUM_PERCENT..MAXIMUM_PERCENT
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
