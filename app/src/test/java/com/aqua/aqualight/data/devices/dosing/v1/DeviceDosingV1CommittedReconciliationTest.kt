package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CommittedReconciliationTest {

    @Test
    fun `persisted ack publishes coherent firmware readback before returning success`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 7L)
                enqueueProgramMutation(revision = 8L, programEnabled = false)
                enqueueRefresh(revision = 8L, programEnabled = false)
            }
            val adapter = DeviceDosingV1StateAdapter(
                repository = DeviceDosingV1Repository(gateway),
                reconciliationScope = backgroundScope
            )
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success

            val result = adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                requireNotNull(initial.snapshot.program).copy(enabled = false)
            )

            assertEquals(7, gateway.actions.size)
            assertTrue(result is DeviceDosingChannelOperationResult.Success)
            assertEquals(
                8L,
                (result as DeviceDosingChannelOperationResult.Success).snapshot.revision
            )
            assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
            val observed = adapter.channelOperations.observeAll(DEVICE_UID.value).first().single()
            assertEquals(8L, observed.revision)
            assertEquals(false, observed.program?.enabled)
            assertEquals(
                1,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    @Test
    fun `back to back saves share committed readback without a user visible conflict`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 7L)
                enqueueProgramMutation(revision = 8L, programEnabled = false)
                enqueueRefresh(revision = 8L, programEnabled = false)
                enqueueProgramMutation(revision = 9L, programEnabled = true)
                enqueueRefresh(revision = 9L, programEnabled = true)
            }
            val adapter = DeviceDosingV1StateAdapter(
                repository = DeviceDosingV1Repository(gateway),
                reconciliationScope = backgroundScope
            )
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success
            val program = requireNotNull(initial.snapshot.program)

            val first = adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                program.copy(enabled = false)
            )
            val second = adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                program.copy(enabled = true)
            )

            assertEquals(
                8L,
                (first as DeviceDosingChannelOperationResult.Success).snapshot.revision
            )
            assertEquals(
                9L,
                (second as DeviceDosingChannelOperationResult.Success).snapshot.revision
            )
            assertEquals(
                2,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )

            assertEquals(9L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
            assertEquals(
                2,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    @Test
    fun `slow post ack readback is bounded and publishes ack projection before navigation`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 7L)
                enqueueProgramMutation(revision = 8L, programEnabled = false)
                enqueueDelayedReadback(
                    revision = 8L,
                    programEnabled = false,
                    delayMillis = 10_000L
                )
                enqueueRefresh(revision = 8L, programEnabled = false)
            }
            val adapter = DeviceDosingV1StateAdapter(
                repository = DeviceDosingV1Repository(gateway),
                reconciliationScope = backgroundScope
            )
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success

            val result = adapter.channelOperations.applyProgram(
                DEVICE_UID.value,
                SLOT_ID,
                requireNotNull(initial.snapshot.program).copy(enabled = false)
            )

            assertEquals(DeviceDosingChannelCommittedResult(8L), result)
            assertEquals(5, gateway.actions.size)
            assertEquals(1_500L, testScheduler.currentTime)
            assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
            val projected = adapter.channelOperations.observeAll(DEVICE_UID.value).first().single()
            assertEquals(8L, projected.revision)
            assertFalse(requireNotNull(projected.program).enabled)

            testScheduler.runCurrent()

            assertEquals(8, gateway.actions.size)
            assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
            assertEquals(
                1,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>,
            val delayMillis: Long = 0L
        )

        private val responses = ArrayDeque<Response>()
        val actions = mutableListOf<String>()

        fun enqueueRefresh(revision: Long, programEnabled: Boolean = true) {
            val (global, channel, progress) = fixtureState(revision, programEnabled)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        fun enqueueProgramMutation(revision: Long, programEnabled: Boolean = true) {
            val parsed = DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            )
            enqueueSuccess(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                parsed.copy(
                    channel = parsed.channel.copy(
                        revision = revision,
                        program = parsed.channel.program?.copy(enabled = programEnabled)
                    )
                )
            )
        }

        fun enqueueDelayedReadback(
            revision: Long,
            programEnabled: Boolean,
            delayMillis: Long
        ) {
            val global = fixtureState(revision, programEnabled).global
            responses.addLast(
                Response(
                    action = DeviceDosingV1Contract.Action.STATUS_GET,
                    outcome = success(DeviceDosingV1Contract.Action.STATUS_GET, global),
                    delayMillis = delayMillis
                )
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
            delay(response.delayMillis)
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-COMMIT-TEST")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun fixtureState(revision: Long, programEnabled: Boolean = true): FixtureState {
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

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )
}
