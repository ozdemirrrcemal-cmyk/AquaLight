package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingCapabilityGateTest {
    @Test
    fun `known unsupported fan display names do not call gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val store = DeviceCoolingRuntimeStateStore()
        store.recordStatus(
            DEVICE_UID,
            DeviceCoolingStatusParser.parse(
                DeviceCoolingRuntimeFixtures.status(fanDisplayNameEditable = false)
            )
        )
        val repository = DeviceCoolingRuntimeRepository(gateway, store)

        val outcome = repository.setFanDisplayNames(
            DEVICE_UID,
            listOf(DeviceCoolingFanDisplayNamePayload("fan1", "Sol Fan"))
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `oversized UTF8 display name is rejected before gateway`() {
        val gateway = RejectingGateway()

        val failure = runCatching {
            DeviceCoolingFanDisplayNamePayload("fan1", "ş".repeat(17))
        }

        assertTrue(failure.isFailure)
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
            error("Unsupported operation reached the command gateway.")
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-COOLING-GATE")
    }
}
