package com.aqua.aqualight.application.aquarium

/** Stable, non-localized category codes persisted for aquarium material selections. */
object AquariumMaterialCategory {
    const val FERTILIZER = "fertilizer"
    const val DECORATION = "decoration"
    const val GRAVEL = "gravel"
    const val SUBSTRATE = "substrate"
    const val AQUARIUM = "aquarium"
    const val CO2 = "co2"
    const val LIGHT = "light"
    const val FILTER = "filter"
    const val HEATER = "heater"
    const val COOLER = "cooler"
    const val DOSING = "dosing"
    const val LED_BACKGROUND = "led_background"

    val codes: Set<String> = linkedSetOf(
        FERTILIZER,
        DECORATION,
        GRAVEL,
        SUBSTRATE,
        AQUARIUM,
        CO2,
        LIGHT,
        FILTER,
        HEATER,
        COOLER,
        DOSING,
        LED_BACKGROUND
    )

    fun isSupported(value: String): Boolean = value in codes
}
