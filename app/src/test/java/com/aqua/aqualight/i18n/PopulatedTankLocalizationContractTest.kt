package com.aqua.aqualight.i18n

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionInputPolicy
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PopulatedTankLocalizationContractTest {

    @Test
    fun populatedTankKeepsCalendarDaysAndLocalizedMeasurementsAcrossLanguagesAndZones() {
        val setupDate = LocalDate.of(2026, 7, 19)
        val livestockDate = LocalDate.of(2026, 7, 20)
        val tank = AquariumTankSnapshot(
            id = 42L,
            name = "Reef 42",
            description = "Populated commercial fixture",
            photoUri = "content://release-smoke/tank/42",
            setupDateEpochDay = setupDate.toEpochDay(),
            widthCm = 60,
            lengthCm = 40,
            heightCm = 40,
            sizeUnit = "in",
            volumeUnit = "gal",
            tankType = "Reef",
            tankStyle = "Mixed reef",
            createdAtMillis = 1_752_883_200_000L,
            smartCareEnabled = true,
            careRemindersEnabled = true,
            plants = listOf(
                AquariumPlantTag(
                    id = 1L,
                    plantName = "Anubias",
                    category = "Rhizome"
                )
            ),
            materials = listOf(
                AquariumMaterialSelection(
                    id = 2L,
                    productId = "soil-1",
                    categoryKey = "substrate",
                    categoryTitle = "Substrate",
                    name = "Active Soil"
                )
            ),
            livestock = listOf(
                AquariumLivestock(
                    id = 3L,
                    name = "Clownfish",
                    category = "Fish",
                    quantity = 2,
                    addedDateEpochDay = livestockDate.toEpochDay(),
                    note = "Pair"
                )
            )
        )
        val zones = listOf(
            ZoneId.of("Europe/Istanbul"),
            ZoneId.of("America/Los_Angeles"),
            ZoneId.of("Asia/Tokyo")
        )

        zones.forEach { zoneId ->
            listOf(
                requireNotNull(tank.setupDateEpochDay),
                requireNotNull(tank.livestock.single().addedDateEpochDay)
            ).forEach { epochDay ->
                assertEquals(
                    epochDay,
                    DateOnly.fromPickerMillis(
                        DateOnly.toPickerMillis(epochDay, zoneId),
                        zoneId
                    )
                )
            }
        }

        val turkish = Locale.forLanguageTag("tr-TR")
        val english = Locale.ENGLISH
        assertEquals(
            "19 Tem 2026",
            LocaleFormatter.formatDateEpochDay(requireNotNull(tank.setupDateEpochDay), turkish)
        )
        assertEquals(
            "Jul 19, 2026",
            LocaleFormatter.formatDateEpochDay(requireNotNull(tank.setupDateEpochDay), english)
        )
        assertEquals(
            "23,62",
            AquariumDimensionInputPolicy.format(tank.widthCm.toDouble(), tank.sizeUnit, turkish)
        )
        assertEquals(
            "23.62",
            AquariumDimensionInputPolicy.format(tank.widthCm.toDouble(), tank.sizeUnit, english)
        )

        val liters = AquariumVolumeCalculator.grossLiters(
            tank.widthCm,
            tank.lengthCm,
            tank.heightCm
        )
        val gallons = AquariumVolumeCalculator.litersToGallons(liters)
        assertEquals(96.0, liters, 0.0)
        assertEquals("25,36", LocaleFormatter.formatDecimal(gallons, turkish))
        assertEquals("25.36", LocaleFormatter.formatDecimal(gallons, english))
        assertNotEquals(
            LocaleFormatter.formatDecimal(gallons, turkish),
            LocaleFormatter.formatDecimal(gallons, english)
        )
    }
}
