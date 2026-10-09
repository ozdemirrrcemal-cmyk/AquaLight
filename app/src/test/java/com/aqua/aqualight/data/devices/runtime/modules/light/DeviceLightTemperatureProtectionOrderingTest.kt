package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightTemperatureProtectionOrderingTest {
    private val uid = DeviceLightProtectionOrderingFixtures.uid
    private val g1 = DeviceLightProtectionOrderingFixtures.firstGeneration
    private val g2 = DeviceLightProtectionOrderingFixtures.nextGeneration
    private val fixtures = DeviceLightProtectionOrderingFixtures
    private val payload = DeviceLightTemperatureProtectionSetPayload(65.0)

    @Test
    fun `delayed GET cannot roll a successful mutation ACK back`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val poll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        assertEquals(g1, fixture.gateway.calls[1].command.expectedGeneration)
        fixture.gateway.calls[1].data.complete(fixtures.acknowledgement())
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
        fixture.gateway.calls[0].data.complete(fixtures.status())
        assertTrue(poll.await() is DeviceRuntimeCommandOutcome.Success)
        assertEquals(fixtures.snapshot(65.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `a new GET after ACK wins while the earlier GET remains rejected`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val oldPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        fixture.gateway.calls[1].data.complete(fixtures.acknowledgement())
        mutation.await()
        val newPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.gateway.calls[2].data.complete(fixtures.status(67.0))
        newPoll.await()
        fixture.gateway.calls[0].data.complete(fixtures.status())
        oldPoll.await()
        assertEquals(fixtures.snapshot(67.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `latest GET wins even when it completes first`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val oldPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        val newPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.gateway.calls[1].data.complete(fixtures.status(66.0))
        newPoll.await()
        fixture.gateway.calls[0].data.complete(fixtures.status())
        oldPoll.await()
        assertEquals(fixtures.snapshot(66.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `disconnect prevents pending GET from restoring protection or System authority`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        fixture.owner.recordThermalStatus(uid, g1, fixtures.thermalStatus())
        val frame = fixture.owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE)
        val poll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.owner.invalidate(uid, g1)
        fixture.gateway.calls.single().data.complete(fixtures.status(64.0))
        poll.await()
        assertNull(fixture.repository.currentStatus(uid))
        assertNull(fixture.owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE))
        assertEquals(frame, fixture.owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.PRESENTATION))
    }

    @Test
    fun `old GET retains its dispatch generation and cannot overwrite reconnect state`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val oldPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.owner.invalidate(uid, g1)
        fixture.owner.beginGeneration(uid, g2)
        fixture.gateway.generation = g2
        val newPoll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.gateway.calls[1].data.complete(fixtures.status(66.0))
        assertEquals(g2, (newPoll.await() as DeviceRuntimeCommandOutcome.Success).generation)
        fixture.gateway.calls[0].data.complete(fixtures.status())
        assertEquals(g1, (oldPoll.await() as DeviceRuntimeCommandOutcome.Success).generation)
        assertEquals(fixtures.snapshot(66.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `bootstrap read grants protection authority before the first bound mutation`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture(authoritative = false)
        val poll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.gateway.calls.single().data.complete(fixtures.status())
        assertTrue(poll.await() is DeviceRuntimeCommandOutcome.Success)
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        assertEquals(g1, fixture.gateway.calls.last().command.expectedGeneration)
        fixture.gateway.calls.last().data.complete(fixtures.acknowledgement())
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
    }

    @Test
    fun `missing revoked and unsupported authority all block mutation dispatch`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture(authoritative = false)
        assertTrue(fixture.repository.setThreshold(uid, payload) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        fixture.owner.beginGeneration(uid, g1)
        assertTrue(fixture.repository.setThreshold(uid, payload) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        fixture.owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(supported = false))
        assertTrue(fixture.repository.setThreshold(uid, payload) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        fixture.owner.recordTemperatureProtection(uid, g1, fixtures.snapshot())
        fixture.owner.invalidate(uid, g1)
        assertTrue(fixture.repository.setThreshold(uid, payload) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(fixture.gateway.calls.isEmpty())
    }

    @Test
    fun `malformed ACK preserves the authoritative snapshot and does not supersede polling`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val poll = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        fixture.gateway.calls[1].data.complete(fixtures.acknowledgement(64.0))
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.ProtocolError)
        assertEquals(fixtures.snapshot(), fixture.repository.currentStatus(uid))
        fixture.gateway.calls[0].data.complete(fixtures.status(63.0))
        poll.await()
        assertEquals(fixtures.snapshot(63.0), fixture.repository.currentStatus(uid))
    }

    @Test
    fun `malformed and cancelled reads do not establish bootstrap authority`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture(authoritative = false)
        val malformed = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        fixture.gateway.calls.single().data.complete(JSONObject())
        assertTrue(malformed.await() is DeviceRuntimeCommandOutcome.ProtocolError)
        assertNull(fixture.repository.currentStatus(uid))
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.requestStatus(uid) }
        cancelled.cancel()
        cancelled.join()
        fixture.gateway.calls.last().data.complete(fixtures.status())
        assertTrue(cancelled.isCancelled)
        assertNull(fixture.repository.currentStatus(uid))
        assertFalse(fixture.repository.isAuthoritative(uid, g1))
    }

    @Test
    fun `queued mutation cannot switch to a rehydrated replacement connection`() = runTest {
        val fixture = DeviceLightProtectionOrderingFixture()
        val releaseGate = CompletableDeferred<Unit>()
        val holdingGate = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.gate.withDevice(uid) { releaseGate.await() }
        }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.setThreshold(uid, payload)
        }
        fixture.owner.invalidate(uid, g1)
        fixture.owner.beginGeneration(uid, g2)
        fixture.owner.recordTemperatureProtection(uid, g2, fixtures.snapshot(66.0))
        fixture.gateway.generation = g2
        releaseGate.complete(Unit)
        holdingGate.await()
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(fixture.gateway.calls.isEmpty())
        assertEquals(fixtures.snapshot(66.0), fixture.repository.currentStatus(uid))
    }
}
