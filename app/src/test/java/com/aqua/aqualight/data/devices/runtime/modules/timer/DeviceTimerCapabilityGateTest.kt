package com.aqua.aqualight.data.devices.runtime.modules.timer

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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerCapabilityGateTest {
    @Test
    fun `dosing timer engine never resolves to standalone Timer API access`() {
        val access = DeviceTimerRuntimeAccess.resolve(
            family = DeviceFamily.DOSING,
            capabilities = capabilities(standaloneTimer = false, dosing = true),
            limits = DeviceLimitSet(0, 0, 0, 0, 2),
            features = setOf(AqlDeviceFeatureKey.DOSING_CONTROL),
            screens = setOf(AqlDeviceScreenKey.DOSING_CONTROL),
            modules = modules(timerApi = false, timerEngine = false, dosing = true)
        )

        assertFalse(access.supportsApi)
        assertEquals(0, access.channelCount)
        assertFalse(access.supportsSchedules)
        assertFalse(access.supportsChannelState)
        assertFalse(access.supportsChannelDisplayName)
    }

    @Test
    fun `unavailable Timer access rejects every command before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { DeviceTimerRuntimeAccess.UNAVAILABLE }
        )

        val status = repository.requestStatus(DEVICE_UID)
        val config = repository.applyConfig(
            DEVICE_UID,
            DeviceTimerConfigApplyPayload(schedules = emptyList())
        )
        val channel = repository.setChannelRegime(
            DEVICE_UID,
            "channel1",
            DeviceTimerRegime.ON
        )

        assertTrue(status is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(config is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(channel is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `missing exact display name feature rejects rename before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = {
                DeviceTimerRuntimeAccess(
                    supportsApi = true,
                    channelCount = 2,
                    supportsSchedules = true,
                    supportsChannelState = true,
                    supportsChannelDisplayName = false
                )
            }
        )

        val outcome = repository.setChannelDisplayName(
            DEVICE_UID,
            "channel1",
            "Return Pump"
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `status channel count must match authenticated product metadata`() {
        val status = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.status())
        val mismatchedAccess = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = 4,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )

        assertTrue(
            runCatching {
                DeviceTimerCommandValidation.validateStatus(status, mismatchedAccess)
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
            error("Unsupported Timer operation reached the command gateway.")
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-GATE")

        fun capabilities(standaloneTimer: Boolean, dosing: Boolean) = DeviceCapabilitySet(
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
            timerApi: Boolean,
            timerEngine: Boolean,
            dosing: Boolean
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
