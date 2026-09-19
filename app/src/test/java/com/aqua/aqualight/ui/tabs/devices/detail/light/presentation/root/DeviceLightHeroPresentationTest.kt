package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightHeroPresentationTest {

    @Test
    fun `automatic active output maps every dynamic hero field`() {
        val presentation = DeviceLightHeroSnapshot(
            mode = DeviceLightControlMode.AUTOMATIC,
            outputActive = true,
            outputCondition = DeviceLightOutputCondition.ACTIVE,
            outputHealthy = true,
            estimatedPowerWatts = 76.0,
            estimatedColorTemperatureKelvin = 5000
        ).toHeroPresentation()

        assertEquals(R.string.device_light_hero_title_on, presentation.titleRes)
        assertEquals(R.string.device_light_hero_mode_automatic, presentation.modeRes)
        assertEquals(R.string.device_light_hero_output_steady, presentation.outputRes)
        assertEquals(R.string.device_light_hero_output_healthy, presentation.healthRes)
        assertEquals(DeviceLightHeroHealthTone.HEALTHY, presentation.healthTone)
        assertEquals(76.0, presentation.estimatedPowerWatts ?: Double.NaN, 0.0)
        assertEquals(5000, presentation.estimatedColorTemperatureKelvin)
    }

    @Test
    fun `hardware fault maps off and attention copy`() {
        val presentation = DeviceLightHeroSnapshot(
            mode = DeviceLightControlMode.MANUAL,
            outputActive = false,
            outputCondition = DeviceLightOutputCondition.HARDWARE_FAULT,
            outputHealthy = false
        ).toHeroPresentation()

        assertEquals(R.string.device_light_hero_title_off, presentation.titleRes)
        assertEquals(R.string.device_light_hero_mode_manual, presentation.modeRes)
        assertEquals(R.string.device_light_hero_output_hardware_fault, presentation.outputRes)
        assertEquals(R.string.device_light_hero_output_attention, presentation.healthRes)
        assertEquals(DeviceLightHeroHealthTone.ATTENTION, presentation.healthTone)
    }

    @Test
    fun `missing telemetry remains explicitly unavailable`() {
        val presentation = DeviceLightHeroSnapshot().toHeroPresentation()

        assertEquals(R.string.device_light_hero_title_unavailable, presentation.titleRes)
        assertEquals(R.string.device_light_hero_mode_unavailable, presentation.modeRes)
        assertEquals(R.string.device_light_hero_output_unavailable, presentation.outputRes)
        assertEquals(
            R.string.device_light_hero_output_health_unavailable,
            presentation.healthRes
        )
        assertEquals(DeviceLightHeroHealthTone.UNAVAILABLE, presentation.healthTone)
        assertEquals(null, presentation.estimatedPowerWatts)
        assertEquals(null, presentation.estimatedColorTemperatureKelvin)
    }
}
