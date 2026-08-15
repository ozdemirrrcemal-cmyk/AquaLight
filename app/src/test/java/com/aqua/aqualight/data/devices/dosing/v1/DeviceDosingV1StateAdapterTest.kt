package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import java.util.ArrayDeque
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1StateAdapterTest {

    @Test
    fun `refresh joins exact global channel and progress state`() = runTest {
        val gateway = ScriptedDosingGateway().apply { enqueueRefresh(revision = 7L) }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))

        val result = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        val snapshot = (result as DeviceDosingChannelOperationResult.Success).snapshot
        assertEquals(SLOT_ID, snapshot.slotId)
        assertEquals(7L, snapshot.revision)
        assertEquals("Macro", snapshot.channelTitle)
        assertEquals(2, snapshot.pumpCount)
        assertEquals(2_400L, snapshot.progress.scheduledAmountMicroliters)
        assertEquals(2, snapshot.progress.occurrences.size)
        assertEquals(500_000L, snapshot.reservoir.capacityMicroliters)
        assertFalse(snapshot.reservoir.lowLevelAlertEnabled)
        assertEquals(7L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertEquals("Macro", adapter.currentCalibration(DEVICE_UID.value, SLOT_ID)?.channelTitle)
        assertEquals(
            listOf("{}", "{\"channelKey\":\"channel1\"}", "{\"channelKey\":\"channel1\"}"),
            gateway.requests.map(ScriptedDosingGateway.Request::data)
        )
    }

    @Test
    fun `owner rejects stale requests and lower revisions`() {
        val owner = DeviceDosingV1StateOwner()
        val key = DeviceDosingV1ChannelKey.from("channel1")
        val staleRequest = owner.beginRequest(DEVICE_UID, key)
        val currentRequest = owner.beginRequest(DEVICE_UID, key)
        val revisionSeven = fixtureState(revision = 7L)

        assertEquals(
            DeviceDosingV1CommitDisposition.STALE_REQUEST,
            owner.commitRefresh(
                staleRequest,
                GENERATION_ONE,
                revisionSeven.global,
                revisionSeven.channel,
                revisionSeven.progress
            )
        )
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                currentRequest,
                GENERATION_ONE,
                revisionSeven.global,
                revisionSeven.channel,
                revisionSeven.progress
            )
        )
        val lowerRevision = owner.beginRequest(DEVICE_UID, key)
        val revisionSix = fixtureState(revision = 6L)
        assertEquals(
            DeviceDosingV1CommitDisposition.STALE_REVISION,
            owner.commitRefresh(
                lowerRevision,
                GENERATION_ONE,
                revisionSix.global,
                revisionSix.channel,
                revisionSix.progress
            )
        )
    }

    @Test
    fun `owner resets revision on reconnect and rejects the old connection`() {
        val owner = DeviceDosingV1StateOwner()
        val key = DeviceDosingV1ChannelKey.from("channel1")
        val initial = owner.beginRequest(DEVICE_UID, key)
        val revisionSeven = fixtureState(revision = 7L)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                initial,
                GENERATION_ONE,
                revisionSeven.global,
                revisionSeven.channel,
                revisionSeven.progress
            )
        )
        val reconnect = owner.beginRequest(DEVICE_UID, key)
        val revisionThree = fixtureState(revision = 3L)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                reconnect,
                GENERATION_TWO,
                revisionThree.global,
                revisionThree.channel,
                revisionThree.progress
            )
        )
        assertEquals(3L, owner.reads.currentChannel(DEVICE_UID, key)?.revision)

        val oldConnection = owner.beginRequest(DEVICE_UID, key)
        val revisionEight = fixtureState(revision = 8L)
        assertEquals(
            DeviceDosingV1CommitDisposition.STALE_CONNECTION,
            owner.commitRefresh(
                oldConnection,
                GENERATION_ONE,
                revisionEight.global,
                revisionEight.channel,
                revisionEight.progress
            )
        )
        assertEquals(3L, owner.reads.currentChannel(DEVICE_UID, key)?.revision)
    }

    @Test
    fun `revision conflict refreshes authoritative state without retrying mutation`() = runTest {
        val gateway = ScriptedDosingGateway().apply {
            enqueueRefresh(revision = 7L)
            enqueue(
                DeviceDosingV1Contract.Action.PROGRAM_APPLY,
                firmwareConflict(DeviceDosingV1Contract.Action.PROGRAM_APPLY)
            )
            enqueueRefresh(revision = 8L)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingChannelOperationResult.Success

        val result = adapter.channelOperations.applyProgram(
            DEVICE_UID.value,
            SLOT_ID,
            requireNotNull(initial.snapshot.program)
        )

        assertEquals(
            DeviceDosingChannelOperationResult.Rejected(DeviceDosingChannelRejection.CONFLICT),
            result
        )
        val mutations = gateway.requests.filter { request ->
            request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY
        }
        assertEquals(1, mutations.size)
        assertEquals(7L, JSONObject(mutations.single().data).getLong("expectedRevision"))
        assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
    }

    @Test
    fun `status changed event invalidates and refreshes instead of becoming a snapshot`() = runTest {
        val gateway = ScriptedDosingGateway().apply {
            enqueueRefresh(revision = 7L)
            enqueueRefresh(revision = 8L)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.consume(
            DeviceRuntimeTypedEvent(
                deviceUid = DEVICE_UID,
                generation = GENERATION_ONE,
                messageId = "event-1",
                type = DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED,
                payload = DeviceRuntimeEventPayload.Snapshot(
                    DeviceDosingV1TestFixtures.directEvent()
                )
            )
        )

        assertTrue(result is DeviceDosingV1EventResult.Refreshed)
        assertEquals(8L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertEquals(6, gateway.requests.size)
    }

    @Test
    fun `same channel persisted mutations are serialized and advance authoritative revision`() =
        runTest {
            val gateway = ScriptedDosingGateway(programDelayMillis = 10L).apply {
                enqueueRefresh(revision = 7L)
                enqueueProgramMutation(revision = 8L)
                enqueueRefresh(revision = 8L)
                enqueueProgramMutation(revision = 9L)
                enqueueRefresh(revision = 9L)
            }
            val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
            val initial = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)
                as DeviceDosingChannelOperationResult.Success
            val program = requireNotNull(initial.snapshot.program)

            val first = async {
                adapter.channelOperations.applyProgram(DEVICE_UID.value, SLOT_ID, program)
            }
            val second = async {
                adapter.channelOperations.applyProgram(DEVICE_UID.value, SLOT_ID, program)
            }

            assertTrue(first.await() is DeviceDosingChannelOperationResult.Success)
            assertTrue(second.await() is DeviceDosingChannelOperationResult.Success)
            assertEquals(1, gateway.maxConcurrentProgramMutations)
            val revisions = gateway.requests
                .filter { request -> request.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY }
                .map { request -> JSONObject(request.data).getLong("expectedRevision") }
            assertEquals(listOf(7L, 8L), revisions)
            assertEquals(9L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        }

    @Test
    fun `malformed cross revision status never replaces authoritative state`() = runTest {
        val gateway = ScriptedDosingGateway().apply {
            enqueueRefresh(revision = 7L)
            val (global, channel, progress) = fixtureState(revision = 8L)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global)
            enqueueSuccess(
                DeviceDosingV1Contract.Action.STATUS_GET,
                channel.copy(channel = channel.channel.copy(revision = 9L))
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val malformed = adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        assertEquals(DeviceDosingChannelOperationResult.Failed, malformed)
        assertEquals(7L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
    }

    @Test
    fun `stale event generation cannot invalidate a reconnected device`() = runTest {
        val gateway = ScriptedDosingGateway().apply {
            enqueueRefresh(revision = 3L, generation = GENERATION_TWO)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        val result = adapter.consume(
            DeviceRuntimeTypedEvent(
                deviceUid = DEVICE_UID,
                generation = GENERATION_ONE,
                messageId = "old-event",
                type = DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED,
                payload = DeviceRuntimeEventPayload.Snapshot(
                    DeviceDosingV1TestFixtures.directEvent()
                )
            )
        )

        assertEquals(DeviceDosingV1EventResult.Ignored, result)
        assertEquals(3L, adapter.currentChannel(DEVICE_UID.value, SLOT_ID)?.revision)
        assertEquals(3, gateway.requests.size)
    }

    @Test
    fun `runtime lifecycle boundary clears the previous session snapshot`() = runTest {
        val gateway = ScriptedDosingGateway().apply { enqueueRefresh(revision = 7L) }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        adapter.channelOperations.refresh(DEVICE_UID.value, SLOT_ID)

        adapter.consume(DeviceRuntimeLifecycleEvent.Unavailable(DEVICE_UID))

        assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertNull(adapter.currentCalibration(DEVICE_UID.value, SLOT_ID))
    }

    private class ScriptedDosingGateway(
        private val programDelayMillis: Long = 0L
    ) : DeviceRuntimeCommandGateway {
        data class Request(val action: String, val data: String)
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>
        )

        private val responses = ArrayDeque<Response>()
        val requests = mutableListOf<Request>()
        var maxConcurrentProgramMutations = 0
            private set
        private var concurrentProgramMutations = 0

        fun enqueue(action: String, outcome: DeviceRuntimeCommandOutcome<*>) {
            responses.addLast(Response(action, outcome))
        }

        fun enqueueRefresh(
            revision: Long,
            generation: DeviceRuntimeConnectionGeneration = GENERATION_ONE
        ) {
            val (global, channel, progress) = fixtureState(revision)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global, generation)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel, generation)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress, generation)
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

        fun <T> enqueueSuccess(
            action: String,
            value: T,
            generation: DeviceRuntimeConnectionGeneration = GENERATION_ONE
        ) {
            enqueue(action, success(action, value, generation))
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
            if (command.action == DeviceDosingV1Contract.Action.PROGRAM_APPLY) {
                concurrentProgramMutations += 1
                maxConcurrentProgramMutations = maxOf(
                    maxConcurrentProgramMutations,
                    concurrentProgramMutations
                )
                delay(programDelayMillis)
                concurrentProgramMutations -= 1
            }
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-STATE-TEST")
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

        fun <T> success(
            action: String,
            value: T,
            generation: DeviceRuntimeConnectionGeneration
        ): DeviceRuntimeCommandOutcome.Success<T> = DeviceRuntimeCommandOutcome.Success(
            deviceUid = DEVICE_UID,
            module = DeviceDosingV1Contract.MODULE,
            action = action,
            messageId = "response-$action-${generation.value}",
            generation = generation,
            statusCode = 200,
            value = value
        )

        fun firmwareConflict(action: String): DeviceRuntimeCommandOutcome.FirmwareError =
            DeviceRuntimeCommandOutcome.FirmwareError(
                deviceUid = DEVICE_UID,
                module = DeviceDosingV1Contract.MODULE,
                action = action,
                messageId = "conflict-1",
                generation = GENERATION_ONE,
                statusCode = 409,
                code = "INVALID_VALUE",
                field = "expectedRevision",
                message = "stale dosing channel revision"
            )
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )
}
