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
    fun `records proof only after current generation firmware success`() = runTest {
        val recorded = mutableListOf<Pair<DeviceUid, DeviceRuntimeConnectionGeneration>>()
        val probe = DeviceAuthenticatedLivenessProbe(
            requestStatus = {
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = DEVICE_UID,
                    module = "network",
                    action = "status.get",
                    messageId = "network-proof-1",
                    generation = CURRENT_GENERATION,
                    statusCode = 200,
                    value = Unit
                )
            },
            recordProof = { deviceUid, generation ->
                recorded += deviceUid to generation
                true
            }
        )

        assertTrue(probe.execute(DEVICE_UID))
        assertEquals(listOf(DEVICE_UID to CURRENT_GENERATION), recorded)
    }

    @Test
    fun `successful response from a replaced generation never records proof`() = runTest {
        val recorded = mutableListOf<DeviceUid>()
        val probe = DeviceAuthenticatedLivenessProbe(
            requestStatus = {
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = DEVICE_UID,
                    module = "network",
                    action = "status.get",
                    messageId = "network-proof-stale",
                    generation = STALE_GENERATION,
                    statusCode = 200,
                    value = Unit
                )
            },
            recordProof = { deviceUid, generation ->
                if (generation == CURRENT_GENERATION) {
                    recorded += deviceUid
                    true
                } else {
                    false
                }
            }
        )

        assertFalse(probe.execute(DEVICE_UID))
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `queued or timed out command never attempts liveness proof write`() = runTest {
        var writeAttempts = 0
        val probe = DeviceAuthenticatedLivenessProbe(
            requestStatus = {
                DeviceRuntimeCommandOutcome.Timeout(
                    deviceUid = DEVICE_UID,
                    module = "network",
                    action = "status.get",
                    messageId = "network-proof-2",
                    generation = CURRENT_GENERATION,
                    timeoutMillis = 8_000L
                )
            },
            recordProof = { _, _ ->
                writeAttempts += 1
                true
            }
        )

        assertFalse(probe.execute(DEVICE_UID))
        assertEquals(0, writeAttempts)
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIVENESS-PROBE")
        val STALE_GENERATION = DeviceRuntimeConnectionGeneration(3L)
        val CURRENT_GENERATION = DeviceRuntimeConnectionGeneration(4L)
    }
}
