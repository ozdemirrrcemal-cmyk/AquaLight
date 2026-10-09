package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightStatusRequestAuthorityTest {
    private val uid = DeviceUid("status-request-order")
    private val g1 = DeviceRuntimeConnectionGeneration(1L)
    private val g2 = DeviceRuntimeConnectionGeneration(2L)
    private val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

    @Test
    fun `older same connection request cannot replace a newer authoritative status`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val first = requireNotNull(owner.beginStatusRequest(uid))
        val second = requireNotNull(owner.beginStatusRequest(uid))
        assertFalse(owner.recordStatus(uid, g1, status, first))
        assertTrue(owner.recordStatus(uid, g1, status, second))
        assertEquals(status, owner.currentStatus(uid, DeviceLightStatusReadAuthority.AUTHORITATIVE))
    }

    @Test
    fun `disconnect invalidates a pending Light response without erasing presentation`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        assertTrue(owner.recordStatus(uid, g1, status))
        val pending = requireNotNull(owner.beginStatusRequest(uid))
        owner.invalidate(uid, g1)

        assertFalse(owner.recordStatus(uid, g1, status, pending))
        assertFalse(owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, uid, g1))
        assertNotNull(owner.currentStatus(uid, DeviceLightStatusReadAuthority.PRESENTATION))
    }

    @Test
    fun `pushed firmware status supersedes an outstanding request`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val pending = requireNotNull(owner.beginStatusRequest(uid))

        assertTrue(owner.recordStatus(uid, g1, status))
        assertFalse(owner.recordStatus(uid, g1, status, pending))
    }

    @Test
    fun `new connection rejects old status tokens`() {
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(uid, g1)
        val old = requireNotNull(owner.beginStatusRequest(uid))
        owner.invalidate(uid, g1)
        owner.beginGeneration(uid, g2)

        assertFalse(owner.recordStatus(uid, g1, status, old))
        val next = requireNotNull(owner.beginStatusRequest(uid))
        assertTrue(owner.recordStatus(uid, g2, status, next))
    }
}
