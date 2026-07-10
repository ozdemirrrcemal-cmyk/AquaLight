package com.aqua.aqualight.data.devices.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerSessionStateMachineTest {

    @Test
    fun firstOwner_requiresRestart() {
        val stateMachine = OwnerSessionStateMachine()

        val decision = stateMachine.start("owner-a")

        assertNotNull(decision)
        assertTrue(requireNotNull(decision).requiresRestart)
        assertTrue(
            stateMachine.isCurrent(
                ownerUid = decision.ownerUid,
                expectedGeneration = decision.generation
            )
        )
    }

    @Test
    fun sameOwner_doesNotRestartAgain() {
        val stateMachine = OwnerSessionStateMachine()
        stateMachine.start("owner-a")

        val secondDecision = requireNotNull(stateMachine.start("owner-a"))

        assertFalse(secondDecision.requiresRestart)
        assertTrue(
            stateMachine.isCurrent(
                ownerUid = secondDecision.ownerUid,
                expectedGeneration = secondDecision.generation
            )
        )
    }

    @Test
    fun switchingOwner_invalidatesOlderTransition() {
        val stateMachine = OwnerSessionStateMachine()
        val ownerADecision = requireNotNull(stateMachine.start("owner-a"))
        val ownerBDecision = requireNotNull(stateMachine.start("owner-b"))

        assertFalse(
            stateMachine.isCurrent(
                ownerUid = ownerADecision.ownerUid,
                expectedGeneration = ownerADecision.generation
            )
        )
        assertTrue(
            stateMachine.isCurrent(
                ownerUid = ownerBDecision.ownerUid,
                expectedGeneration = ownerBDecision.generation
            )
        )
    }

    @Test
    fun stop_invalidatesPendingTransition() {
        val stateMachine = OwnerSessionStateMachine()
        val decision = requireNotNull(stateMachine.start("owner-a"))

        stateMachine.stop()

        assertFalse(
            stateMachine.isCurrent(
                ownerUid = decision.ownerUid,
                expectedGeneration = decision.generation
            )
        )
    }

    @Test
    fun blankOwner_isRejected() {
        val stateMachine = OwnerSessionStateMachine()

        assertNull(stateMachine.start("   "))
    }
}
