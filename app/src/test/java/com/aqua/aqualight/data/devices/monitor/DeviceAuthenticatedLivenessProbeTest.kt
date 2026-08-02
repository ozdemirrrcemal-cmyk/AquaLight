package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthenticatedLivenessProbeTest {

    @Test
    fun `records proof only after correlated firmware success`() = runTest {
        val recorded = mutableListOf<DeviceUid>()
        val probe = DeviceAuthenticatedLivenessProbe(
            requestStatus = {
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = DEVICE_UID,
                    module = "network",
                    action = "status.get",
                    messageId = "network-proof-1",
                    generation = GENERATION,
                    statusCode = 200,
                    value = Unit
                )
            },
            recordProof = recorded::add
        )

        assertTrue(probe.execute(DEVICE_UID))
        assertEquals(listOf(DEVICE_UID), recorded)
    }

    @Test
    fun `queued or timed out command never records liveness proof`() = runTest {
        val recorded = mutableListOf<DeviceUid>()
        val probe = DeviceAuthenticatedLivenessProbe(
            requestStatus = {
                DeviceRuntimeCommandOutcome.Timeout(
                    deviceUid = DEVICE_UID,
                    module = "network",
                    action = "status.get",
                    messageId = "network-proof-2",
                    generation = GENERATION,
                    timeoutMillis = 8_000L
                )
            },
            recordProof = recorded::add
        )

        assertFalse(probe.execute(DEVICE_UID))
        assertTrue(recorded.isEmpty())
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIVENESS-PROBE")
        val GENERATION = DeviceRuntimeConnectionGeneration(3L)
    }
}
