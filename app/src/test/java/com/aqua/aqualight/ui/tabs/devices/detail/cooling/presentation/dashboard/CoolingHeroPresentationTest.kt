package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingDashboardOverviewPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingHealthState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingHeroPresentationTest {

    @Test
    fun `current positive fan output drives cooling motion`() {
        val presentation = state(fanPercent = 60).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.COOLING, presentation.status)
        assertEquals(60, presentation.fanPercent)
        assertTrue(presentation.isCooling)
        assertEquals(EXPECTED_SIXTY_PERCENT_INTENSITY, presentation.motionIntensity, NO_DELTA)
    }

    @Test
    fun `zero fan output keeps the scene calmly ready`() {
        val presentation = state(fanPercent = 0).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.STANDBY, presentation.status)
        assertFalse(presentation.isCooling)
        assertEquals(NO_MOTION, presentation.motionIntensity, NO_DELTA)
    }

    @Test
    fun `stale telemetry remains visible without pretending the fan is moving`() {
        val presentation = state(
            fanPercent = 60,
            freshness = CoolingDataFreshness.STALE
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.WAITING_FOR_DATA, presentation.status)
        assertEquals(60, presentation.fanPercent)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `enabled waiting state motion starts before first telemetry`() {
        val motion = state(
            fanPercent = 0,
            freshness = CoolingDataFreshness.STALE
        ).toCoolingHeroPresentation().resolveMotion(allowWaitingMotion = true)

        assertTrue(motion.isActive)
        assertEquals(WAITING_MOTION_INTENSITY, motion.intensity, NO_DELTA)
    }

    @Test
    fun `disabled waiting state motion does not imply live cooling`() {
        val motion = state(
            fanPercent = 0,
            freshness = CoolingDataFreshness.STALE
        ).toCoolingHeroPresentation().resolveMotion(allowWaitingMotion = false)

        assertFalse(motion.isActive)
        assertEquals(NO_MOTION, motion.intensity, NO_DELTA)
    }

    @Test
    fun `alarm takes precedence over live fan output`() {
        val presentation = state(fanPercent = 60, alarmCount = 1).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.ATTENTION, presentation.status)
        assertTrue(presentation.isCooling)
    }

    @Test
    fun `fan fault prevents false motion even when output is retained`() {
        val presentation = state(
            fanPercent = 60,
            fanHealth = CoolingHealthState.FAULT
        ).toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.ATTENTION, presentation.status)
        assertFalse(presentation.isCooling)
    }

    @Test
    fun `offline root never animates retained telemetry`() {
        val presentation = state(fanPercent = 60, contentEnabled = false)
            .copy(connectionVisualState = DeviceConnectionVisualState.OFFLINE)
            .toCoolingHeroPresentation()

        assertEquals(CoolingHeroVisualStatus.OFFLINE, presentation.status)
        assertFalse(presentation.isCooling)
    }

    private fun state(
        fanPercent: Int,
        freshness: CoolingDataFreshness = CoolingDataFreshness.CURRENT,
        alarmCount: Int = 0,
        fanHealth: CoolingHealthState = CoolingHealthState.READY,
        contentEnabled: Boolean = true
    ): DeviceCoolingRootUiState = DeviceCoolingRootUiState(
        connectionVisualState = DeviceConnectionVisualState.ONLINE,
        contentEnabled = contentEnabled,
        controlState = CoolingDataState.Content(
            value = CoolingControlPresentation(
                selectedMode = DeviceCoolingControlMode.AUTOMATIC,
                supportedModes = setOf(DeviceCoolingControlMode.AUTOMATIC),
                modeSelectionWritable = true,
                manualFanCapabilities = null,
                manualFanPercent = null,
                actualFanPercent = fanPercent,
                tankTemperatureC = TANK_TEMPERATURE_C
            ),
            freshness = freshness
        ),
        dashboardOverviewState = CoolingDataState.Content(
            CoolingDashboardOverviewPresentation(
                roomTemperatureC = null,
                humidityPercent = null,
                powerWatts = null,
                estimatedKwhPerDay = null,
                roomTemperatureHistoryC = emptyList(),
                programSlotCount = null,
                nextProgramStartMinutesOfDay = null,
                fanHealth = fanHealth,
                sensorHealth = CoolingHealthState.READY,
                activeAlarmCount = alarmCount
            )
        )
    )

    private companion object {
        const val TANK_TEMPERATURE_C = 25.6
        const val EXPECTED_SIXTY_PERCENT_INTENSITY = 0.6f
        const val WAITING_MOTION_INTENSITY = 0.58f
        const val NO_MOTION = 0f
        const val NO_DELTA = 0f
    }
}
