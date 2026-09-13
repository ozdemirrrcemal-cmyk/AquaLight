package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerRepositoryContractTest {
    @Test
    fun `repository uses scoped readback revision CAS and exact Timer serializers`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { SUPPORTED_ACCESS }
        )

        val status = repository.requestStatus(DEVICE_UID)
        val replace = repository.replaceSchedules(
            deviceUid = DEVICE_UID,
            channelKey = "channel1",
            expectedRevision = TIMER_TEST_BASE_REVISION,
            schedules = listOf(DeviceTimerRuntimeFixtures.schedulePayload())
        )
        val temporary = repository.setTemporaryOverride(
            deviceUid = DEVICE_UID,
            channelKey = "channel1",
            regime = DeviceTimerRegime.OFF,
            durationMs = TIMER_TEST_OVERRIDE_DURATION_MILLIS
        )

        assertSuccessful(status, replace, temporary)
        assertEquals(
            listOf("status.get", "config.apply", "status.get", "channel.set", "status.get"),
            gateway.actions
        )
        assertEquals(emptySet<String>(), gateway.encoded[0].keySetExact())
        assertEquals(
            setOf("channelKey", "expectedRevision", "schedules", "save"),
            gateway.encoded[1].keySetExact()
        )
        assertEquals(TIMER_TEST_BASE_REVISION, gateway.encoded[1].getLong("expectedRevision"))
        assertFalse(gateway.encoded[1].getJSONArray("schedules").getJSONObject(0)
            .has("channelKey"))
        assertEquals(setOf("channelKey"), gateway.encoded[2].keySetExact())
        val temporaryOverrideRequest = gateway.actions.zip(gateway.encoded)
            .single { (action, _) -> action == DeviceTimerRuntimeContract.Action.CHANNEL_SET }
            .second
        assertEquals(
            setOf("channelKey", "expectedRevision", "regime", "durationMs", "save"),
            temporaryOverrideRequest.keySetExact()
        )
        assertEquals(
            TIMER_TEST_APPLIED_REVISION,
            temporaryOverrideRequest.getLong("expectedRevision")
        )
        assertFalse(temporaryOverrideRequest.getBoolean("save"))

        val state = repository.states.value.getValue(DEVICE_UID)
        assertEquals(TIMER_TEST_APPLIED_REVISION, state.status?.revision)
        assertFalse(requireNotNull(state.status).channelScoped)
        assertEquals("Day Filter", state.channelDetails.getValue("channel1").schedules.single().name)
        assertFalse(state.requiresStatusRefresh)
    }

    @Test
    fun `stale editor revision is sent once and conflict refreshes central state`() = runBlocking {
        val gateway = ConflictGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { SUPPORTED_ACCESS }
        )

        repository.requestStatus(DEVICE_UID)
        val result = repository.replaceSchedules(
            deviceUid = DEVICE_UID,
            channelKey = "channel1",
            expectedRevision = TIMER_TEST_BASE_REVISION,
            schedules = listOf(DeviceTimerRuntimeFixtures.schedulePayload())
        )

        assertTrue(result is DeviceRuntimeCommandOutcome.FirmwareError)
        assertEquals(
            listOf("status.get", "config.apply", "status.get", "status.get"),
            gateway.actions
        )
        assertEquals(TIMER_TEST_BASE_REVISION, gateway.encoded[1].getLong("expectedRevision"))
        val state = requireNotNull(repository.currentAuthoritativeState(DEVICE_UID))
        assertEquals(TIMER_TEST_RECOVERY_REVISION, state.status?.revision)
        assertEquals(TIMER_TEST_RECOVERY_REVISION, state.channelDetails["channel1"]?.revision)
    }

    @Test
    fun `malformed direct event is refreshed through central repository`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { SUPPORTED_ACCESS }
        )
        repository.requestStatus(DEVICE_UID)

        repository.consume(
            DeviceRuntimeTypedEvent(
                deviceUid = DEVICE_UID,
                generation = GENERATION,
                messageId = "evt-malformed",
                type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
                payload = DeviceRuntimeEventPayload.Snapshot(JSONObject())
            )
        )

        assertEquals(listOf("status.get", "status.get"), gateway.actions)
        assertTrue(requireNotNull(repository.currentAuthoritativeState(DEVICE_UID)).authoritative)
    }

    private fun assertSuccessful(vararg outcomes: DeviceRuntimeCommandOutcome<*>) {
        assertTrue(outcomes.all { outcome -> outcome is DeviceRuntimeCommandOutcome.Success })
    }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var revision = TIMER_TEST_BASE_REVISION
        private var temporaryOverride = false

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val request = command.encodeData()
            encoded += JSONObject(request.toString())
            val response = when (command.action) {
                DeviceTimerRuntimeContract.Action.STATUS_GET -> status(request)
                DeviceTimerRuntimeContract.Action.CONFIG_APPLY -> configApply(request)
                DeviceTimerRuntimeContract.Action.CHANNEL_SET -> channelSet(request)
                else -> error("Unexpected Timer action: " + command.action)
            }
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = "res-" + command.action,
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = response,
                    ok = true,
                    statusCode = TIMER_TEST_HTTP_OK
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "res-" + command.action,
                generation = GENERATION,
                statusCode = TIMER_TEST_HTTP_OK,
                value = value
            )
        }

        private fun status(request: JSONObject): JSONObject =
            if (request.has("channelKey")) {
                DeviceTimerRuntimeFixtures.channelStatus(revision = revision).also { status ->
                    if (temporaryOverride) {
                        status.getJSONArray("channels").getJSONObject(0)
                            .put("operatingState", "OFF")
                            .put("runtimeReason", "temporaryOverrideOff")
                            .put("temporaryOverrideActive", true)
                            .put(
                                "temporaryOverrideRemainingMs",
                                TIMER_TEST_OVERRIDE_REMAINING_MILLIS
                            )
                    }
                }
            } else {
                DeviceTimerRuntimeFixtures.globalStatus(revision = revision)
            }

        private fun configApply(request: JSONObject): JSONObject {
            revision += 1L
            return DeviceTimerRuntimeFixtures.configApply(revision = revision).also { result ->
                result.put("appliedDisplayName", request.has("displayName"))
                result.put("replacedSchedules", request.has("schedules"))
                result.getJSONObject("channel").put(
                    "scheduleCount",
                    request.optJSONArray("schedules")?.length() ?: 1
                )
            }
        }

        private fun channelSet(request: JSONObject): JSONObject {
            assertEquals(revision, request.getLong("expectedRevision"))
            temporaryOverride = request.has("durationMs")
            return DeviceTimerRuntimeFixtures.channelSet(revision).also { result ->
                result.getJSONObject("channel").put("scheduleCount", 1)
            }
        }
    }

    private class ConflictGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var revision = TIMER_TEST_APPLIED_REVISION

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val request = command.encodeData()
            encoded += JSONObject(request.toString())
            if (command.action == DeviceTimerRuntimeContract.Action.CONFIG_APPLY) {
                revision = TIMER_TEST_RECOVERY_REVISION
                return DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = "res-conflict",
                    generation = GENERATION,
                    statusCode = TIMER_TEST_CONFLICT_STATUS,
                    code = DeviceTimerRuntimeContract.Error.CONFLICT,
                    field = DeviceTimerRuntimeContract.Field.EXPECTED_REVISION,
                    message = "Command rejected."
                )
            }
            val response = if (request.has("channelKey")) {
                DeviceTimerRuntimeFixtures.channelStatus(revision = revision)
            } else {
                DeviceTimerRuntimeFixtures.globalStatus(revision = revision)
            }
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "res-status",
                generation = GENERATION,
                statusCode = TIMER_TEST_HTTP_OK,
                value = command.parseSuccess(
                    AqlWsIncomingMessage.Response(
                        id = "res-status",
                        type = "res",
                        module = command.module,
                        action = command.action,
                        data = response,
                        ok = true,
                        statusCode = TIMER_TEST_HTTP_OK
                    )
                )
            )
        }
    }

    private fun JSONObject.keySetExact(): Set<String> =
        keys().asSequence().toCollection(linkedSetOf())

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-REPOSITORY")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = TIMER_TEST_CHANNEL_COUNT,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
