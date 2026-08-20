package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CommittedMutationTest {

    @Test
    fun `persisted firmware ack survives post write transport loss without replaying mutation`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 137L, generation = GENERATION_ONE)
                enqueueProgramMutation(revision = 138L, generation = GENERATION_ONE)
                enqueue(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    DeviceRuntimeCommandOutcome.Cancelled(
                        deviceUid = DEVICE_UID,
                        module = DeviceDosingV1Contract.MODULE,
                        action = DeviceDosingV1Contract.Action.STATUS_GET,
                        messageId = "post-write-global",
                        generation = GENERATION_ONE,
                        reason = "runtime transport unavailable"
                    )
                )
            }
            val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success
            val program = requireNotNull(initial.snapshot.program)

            val result = adapter.channelOperations.applyProgramAtRevision(
                deviceUid = DEVICE_UID.value,
                slotId = SLOT_ID,
                program = program,
                expectedRevision = 137L
            )

            assertEquals(DeviceDosingChannelCommittedResult(138L), result)
            assertEquals(
                1,
                gateway.requests.count { request ->
                    request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
            // recordMutation intentionally invalidates authoritative reads until a coherent
            // global/channel/progress readback succeeds.
            assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))

            gateway.enqueueRefresh(revision = 138L, generation = GENERATION_TWO)
            val reconciled = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

            assertTrue(reconciled is DeviceDosingChannelOperationResult.Success)
            assertEquals(
                138L,
                (reconciled as DeviceDosingChannelOperationResult.Success).snapshot.revision
            )
            assertEquals(
                1,
                gateway.requests.count { request ->
                    request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        data class Request(val action: String)

        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>
        )

        private val responses = ArrayDeque<Response>()
        val requests = mutableListOf<Request>()

        fun enqueue(action: String, outcome: DeviceRuntimeCommandOutcome<*>) {
            responses.addLast(Response(action, outcome))
        }

        fun enqueueRefresh(revision: Long, generation: DeviceRuntimeConnectionGeneration) {
            val state = fixtureState(revision)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, state.global, generation)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, state.channel, generation)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, state.progress, generation)
        }

        fun enqueueProgramMutation(revision: Long, generation: DeviceRuntimeConnectionGeneration) {
            val parsed = DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            )
            enqueueSuccess(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                parsed.copy(channel = parsed.channel.copy(revision = revision)),
                generation
            )
        }

        private fun <T> enqueueSuccess(
            action: String,
            value: T,
            generation: DeviceRuntimeConnectionGeneration
        ) {
            enqueue(
                action,
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = DEVICE_UID,
                    module = DeviceDosingV1Contract.MODULE,
                    action = action,
                    messageId = "response-$action-${generation.value}",
                    generation = generation,
                    statusCode = 200,
                    value = value
                )
            )
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            val response = responses.removeFirst()
            assertEquals(response.action, command.action)
            requests += Request(command.action)
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-COMMIT-TEST")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)

        fun fixtureState(revision: Long): FixtureState {
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
            return FixtureState(global, channel, progress)
        }
    }
}
