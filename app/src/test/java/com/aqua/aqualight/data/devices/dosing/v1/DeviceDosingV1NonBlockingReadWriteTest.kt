package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingV1NonBlockingReadWriteTest {

    @Test
    fun `persisted ack continuation sends consecutive writes without automatic readback`() = runTest {
        val gateway = ConsecutiveWriteGateway()
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success
        val initialProgram = requireNotNull(initial.snapshot.program)
        gateway.actions.clear()

        val first = adapter.channelOperations.applyProgram(
            DEVICE_UID.value,
            SLOT_ID,
            initialProgram.copy(enabled = false)
        )
        val second = adapter.channelOperations.applyProgram(
            DEVICE_UID.value,
            SLOT_ID,
            initialProgram.copy(enabled = true)
        )

        assertEquals(DeviceDosingChannelCommittedResult(8L), first)
        assertEquals(DeviceDosingChannelCommittedResult(9L), second)
        assertEquals(
            listOf(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                DeviceDosingV1Contract.Action.PROGRAM_APPLY
            ),
            gateway.actions
        )
        assertEquals(listOf(7L, 8L), gateway.expectedRevisions)
    }

    private class ConsecutiveWriteGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val expectedRevisions = mutableListOf<Long>()
        private var statusGetCount = 0
        private var programApplyCount = 0

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            assertEquals(DEVICE_UID, deviceUid)
            actions += command.action
            val outcome: DeviceRuntimeCommandOutcome<*> = when (command.action) {
                DeviceDosingV1Contract.Action.STATUS_GET -> nextInitialStatus()
                DeviceDosingV1Contract.Action.PROGRESS_GET -> success(
                    DeviceDosingV1Contract.Action.PROGRESS_GET,
                    fixtureState(7L, true).progress
                )
                DeviceDosingV1Contract.Action.PROGRAM_APPLY -> nextProgramApply(command)
                else -> error("Unexpected action ${command.action}")
            }
            return outcome as DeviceRuntimeCommandOutcome<T>
        }

        private fun nextInitialStatus(): DeviceRuntimeCommandOutcome<*> {
            statusGetCount += 1
            val state = fixtureState(7L, true)
            return when (statusGetCount) {
                1 -> success(DeviceDosingV1Contract.Action.STATUS_GET, state.global)
                2 -> success(DeviceDosingV1Contract.Action.STATUS_GET, state.channel)
                else -> error("Persisted ACK path sent unexpected status.get #$statusGetCount")
            }
        }

        private fun nextProgramApply(command: DeviceRuntimeCommand<*>): DeviceRuntimeCommandOutcome<*> {
            val request = command.encodeData()
            expectedRevisions += request.getLong("expectedRevision")
            programApplyCount += 1
            val revision = 7L + programApplyCount
            val enabled = programApplyCount != 1
            val parsed = DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            )
            return success(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                parsed.copy(
                    channel = parsed.channel.copy(
                        revision = revision,
                        program = parsed.channel.program?.copy(enabled = enabled)
                    )
                )
            )
        }
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-NON-BLOCKING-TEST")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun fixtureState(revision: Long, programEnabled: Boolean): FixtureState {
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
            ).let { status ->
                status.copy(
                    channels = status.channels.map { channel ->
                        if (channel.channelKey.value == "channel1") {
                            channel.copy(
                                revision = revision,
                                programEnabled = programEnabled
                            )
                        } else {
                            channel
                        }
                    }
                )
            }
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus()
            ).let { status ->
                status.copy(
                    channel = status.channel.copy(
                        revision = revision,
                        program = status.channel.program?.copy(enabled = programEnabled)
                    )
                )
            }
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            ).copy(revision = revision, programEnabled = programEnabled)
            return FixtureState(global, channel, progress)
        }

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
