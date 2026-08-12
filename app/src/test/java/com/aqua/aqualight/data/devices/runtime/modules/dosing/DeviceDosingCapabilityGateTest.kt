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
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingCommandValidation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.repository.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCapabilityGateTest {
    @Test
    fun `exact commercial Dosing metadata enables every Stage 08 capability`() {
        val access = DeviceDosingRuntimeAccess.resolve(
            family = DeviceFamily.DOSING,
            capabilities = capabilities(dosing = true),
            limits = DeviceLimitSet(0, 0, 0, 0, 2),
            features = DOSING_FEATURES,
            screens = DOSING_SCREENS,
            modules = modules(dosing = true, timerEngine = true)
        )

        assertTrue(access.supportsApi)
        assertEquals(2, access.channelCount)
        assertTrue(access.supportsSchedules)
        assertTrue(access.supportsPrime)
        assertTrue(access.supportsManualDose)
        assertTrue(access.supportsCalibrationWorkflow)
        assertTrue(access.supportsReservoirRefill)
        assertTrue(access.supportsChannelDisplayName)
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
        assertFalse(access.supportsManualDose)
    }

    @Test
    fun `unavailable Dosing access rejects all eleven commands before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val repository = DeviceDosingRuntimeRepository(
            gateway,
            DeviceDosingRuntimeStateStore()
        ) { DeviceDosingRuntimeAccess.UNAVAILABLE }

        val outcomes = listOf(
            repository.requestStatus(DEVICE_UID),
            repository.applyConfig(
                DEVICE_UID,
                DeviceDosingConfigApplyPayload(schedules = emptyList())
            ),
            repository.primeStart(DEVICE_UID, "channel1"),
            repository.primeStop(DEVICE_UID, "channel1"),
            repository.calibrationStart(
                DEVICE_UID,
                DeviceDosingCalibrationStartPayload("channel1")
            ),
            repository.calibrationFinish(
                DEVICE_UID,
                DeviceDosingCalibrationFinishPayload("channel1", measuredMl = 5.0)
            ),
            repository.calibrationConfirm(DEVICE_UID, "channel1"),
            repository.calibrationCancel(DEVICE_UID, "channel1"),
            repository.doseNow(
                DEVICE_UID,
                DeviceDosingDoseNowPayload("channel1", amountMl = 5.0)
            ),
            repository.doseStop(DEVICE_UID, "channel1"),
            repository.reservoirRefill(DEVICE_UID, "channel1")
        )

        assertEquals(11, outcomes.size)
        assertTrue(
            outcomes.all { outcome ->
                outcome is DeviceRuntimeCommandOutcome.UnsupportedByDevice
            }
        )
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `missing exact Dosing display feature rejects rename before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val access = SUPPORTED_ACCESS.copy(supportsChannelDisplayName = false)
        val repository = DeviceDosingRuntimeRepository(
            gateway,
            DeviceDosingRuntimeStateStore()
        ) { access }

        val outcome = repository.setChannelDisplayName(
            DEVICE_UID,
            "channel1",
            "Macro Pump"
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `status channel count must match authenticated product metadata`() {
        val status = DeviceDosingStatusParser.parse(DeviceDosingRuntimeFixtures.status())
        val mismatchedAccess = SUPPORTED_ACCESS.copy(channelCount = 4)

        assertTrue(
            runCatching {
                DeviceDosingCommandValidation.validateStatus(status, mismatchedAccess)
            }.isFailure
        )
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
        val SUPPORTED_ACCESS = DeviceDosingRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsPrime = true,
            supportsManualDose = true,
            supportsCalibrationWorkflow = true,
            supportsReservoirRefill = true,
            supportsChannelDisplayName = true
        )
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
