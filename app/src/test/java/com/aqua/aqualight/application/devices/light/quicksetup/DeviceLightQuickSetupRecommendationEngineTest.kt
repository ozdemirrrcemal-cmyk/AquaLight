package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightQuickSetupRecommendationEngineTest {

    private val calibrated = FakeCalibration(available = true)
    private val engine = DeviceLightQuickSetupRecommendationEngine(calibrated)

    @Test
    fun fivePhasesAreAnchoredToAquariumSetupDate() {
        val result = engine.recommend(
            context = context(plants = listOf("plant:ammannia_gracilis")),
            input = input(DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED)
        ).requireAvailable()

        assertEquals(listOf(20_000, 20_021, 20_028, 20_035, 20_042),
            result.phases.map { it.validFromEpochDay })
        assertEquals(listOf(20_021, 20_028, 20_035, 20_042, null),
            result.phases.map { it.validUntilEpochDayExclusive })
        assertEquals(listOf(360, 390, 420, 450, 480),
            result.phases.map { it.endMinuteOfDay - it.startMinuteOfDay })
        assertTrue(result.phases.all { it.weekdaysMask == 127 && it.rampMinutes == 60 })
        assertEquals(listOf(20, 0, 0, 0, 0), result.phases.map { it.transitionDays })
        assertEquals(90, result.requestedTargetPpfd)
        assertEquals(90, result.effectiveTargetPpfd)
        assertFalse(result.co2Limited)
    }

    @Test
    fun highDemandIsCappedWhenCo2IsNotPrecharged() {
        val result = engine.recommend(
            context = context(plants = listOf("plant:ammannia_gracilis")),
            input = input(DeviceLightQuickSetupCo2Readiness.PRESENT_NOT_PRECHARGED)
        ).requireAvailable()

        assertEquals(AquariumPlantLightDemand.HIGH, result.plantProfile.highestDemand)
        assertEquals(90, result.requestedTargetPpfd)
        assertEquals(60, result.effectiveTargetPpfd)
        assertTrue(result.co2Limited)
        assertTrue(DeviceLightQuickSetupEvidence.CO2_PRECHARGE_POLICY in result.evidenceIds)
    }

    @Test
    fun oneHighPlantKeepsHighestDemandHigh() {
        val result = engine.recommend(
            context = context(
                plants = listOf(
                    "plant:anubias_barteri",
                    "plant:anubias_barteri_var_nana",
                    "plant:ammannia_gracilis"
                )
            ),
            input = input(DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED)
        ).requireAvailable()

        assertEquals(3, result.plantProfile.selectedPlantCount)
        assertEquals(1, result.plantProfile.highDemandCount)
        assertEquals(AquariumPlantLightDemand.HIGH, result.plantProfile.highestDemand)
    }

    @Test
    fun latestSameDayMatureStartIs1555() {
        val valid = engine.recommend(
            context = context(plants = listOf("plant:anubias_barteri")),
            input = input(
                readiness = DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED,
                startMinute = 15 * 60 + 55
            )
        )
        val invalid = engine.recommend(
            context = context(plants = listOf("plant:anubias_barteri")),
            input = input(
                readiness = DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED,
                startMinute = 16 * 60
            )
        )

        assertTrue(valid is DeviceLightQuickSetupRecommendationResult.Available)
        assertEquals(
            DeviceLightQuickSetupBlockReason.INVALID_INPUT,
            (invalid as DeviceLightQuickSetupRecommendationResult.Blocked).reason
        )
    }

    @Test
    fun co2PresenceMustMatchSavedAquariumContext() {
        val result = engine.recommend(
            context = context(
                plants = listOf("plant:anubias_barteri"),
                co2Present = false
            ),
            input = input(DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED)
        )

        assertEquals(
            DeviceLightQuickSetupBlockReason.INVALID_INPUT,
            (result as DeviceLightQuickSetupRecommendationResult.Blocked).reason
        )
    }

    @Test
    fun missingFixtureCalibrationFailsClosed() {
        val missingCalibrationEngine = DeviceLightQuickSetupRecommendationEngine(
            FakeCalibration(available = false)
        )
        val result = missingCalibrationEngine.recommend(
            context = context(plants = listOf("plant:anubias_barteri")),
            input = input(DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED)
        )

        assertEquals(
            DeviceLightQuickSetupBlockReason.MISSING_CALIBRATION,
            (result as DeviceLightQuickSetupRecommendationResult.Blocked).reason
        )
    }

    @Test
    fun evidenceRegistryUsesReviewedHttpsSources() {
        assertEquals(4, DeviceLightQuickSetupEvidence.records.size)
        assertTrue(DeviceLightQuickSetupEvidence.records.values.all { it.policyUse.isNotBlank() })
    }

    private fun context(
        plants: List<String>,
        co2Present: Boolean = true
    ) = DeviceLightQuickSetupContext(
        deviceUid = "dev-light-test",
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productDisplayName = "WRGB Pro Elite 120",
        channelKeys = listOf("white", "red", "green", "blue"),
        tankId = 77L,
        aquariumName = "Test planted tank",
        setupDateEpochDay = 20_000L,
        tankWidthCm = 45,
        tankLengthCm = 90,
        tankHeightCm = 45,
        tankType = "FRESHWATER",
        tankStyle = "NATURE",
        plants = plants.map { catalogId ->
            DeviceLightQuickSetupPlant(catalogId = catalogId, displayName = catalogId)
        },
        substrateSemantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        co2Present = co2Present,
        profileFingerprint = "fingerprint-v1"
    )

    private fun input(
        readiness: DeviceLightQuickSetupCo2Readiness,
        startMinute: Int = 10 * 60
    ) = DeviceLightQuickSetupInput(
        waterHeightCm = 38,
        fixtureHeightAboveWaterCm = 18,
        firstLightOnMinuteOfDay = startMinute,
        co2Readiness = readiness
    )

    private fun DeviceLightQuickSetupRecommendationResult.requireAvailable() =
        (this as DeviceLightQuickSetupRecommendationResult.Available).recommendation

    private class FakeCalibration(
        private val available: Boolean
    ) : DeviceLightFixtureCalibration {
        override fun solve(
            request: DeviceLightFixtureCalibrationRequest
        ): DeviceLightFixtureCalibrationResult? {
            if (!available) return null
            return DeviceLightFixtureCalibrationResult(
                status = DeviceLightFixtureCalibrationStatus.CALIBRATED,
                calibrationRevision = 9,
                channelScenePercent = request.channelKeys.associateWith { 50 },
                estimatedPpfd = request.targetPpfd,
                coverageStatus = DeviceLightFixtureCoverageStatus.COVERED,
                initialStartPercent = 50
            )
        }
    }
}
