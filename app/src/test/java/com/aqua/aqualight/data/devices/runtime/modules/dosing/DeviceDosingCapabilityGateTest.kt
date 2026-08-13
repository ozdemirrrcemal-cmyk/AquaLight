package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.repository.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCapabilityGateTest {
    @Test
    fun `Dose Pro metadata enables dedicated Dosing runtime without Timer engine`() {
        val access = DeviceDosingRuntimeAccess.resolve(
            family = DeviceFamily.DOSING,
            capabilities = capabilities(dosing = true),
            limits = DeviceLimitSet(0, 0, 0, 0, 2),
            features = DOSING_FEATURES,
            screens = DOSING_SCREENS,
            modules = modules(dosing = true)
        )

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
    fun `Dosing metadata with Timer engine is rejected`() {
        val access = DeviceDosingRuntimeAccess.resolve(
            family = DeviceFamily.DOSING,
            capabilities = capabilities(dosing = true),
            limits = DeviceLimitSet(0, 0, 0, 0, 2),
            features = DOSING_FEATURES,
            screens = DOSING_SCREENS,
            modules = modules(dosing = true, timerEngine = true)
        )

        assertFalse(access.supportsApi)
    }

    @Test
    fun `standalone Timer metadata never resolves to Dosing API access`() {
        val access = DeviceDosingRuntimeAccess.resolve(
            family = DeviceFamily.TIMER,
            capabilities = capabilities(standaloneTimer = true),
            limits = DeviceLimitSet(0, 0, 0, 2, 0),
            features = setOf(AqlDeviceFeatureKey.TIMER_CONTROL),
            screens = setOf(AqlDeviceScreenKey.TIMER_CONTROL),
            modules = modules(timerApi = true, timerEngine = true)
        )

        assertFalse(access.supportsApi)
        assertEquals(0, access.channelCount)
    }

    @Test
    fun `unavailable Dosing access rejects every command family before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val repository = DeviceDosingRuntimeRepository(
            gateway,
            DeviceDosingRuntimeStateStore()
        ) { DeviceDosingRuntimeAccess.UNAVAILABLE }
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            mode = DeviceDosingProgramMode.SINGLE,
            missedDoseRecoveryEnabled = false,
            config = DeviceDosingDistributedProgramConfig(10.0, 28_800_000L)
        )

        val outcomes = listOf(
            repository.requestStatus(DEVICE_UID),
            repository.requestChannelStatus(DEVICE_UID, "channel1"),
            repository.setChannelDisplayName(DEVICE_UID, "channel1", "Macro"),
            repository.saveProgram(DEVICE_UID, "channel1", program),
            repository.resetChannel(DEVICE_UID, "channel1"),
            repository.primeStart(DEVICE_UID, "channel1"),
            repository.primeStop(DEVICE_UID, "channel1"),
            repository.calibrationStart(DEVICE_UID, DeviceDosingCalibrationStartPayload("channel1")),
            repository.calibrationFinish(
                DEVICE_UID,
                DeviceDosingCalibrationFinishPayload("channel1", 5.0)
            ),
            repository.calibrationConfirm(DEVICE_UID, "channel1"),
            repository.calibrationCancel(DEVICE_UID, "channel1"),
            repository.doseNow(DEVICE_UID, DeviceDosingDoseNowPayload("channel1", 5.0)),
            repository.doseStop(DEVICE_UID, "channel1"),
            repository.reservoirRefill(DEVICE_UID, "channel1")
        )

        assertTrue(outcomes.all { it is DeviceRuntimeCommandOutcome.UnsupportedByDevice })
        assertEquals(0, gateway.calls)
    }

    private class RejectingGateway : DeviceRuntimeCommandGateway {
        var calls = 0
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            calls++
            error("Unsupported Dosing operation reached the command gateway.")
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-GATE")
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
            standaloneTimer: Boolean = false,
            dosing: Boolean = false
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
            timerApi: Boolean = false,
            timerEngine: Boolean = false,
            dosing: Boolean = false
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
