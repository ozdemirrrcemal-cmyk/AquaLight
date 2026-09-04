package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightCapabilityGateTest {
    @Test
    fun `known unsupported manual capability does not call gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val store = DeviceLightRuntimeStateStore()
        val unsupported = DeviceLightRuntimeFixtures.status().also { status ->
            status.put("manualSupported", false)
            status.getJSONObject("runtime").put("supportsManualSet", false)
        }
        store.recordStatus(
            DEVICE_UID,
            GENERATION,
            DeviceLightStatusParser.parse(unsupported)
        )
        val repository = DeviceLightRuntimeRepository(gateway, store)

        val outcome = repository.setManual(
            DEVICE_UID,
            DeviceLightManualSetPayload(
                channels = listOf(DeviceLightManualChannelPayload("white", percent = 25.0))
            )
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `ambiguous manual request is rejected before gateway send`() = runBlocking {
        val gateway = EncodingGateway()
        val repository = DeviceLightRuntimeRepository(gateway)

        val failure = runCatching {
            repository.setManual(
                DEVICE_UID,
                DeviceLightManualSetPayload(
                    channels = listOf(
                        DeviceLightManualChannelPayload(
                            channelKey = "white",
                            percent = 25.0,
                            value = 0.25
                        )
                    )
                )
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(0, gateway.sent)
    }

    private class RejectingGateway : DeviceRuntimeCommandGateway {
        var calls = 0

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            calls++
            error("Unsupported operation reached the command gateway.")
        }
    }

    private class EncodingGateway : DeviceRuntimeCommandGateway {
        var sent = 0

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            command.encodeData()
            sent++
            error("Test stops after request encoding.")
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-GATE")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
