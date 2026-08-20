package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceDosingV1TimeStatusRecoveryTest {
    @Test
    fun `time status change refreshes all dosing state through the central coordinator`() = runTest {
        val gateway = ScriptedGateway().apply { enqueueSingleChannelRefresh() }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))

        val result = adapter.consume(
            DeviceRuntimeTypedEvent(
                deviceUid = DEVICE_UID,
                generation = GENERATION,
                messageId = "time-status-1",
                type = DeviceRuntimeTypedEvent.Type.TIME_STATUS_CHANGED,
                payload = DeviceRuntimeEventPayload.Snapshot(
                    DeviceDosingV1TestFixtures.directEvent()
                )
            )
        )

        assertEquals(DeviceDosingV1EventResult.RefreshedAll, result)
        assertNotNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertEquals(
            listOf(
                DeviceDosingV1Contract.Action.STATUS_GET,
                DeviceDosingV1Contract.Action.STATUS_GET,
                DeviceDosingV1Contract.Action.PROGRESS_GET
            ),
            gateway.actions
        )
    }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>
        )

        private val responses = ArrayDeque<Response>()
        val actions = mutableListOf<String>()

        fun enqueueSingleChannelRefresh() {
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
            ).let { status ->
                status.copy(
                    channels = status.channels.filter { channel ->
                        channel.channelKey.value == "channel1"
                    }
                )
            }
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus()
            )
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        private fun <T> enqueueSuccess(action: String, value: T) {
            responses.addLast(Response(action, success(action, value)))
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            val response = responses.removeFirst()
            assertEquals(response.action, command.action)
            actions += command.action
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-TIME-RECOVERY")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun <T> success(
            action: String,
            value: T
        ): DeviceRuntimeCommandOutcome.Success<T> = DeviceRuntimeCommandOutcome.Success(
            deviceUid = DEVICE_UID,
            module = DeviceDosingV1Contract.MODULE,
            action = action,
            messageId = "response-$action",
            generation = GENERATION,
            statusCode = 200,
            value = value
        )
    }
}
