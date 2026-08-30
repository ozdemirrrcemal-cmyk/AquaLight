package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ChannelDetailCutoverTest {
    @Test
    fun `missed dose recovery mutates the complete authoritative program`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L)
            enqueueProgramMutation(revision = 8L, missedDoseRecoveryEnabled = true)
            enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        val request = gateway.requests.single { it.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY }
        val payload = JSONObject(request.data)
        val program = payload.getJSONObject("program")
        val config = program.getJSONObject("config")
        assertEquals(7L, payload.getLong("expectedRevision"))
        assertTrue(program.getBoolean("enabled"))
        assertEquals(7, program.getJSONArray("weekdays").length())
        assertEquals("hourly24", program.getString("mode"))
        assertTrue(program.getBoolean("missedDoseRecoveryEnabled"))
        assertEquals(2.4, config.getDouble("dailyDoseMl"), 0.0)
        assertEquals(15, config.getInt("minuteOfHour"))
        assertFalse(config.has("startTimeMs"))
        assertTrue(
            (result as DeviceDosingChannelOperationResult.Success)
                .snapshot.program?.missedDoseRecoveryEnabled == true
        )
    }

    @Test
    fun `unchanged plan after switch skips a duplicate write and preserves the switch field`() =
        runTest {
            val gateway = Stage9Gateway().apply {
                enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
                enqueueProgramMutation(revision = 8L, missedDoseRecoveryEnabled = true)
                enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = true)
            }
            val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success
            val planFromBeforeSwitch = requireNotNull(initial.snapshot.program)

            val switchResult = adapter.channelOperations.setMissedDoseRecoveryEnabled(
                DEVICE_UID.value,
                SLOT_ID,
                true
            )
            val planResult = adapter.channelOperations.applyProgramAtRevision(
                deviceUid = DEVICE_UID.value,
                slotId = SLOT_ID,
                program = planFromBeforeSwitch,
                expectedRevision = 7L
            )

            assertTrue(switchResult is DeviceDosingChannelOperationResult.Success)
            assertTrue(planResult is DeviceDosingChannelOperationResult.Success)
            val mutations = gateway.requests.filter { request ->
                request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
            }
            assertEquals(1, mutations.size)
            assertEquals(
                listOf(7L),
                mutations.map { request -> JSONObject(request.data).getLong("expectedRevision") }
            )
            assertTrue(
                mutations.all { request ->
                    JSONObject(request.data)
                        .getJSONObject("program")
                        .getBoolean("missedDoseRecoveryEnabled")
                }
            )
            assertTrue(
                (planResult as DeviceDosingChannelOperationResult.Success)
                    .snapshot.program?.missedDoseRecoveryEnabled == true
            )
            assertEquals(8L, planResult.snapshot.revision)
        }

    @Test
    fun `ambiguous switch timeout rereads and retries inside one user action`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueTimeout(DeviceDosingV1Contract.Action.PROGRAM_APPLY)
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueProgramMutation(revision = 8L, missedDoseRecoveryEnabled = true)
            enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        val mutations = gateway.requests.filter { request ->
            request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
        }
        assertEquals(2, mutations.size)
        assertEquals(
            listOf(7L, 7L),
            mutations.map { request -> JSONObject(request.data).getLong("expectedRevision") }
        )
        assertTrue(
            (result as DeviceDosingChannelOperationResult.Success)
                .snapshot.program?.missedDoseRecoveryEnabled == true
        )
    }

    @Test
    fun `switch revision conflict rebases and completes inside one user action`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueRevisionConflict(DeviceDosingV1Contract.Action.PROGRAM_APPLY)
            enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = false)
            enqueueProgramMutation(revision = 9L, missedDoseRecoveryEnabled = true)
            enqueueRefresh(revision = 9L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        val mutations = gateway.requests.filter { request ->
            request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
        }
        assertEquals(2, mutations.size)
        assertEquals(
            listOf(7L, 8L),
            mutations.map { request -> JSONObject(request.data).getLong("expectedRevision") }
        )
        val snapshot = (result as DeviceDosingChannelOperationResult.Success).snapshot
        assertEquals(9L, snapshot.revision)
        assertTrue(snapshot.program?.missedDoseRecoveryEnabled == true)
    }

    @Test
    fun `lost switch ack is accepted only after readback proves the requested state`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = false)
            enqueueTimeout(DeviceDosingV1Contract.Action.PROGRAM_APPLY)
            enqueueRefresh(revision = 8L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        assertEquals(
            1,
            gateway.requests.count { request ->
                request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
            }
        )
        val snapshot = (result as DeviceDosingChannelOperationResult.Success).snapshot
        assertEquals(8L, snapshot.revision)
        assertTrue(snapshot.program?.missedDoseRecoveryEnabled == true)
    }

    @Test
    fun `already current switch assignment skips the firmware mutation`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, missedDoseRecoveryEnabled = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.setMissedDoseRecoveryEnabled(
            DEVICE_UID.value,
            SLOT_ID,
            true
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        assertEquals(
            0,
            gateway.requests.count { request ->
                request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
            }
        )
        assertEquals(7L, (result as DeviceDosingChannelOperationResult.Success).snapshot.revision)
    }

    @Test
    fun `manual start stop render active run only from refreshed authoritative state`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, manualActive = false)
            enqueueDoseNowMutation(revision = 7L, manualActive = true)
            enqueueRefresh(revision = 7L, manualActive = true)
            enqueueDoseStopMutation(revision = 7L)
            enqueueRefresh(revision = 7L, manualActive = false)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val started = adapter.channelOperations.doseNow(DEVICE_UID.value, SLOT_ID, 2_500L)

        assertTrue(started is DeviceDosingChannelOperationResult.Success)
        val startedSnapshot = (started as DeviceDosingChannelOperationResult.Success).snapshot
        assertTrue(startedSnapshot.activeRun.active)
        assertEquals(DeviceDosingRunSource.MANUAL, startedSnapshot.activeRun.source)
        val doseRequest = gateway.requests.single { it.action == DeviceDosingV1Contract.Action.DOSE_NOW }
        assertEquals(2.5, JSONObject(doseRequest.data).getDouble("amountMl"), 0.0)
        assertFalse(JSONObject(doseRequest.data).has("expectedRevision"))

        val stopped = adapter.channelOperations.doseStop(DEVICE_UID.value, SLOT_ID)

        assertTrue(stopped is DeviceDosingChannelOperationResult.Success)
        assertFalse((stopped as DeviceDosingChannelOperationResult.Success).snapshot.activeRun.active)
    }

    @Test
    fun `channel reset sends authoritative revision before reset execution`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L)
            enqueueResetMutation(revision = 8L)
            enqueueRefresh(revision = 8L)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.reset(DEVICE_UID.value, SLOT_ID)

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        val request = gateway.requests.single { it.action == DeviceDosingV1Contract.Action.CHANNEL_RESET }
        assertEquals(7L, JSONObject(request.data).getLong("expectedRevision"))
        assertEquals(8L, (result as DeviceDosingChannelOperationResult.Success).snapshot.revision)
    }

    @Test
    fun `semantic mutation failure refreshes central state before returning`() = runTest {
        val gateway = Stage9Gateway().apply {
            enqueueRefresh(revision = 7L, manualActive = false)
            enqueueBusy(DeviceDosingV1Contract.Action.DOSE_NOW)
            enqueueRefresh(revision = 8L, manualActive = true)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.channelOperations.doseNow(DEVICE_UID.value, SLOT_ID, 1_000L)

        assertEquals(
            DeviceDosingChannelOperationResult.Rejected(DeviceDosingChannelRejection.BUSY),
            result
        )
        val authoritative = requireNotNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertEquals(8L, authoritative.revision)
        assertTrue(authoritative.activeRun.active)
        assertEquals(DeviceDosingRunSource.MANUAL, authoritative.activeRun.source)
        assertEquals(7, gateway.requests.size)
    }

    private class Stage9Gateway : DeviceRuntimeCommandGateway {
        data class Request(val action: String, val data: String)
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>
        )

        private val responses = ArrayDeque<Response>()
        val requests = mutableListOf<Request>()

        fun enqueueRefresh(
            revision: Long,
            manualActive: Boolean = false,
            missedDoseRecoveryEnabled: Boolean = false
        ) {
            val state = fixtureState(revision, manualActive, missedDoseRecoveryEnabled)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, state.global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, state.channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, state.progress)
        }

        fun enqueueProgramMutation(revision: Long, missedDoseRecoveryEnabled: Boolean) {
            val detail = channelDetail(revision, false, missedDoseRecoveryEnabled)
            val parsed = DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(DeviceDosingV1Contract.Literal.PROGRAM_APPLY)
                    .put("channel", detail)
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRAM_APPLY, parsed)
        }

        fun enqueueResetMutation(revision: Long) {
            val parsed = DeviceDosingV1MutationParser.parseChannelReset(
                DeviceDosingV1TestFixtures.savedMutation(DeviceDosingV1Contract.Literal.CHANNEL_RESET)
                    .put("channel", channelDetail(revision, false, false))
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.CHANNEL_RESET, parsed)
        }

        fun enqueueDoseNowMutation(revision: Long, manualActive: Boolean) {
            val parsed = DeviceDosingV1MutationParser.parseDoseNow(
                DeviceDosingV1TestFixtures.doseNow()
                    .put("channel", channelDetail(revision, manualActive, false))
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.DOSE_NOW, parsed)
        }

        fun enqueueDoseStopMutation(revision: Long) {
            val parsed = DeviceDosingV1MutationParser.parseDoseStop(
                DeviceDosingV1TestFixtures.simpleStop(DeviceDosingV1Contract.Literal.DOSE_STOP)
                    .put("channel", channelDetail(revision, false, false))
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.DOSE_STOP, parsed)
        }

        fun enqueueBusy(action: String) {
            enqueue(
                action,
                DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = DEVICE_UID,
                    module = DeviceDosingV1Contract.MODULE,
                    action = action,
                    messageId = "busy-$action",
                    generation = GENERATION,
                    statusCode = 409,
                    code = "DEVICE_BUSY",
                    field = "",
                    message = "dosing operation in progress"
                )
            )
        }

        fun enqueueTimeout(action: String) {
            enqueue(
                action,
                DeviceRuntimeCommandOutcome.Timeout(
                    deviceUid = DEVICE_UID,
                    module = DeviceDosingV1Contract.MODULE,
                    action = action,
                    messageId = "timeout-$action",
                    generation = GENERATION,
                    timeoutMillis = 5_000L
                )
            )
        }

        fun enqueueRevisionConflict(action: String) {
            enqueue(
                action,
                DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = DEVICE_UID,
                    module = DeviceDosingV1Contract.MODULE,
                    action = action,
                    messageId = "conflict-$action",
                    generation = GENERATION,
                    statusCode = 409,
                    code = "INVALID_VALUE",
                    field = "expectedRevision",
                    message = "stale dosing channel revision"
                )
            )
        }

        private fun enqueue(action: String, outcome: DeviceRuntimeCommandOutcome<*>) {
            responses.addLast(Response(action, outcome))
        }

        private fun <T> enqueueSuccess(action: String, value: T) {
            enqueue(
                action,
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = DEVICE_UID,
                    module = DeviceDosingV1Contract.MODULE,
                    action = action,
                    messageId = "response-$action-${requests.size}",
                    generation = GENERATION,
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
            requests += Request(command.action, command.encodeData().toString())
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-STAGE9")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        const val SLOT_ID = "dosing:channel1"

        fun fixtureState(
            revision: Long,
            manualActive: Boolean,
            missedDoseRecoveryEnabled: Boolean
        ): FixtureState {
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus().also { root ->
                    root.getJSONArray("channels").getJSONObject(0).put("revision", revision)
                        .put("active", manualActive)
                }
            )
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus(
                    channelDetail(revision, manualActive, missedDoseRecoveryEnabled)
                )
            )
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus().put("revision", revision)
            )
            return FixtureState(global, channel, progress)
        }

        fun channelDetail(
            revision: Long,
            manualActive: Boolean,
            missedDoseRecoveryEnabled: Boolean
        ): JSONObject = DeviceDosingV1TestFixtures.channelDetail(revision).also { detail ->
            detail.getJSONObject("program")
                .put("missedDoseRecoveryEnabled", missedDoseRecoveryEnabled)
            detail.put(
                "activeRun",
                JSONObject()
                    .put("active", manualActive)
                    .put("source", if (manualActive) "manual" else "none")
                    .put("targetAmountMl", if (manualActive) 2.5 else 0.0)
                    .put("remainingMs", if (manualActive) 2_000 else 0)
            )
        }
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )
}
