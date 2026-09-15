package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserDataArchiveSmartLightProfileTest {

    @Test
    fun `archive mapping preserves every semantic light fact`() {
        val profile = AquariumLightingProfile(
            plantDensity = PlantDensity.HIGH,
            highestPlantLightDemand = PlantLightDemand.MEDIUM,
            co2Status = Co2Status.ACTIVE,
            isActiveSoil = true,
            waterDepthCm = 43,
            fixtureMountHeightCm = 11,
            preferredViewingStartMinuteOfDay = 600,
            preferredViewingEndMinuteOfDay = 1_200,
            algaeObservation = AquariumObservationSeverity.MILD,
            plantStressObservation = AquariumObservationSeverity.NONE,
            observationDateEpochDay = 20_500L
        )
        val archived = savedTank(profile).toArchiveAquarium(photoReference = null)

        assertEquals(profile, archived.toTankDraft(photoUri = null).lightingProfile)
        assertEquals("HIGH", archived.smartLightProfile.plantDensity)
        assertEquals("ACTIVE", archived.smartLightProfile.co2Status)
    }

    @Test
    fun `archive mapping rejects unknown semantic code instead of defaulting`() {
        val invalid = emptyArchiveSmartLightProfile().copy(plantDensity = "DENSE")

        assertThrows(IllegalArgumentException::class.java) {
            invalid.toAquariumLightingProfile()
        }
    }

    private fun savedTank(profile: AquariumLightingProfile) = SavedAquariumTank(
        id = 7L,
        ownerUid = "owner",
        name = "Display Tank",
        description = "",
        photoUri = null,
        setupDateEpochDay = 20_400L,
        widthCm = 60,
        lengthCm = 30,
        heightCm = 36,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Planted",
        tankStyle = "Nature",
        createdAtMillis = 1_000L,
        smartCareEnabled = true,
        careRemindersEnabled = true,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList(),
        lightingProfile = profile
    )
}
