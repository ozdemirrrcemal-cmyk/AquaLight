package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightProtectionRequestAuthorityTest {
    private val uid = DeviceLightProtectionOrderingFixtures.uid
    private val g1 = DeviceLightProtectionOrderingFixtures.firstGeneration
    private val g2 = DeviceLightProtectionOrderingFixtures.nextGeneration
    private val fixtures = DeviceLightProtectionOrderingFixtures
    private val projection = DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION
    private val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

    @Test
    fun `accepted unsolicited protection supersedes polling and preserves the newer System frame`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        owner.recordThermalStatus(uid, g1, fixtures.thermalStatus())
        val pending = owner.beginStatusRequest(uid, projection)
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(65.0)))
        val frame = owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE)
        val revision = owner.stateRevision.value

        assertNotNull(frame)
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), pending))
        assertEquals(revision, owner.stateRevision.value)
        assertEquals(frame, owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE))
    }

    @Test
    fun `rejected ordering never grants protection or System authority`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        owner.recordThermalStatus(uid, g1, fixtures.thermalStatus())
        val stale = owner.beginStatusRequest(uid, projection)
        val current = owner.beginStatusRequest(uid, projection)

        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), stale))
        assertFalse(owner.isAuthoritative(projection, uid, g1))
        assertNull(owner.currentAuthoritativeTemperatureProtection(uid))
        assertNull(owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE))
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(65.0), current))
        assertNotNull(owner.systemProjection.current(uid, DeviceLightSystemReadAuthority.AUTHORITATIVE))
    }

    @Test
    fun `tokens cannot cross devices projections or generations`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val protection = owner.beginStatusRequest(uid, projection)
        val light = owner.beginStatusRequest(uid)
        val anotherDevice = DeviceUid("another-protection-device")

        assertFalse(owner.recordTemperatureProtection(anotherDevice, g1, fixtures.snapshot(), protection))
        assertFalse(owner.recordTemperatureProtection(uid, g2, fixtures.snapshot(), protection))
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), light))
        assertFalse(owner.recordStatus(uid, g1, status, protection))
        assertFalse(owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, uid, g1))
        assertFalse(owner.isAuthoritative(DeviceLightRuntimeProjection.GRAPH, uid, g1))
        assertTrue(owner.recordStatus(uid, g1, status, light))
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), protection))
    }

    @Test
    fun `clear cannot reuse a pending token even when the same generation is reintroduced`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val protectionBeforeClear = owner.beginStatusRequest(uid, projection)
        val lightBeforeClear = owner.beginStatusRequest(uid)
        owner.clear(uid)
        owner.beginGeneration(uid, g1)
        val protectionAfterClear = owner.beginStatusRequest(uid, projection)
        val lightAfterClear = owner.beginStatusRequest(uid)

        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), protectionBeforeClear))
        assertFalse(owner.recordStatus(uid, g1, status, lightBeforeClear))
        assertTrue(owner.recordStatus(uid, g1, status, lightAfterClear))
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(65.0), protectionAfterClear))
        assertEquals(fixtures.snapshot(65.0), owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `bootstrap tokens participate in ordering before generation exists`() {
        val owner = DeviceLightRuntimeStateOwner()
        val first = owner.beginStatusRequest(uid, projection)
        val second = owner.beginStatusRequest(uid, projection)
        val firstLight = owner.beginStatusRequest(uid)
        val secondLight = owner.beginStatusRequest(uid)

        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(65.0), second))
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), first))
        assertTrue(owner.recordStatus(uid, g1, status, secondLight))
        assertFalse(owner.recordStatus(uid, g1, status, firstLight))
        assertEquals(fixtures.snapshot(65.0), owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `accepted request tokens cannot be replayed`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val pending = owner.beginStatusRequest(uid, projection)
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(65.0), pending))
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), pending))
        assertEquals(fixtures.snapshot(65.0), owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `rejected old event does not invalidate the new connection poll`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g2)
        val pending = owner.beginStatusRequest(uid, projection)
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot()))
        assertTrue(owner.recordTemperatureProtection(uid, g2, fixtures.snapshot(65.0), pending))
        assertEquals(fixtures.snapshot(65.0), owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `duplicate generation announcement preserves pending reads but reconnect invalidates them`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val pending = owner.beginStatusRequest(uid, projection)
        owner.beginGeneration(uid, g1)
        assertTrue(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), pending))
        val old = owner.beginStatusRequest(uid, projection)
        owner.beginGeneration(uid, g2)
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), old))
        assertNull(owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `generation specific disconnect invalidates a pending bootstrap read`() {
        val owner = DeviceLightRuntimeStateOwner()
        val pendingProtection = owner.beginStatusRequest(uid, projection)
        val pendingLight = owner.beginStatusRequest(uid)
        owner.invalidate(uid, g1)
        assertFalse(owner.recordTemperatureProtection(uid, g1, fixtures.snapshot(), pendingProtection))
        assertFalse(owner.recordStatus(uid, g1, status, pendingLight))
        assertNull(owner.currentAuthoritativeTemperatureProtection(uid))
    }

    @Test
    fun `rejected Light read does not revoke an already coherent dashboard`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val old = owner.beginStatusRequest(uid)
        val current = owner.beginStatusRequest(uid)
        assertTrue(owner.recordStatus(uid, g1, status, current))
        val graph = DeviceLightMutationParser.Graph.parseGraph(DeviceLightRuntimeFixtures.graph(), status.product)
        assertTrue(owner.dashboardProjection.record(uid, g1, graph))
        val frame = owner.dashboardProjection.current(uid, DeviceLightDashboardReadAuthority.AUTHORITATIVE)
        assertNotNull(frame)
        assertFalse(owner.recordStatus(uid, g1, status, old))
        assertEquals(frame, owner.dashboardProjection.current(uid, DeviceLightDashboardReadAuthority.AUTHORITATIVE))
    }
}
