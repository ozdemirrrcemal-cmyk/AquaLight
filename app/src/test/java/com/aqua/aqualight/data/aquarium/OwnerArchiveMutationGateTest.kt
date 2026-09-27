package com.aqua.aqualight.data.aquarium

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OwnerArchiveMutationGateTest {
    @Test
    fun `owner coordinator child waits while another owner can continue`() = runBlocking {
        withTimeout(5_000) {
            val gate = OwnerArchiveMutationGate()
            val order = mutableListOf<String>()
            val child = gate.withOwner("owner") {
                val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                    gate.withOwner("owner") { order += "delete" }
                }
                assertFalse(waiting.isCompleted)
                gate.withOwner("other") { order += "other-owner" }
                order += "restore-finished"
                waiting
            }
            child.await()
            assertEquals(listOf("other-owner", "restore-finished", "delete"), order)
            assertEquals(0, gate.reservedOwnerCount)
        }
    }

    @Test
    fun `cancelled waiting coordinator releases its reservation without entering`() = runBlocking {
        val gate = OwnerArchiveMutationGate()
        gate.withOwner("owner") {
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withOwner("owner") { error("Cancelled waiter must not enter") }
            }
            waiting.cancelAndJoin()
            assertEquals(1, gate.reservedOwnerCount)
        }
        assertEquals(0, gate.reservedOwnerCount)
    }
}
