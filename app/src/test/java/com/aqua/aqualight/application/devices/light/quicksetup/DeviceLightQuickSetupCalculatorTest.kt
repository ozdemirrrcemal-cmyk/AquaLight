package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightQuickSetupCalculatorTest {
    @Test
    fun `new planted tank receives five contiguous lifecycle phases`() {
        val plan = calculate(
            tank = tank(setupDateEpochDay = TODAY - 11),
            input = input().copy(activeSoil = true, co2Installed = true)
        )

        assertEquals(DeviceLightPlanConfidence.ESTIMATED, plan.confidence)
        assertEquals(5, plan.phases.size)
        assertEquals(60, plan.initialStartPercent)
        assertEquals(TODAY, plan.phases.first().draft.validFromEpochDay)
        assertNull(plan.phases.last().draft.validUntilEpochDayExclusive)
        assertTrue(
            plan.phases.zipWithNext().all { (left, right) ->
                left.draft.validUntilEpochDayExclusive == right.draft.validFromEpochDay
            }
        )
        assertEquals(
            listOf(360, 390, 420, 450, 480),
            plan.phases.map { phase ->
                ((phase.draft.endTimeMs - phase.draft.startTimeMs) / MINUTE_MS).toInt()
            }
        )
        assertTrue(DeviceLightPlanReason.NEW_TANK in plan.reasons)
        assertTrue(DeviceLightPlanReason.ACTIVE_SOIL_STARTUP in plan.reasons)
        assertTrue(DeviceLightPlanWarning.PAR_NOT_MEASURED in plan.warnings)
        assertTrue(plan.profileFingerprint.matches(Regex("^[0-9a-f]{16}$")))
    }

    @Test
    fun `mature tank receives one open ended eight hour phase`() {
        val plan = calculate(
            tank = tank(setupDateEpochDay = TODAY - 120),
            input = input().copy(co2Installed = true)
        )

        assertEquals(1, plan.phases.size)
        assertEquals(DeviceLightLifecycleStage.MATURE, plan.currentPhase.lifecycleStage)
        assertEquals(480 * MINUTE_MS, plan.currentPhase.draft.endTimeMs - plan.currentPhase.draft.startTimeMs)
        assertNull(plan.currentPhase.draft.validUntilEpochDayExclusive)
        assertEquals(85, plan.initialStartPercent)
        assertTrue(DeviceLightPlanReason.ESTABLISHED_TANK in plan.reasons)
    }

    @Test
    fun `missing CO2 caps a demanding mature profile at fifty five percent`() {
        val plan = calculate(
            tank = tank(setupDateEpochDay = TODAY - 120),
            input = input().copy(
                plantDemand = DeviceLightPlantDemand.HIGH,
                plantDensity = DeviceLightPlantDensity.DENSE,
                aquariumHeightCm = 100,
                co2Installed = false
            )
        )

        assertEquals(55, plan.scene.channels.values.maxOrNull())
        assertEquals(19, plan.currentTargetPpfd)
        assertTrue(DeviceLightPlanReason.NO_CO2_SAFETY_CAP in plan.reasons)
        assertTrue(DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_CO2 in plan.warnings)
    }

    @Test
    fun `automatic device model keeps PPFD transparent as an estimate`() {
        val plan = calculate(
            tank = tank(setupDateEpochDay = TODAY - 120),
            input = input().copy(co2Installed = true)
        )

        assertEquals(DeviceLightPlanConfidence.ESTIMATED, plan.confidence)
        assertTrue(DeviceLightPlanWarning.PAR_NOT_MEASURED in plan.warnings)
        assertTrue(DeviceLightPlanReason.ESTIMATED_PAR in plan.reasons)
        assertEquals(
            plan.currentTargetPpfd * 420 * 60.0 / 1_000_000.0,
            plan.currentEstimatedDliMolPerM2Day,
            0.0001
        )
    }

    @Test
    fun `stored aquarium height directly changes the optical model`() {
        val shallow = calculate(
            tank = tank(),
            input = input().copy(aquariumHeightCm = 30, co2Installed = true)
        )
        val deep = calculate(
            tank = tank(),
            input = input().copy(aquariumHeightCm = 80, co2Installed = true)
        )

        assertTrue(shallow.currentTargetPpfd > deep.currentTargetPpfd)
        assertFalse(shallow.profileFingerprint == deep.profileFingerprint)
    }

    @Test
    fun `scene channel shape follows the selected fixture product`() {
        val wrgb = calculate(
            tank = tank(productKey = "LIGHT_WRGB_PRO_ELITE"),
            input = input().copy(co2Installed = true)
        )
        val rgb = calculate(
            tank = tank(productKey = "LIGHT_RGB_PRO_SLIM"),
            input = input().copy(co2Installed = true)
        )

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
    fun `indirect daylight and mild algae hold progression for seven days`() {
        val plan = calculate(
            tank = tank(setupDateEpochDay = TODAY - 11),
            input = input().copy(
                plantDemand = DeviceLightPlantDemand.HIGH,
                plantDensity = DeviceLightPlantDensity.DENSE,
                aquariumHeightCm = 45,
                co2Installed = true,
                activeSoil = true,
                ambientLight = DeviceLightAmbientLight.INDIRECT,
                algaeLevel = DeviceLightAlgaeLevel.MILD
            )
        )

        assertEquals(1, plan.phases.size)
        assertNull(plan.currentPhase.draft.validUntilEpochDayExclusive)
        assertEquals(TODAY + 7, plan.reevaluationEpochDay)
        assertEquals(42, plan.scene.channels.values.maxOrNull())
        assertTrue(DeviceLightPlanReason.INDIRECT_DAYLIGHT in plan.reasons)
        assertTrue(DeviceLightPlanReason.MILD_ALGAE_GUARD in plan.reasons)
    }

    @Test
    fun `direct daylight and visible algae reduce output below clear conditions`() {
        val clear = calculate(
            tank = tank(),
            input = input().copy(co2Installed = true)
        )
        val guarded = calculate(
            tank = tank(),
            input = input().copy(
                co2Installed = true,
                ambientLight = DeviceLightAmbientLight.DIRECT,
                algaeLevel = DeviceLightAlgaeLevel.VISIBLE
            )
        )

        assertTrue(
            guarded.scene.channels.values.maxOrNull()!! < clear.scene.channels.values.maxOrNull()!!
        )
        assertEquals(1, guarded.phases.size)
        assertEquals(TODAY + 7, guarded.reevaluationEpochDay)
        assertTrue(DeviceLightPlanReason.DIRECT_DAYLIGHT_CAP in guarded.reasons)
        assertTrue(DeviceLightPlanReason.VISIBLE_ALGAE_GUARD in guarded.reasons)
    }

    @Test
    fun `future or missing setup date fails safe as a new tank`() {
        val future = calculate(
            tank = tank(setupDateEpochDay = TODAY + 10),
            input = input().copy(co2Installed = true)
        )
        val missing = calculate(
            tank = tank(setupDateEpochDay = null),
            input = input().copy(co2Installed = true)
        )

        assertTrue(DeviceLightPlanWarning.SETUP_DATE_IN_FUTURE in future.warnings)
        assertTrue(DeviceLightPlanWarning.SETUP_DATE_MISSING in missing.warnings)
        assertEquals(DeviceLightLifecycleStage.STARTUP, future.currentPhase.lifecycleStage)
        assertEquals(DeviceLightLifecycleStage.STARTUP, missing.currentPhase.lifecycleStage)
        assertEquals(TODAY, future.currentPhase.draft.validFromEpochDay)
        assertEquals(TODAY, missing.currentPhase.draft.validFromEpochDay)
    }

    private fun calculate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput
    ): DeviceLightQuickSetupPlan = DeviceLightQuickSetupCalculator.calculate(
        tank = tank,
        input = input,
        todayEpochDay = TODAY
    )

    private fun tank(
        setupDateEpochDay: Long? = TODAY - 11,
        productKey: String = "LIGHT_WRGB_PRO_ELITE"
    ) = DeviceLightQuickSetupTank(
        tankId = 7,
        tankName = "Living room",
        setupDateEpochDay = setupDateEpochDay,
        widthCm = 90,
        lengthCm = 45,
        heightCm = 45,
        plantCount = 12,
        inferredPlantDemand = DeviceLightPlantDemand.MEDIUM,
        inferredPlantDensity = DeviceLightPlantDensity.MEDIUM,
        inferredCo2Installed = true,
        inferredActiveSoil = false,
        plantedFreshwater = true,
        productKey = productKey,
        productDisplayName = "AquaLight"
    )

    private fun input() = DeviceLightQuickSetupInput(
        plantDemand = DeviceLightPlantDemand.MEDIUM,
        plantDensity = DeviceLightPlantDensity.MEDIUM,
        aquariumHeightCm = 35,
        co2Installed = false,
        activeSoil = false,
        ambientLight = DeviceLightAmbientLight.LOW,
        algaeLevel = DeviceLightAlgaeLevel.NONE,
        programEndMinute = 22 * 60
    )

    private companion object {
        const val TODAY = 20_000L
        const val MINUTE_MS = 60_000L
    }
}
