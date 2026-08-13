package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCapabilityGateTest {

    @Test
    fun `exact Dose Pro metadata enables dedicated Dosing capabilities`() {
        val access = resolve()

        assertTrue(access.supportsApi)
        assertEquals(2, access.channelCount)
        assertTrue(access.supportsProgramEditing)
        assertTrue(access.supportsChannelReset)
        assertTrue(access.supportsPrime)
        assertTrue(access.supportsManualDose)
        assertTrue(access.supportsCalibrationWorkflow)
        assertTrue(access.supportsReservoirRefill)
        assertTrue(access.supportsChannelDisplayName)
    }

    @Test
    fun `any standalone Timer identity rejects Dosing runtime access`() {
        val access = resolve(
            capabilities = capabilities(dosing = true, standaloneTimer = true),
            limits = DeviceLimitSet(0, 0, 0, 2, 2),
            modules = modules(dosing = true, timerApi = true, timerEngine = true)
        )

        assertFalse(access.supportsApi)
        assertEquals(0, access.channelCount)
    }

    @Test
    fun `timer engine flag alone rejects Dose Pro metadata`() {
        val access = resolve(
            modules = modules(dosing = true, timerEngine = true)
        )

        assertFalse(access.supportsApi)
    }

    @Test
    fun `missing program surface disables program editing without inventing fallback`() {
        val access = resolve(
            screens = DOSING_SCREENS - AqlDeviceScreenKey.DOSING_SCHEDULES
        )

        assertTrue(access.supportsApi)
        assertFalse(access.supportsProgramEditing)
    }

    private fun resolve(
        family: DeviceFamily = DeviceFamily.DOSING,
        capabilities: DeviceCapabilitySet = capabilities(dosing = true),
        limits: DeviceLimitSet = DeviceLimitSet(0, 0, 0, 0, 2),
        features: Set<AqlDeviceFeatureKey> = DOSING_FEATURES,
        screens: Set<AqlDeviceScreenKey> = DOSING_SCREENS,
        modules: DeviceRuntimeModules = modules(dosing = true)
    ) = DeviceDosingRuntimeAccess.resolve(
        family = family,
        capabilities = capabilities,
        limits = limits,
        features = features,
        screens = screens,
        modules = modules
    )

    private companion object {
        val DOSING_FEATURES = setOf(
            AqlDeviceFeatureKey.DOSING_CONTROL,
            AqlDeviceFeatureKey.DOSING_CALIBRATION,
            AqlDeviceFeatureKey.DOSING_RESERVOIR_TRACKING,
            AqlDeviceFeatureKey.DOSING_CHANNEL_DISPLAY_NAME
        )
        val DOSING_SCREENS = setOf(
            AqlDeviceScreenKey.DOSING_CONTROL,
            AqlDeviceScreenKey.DOSING_CHANNELS,
            AqlDeviceScreenKey.DOSING_SCHEDULES,
            AqlDeviceScreenKey.DOSING_CALIBRATION,
            AqlDeviceScreenKey.DOSING_RESERVOIR,
            AqlDeviceScreenKey.DOSING_MANUAL_RUN
        )

        fun capabilities(
            dosing: Boolean = false,
            standaloneTimer: Boolean = false
        ) = DeviceCapabilitySet(
            light = false,
            manualLight = false,
            lightProgram = false,
            lightPresets = false,
            lightSimulation = false,
            fan = false,
            cooling = false,
            temperature = false,
            standaloneTimer = standaloneTimer,
            dosing = dosing,
            timeSync = true,
            ota = true
        )

        fun modules(
            dosing: Boolean = false,
            timerApi: Boolean = false,
            timerEngine: Boolean = false
        ) = DeviceRuntimeModules(
            light = false,
            cooling = false,
            temperature = false,
            timerApi = timerApi,
            timerEngine = timerEngine,
            dosing = dosing,
            network = true,
            discovery = true,
            firmware = true,
            system = true
        )
    }
}
