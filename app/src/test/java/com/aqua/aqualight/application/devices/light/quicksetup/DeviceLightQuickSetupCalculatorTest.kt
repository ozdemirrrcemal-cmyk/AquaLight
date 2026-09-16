package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightQuickSetupCalculatorTest {
    @Test
    fun `new or recently changed tank receives one open ended six hour phase`() {
        val plan = calculate(tank = tank(lastLightingResetEpochDay = TODAY - 5))

        assertEquals(
            DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED,
            plan.confidence
        )
        assertEquals(1, plan.phases.size)
        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertEquals(360 * MINUTE_MS, plan.durationMillis())
        assertNull(plan.currentPhase.draft.validUntilEpochDayExclusive)
        assertEquals(TODAY, plan.currentPhase.draft.validFromEpochDay)
        assertTrue(DeviceLightPlanReason.EVIDENCE_SIX_HOUR_START in plan.reasons)
        assertTrue(DeviceLightPlanReason.ACTIVE_SOIL_STARTUP in plan.reasons)
        assertTrue(DeviceLightPlanWarning.CALIBRATION_UNAVAILABLE in plan.warnings)
        assertTrue(plan.maximumChannelPercent <= 30)
        assertTrue("chihiros_light_intensity_guidance" in plan.evidenceSourceIds)
        assertEquals(TODAY - 5 + 21, plan.reevaluationEpochDay)
    }

    @Test
    fun `applied six and seven hour plans advance one reviewed step at a time`() {
        val sevenHours = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 360,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 14
            )
        )
        val eightHours = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 420,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 14
            )
        )

        assertEquals(DeviceLightLifecycleStage.ACCLIMATION, sevenHours.currentPhase.lifecycleStage)
        assertEquals(420 * MINUTE_MS, sevenHours.durationMillis())
        assertTrue(DeviceLightPlanReason.CONTROLLED_SEVEN_HOUR_STEP in sevenHours.reasons)
        assertEquals(DeviceLightLifecycleStage.ESTABLISHED, eightHours.currentPhase.lifecycleStage)
        assertEquals(480 * MINUTE_MS, eightHours.durationMillis())
        assertTrue(DeviceLightPlanReason.EVIDENCE_EIGHT_HOUR_BASELINE in eightHours.reasons)
    }

    @Test
    fun `review interval prevents an early automatic increase`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 360,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 4
            )
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertTrue(DeviceLightPlanReason.REVIEW_INTERVAL_HOLD in plan.reasons)
        assertEquals(TODAY - 4 + 14, plan.reevaluationEpochDay)
    }

    @Test
    fun `algae holds the last duration and reduces channel output`() {
        val clear = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 420,
                lastAppliedMaximumChannelPercent = 50,
                lastAppliedEpochDay = TODAY - 20
            )
        )
        val worsening = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 420,
                lastAppliedMaximumChannelPercent = 50,
                lastAppliedEpochDay = TODAY - 20
            ),
            input = input().copy(surfaceGrowth = AquariumSurfaceGrowth.WORSENING_ALGAE)
        )

        assertEquals(DeviceLightLifecycleStage.ESTABLISHED, clear.currentPhase.lifecycleStage)
        assertEquals(DeviceLightLifecycleStage.ACCLIMATION, worsening.currentPhase.lifecycleStage)
        assertTrue(DeviceLightPlanReason.REVIEW_INTERVAL_HOLD in worsening.reasons)
        assertTrue(DeviceLightPlanReason.WORSENING_ALGAE_GUARD in worsening.reasons)
        assertTrue("tropica_algae_control" in worsening.evidenceSourceIds)
        assertTrue(worsening.maximumChannelPercent < clear.maximumChannelPercent)
        assertEquals(TODAY + 7, worsening.reevaluationEpochDay)
    }

    @Test
    fun `a cleared guard never raises the last applied output automatically`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 420,
                lastAppliedMaximumChannelPercent = 30,
                lastAppliedEpochDay = TODAY - 20
            )
        )

        assertEquals(DeviceLightLifecycleStage.ESTABLISHED, plan.currentPhase.lifecycleStage)
        assertTrue(plan.maximumChannelPercent <= 30)
        assertTrue(DeviceLightPlanReason.LAST_APPLIED_OUTPUT_HOLD in plan.reasons)
    }

    @Test
    fun `mounting height change immediately resets an established plan to six hours`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 480,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 20
            ),
            input = input().copy(fixtureHeightAboveWaterCm = 4)
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertEquals(360 * MINUTE_MS, plan.durationMillis())
        assertEquals(TODAY + 21, plan.reevaluationEpochDay)
        assertTrue(DeviceLightPlanReason.OPTICAL_GEOMETRY_CHANGE_RESET in plan.reasons)
    }

    @Test
    fun `water depth change also resets an established plan to six hours`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 480,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 20
            ),
            input = input().copy(waterDepthCm = 30)
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertEquals(TODAY + 21, plan.reevaluationEpochDay)
        assertTrue(DeviceLightPlanReason.OPTICAL_GEOMETRY_CHANGE_RESET in plan.reasons)
    }

    @Test
    fun `invalidated water depth is compared with the last applied optical baseline`() {
        val plan = calculate(
            tank = tank(
                waterDepthCm = null,
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 480,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 20,
                lastAppliedWaterDepthCm = 35
            ),
            input = input().copy(waterDepthCm = 30)
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertEquals(TODAY + 21, plan.reevaluationEpochDay)
        assertTrue(DeviceLightPlanReason.OPTICAL_GEOMETRY_CHANGE_RESET in plan.reasons)
    }

    @Test
    fun `invalidated plant profile resets an established plan using fixture date`() {
        val establishedTank = tank(
            lastLightingResetEpochDay = TODAY - 120,
            lastAppliedPhotoperiodMinutes = 480,
            lastAppliedMaximumChannelPercent = 45,
            lastAppliedEpochDay = TODAY - 20
        )
        val plan = calculate(
            tank = establishedTank.copy(plantCoverage = AquariumPlantCoverage.UNKNOWN)
        )
        val unchanged = calculate(tank = establishedTank)

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertEquals(360 * MINUTE_MS, plan.durationMillis())
        assertEquals(TODAY + 21, plan.reevaluationEpochDay)
        assertTrue(DeviceLightPlanReason.BIOLOGICAL_PROFILE_CHANGE_RESET in plan.reasons)
        assertFalse(plan.profileFingerprint == unchanged.profileFingerprint)
        assertFalse(plan.recommendationId == unchanged.recommendationId)
    }

    @Test
    fun `phone date can never replace the verified fixture date`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightQuickSetupCalculator.calculate(
                tank = tank(),
                input = input(),
                todayEpochDay = TODAY + 1
            )
        }
    }

    @Test
    fun `input cannot reduce an exact reviewed plant demand`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightQuickSetupCalculator.calculate(
                tank = tank().copy(
                    reviewedPlantDemandFloor = DeviceLightPlantDemand.HIGH
                ),
                input = input().copy(plantDemand = DeviceLightPlantDemand.LOW),
                todayEpochDay = TODAY
            )
        }
    }

    @Test
    fun `unsupported tank cannot produce a commercial recommendation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightQuickSetupCalculator.calculate(
                tank = tank().copy(
                    hasPlants = false,
                    plantedFreshwater = false,
                    plantDemand = DeviceLightPlantDemand.UNKNOWN,
                    reviewedPlantDemandFloor = DeviceLightPlantDemand.UNKNOWN,
                    plantCatalogIds = emptySet(),
                    plantEvidenceSourceIds = emptySet()
                ),
                input = input(),
                todayEpochDay = TODAY
            )
        }
    }

    @Test
    fun `CO2 equipment not ready at light on blocks progression and caps output`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 360,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 20
            ),
            input = input().copy(
                plantDemand = DeviceLightPlantDemand.HIGH,
                co2Readiness = AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON
            )
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, plan.currentPhase.lifecycleStage)
        assertTrue(plan.maximumChannelPercent <= 40)
        assertTrue(DeviceLightPlanReason.CO2_NOT_READY_GUARD in plan.reasons)
        assertTrue(DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_READY_CO2 in plan.warnings)
    }

    @Test
    fun `direct daylight overlap is explicit and cannot trigger progression`() {
        val plan = calculate(
            tank = tank(
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 420,
                lastAppliedMaximumChannelPercent = 50,
                lastAppliedEpochDay = TODAY - 20
            ),
            input = input().copy(
                daylightExposure = AquariumDaylightExposure.DIRECT,
                daylightStartMinute = 14 * 60,
                daylightEndMinute = 16 * 60,
                programEndMinute = 20 * 60
            )
        )

        assertEquals(DeviceLightLifecycleStage.ACCLIMATION, plan.currentPhase.lifecycleStage)
        assertTrue(DeviceLightPlanReason.DIRECT_DAYLIGHT_GUARD in plan.reasons)
        assertTrue(DeviceLightPlanWarning.DIRECT_DAYLIGHT_OVERLAP in plan.warnings)
        assertTrue(plan.maximumChannelPercent <= 35)
    }

    @Test
    fun `wanted shrimp biofilm is not treated as nuisance algae`() {
        val plan = calculate(
            tank = tank(
                hasShrimp = true,
                lastLightingResetEpochDay = TODAY - 120,
                lastAppliedPhotoperiodMinutes = 360,
                lastAppliedMaximumChannelPercent = 45,
                lastAppliedEpochDay = TODAY - 14
            ),
            input = input().copy(
                surfaceGrowth = AquariumSurfaceGrowth.TARGET_BIOFILM,
                shelterAvailability = AquariumShelterAvailability.ADEQUATE
            )
        )

        assertEquals(DeviceLightLifecycleStage.ACCLIMATION, plan.currentPhase.lifecycleStage)
        assertTrue(DeviceLightPlanReason.TARGET_BIOFILM_PROTECTED in plan.reasons)
        assertFalse(DeviceLightPlanReason.STABLE_ALGAE_HOLD in plan.reasons)
    }

    @Test
    fun `evidence and identity are included in the deterministic fingerprint`() {
        val base = calculate(tank = tank())
        val changedHardware = calculate(tank = tank(hardwareRevision = "rev-b"))

        assertTrue("chihiros_aqua_soil_launch" in base.evidenceSourceIds)
        assertTrue("tropica_growing_in" in base.evidenceSourceIds)
        assertTrue("tropica_plant_database" in base.evidenceSourceIds)
        assertTrue("tropica_plant_4442" in base.evidenceSourceIds)
        assertFalse(base.profileFingerprint == changedHardware.profileFingerprint)
        assertTrue(base.profileFingerprint.matches(Regex("^[0-9a-f]{16}$")))
    }

    @Test
    fun `fixture product controls the emitted channel set`() {
        val wrgb = calculate(tank = tank(productKey = "LIGHT_WRGB_PRO_ELITE"))
        val rgb = calculate(tank = tank(productKey = "LIGHT_RGB_PRO_SLIM"))

        assertEquals(DeviceLightAutomaticChannel.entries.toSet(), wrgb.scene.channels.keys)
        assertEquals(
            setOf(
                DeviceLightAutomaticChannel.RED,
                DeviceLightAutomaticChannel.GREEN,
                DeviceLightAutomaticChannel.BLUE
            ),
            rgb.scene.channels.keys
        )
    }

    @Test
    fun `missing and future setup dates stay in the safe startup stage`() {
        val missing = calculate(
            tank = tank(setupDateEpochDay = null, lastLightingResetEpochDay = null)
        )
        val future = calculate(
            tank = tank(
                setupDateEpochDay = TODAY + 3,
                lastLightingResetEpochDay = TODAY + 3
            )
        )

        assertEquals(DeviceLightLifecycleStage.STARTUP, missing.currentPhase.lifecycleStage)
        assertEquals(DeviceLightLifecycleStage.STARTUP, future.currentPhase.lifecycleStage)
        assertTrue(DeviceLightPlanWarning.SETUP_DATE_MISSING in missing.warnings)
        assertTrue(DeviceLightPlanWarning.SETUP_DATE_IN_FUTURE in future.warnings)
    }

    private fun calculate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput = input()
    ): DeviceLightQuickSetupPlan = DeviceLightQuickSetupCalculator.calculate(
        tank = tank,
        input = input,
        todayEpochDay = TODAY
    )

    private fun DeviceLightQuickSetupPlan.durationMillis(): Long =
        currentPhase.draft.endTimeMs - currentPhase.draft.startTimeMs

    private fun tank(
        setupDateEpochDay: Long? = TODAY - 5,
        productKey: String = "LIGHT_WRGB_PRO_ELITE",
        hardwareRevision: String = "rev-a",
        hasShrimp: Boolean = false,
        plantCoverage: AquariumPlantCoverage = AquariumPlantCoverage.MEDIUM,
        waterDepthCm: Int? = 35,
        lastLightingResetEpochDay: Long? = setupDateEpochDay,
        lastAppliedPhotoperiodMinutes: Int? = null,
        lastAppliedMaximumChannelPercent: Int? = null,
        lastAppliedEpochDay: Long? = null,
        lastAppliedWaterDepthCm: Int? = if (lastAppliedPhotoperiodMinutes == null) {
            null
        } else {
            waterDepthCm
        }
    ) = DeviceLightQuickSetupTank(
        tankId = 7,
        tankName = "Living room",
        deviceLocalEpochDay = TODAY,
        setupDateEpochDay = setupDateEpochDay,
        tankHeightCm = 45,
        hasPlants = true,
        plantDemand = DeviceLightPlantDemand.MEDIUM,
        reviewedPlantDemandFloor = DeviceLightPlantDemand.MEDIUM,
        plantCatalogIds = setOf("plant:micranthemum_tweediei_monte_carlo"),
        plantEvidenceSourceIds = setOf("tropica_plant_4442"),
        plantCoverage = plantCoverage,
        co2ComponentPresent = true,
        co2Readiness = AquariumCo2Readiness.READY_AT_LIGHT_ON,
        substrateSemantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        substrateProductIds = setOf("substrate_chihiros_aquasoil_9l"),
        substrateEvidenceSourceIds = setOf("chihiros_aqua_soil_launch"),
        daylightExposure = AquariumDaylightExposure.LOW,
        daylightStartMinute = null,
        daylightEndMinute = null,
        preferredLightEndMinute = 20 * 60,
        surfaceGrowth = AquariumSurfaceGrowth.NONE,
        latestObservationEpochDay = TODAY,
        hasShrimp = hasShrimp,
        shelterAvailability = if (hasShrimp) {
            AquariumShelterAvailability.ADEQUATE
        } else {
            AquariumShelterAvailability.NOT_REQUIRED
        },
        waterDepthCm = waterDepthCm,
        fixtureHeightAboveWaterCm = 10,
        profileUpdatedAtMillis = null,
        installationUpdatedAtMillis = null,
        plantedFreshwater = true,
        productKey = productKey,
        productDisplayName = "AquaLight",
        hardwareRevision = hardwareRevision,
        fixtureLengthMm = 900,
        calibrationProfile = null,
        lastLightingResetEpochDay = lastLightingResetEpochDay,
        lastAppliedPhotoperiodMinutes = lastAppliedPhotoperiodMinutes,
        lastAppliedMaximumChannelPercent = lastAppliedMaximumChannelPercent,
        lastAppliedEpochDay = lastAppliedEpochDay,
        nextReevaluationEpochDay = null,
        lastAppliedWaterDepthCm = lastAppliedWaterDepthCm
    )

    private fun input() = DeviceLightQuickSetupInput(
        plantDemand = DeviceLightPlantDemand.MEDIUM,
        plantCoverage = AquariumPlantCoverage.MEDIUM,
        waterDepthCm = 35,
        fixtureHeightAboveWaterCm = 10,
        co2Readiness = AquariumCo2Readiness.READY_AT_LIGHT_ON,
        substrateSemantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        daylightExposure = AquariumDaylightExposure.LOW,
        daylightStartMinute = null,
        daylightEndMinute = null,
        surfaceGrowth = AquariumSurfaceGrowth.NONE,
        shelterAvailability = AquariumShelterAvailability.NOT_REQUIRED,
        programEndMinute = 20 * 60
    )

    private companion object {
        const val TODAY = 20_000L
        const val MINUTE_MS = 60_000L
    }
}
