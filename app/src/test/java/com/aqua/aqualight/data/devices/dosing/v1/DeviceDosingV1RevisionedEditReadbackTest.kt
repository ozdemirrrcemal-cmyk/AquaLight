package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingV1RevisionedEditReadbackTest {

    @Test
    fun `revisioned editor save returns after firmware ack and reconciles in background`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueRefresh(revision = 7L)
            enqueueProgramMutation(revision = 8L)
            enqueueRefresh(revision = 8L)
        }
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success
        val provenPresentation = adapter.channelOperations.observeAll(DEVICE_UID.value).first()

        val result = adapter.channelOperations.applyProgramAtRevision(
            deviceUid = DEVICE_UID.value,
            slotId = SLOT_ID,
            program = requireNotNull(initial.snapshot.program),
            expectedRevision = 7L
        )

        // The user-visible save completes at the durable firmware ACK. The three-document
        // authoritative readback must not sit on the save/navigation critical path.
        assertEquals(DeviceDosingChannelCommittedResult(8L), result)
        assertEquals(4, gateway.actions.size)
        assertEquals(
            1,
            gateway.actions.count { action ->
                action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
            }
        )

        // Authority is withdrawn until coherent readback, while the same central owner keeps the
        // last proven presentation so the root screen does not flash empty or rebuild fake cards.
        assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertEquals(
            provenPresentation,
            adapter.channelOperations.observeAll(DEVICE_UID.value).first()
        )

        testScheduler.runCurrent()

        assertEquals(7, gateway.actions.size)
        assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertEquals(
            8L,
            adapter.channelOperations.observeAll(DEVICE_UID.value).first().single().revision
        )
    }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>
        )

        private val responses = ArrayDeque<Response>()
        val actions = mutableListOf<String>()

        fun enqueueRefresh(revision: Long) {
            val documents = documents(revision)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, documents.global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, documents.channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, documents.progress)
        }

        fun enqueueProgramMutation(revision: Long) {
            val parsed = DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            )
            enqueueSuccess(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                parsed.copy(channel = parsed.channel.copy(revision = revision))
            )
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

    private data class Documents(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-REVISIONED-EDIT")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun documents(revision: Long): Documents {
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
            ).let { status ->
                status.copy(
                    channels = status.channels.map { channel ->
                        if (channel.channelKey.value == "channel1") {
                            channel.copy(revision = revision)
                        } else {
                            channel
                        }
                    }
                )
            }
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus()
            ).let { status ->
                status.copy(channel = status.channel.copy(revision = revision))
            }
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            ).copy(revision = revision)
            return Documents(global, channel, progress)
        }

        fun <T> success(action: String, value: T): DeviceRuntimeCommandOutcome.Success<T> =
            DeviceRuntimeCommandOutcome.Success(
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
