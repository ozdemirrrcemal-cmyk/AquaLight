package com.aqua.aqualight.composition

import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OwnerDependencyGraphSessionTest {

    @Test
    fun `committed matching owner exposes generation`() {
        val generation = requireActiveOwnerGeneration(
            ownerUid = "owner-a",
            snapshot = OwnerSessionStateMachine.Snapshot(
                generation = 7L,
                activeOwnerUid = "owner-a",
                pendingOwnerUid = null
            )
        )

        assertEquals(7L, generation)
    }

    @Test
    fun `pending transition fails before dependency construction`() {
        assertThrows(IllegalStateException::class.java) {
            requireActiveOwnerGeneration(
                ownerUid = "owner-a",
                snapshot = OwnerSessionStateMachine.Snapshot(
                    generation = 8L,
                    activeOwnerUid = null,
                    pendingOwnerUid = "owner-a"
                )
            )
        }
    }

    @Test
    fun `different active owner fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            requireActiveOwnerGeneration(
                ownerUid = "owner-b",
                snapshot = OwnerSessionStateMachine.Snapshot(
                    generation = 9L,
                    activeOwnerUid = "owner-a",
                    pendingOwnerUid = null
                )
            )
        }
    }

    @Test
    fun `signed out session fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            requireActiveOwnerGeneration(
                ownerUid = "owner-a",
                snapshot = OwnerSessionStateMachine.Snapshot(
                    generation = 10L,
                    activeOwnerUid = null,
                    pendingOwnerUid = null
                )
            )
        }
    }
}
