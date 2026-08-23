package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1NonBlockingReadWriteTest {

    @Test
    fun `hung background reconciliation never blocks a newer program mutation`() = runTest {
        val gateway = OverlappingGateway()
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success
        val initialProgram = requireNotNull(initial.snapshot.program)

        val first = async {
            adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                initialProgram.copy(enabled = false)
            )
        }
        runCurrent()
        assertEquals(DeviceDosingChannelCommittedResult(8L), first.await())

        gateway.backgroundReadStarted.await()
        val second = async {
            adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                initialProgram.copy(enabled = true)
            )
        }
        runCurrent()

        // The old status.get is deliberately still suspended. The foreground write must already
        // have crossed the firmware commit boundary without cancelling or waiting for that read.
        assertTrue(second.isCompleted)
        assertEquals(DeviceDosingChannelCommittedResult(9L), second.await())
        assertFalse(gateway.releaseBackgroundRead.isCompleted)
        assertEquals(2, gateway.programApplyCount)

        gateway.releaseBackgroundRead.complete(Unit)
        runCurrent()

        // The pre-write refresh token is stale. Its stability retry occurs after the write and is
        // allowed to publish only the revision-9 coherent triplet.
        assertEquals(9L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertEquals(2, gateway.programApplyCount)
    }

    private class OverlappingGateway : DeviceRuntimeCommandGateway {
        val backgroundReadStarted = CompletableDeferred<Unit>()
        val releaseBackgroundRead = CompletableDeferred<Unit>()
        var programApplyCount: Int = 0
            private set
        private var statusGetCount: Int = 0
        private var progressGetCount: Int = 0

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            assertEquals(DEVICE_UID, deviceUid)
            val outcome: DeviceRuntimeCommandOutcome<*> = when (command.action) {
                DeviceDosingV1Contract.Action.STATUS_GET -> nextStatus()
                DeviceDosingV1Contract.Action.PROGRESS_GET -> nextProgress()
                DeviceDosingV1Contract.Action.PROGRAM_APPLY -> nextProgramApply()
                else -> error("Unexpected action ${command.action}")
            }
            return outcome as DeviceRuntimeCommandOutcome<T>
        }

        private suspend fun nextStatus(): DeviceRuntimeCommandOutcome<*> {
            statusGetCount += 1
            return when (statusGetCount) {
                1 -> success(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    fixtureState(7L, true).global
                )
                2 -> success(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    fixtureState(7L, true).channel
                )
                3 -> {
                    backgroundReadStarted.complete(Unit)
                    releaseBackgroundRead.await()
                    success(
                        DeviceDosingV1Contract.Action.STATUS_GET,
                        fixtureState(8L, false).global
                    )
                }
                4 -> success(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    fixtureState(8L, false).channel
                )
                5 -> success(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    fixtureState(9L, true).global
                )
                6 -> success(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    fixtureState(9L, true).channel
                )
                else -> error("Unexpected status.get #$statusGetCount")
            }
        }

        private fun nextProgress(): DeviceRuntimeCommandOutcome<*> {
            progressGetCount += 1
            val state = when (progressGetCount) {
                1 -> fixtureState(7L, true)
                2 -> fixtureState(8L, false)
                3 -> fixtureState(9L, true)
                else -> error("Unexpected progress.get #$progressGetCount")
            }
            return success(DeviceDosingV1Contract.Action.PROGRESS_GET, state.progress)
        }

        private fun nextProgramApply(): DeviceRuntimeCommandOutcome<*> {
            programApplyCount += 1
            val revision = if (programApplyCount == 1) 8L else 9L
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
