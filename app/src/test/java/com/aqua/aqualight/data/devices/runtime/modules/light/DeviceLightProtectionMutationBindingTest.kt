package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightProtectionMutationBindingTest {
    private val fixtures = DeviceLightProtectionOrderingFixtures
    private val uid = fixtures.uid
    private val payload = DeviceLightTemperatureProtectionSetPayload(65.0)

    @Test
    fun `generation bound mutation is rejected when reconnect occurs at dispatch`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        fixture.gateway.beforeDispatch = {
            fixture.owner.invalidate(uid, fixtures.firstGeneration)
            fixture.owner.beginGeneration(uid, fixtures.nextGeneration)
            fixture.owner.recordTemperatureProtection(uid, fixtures.nextGeneration, fixtures.snapshot(66.0))
            fixture.gateway.generation = fixtures.nextGeneration
        }
        assertTrue(fixture.repository.setThreshold(uid, payload) is DeviceRuntimeCommandOutcome.Cancelled)
        assertTrue(fixture.gateway.calls.isEmpty())
        assertEquals(fixtures.snapshot(66.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `cancelled mutation preserves the snapshot and leaves the gate available`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        cancelled.cancel()
        cancelled.join()
        fixture.gateway.calls.single().data.complete(fixtures.acknowledgement())
        assertTrue(cancelled.isCancelled)
        assertEquals(fixtures.snapshot(), fixture.repository.currentStatus(uid))

        val next = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        fixture.gateway.calls.last().data.complete(fixtures.acknowledgement())
        assertTrue(next.await() is DeviceRuntimeCommandOutcome.Success)
        assertEquals(fixtures.snapshot(65.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `known generation read is rejected when reconnect occurs at dispatch`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        fixture.gateway.beforeDispatch = {
            fixture.owner.beginGeneration(uid, fixtures.nextGeneration)
            fixture.gateway.generation = fixtures.nextGeneration
        }
        assertTrue(fixture.repository.requestStatus(uid) is DeviceRuntimeCommandOutcome.Cancelled)
        assertTrue(fixture.gateway.calls.isEmpty())
        assertNull(fixture.repository.currentStatus(uid))
    }

    @Test
    fun `old ACK cannot replace reconnect state or launch a read on the replacement connection`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        fixture.owner.beginGeneration(uid, fixtures.nextGeneration)
        fixture.owner.recordTemperatureProtection(uid, fixtures.nextGeneration, fixtures.snapshot(66.0))
        fixture.gateway.generation = fixtures.nextGeneration
        fixture.gateway.calls.single().data.complete(fixtures.acknowledgement())
        val outcome = mutation.await() as DeviceRuntimeCommandOutcome.Success
        assertEquals(fixtures.firstGeneration, outcome.generation)
        assertEquals(1, fixture.gateway.calls.size)
        assertEquals(fixtures.snapshot(66.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `old ACK cannot recreate cleared or revoked authority`() = runTest {
        for (clear in listOf(false, true)) {
            val fixture = DeviceLightProtectionOrderingFixture()
            val mutation = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.repository.setThreshold(uid, payload)
            }
            if (clear) fixture.owner.clear(uid) else fixture.owner.invalidate(uid, fixtures.firstGeneration)
            fixture.gateway.calls.single().data.complete(fixtures.acknowledgement())
            assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
            assertEquals(1, fixture.gateway.calls.size)
            assertNull(fixture.repository.currentStatus(uid))
            assertEquals(if (clear) null else fixtures.snapshot(), fixture.repository.states.value[uid])
        }
    }
}
