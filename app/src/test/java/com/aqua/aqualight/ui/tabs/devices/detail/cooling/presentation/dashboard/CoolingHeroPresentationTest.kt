package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.ui.common.cooling.fanMotionDegreesPerSecond
import com.aqua.aqualight.ui.common.cooling.fanMotionDurationMillis
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingControlPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDashboardOverviewPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingHealthState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingHeroPresentationTest {

    @Test
    fun `current positive fan output drives cooling motion`() {
        val presentation = state(fanPercent = 60.0).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.COOLING, presentation.status)
        assertEquals(60.0, presentation.fanPercent ?: 0.0, 0.0)
        assertTrue(presentation.isCooling)
        assertEquals(EXPECTED_SIXTY_PERCENT_INTENSITY, presentation.motionIntensity, NO_DELTA)
    }

    @Test
    fun `fractional automatic output keeps proportional cooling motion`() {
        val presentation = state(fanPercent = CONTINUOUS_AUTOMATIC_PERCENT)
            .toCoolingHeroPresentation()

        assertTrue(presentation.isCooling)
        assertEquals(
            EXPECTED_CONTINUOUS_AUTOMATIC_INTENSITY,
            presentation.motionIntensity,
            continuousAutomaticIntensityDelta
        )
    }

    @Test
    fun `manual target never overrides applied fan output for animation`() {
        val presentation = state(
            fanPercent = 35.0,
            options = HeroStateOptions(manualFanPercent = 90)
        ).toCoolingHeroPresentation()

        assertEquals(35.0, presentation.fanPercent ?: 0.0, 0.0)
        assertEquals(EXPECTED_THIRTY_FIVE_PERCENT_INTENSITY, presentation.motionIntensity, NO_DELTA)
        assertTrue(presentation.isCooling)
    }

    @Test
    fun `fan rotation period follows applied output proportionally`() {
        assertEquals(FULL_OUTPUT_PERIOD_MILLIS, fanMotionDurationMillis(1f))
        assertEquals(HALF_OUTPUT_PERIOD_MILLIS, fanMotionDurationMillis(0.5f))
        assertEquals(QUARTER_OUTPUT_PERIOD_MILLIS, fanMotionDurationMillis(0.25f))
    }

    @Test
    fun `fan angular velocity stops at zero and follows output proportionally`() {
        val fullOutputSpeed = fanMotionDegreesPerSecond(1f)

        assertEquals(NO_MOTION, fanMotionDegreesPerSecond(0f), NO_DELTA)
        assertEquals(fullOutputSpeed / 2f, fanMotionDegreesPerSecond(0.5f), NO_DELTA)
        assertTrue(fullOutputSpeed > NO_MOTION)
    }

    @Test
    fun `zero fan output keeps the scene calmly ready`() {
        val presentation = state(
            fanPercent = 0.0,
            options = HeroStateOptions(operatingState = DeviceCoolingOperatingState.IDLE)
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.STANDBY, presentation.status)
        assertFalse(presentation.isCooling)
        assertEquals(NO_MOTION, presentation.motionIntensity, NO_DELTA)
    }

    @Test
    fun `positive retained output cannot override firmware idle state`() {
        val presentation = state(
            fanPercent = 60.0,
            options = HeroStateOptions(operatingState = DeviceCoolingOperatingState.IDLE)
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.STANDBY, presentation.status)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `firmware cooling state is preserved while motion still reflects applied output`() {
        val presentation = state(fanPercent = 0.0).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.COOLING, presentation.status)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `stale telemetry remains visible without pretending the fan is moving`() {
        val presentation = state(
            fanPercent = 60.0,
            options = HeroStateOptions(freshness = CoolingDataFreshness.STALE)
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.WAITING_FOR_DATA, presentation.status)
        assertEquals(60.0, presentation.fanPercent ?: 0.0, 0.0)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `waiting state motion never implies live cooling`() {
        val motion = state(
            fanPercent = 0.0,
            options = HeroStateOptions(freshness = CoolingDataFreshness.STALE)
        ).toCoolingHeroPresentation().resolveMotion()

        assertFalse(motion.isActive)
        assertEquals(NO_MOTION, motion.intensity, NO_DELTA)
    }

    @Test
    fun `alarm takes precedence over live fan output`() {
        val presentation = state(
            fanPercent = 60.0,
            options = HeroStateOptions(alarmCount = 1)
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.ATTENTION, presentation.status)
        assertTrue(presentation.isCooling)
    }

    @Test
    fun `fan fault prevents false motion even when output is retained`() {
        val presentation = state(
            fanPercent = 60.0,
            options = HeroStateOptions(fanOutputHealth = CoolingHealthState.FAULT)
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.ATTENTION, presentation.status)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `offline root never animates retained telemetry`() {
        val presentation = state(
            fanPercent = 60.0,
            options = HeroStateOptions(contentEnabled = false)
        )
            .copy(connectionVisualState = DeviceConnectionVisualState.OFFLINE)
            .toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.OFFLINE, presentation.status)
        assertFalse(presentation.isCooling)
    }

    private fun state(
        fanPercent: Double,
        options: HeroStateOptions = HeroStateOptions()
    ): DeviceCoolingRootUiState = DeviceCoolingRootUiState(
        connectionVisualState = DeviceConnectionVisualState.ONLINE,
        contentEnabled = options.contentEnabled,
        controlState = CoolingDataState.Content(
            value = CoolingControlPresentation(
                selectedMode = DeviceCoolingControlMode.AUTOMATIC,
                supportedModes = setOf(DeviceCoolingControlMode.AUTOMATIC),
                modeSelectionWritable = true,
                manualFanCapabilities = null,
                manualFanPercent = options.manualFanPercent,
                actualFanPercent = fanPercent,
                tankTemperatureC = TANK_TEMPERATURE_C,
                operatingState = options.operatingState
            ),
            freshness = options.freshness
        ),
        dashboardOverviewState = CoolingDataState.Content(
            CoolingDashboardOverviewPresentation(
                roomTemperatureC = null,
                humidityPercent = null,
                powerWatts = null,
                estimatedKwhPerDay = null,
                programSlotCount = null,
                fanOutputHealth = options.fanOutputHealth,
                sensorHealth = CoolingHealthState.READY,
                activeAlarmCount = options.alarmCount
            )
        )
    )

    private data class HeroStateOptions(
        val manualFanPercent: Int? = null,
        val freshness: CoolingDataFreshness = CoolingDataFreshness.CURRENT,
        val alarmCount: Int = 0,
        val fanOutputHealth: CoolingHealthState = CoolingHealthState.READY,
        val contentEnabled: Boolean = true,
        val operatingState: DeviceCoolingOperatingState = DeviceCoolingOperatingState.COOLING
    )

    private companion object {
        const val TANK_TEMPERATURE_C = 25.6
        const val EXPECTED_SIXTY_PERCENT_INTENSITY = 0.6f
        const val EXPECTED_THIRTY_FIVE_PERCENT_INTENSITY = 0.35f
        const val CONTINUOUS_AUTOMATIC_PERCENT = 35.95
        const val EXPECTED_CONTINUOUS_AUTOMATIC_INTENSITY = 0.3595f
        val continuousAutomaticIntensityDelta =
            Math.ulp(EXPECTED_CONTINUOUS_AUTOMATIC_INTENSITY)
        const val FULL_OUTPUT_PERIOD_MILLIS = 620
        const val HALF_OUTPUT_PERIOD_MILLIS = 1240
        const val QUARTER_OUTPUT_PERIOD_MILLIS = 2480
        const val NO_MOTION = 0f
        const val NO_DELTA = 0f
    }
}
