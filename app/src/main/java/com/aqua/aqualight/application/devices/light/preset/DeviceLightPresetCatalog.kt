package com.aqua.aqualight.application.devices.light.preset

/** Stable identifiers shared by Manual scenes and Automatic program templates. */
enum class DeviceLightPresetId {
    NATURAL_AQUARIUM,
    PLANTED_AQUARIUM,
    RED_PLANTS,
    VIVID_COLORS,
    LOW_TECH,
    AQUASCAPE,
    NEW_SETUP,
    SHADE_PLANTS;

    companion object {
        fun fromStorageName(value: String?): DeviceLightPresetId? =
            entries.singleOrNull { preset -> preset.name == value }
    }
}

data class DeviceLightPresetScene(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    init {
        require(listOf(red, green, blue, white).all { percent -> percent in PERCENT_RANGE })
    }
}

data class DeviceLightAutomaticPresetSchedule(
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val rampMinutes: Int
) {
    init {
        require(startMinuteOfDay in MINUTE_OF_DAY_RANGE)
        require(durationMinutes in MINIMUM_DURATION_MINUTES..MINUTES_PER_DAY)
        require(rampMinutes in MINIMUM_RAMP_MINUTES..durationMinutes / RAMP_SIDES)
    }

    val endMinuteOfDay: Int
        get() = (startMinuteOfDay + durationMinutes) % MINUTES_PER_DAY
}

data class DeviceLightBuiltInPreset(
    val id: DeviceLightPresetId,
    val scene: DeviceLightPresetScene,
    val automaticSchedule: DeviceLightAutomaticPresetSchedule,
    val availableInManual: Boolean
)

/**
 * Product-neutral, research-informed starting points for common freshwater use cases.
 *
 * Percentages are channel-drive baselines rather than universal PAR/PPFD targets. Optical output
 * still depends on the product, installation height, aquarium depth, plants, CO2, nutrients and
 * water clarity. The presentation layer keeps only channels supported by the connected product.
 */
object DeviceLightPresetCatalog {
    val presets: List<DeviceLightBuiltInPreset> = listOf(
        preset(
            id = DeviceLightPresetId.NATURAL_AQUARIUM,
            red = 45,
            green = 50,
            blue = 50,
            white = 60,
            durationMinutes = hours(7),
            rampMinutes = 60
        ),
        preset(
            id = DeviceLightPresetId.PLANTED_AQUARIUM,
            red = 60,
            green = 50,
            blue = 65,
            white = 55,
            durationMinutes = hours(8),
            rampMinutes = 60
        ),
        preset(
            id = DeviceLightPresetId.RED_PLANTS,
            red = 65,
            green = 45,
            blue = 70,
            white = 45,
            durationMinutes = hours(8),
            rampMinutes = 90
        ),
        preset(
            id = DeviceLightPresetId.VIVID_COLORS,
            red = 65,
            green = 50,
            blue = 65,
            white = 60,
            durationMinutes = hours(7),
            rampMinutes = 60
        ),
        preset(
            id = DeviceLightPresetId.LOW_TECH,
            red = 30,
            green = 30,
            blue = 30,
            white = 35,
            durationMinutes = hours(6),
            rampMinutes = 90
        ),
        preset(
            id = DeviceLightPresetId.AQUASCAPE,
            red = 55,
            green = 55,
            blue = 60,
            white = 65,
            durationMinutes = hours(8),
            rampMinutes = 60
        ),
        preset(
            id = DeviceLightPresetId.NEW_SETUP,
            red = 40,
            green = 40,
            blue = 45,
            white = 50,
            durationMinutes = hours(6),
            rampMinutes = 120,
            availableInManual = false
        ),
        preset(
            id = DeviceLightPresetId.SHADE_PLANTS,
            red = 40,
            green = 50,
            blue = 55,
            white = 45,
            durationMinutes = hours(7),
            rampMinutes = 90,
            availableInManual = false
        )
    )

    val manualPresets: List<DeviceLightBuiltInPreset> =
        presets.filter(DeviceLightBuiltInPreset::availableInManual)

    fun find(id: DeviceLightPresetId): DeviceLightBuiltInPreset? =
        presets.singleOrNull { preset -> preset.id == id }
}

@Suppress("LongParameterList")
private fun preset(
    id: DeviceLightPresetId,
    red: Int,
    green: Int,
    blue: Int,
    white: Int,
    durationMinutes: Int,
    rampMinutes: Int,
    availableInManual: Boolean = true
) = DeviceLightBuiltInPreset(
    id = id,
    scene = DeviceLightPresetScene(red, green, blue, white),
    automaticSchedule = DeviceLightAutomaticPresetSchedule(
        startMinuteOfDay = DEFAULT_START_MINUTE_OF_DAY,
        durationMinutes = durationMinutes,
        rampMinutes = rampMinutes
    ),
    availableInManual = availableInManual
)

private fun hours(value: Int) = value * MINUTES_PER_HOUR

private val PERCENT_RANGE = MINIMUM_PERCENT..MAXIMUM_PERCENT
private val MINUTE_OF_DAY_RANGE = 0 until MINUTES_PER_DAY
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
private const val MINIMUM_DURATION_MINUTES = 1
private const val MINIMUM_RAMP_MINUTES = 0
private const val RAMP_SIDES = 2
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DEFAULT_START_HOUR = 10
private const val DEFAULT_START_MINUTE_OF_DAY = DEFAULT_START_HOUR * MINUTES_PER_HOUR
