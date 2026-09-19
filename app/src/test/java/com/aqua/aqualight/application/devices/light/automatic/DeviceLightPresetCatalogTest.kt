package com.aqua.aqualight.application.devices.light.automatic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightPresetCatalogTest {

    @Test
    fun catalogKeepsCommercialOrderAndStableChannelValues() {
        assertEquals(EXPECTED_IDS, DeviceLightPresetCatalog.presets.map { preset -> preset.id })
        assertEquals(EXPECTED_SCENES, DeviceLightPresetCatalog.presets.map { preset -> preset.scene })
    }

    @Test
    fun automaticSchedulesKeepStableDurationsAndRamps() {
        DeviceLightPresetCatalog.presets.zip(EXPECTED_SCHEDULES).forEach { (preset, expected) ->
            assertEquals(DEFAULT_START_MINUTE, preset.automaticSchedule.startMinuteOfDay)
            assertEquals(expected.first, preset.automaticSchedule.durationMinutes)
            assertEquals(expected.second, preset.automaticSchedule.rampMinutes)
            assertEquals(
                (DEFAULT_START_MINUTE + expected.first) % MINUTES_PER_DAY,
                preset.automaticSchedule.endMinuteOfDay
            )
        }
    }

    @Test
    fun manualAndAutomaticExperiencesShareTheSameFirstSixPresets() {
        assertEquals(EXPECTED_MANUAL_IDS, DeviceLightPresetCatalog.manualPresets.map { it.id })
        assertTrue(DeviceLightPresetCatalog.manualPresets.all { preset -> preset.availableInManual })
        assertFalse(DeviceLightPresetCatalog.find(DeviceLightPresetId.NEW_SETUP)!!.availableInManual)
        assertFalse(DeviceLightPresetCatalog.find(DeviceLightPresetId.SHADE_PLANTS)!!.availableInManual)
    }

    @Test
    fun storageNamesAreParsedWithoutAcceptingUnknownValues() {
        assertEquals(
            DeviceLightPresetId.AQUASCAPE,
            DeviceLightPresetId.fromStorageName(DeviceLightPresetId.AQUASCAPE.name)
        )
        assertNull(DeviceLightPresetId.fromStorageName("unknown"))
        assertNull(DeviceLightPresetId.fromStorageName(null))
    }
}

private val EXPECTED_IDS = listOf(
    DeviceLightPresetId.NATURAL_AQUARIUM,
    DeviceLightPresetId.PLANTED_AQUARIUM,
    DeviceLightPresetId.RED_PLANTS,
    DeviceLightPresetId.VIVID_COLORS,
    DeviceLightPresetId.LOW_TECH,
    DeviceLightPresetId.AQUASCAPE,
    DeviceLightPresetId.NEW_SETUP,
    DeviceLightPresetId.SHADE_PLANTS
)

private val EXPECTED_SCENES = listOf(
    DeviceLightPresetScene(red = 45, green = 50, blue = 50, white = 60),
    DeviceLightPresetScene(red = 60, green = 50, blue = 65, white = 55),
    DeviceLightPresetScene(red = 65, green = 45, blue = 70, white = 45),
    DeviceLightPresetScene(red = 65, green = 50, blue = 65, white = 60),
    DeviceLightPresetScene(red = 30, green = 30, blue = 30, white = 35),
    DeviceLightPresetScene(red = 55, green = 55, blue = 60, white = 65),
    DeviceLightPresetScene(red = 40, green = 40, blue = 45, white = 50),
    DeviceLightPresetScene(red = 40, green = 50, blue = 55, white = 45)
)

private val EXPECTED_MANUAL_IDS = listOf(
    DeviceLightPresetId.NATURAL_AQUARIUM,
    DeviceLightPresetId.PLANTED_AQUARIUM,
    DeviceLightPresetId.RED_PLANTS,
    DeviceLightPresetId.VIVID_COLORS,
    DeviceLightPresetId.LOW_TECH,
    DeviceLightPresetId.AQUASCAPE
)

private val EXPECTED_SCHEDULES = listOf(
    7 * MINUTES_PER_HOUR to 60,
    8 * MINUTES_PER_HOUR to 60,
    8 * MINUTES_PER_HOUR to 90,
    7 * MINUTES_PER_HOUR to 60,
    6 * MINUTES_PER_HOUR to 90,
    8 * MINUTES_PER_HOUR to 60,
    6 * MINUTES_PER_HOUR to 120,
    7 * MINUTES_PER_HOUR to 90
)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DEFAULT_START_MINUTE = 10 * MINUTES_PER_HOUR
