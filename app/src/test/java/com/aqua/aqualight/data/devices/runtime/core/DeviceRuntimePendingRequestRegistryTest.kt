package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimePendingRequestRegistryTest {

    @Test
    fun `same message id is isolated by device and connection generation`() {
        val registry = DeviceRuntimePendingRequestRegistry()
        val firstDevice = DeviceUid("AQL-BROKER-ONE")
        val secondDevice = DeviceUid("AQL-BROKER-TWO")
        val firstGeneration = DeviceRuntimeConnectionGeneration(1L)
        val secondGeneration = DeviceRuntimeConnectionGeneration(2L)

        val first = registry.register(
            key(firstDevice, firstGeneration, SHARED_ID)
        ) { it.data }
        val second = registry.register(
            key(secondDevice, firstGeneration, SHARED_ID)
        ) { it.data }
        val third = registry.register(
            key(firstDevice, secondGeneration, SHARED_ID)
        ) { it.data }

        assertEquals(3, registry.size)
        assertSame(first, registry.find(firstDevice, firstGeneration, SHARED_ID))
        assertSame(second, registry.find(secondDevice, firstGeneration, SHARED_ID))
        assertSame(third, registry.find(firstDevice, secondGeneration, SHARED_ID))
        assertNotSame(first, second)
        assertNotSame(first, third)

        assertTrue(registry.remove(first))
        assertNull(registry.find(firstDevice, firstGeneration, SHARED_ID))
        assertSame(second, registry.find(secondDevice, firstGeneration, SHARED_ID))
        assertSame(third, registry.find(firstDevice, secondGeneration, SHARED_ID))
    }

    @Test
    fun `terminal request id cannot be reused within the same generation`() {
        val registry = DeviceRuntimePendingRequestRegistry()
        val deviceUid = DeviceUid("AQL-BROKER-TERMINAL")
        val generation = DeviceRuntimeConnectionGeneration(7L)
        val pending = registry.register(key(deviceUid, generation, SHARED_ID)) { JSONObject() }

        assertTrue(registry.remove(pending))
        assertTrue(registry.isTerminal(deviceUid, generation, SHARED_ID))

        val failure = runCatching {
            registry.register(key(deviceUid, generation, SHARED_ID)) { JSONObject() }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    private fun key(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        messageId: String
    ): DeviceRuntimeCorrelationKey = DeviceRuntimeCorrelationKey(
        deviceUid = deviceUid,
        generation = generation,
        messageId = messageId,
        module = AqlWsContract.MODULE_NETWORK,
        action = AqlWsContract.ACTION_NETWORK_STATUS_GET
    )

    private companion object {
        const val SHARED_ID = "android-shared-id"
    }
}
