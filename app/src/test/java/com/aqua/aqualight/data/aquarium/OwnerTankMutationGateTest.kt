package com.aqua.aqualight.data.aquarium

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTankMutationGateTest {
    @Test
    fun `same tank child waits until the whole parent transaction exits`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val gate = OwnerTankMutationGate()
            val events = mutableListOf<String>()
            val child = gate.withTanks(OWNER, listOf(1L)) {
                events += "parent"
                async(start = CoroutineStart.UNDISPATCHED) {
                    gate.withTanks(OWNER, listOf(1L)) { events += "child" }
                }.also { assertFalse(it.isCompleted) }
            }
            child.await()
            assertEquals(listOf("parent", "child"), events)
            assertEquals(0, gate.reservedTankCount)
        }
    }

    @Test
    fun `different tanks and owners can progress while a tank is held`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val gate = OwnerTankMutationGate()
            gate.withTanks(OWNER, listOf(1L)) {
                gate.withTanks(OWNER, listOf(2L)) { assertEquals(2, gate.reservedTankCount) }
                gate.withTanks("another-owner", listOf(1L)) { assertEquals(2, gate.reservedTankCount) }
            }
            assertEquals(0, gate.reservedTankCount)
        }
    }

    @Test
    fun `opposing multi tank requests acquire the same sorted order`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val gate = OwnerTankMutationGate()
            val reversed = gate.withTanks(OWNER, listOf(1L)) {
                val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                    gate.withTanks(OWNER, listOf(2L, 1L, 2L)) { "finished" }
                }
                // A request honoring input order would hold 2 while waiting for 1.
                gate.withTanks(OWNER, listOf(2L)) { assertFalse(waiting.isCompleted) }
                waiting
            }
            assertEquals("finished", reversed.await())
            assertEquals(0, gate.reservedTankCount)
        }
    }

    @Test
    fun `cancelling a partial acquisition releases its held tank and all reservations`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val gate = OwnerTankMutationGate()
            gate.withTanks(OWNER, listOf(2L)) {
                val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
                    gate.withTanks(OWNER, listOf(1L, 2L)) { error("Must not enter") }
                }
                assertEquals(2, gate.reservedTankCount)
                waiting.cancelAndJoin()
                assertEquals(1, gate.reservedTankCount)
                gate.withTanks(OWNER, listOf(1L)) { assertTrue(waiting.isCancelled) }
            }
            assertEquals(0, gate.reservedTankCount)
        }
    }

    @Test
    fun `cancelling a holder admits the next waiter without losing its reservation`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val gate = OwnerTankMutationGate()
            val entered = CompletableDeferred<Unit>()
            val holder = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withTanks(OWNER, listOf(1L)) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            entered.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withTanks(OWNER, listOf(1L)) { "next" }
            }
            holder.cancelAndJoin()
            assertEquals("next", waiter.await())
            assertEquals(0, gate.reservedTankCount)
        }
    }

    @Test
    fun `failure releases entries and invalid keys allocate nothing`() = runBlocking {
        val gate = OwnerTankMutationGate()
        val failure = IllegalStateException("injected")
        assertSame(failure, runCatching {
            gate.withTanks(OWNER, listOf(1L, 2L)) { throw failure }
        }.exceptionOrNull())
        assertTrue(runCatching { gate.withTanks(" ", listOf(1L)) {} }.isFailure)
        assertTrue(runCatching { gate.withTanks(OWNER, listOf(1L, -2L)) {} }.isFailure)
        assertTrue(runCatching { gate.withTanks(OWNER, emptyList()) {} }.isFailure)
        assertEquals(0, gate.reservedTankCount)
    }

    @Test
    fun `an already cancelled writer cannot enter even an uncontended gate`() = runBlocking {
        val gate = OwnerTankMutationGate()
        var entered = false
        val cancelled = Job().also { it.cancel() }
        launch(cancelled, start = CoroutineStart.UNDISPATCHED) {
            gate.withTanks(OWNER, listOf(1L)) { entered = true }
        }.join()
        assertFalse(entered)
        assertEquals(0, gate.reservedTankCount)
    }

    private companion object {
        const val OWNER = "gate-owner"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
