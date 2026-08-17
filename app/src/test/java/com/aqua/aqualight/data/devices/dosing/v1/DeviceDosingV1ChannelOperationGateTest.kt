package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
                gate.withChannel(address) {
                    mutationEntered.complete(Unit)
                    releaseMutation.await()
                }
            }
            mutationEntered.await()

            val statusEvent = launch {
                gate.withChannel(address) {
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
}
