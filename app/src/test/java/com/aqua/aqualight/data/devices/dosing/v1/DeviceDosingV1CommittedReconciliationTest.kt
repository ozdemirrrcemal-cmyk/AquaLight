package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingV1CommittedReconciliationTest {

    @Test
    fun `persisted ack returns immediately and publishes coherent background readback`() =
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

            assertEquals(DeviceDosingChannelCommittedResult(8L), result)
            assertEquals(4, gateway.actions.size)
            val projected = adapter.channelOperations.observeAll(DEVICE_UID.value).first().single()
            assertEquals(8L, projected.revision)
            assertEquals(false, projected.program?.enabled)

            testScheduler.runCurrent()

            assertEquals(7, gateway.actions.size)
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

            assertEquals(DeviceDosingChannelCommittedResult(8L), first)
            assertEquals(DeviceDosingChannelCommittedResult(9L), second)
            assertEquals(
                2,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )

            assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
            val projected = adapter.channelOperations.observeAll(DEVICE_UID.value).first().single()
            assertEquals(9L, projected.revision)

            testScheduler.runCurrent()

            assertEquals(9L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
            assertEquals(
                2,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    @Test
    fun `slow post ack readback never delays committed UI result`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 7L)
                enqueueProgramMutation(revision = 8L, programEnabled = false)
                enqueueDelayedReadback(
                    revision = 8L,
                    programEnabled = false,
                    delayMillis = 10_000L
                )
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
            assertEquals(4, gateway.actions.size)
            assertEquals(0L, testScheduler.currentTime)
            assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
            val projected = adapter.channelOperations.observeAll(DEVICE_UID.value).first().single()
            assertEquals(8L, projected.revision)
            assertFalse(requireNotNull(projected.program).enabled)

            testScheduler.runCurrent()

            assertEquals(5, gateway.actions.size)
            assertEquals(0L, testScheduler.currentTime)
            assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))

            testScheduler.advanceTimeBy(10_000L)
            testScheduler.runCurrent()

            assertEquals(7, gateway.actions.size)
            assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
            assertEquals(
                1,
                gateway.actions.count { action ->
                    action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
                }
            )
        }

    @Test
    fun `ten second readback cannot block rapid missed dose off then on intent`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = true)
            enqueueProgramMutation(revision = 8L, missedDoseRecoveryEnabled = false)
            enqueueDelayedGlobalReadback(
                revision = 8L,
                programEnabled = true,
                missedDoseRecoveryEnabled = false,
                delayMillis = 10_000L
            )
            enqueueProgramMutation(revision = 9L, missedDoseRecoveryEnabled = true)
            enqueueRefresh(revision = 9L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val disabled = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            false
        )
        testScheduler.runCurrent()
        val enabled = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertEquals(DeviceDosingChannelCommittedResult(8L), disabled)
        assertEquals(DeviceDosingChannelCommittedResult(9L), enabled)
        assertEquals(0L, testScheduler.currentTime)
        assertProgramRequests(
            gateway = gateway,
            expectedRevisions = listOf(7L, 8L),
            expectedRecoveryValues = listOf(false, true)
        )

        testScheduler.runCurrent()
        assertEquals(9L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertTrue(
            adapter.currentChannel(DEVICE_UID.value, SLOT_ID)
                ?.program
                ?.missedDoseRecoveryEnabled == true
        )
    }

    @Test
    fun `latest missed dose intent wins while the earlier firmware write is in flight`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = true)
            enqueueProgramMutation(
                revision = 8L,
                missedDoseRecoveryEnabled = false,
                delayMillis = 100L
            )
            enqueueProgramMutation(revision = 9L, missedDoseRecoveryEnabled = true)
            enqueueRefresh(revision = 9L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val disable = async {
            adapter.channelOperations.setMissedDoseRecoveryEnabled(
                DEVICE_UID.value,
                SLOT_ID,
                false
            )
        }
        testScheduler.runCurrent()
        val enable = async {
            adapter.channelOperations.setMissedDoseRecoveryEnabled(
                DEVICE_UID.value,
                SLOT_ID,
                true
            )
        }
        testScheduler.runCurrent()

        assertEquals(1, gateway.programRequests().size)

        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()

        assertEquals(DeviceDosingChannelCommittedResult(9L), disable.await())
        assertEquals(DeviceDosingChannelCommittedResult(9L), enable.await())
        assertProgramRequests(
            gateway = gateway,
            expectedRevisions = listOf(7L, 8L),
            expectedRecoveryValues = listOf(false, true)
        )
    }

    @Test
    fun `concurrent channel refresh callers share one firmware readback flight`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueDelayedReadback(
                revision = 7L,
                programEnabled = true,
                delayMillis = 10_000L
            )
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))

        val first = async { adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID) }
        testScheduler.runCurrent()
        val second = async { adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID) }
        testScheduler.runCurrent()

        assertEquals(listOf(DeviceDosingV1Contract.Action.STATUS_GET), gateway.actions)

        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()

        assertTrue(first.await() is DeviceDosingChannelOperationResult.Success)
        assertTrue(second.await() is DeviceDosingChannelOperationResult.Success)
        assertEquals(
            listOf(
                DeviceDosingV1Contract.Action.STATUS_GET,
                DeviceDosingV1Contract.Action.STATUS_GET,
                DeviceDosingV1Contract.Action.PROGRESS_GET
            ),
            gateway.actions
        )
    }

    @Test
    fun `switch then save share ack revision and preserve both program intents`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueProgramMutation(revision = 8L, missedDoseRecoveryEnabled = true)
            enqueueDelayedGlobalReadback(
                revision = 8L,
                programEnabled = true,
                missedDoseRecoveryEnabled = true,
                delayMillis = 10_000L
            )
            enqueueProgramMutation(
                revision = 9L,
                programEnabled = false,
                missedDoseRecoveryEnabled = true
            )
            enqueueRefresh(
                revision = 9L,
                programEnabled = false,
                missedDoseRecoveryEnabled = true
            )
        }
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success

        val switchResult = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )
        testScheduler.runCurrent()
        val saveResult = adapter.channelOperations.applyProgramAtRevision(
            deviceUid = DEVICE_UID.value,
            slotId = SLOT_ID,
            program = requireNotNull(initial.snapshot.program).copy(enabled = false),
            expectedRevision = 7L
        )

        assertEquals(DeviceDosingChannelCommittedResult(8L), switchResult)
        assertEquals(DeviceDosingChannelCommittedResult(9L), saveResult)
        assertEquals(0L, testScheduler.currentTime)
        assertProgramRequests(
            gateway = gateway,
            expectedRevisions = listOf(7L, 8L),
            expectedRecoveryValues = listOf(true, true)
        )
        val lastProgram = gateway.programRequests().last().getJSONObject("program")
        assertFalse(lastProgram.getBoolean("enabled"))

        testScheduler.runCurrent()
        val current = requireNotNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertFalse(requireNotNull(current.program).enabled)
        assertTrue(current.program?.missedDoseRecoveryEnabled == true)
    }

    @Test
    fun `save then switch share ack revision and preserve both program intents`() = runTest {
        val gateway = ScriptedGateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueProgramMutation(
                revision = 8L,
                programEnabled = false,
                missedDoseRecoveryEnabled = false
            )
            enqueueDelayedGlobalReadback(
                revision = 8L,
                programEnabled = false,
                missedDoseRecoveryEnabled = false,
                delayMillis = 10_000L
            )
            enqueueProgramMutation(
                revision = 9L,
                programEnabled = false,
                missedDoseRecoveryEnabled = true
            )
            enqueueRefresh(
                revision = 9L,
                programEnabled = false,
                missedDoseRecoveryEnabled = true
            )
        }
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(gateway),
            reconciliationScope = backgroundScope
        )
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success

        val saveResult = adapter.channelOperations.applyProgramAtRevision(
            deviceUid = DEVICE_UID.value,
            slotId = SLOT_ID,
            program = requireNotNull(initial.snapshot.program).copy(enabled = false),
            expectedRevision = 7L
        )
        testScheduler.runCurrent()
        val switchResult = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertEquals(DeviceDosingChannelCommittedResult(8L), saveResult)
        assertEquals(DeviceDosingChannelCommittedResult(9L), switchResult)
        assertEquals(0L, testScheduler.currentTime)
        assertProgramRequests(
            gateway = gateway,
            expectedRevisions = listOf(7L, 8L),
            expectedRecoveryValues = listOf(false, true)
        )
        val lastProgram = gateway.programRequests().last().getJSONObject("program")
        assertFalse(lastProgram.getBoolean("enabled"))

        testScheduler.runCurrent()
        val current = requireNotNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertFalse(requireNotNull(current.program).enabled)
        assertTrue(current.program?.missedDoseRecoveryEnabled == true)
    }

    @Test
    fun `calibration confirmation exposes current snapshot before first switch mutation`() =
        runTest {
            val gateway = ScriptedGateway().apply {
                enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
                enqueueCalibrationConfirm()
                enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = false)
                enqueueProgramMutation(revision = 9L, missedDoseRecoveryEnabled = true)
                enqueueRefresh(revision = 9L, missedDoseRecoveryEnabled = true)
            }
            val adapter = DeviceDosingV1StateAdapter(
                repository = DeviceDosingV1Repository(gateway),
                reconciliationScope = backgroundScope
            )
            adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

            val calibration = adapter.calibrationOperations.confirm(
                deviceUid = DEVICE_UID.value,
                slotId = SLOT_ID,
                displayName = "Macro"
            )

            assertTrue(calibration is DeviceDosingCalibrationResult.Success)
            assertEquals(8L, adapter.channelOperations.current(DEVICE_UID.value, SLOT_ID)?.revision)

            val switchResult = adapter.channelOperations.setMissedDoseRecoveryEnabled(
                DEVICE_UID.value,
                SLOT_ID,
                true
            )

            assertEquals(DeviceDosingChannelCommittedResult(9L), switchResult)
            val actionsBeforeBackground = gateway.actions.take(8)
            assertEquals(
                listOf(
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    DeviceDosingV1Contract.Action.PROGRESS_GET,
                    DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM,
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    DeviceDosingV1Contract.Action.STATUS_GET,
                    DeviceDosingV1Contract.Action.PROGRESS_GET,
                    DeviceDosingV1Contract.Action.PROGRAM_APPLY
                ),
                actionsBeforeBackground
            )
        }

    private fun assertProgramRequests(
        gateway: ScriptedGateway,
        expectedRevisions: List<Long>,
        expectedRecoveryValues: List<Boolean>
    ) {
        val requests = gateway.programRequests()
        assertEquals(
            expectedRevisions,
            requests.map { request -> request.getLong("expectedRevision") }
        )
        assertEquals(
            expectedRecoveryValues,
            requests.map { request ->
                request.getJSONObject("program").getBoolean("missedDoseRecoveryEnabled")
            }
        )
    }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        data class Request(val action: String, val data: String)

        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>,
            val delayMillis: Long = 0L
        )

        private val responses = ArrayDeque<Response>()
        val actions = mutableListOf<String>()
        val requests = mutableListOf<Request>()

        fun programRequests(): List<JSONObject> = requests
            .filter { request -> request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY }
            .map { request -> JSONObject(request.data) }

        fun enqueueRefresh(
            revision: Long,
            programEnabled: Boolean = true,
            missedDoseRecoveryEnabled: Boolean = false
        ) {
            val (global, channel, progress) = fixtureState(
                revision,
                programEnabled,
                missedDoseRecoveryEnabled
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        fun enqueueProgramMutation(
            revision: Long,
            programEnabled: Boolean = true,
            missedDoseRecoveryEnabled: Boolean = false,
            delayMillis: Long = 0L
        ) {
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
                        program = parsed.channel.program?.copy(
                            enabled = programEnabled,
                            missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
                        )
                    )
                ),
                delayMillis
            )
        }

        fun enqueueCalibrationConfirm() {
            enqueueSuccess(
                DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM,
                DeviceDosingV1MutationParser.parseCalibrationConfirm(
                    DeviceDosingV1TestFixtures.calibrationConfirm()
                )
            )
        }

        fun enqueueDelayedReadback(
            revision: Long,
            programEnabled: Boolean,
            missedDoseRecoveryEnabled: Boolean = false,
            delayMillis: Long
        ) {
            val (global, channel, progress) = fixtureState(
                revision,
                programEnabled,
                missedDoseRecoveryEnabled
            )
            responses.addLast(
                Response(
                    action = DeviceDosingV1Contract.Action.STATUS_GET,
                    outcome = success(DeviceDosingV1Contract.Action.STATUS_GET, global),
                    delayMillis = delayMillis
                )
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        fun enqueueDelayedGlobalReadback(
            revision: Long,
            programEnabled: Boolean,
            missedDoseRecoveryEnabled: Boolean,
            delayMillis: Long
        ) {
            val global = fixtureState(
                revision,
                programEnabled,
                missedDoseRecoveryEnabled
            ).global
            responses.addLast(
                Response(
                    action = DeviceDosingV1Contract.Action.STATUS_GET,
                    outcome = success(DeviceDosingV1Contract.Action.STATUS_GET, global),
                    delayMillis = delayMillis
                )
            )
        }

        private fun <T> enqueueSuccess(
            action: String,
            value: T,
            delayMillis: Long = 0L
        ) {
            responses.addLast(Response(action, success(action, value), delayMillis))
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
            requests += Request(command.action, command.encodeData().toString())
            delay(response.delayMillis)
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-COMMIT-TEST")
        const val SLOT_ID = "dosing:channel1"
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun fixtureState(
            revision: Long,
            programEnabled: Boolean = true,
            missedDoseRecoveryEnabled: Boolean = false
        ): FixtureState {
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
                        program = status.channel.program?.copy(
                            enabled = programEnabled,
                            missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
                        )
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
