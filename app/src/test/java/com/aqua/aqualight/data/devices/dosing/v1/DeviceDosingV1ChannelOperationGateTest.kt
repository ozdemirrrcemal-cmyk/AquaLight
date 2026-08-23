package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ChannelOperationGateTest {
    @Test
    fun `status event reconciliation waits for the accepted mutation on the same channel`() =
        runTest {
            val gate = DeviceDosingV1ChannelOperationGate()
            val address = DeviceDosingV1Address(
                deviceUid = DeviceUid("AQL-DOSING-GATE-TEST"),
                channelKey = DeviceDosingV1ChannelKey.from("channel1")
            )
            val mutationEntered = CompletableDeferred<Unit>()
            val releaseMutation = CompletableDeferred<Unit>()
            val eventEntered = CompletableDeferred<Unit>()

            val mutation = launch {
                gate.withMutation(address, preemptRefresh = {}) {
                    mutationEntered.complete(Unit)
                    releaseMutation.await()
                }
            }
            mutationEntered.await()

            val statusEvent = launch {
                gate.withRefresh(address, preempted = Unit) {
                    eventEntered.complete(Unit)
                }
            }
            runCurrent()

            assertFalse(eventEntered.isCompleted)
            releaseMutation.complete(Unit)
            mutation.join()
            statusEvent.join()
            assertTrue(eventEntered.isCompleted)
        }

    @Test
    fun `a later mutation cannot overtake one that is preempting a refresh`() = runTest {
        val gate = DeviceDosingV1ChannelOperationGate()
        val address = address()
        val firstPreemptEntered = CompletableDeferred<Unit>()
        val releaseFirstPreempt = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val first = launch {
            gate.withMutation(
                address,
                preemptRefresh = {
                    firstPreemptEntered.complete(Unit)
                    releaseFirstPreempt.await()
                }
            ) { order += "first" }
        }
        firstPreemptEntered.await()
        val second = launch {
            gate.withMutation(address, preemptRefresh = {}) { order += "second" }
        }
        runCurrent()

        assertTrue(order.isEmpty())
        releaseFirstPreempt.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun `a queued refresh yields when another mutation is pending`() = runTest {
        val gate = DeviceDosingV1ChannelOperationGate()
        val address = address()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            gate.withMutation(address, preemptRefresh = {}) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        val refresh = async {
            gate.withRefresh(address, preempted = false) { true }
        }
        runCurrent()
        val second = launch {
            gate.withMutation(address, preemptRefresh = {}) { Unit }
        }
        runCurrent()

        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertFalse(refresh.await())
    }

    @Test
    fun `cancelled mutation admission releases refresh priority`() = runTest {
        val gate = DeviceDosingV1ChannelOperationGate()
        val address = address()
        val preemptEntered = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val mutation = launch {
            gate.withMutation(
                address,
                preemptRefresh = {
                    preemptEntered.complete(Unit)
                    neverReleased.await()
                }
            ) { Unit }
        }
        preemptEntered.await()

        mutation.cancelAndJoin()

        assertTrue(gate.withRefresh(address, preempted = false) { true })
    }

    private fun address() = DeviceDosingV1Address(
        deviceUid = DeviceUid("AQL-DOSING-GATE-TEST"),
        channelKey = DeviceDosingV1ChannelKey.from("channel1")
    )
}
