package com.aqua.aqualight.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerSessionStateMachineTest {

    @Test
    fun `committed transition becomes active owner`() {
        val machine = OwnerSessionStateMachine()
        val transition = machine.begin("owner-a")

        assertTrue(machine.commit(transition))
        assertEquals("owner-a", machine.snapshot().activeOwnerUid)
        assertNull(machine.snapshot().pendingOwnerUid)
    }

    @Test
    fun `newer transition invalidates delayed older work`() {
        val machine = OwnerSessionStateMachine()
        val ownerA = machine.begin("owner-a")
        val ownerB = machine.begin("owner-b")

        assertFalse(machine.isCurrent(ownerA))
        assertFalse(machine.commit(ownerA))
        assertTrue(machine.commit(ownerB))
        assertEquals("owner-b", machine.snapshot().activeOwnerUid)
    }

    @Test
    fun `A to B to A allows only final generation to commit`() {
        val machine = OwnerSessionStateMachine()
        val firstA = machine.begin("owner-a")
        assertTrue(machine.commit(firstA))

        val ownerB = machine.begin("owner-b")
        val secondA = machine.begin("owner-a")

        assertFalse(machine.commit(ownerB))
        assertTrue(machine.commit(secondA))
        assertEquals("owner-a", machine.snapshot().activeOwnerUid)
        assertTrue(secondA.generation > ownerB.generation)
    }

    @Test
    fun `close with stale expected owner cannot clear current session`() {
        val machine = OwnerSessionStateMachine()
        val ownerB = machine.begin("owner-b")
        assertTrue(machine.commit(ownerB))

        val staleClose = machine.close(expectedOwnerUid = "owner-a")

        assertNull(staleClose)
        assertEquals("owner-b", machine.snapshot().activeOwnerUid)
    }

    @Test
    fun `matching close clears current owner and advances generation`() {
        val machine = OwnerSessionStateMachine()
        val ownerA = machine.begin("owner-a")
        assertTrue(machine.commit(ownerA))

        val close = machine.close(expectedOwnerUid = "owner-a")

        assertEquals("owner-a", close?.previousOwnerUid)
        assertNull(machine.snapshot().activeOwnerUid)
        assertTrue(requireNotNull(close).generation > ownerA.generation)
    }

    @Test
    fun `aborted current transition cannot later commit`() {
        val machine = OwnerSessionStateMachine()
        val transition = machine.begin("owner-a")

        assertTrue(machine.abort(transition))
        assertFalse(machine.commit(transition))
        assertNull(machine.snapshot().activeOwnerUid)
    }
}
