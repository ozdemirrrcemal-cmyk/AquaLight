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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ChannelMutationProcessorTest {

    @Test
    fun `caller cancellation never cancels an accepted owner scoped mutation`() = runTest {
        val processor = DeviceDosingV1ChannelMutationProcessor(backgroundScope)
        val address = address("channel1")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val obsoleteWaiter = launch {
            processor.submit(address) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first"
            }
        }
        firstEntered.await()
        obsoleteWaiter.cancelAndJoin()

        val latest = async {
            processor.submit(address) {
                order += "second"
                "settled"
            }
        }
        runCurrent()
        assertFalse(latest.isCompleted)

        releaseFirst.complete(Unit)
        assertEquals("settled", latest.await())
        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun `different channels have independent mutation workers`() = runTest {
        val processor = DeviceDosingV1ChannelMutationProcessor(backgroundScope)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val blocked = async {
            processor.submit(address("channel1")) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstEntered.await()

        val independent = async {
            processor.submit(address("channel2")) { "second" }
        }
        runCurrent()

        assertTrue(independent.isCompleted)
        assertEquals("second", independent.await())
        assertFalse(blocked.isCompleted)
        releaseFirst.complete(Unit)
        assertEquals("first", blocked.await())
    }

    @Test
    fun `background read admission skips active mutation without waiting`() {
        val admission = DeviceDosingV1ChannelOperationAdmission()
        val address = address("channel1")
        admission.beginMutation(address)

        assertNull(admission.admitBackgroundRead(address) { "must-not-run" })

        admission.endMutation(address)
        assertEquals("read", admission.admitBackgroundRead(address) { "read" })
    }

    private fun address(channelKey: String) = DeviceDosingV1Address(
        deviceUid = DeviceUid("AQL-DOSING-MUTATION-PROCESSOR-TEST"),
        channelKey = DeviceDosingV1ChannelKey.from(channelKey)
    )
}
