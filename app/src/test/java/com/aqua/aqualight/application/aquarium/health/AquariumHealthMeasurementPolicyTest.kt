package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AquariumHealthMeasurementPolicyTest {

    @Test
    fun singleReadingWaterTestIsValid() {
        val input = waterInput(
            readings = listOf(
                AquariumWaterReading(
                    parameter = HealthWaterParameter.PH,
                    value = 7.2
                )
            )
        )

        assertEquals(
            input,
            AquariumHealthMeasurementPolicy.validateWaterTestInput(
                input = input,
                nowMillis = NOW_MILLIS
            )
        )
    }

    @Test
    fun duplicateWaterParameterIsRejected() {
        val input = waterInput(
            readings = listOf(
                AquariumWaterReading(HealthWaterParameter.PH, 7.0),
                AquariumWaterReading(HealthWaterParameter.PH, 7.1)
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validateWaterTestInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun nonFiniteWaterValueIsRejected() {
        val input = waterInput(
            readings = listOf(
                AquariumWaterReading(
                    HealthWaterParameter.NITRITE_PPM,
                    Double.NaN
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validateWaterTestInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun futureMeasurementIsRejected() {
        val input = waterInput(
            measuredAtMillis = NOW_MILLIS + 1L
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validateWaterTestInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun symptomMustBeApplicableToLivestockCategory() {
        val input = LivestockHealthObservationInput(
            tankId = 7L,
            livestockId = null,
            categoryKey = AquariumLivestockTaxonomy.SHRIMP,
            symptomKey = LivestockHealthSymptomCatalog.FISH_SURFACE_GASPING,
            intensity = ObservationIntensity.MODERATE,
            observedAtMillis = MEASURED_MILLIS
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validateObservationInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun genericObservationSupportsCategoryLevelTarget() {
        val input = LivestockHealthObservationInput(
            tankId = 7L,
            livestockId = null,
            categoryKey = AquariumLivestockTaxonomy.SHRIMP,
            symptomKey = LivestockHealthSymptomCatalog.ABNORMAL_BEHAVIOR,
            intensity = ObservationIntensity.MILD,
            observedAtMillis = MEASURED_MILLIS
        )

        assertEquals(
            input,
            AquariumHealthMeasurementPolicy.validateObservationInput(
                input,
                NOW_MILLIS
            )
        )
    }

    @Test
    fun algaeObservationRequiresKnownAlgaeType() {
        val input = PlantHealthObservationInput(
            tankId = 7L,
            plantId = null,
            symptomKey = PlantHealthSymptomCatalog.ALGAE_PRESENCE,
            algaeTypeKey = null,
            intensity = ObservationIntensity.MODERATE,
            observedAtMillis = MEASURED_MILLIS
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validatePlantObservationInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun nonAlgaePlantSymptomRejectsAlgaeType() {
        val input = PlantHealthObservationInput(
            tankId = 7L,
            plantId = 11L,
            symptomKey = PlantHealthSymptomCatalog.YELLOWING,
            algaeTypeKey = AquariumAlgaeCatalog.BLACK_BEARD_ALGAE,
            intensity = ObservationIntensity.MILD,
            observedAtMillis = MEASURED_MILLIS
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquariumHealthMeasurementPolicy.validatePlantObservationInput(
                input,
                NOW_MILLIS
            )
        }
    }

    @Test
    fun tankWideKnownAlgaeObservationIsValid() {
        val input = PlantHealthObservationInput(
            tankId = 7L,
            plantId = null,
            symptomKey = PlantHealthSymptomCatalog.ALGAE_PRESENCE,
            algaeTypeKey = AquariumAlgaeCatalog.BLACK_BEARD_ALGAE,
            intensity = ObservationIntensity.MODERATE,
            observedAtMillis = MEASURED_MILLIS
        )

        assertEquals(
            input,
            AquariumHealthMeasurementPolicy.validatePlantObservationInput(
                input,
                NOW_MILLIS
            )
        )
    }

    private fun waterInput(
        measuredAtMillis: Long = MEASURED_MILLIS,
        readings: List<AquariumWaterReading> = listOf(
            AquariumWaterReading(
                parameter = HealthWaterParameter.NITRATE_PPM,
                value = 20.0
            )
        )
    ) = AquariumWaterTestInput(
        tankId = 7L,
        measuredAtMillis = measuredAtMillis,
        readings = readings
    )

    private companion object {
        const val MEASURED_MILLIS = 1_767_225_600_000L
        const val NOW_MILLIS = 1_767_229_200_000L
    }
}
