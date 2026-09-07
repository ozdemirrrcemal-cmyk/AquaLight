package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyPayload
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingMutationReconciliationTest {
    @Test
    fun `mutation succeeds only after matching authoritative readback`() = runBlocking {
        val status = statusJson()
        val gateway = ScriptedGateway(
            ScriptedResponse.Success(
                DeviceCoolingV1Contract.Action.CONFIG_APPLY,
                configApplyJson(status.getJSONObject(CONFIG_FIELD))
            ),
            ScriptedResponse.Success(DeviceCoolingV1Contract.Action.STATUS_GET, status)
        )
        val repository = DeviceCoolingRuntimeRepository(gateway)

        val outcome = repository.applyConfig(
            DEVICE_UID,
            DeviceCoolingV1ConfigApplyPayload(
                expectedConfigRevision = PREVIOUS_CONFIG_REVISION,
                controlMode = DeviceCoolingV1ControlMode.AUTOMATIC
            )
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(
            INITIAL_CONFIG_REVISION,
            repository.currentAuthoritativeState(DEVICE_UID)?.config?.configRevision
        )
        assertEquals(
            listOf(
                DeviceCoolingV1Contract.Action.CONFIG_APPLY,
                DeviceCoolingV1Contract.Action.STATUS_GET
            ),
            gateway.actions
        )
    }

    @Test
    fun `manual mutation ack with failed readback revokes cached authority`() = runBlocking {
        val initialStatus = statusJson()
        val gateway = ScriptedGateway(
            ScriptedResponse.Success(DeviceCoolingV1Contract.Action.STATUS_GET, initialStatus),
            ScriptedResponse.Success(
                DeviceCoolingV1Contract.Action.MANUAL_APPLY,
                manualApplyJson()
            ),
            ScriptedResponse.Timeout(DeviceCoolingV1Contract.Action.STATUS_GET)
        )
        val repository = DeviceCoolingRuntimeRepository(gateway)
        assertTrue(repository.requestStatus(DEVICE_UID) is DeviceRuntimeCommandOutcome.Success)

        val outcome = repository.applyManual(
            DEVICE_UID,
            DeviceCoolingV1ManualApplyPayload(
                expectedConfigRevision = INITIAL_CONFIG_REVISION,
                targetPercent = MANUAL_TARGET_PERCENT
            )
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Timeout)
        assertNull(repository.currentAuthoritativeState(DEVICE_UID))
        assertFalse(repository.states.value.getValue(DEVICE_UID).authoritative)
    }

    @Test
    fun `readback that does not confirm mutation ack fails closed`() = runBlocking {
        val staleStatus = statusJson()
        val committedConfig = JSONObject(staleStatus.getJSONObject(CONFIG_FIELD).toString())
            .put(CONFIG_REVISION_FIELD, COMMITTED_CONFIG_REVISION)
            .put("controlMode", DeviceCoolingV1ControlMode.MANUAL.wireValue)
        val gateway = ScriptedGateway(
            ScriptedResponse.Success(
                DeviceCoolingV1Contract.Action.CONFIG_APPLY,
                configApplyJson(committedConfig)
            ),
            ScriptedResponse.Success(DeviceCoolingV1Contract.Action.STATUS_GET, staleStatus)
        )
        val repository = DeviceCoolingRuntimeRepository(gateway)

        val outcome = repository.applyConfig(
            DEVICE_UID,
            DeviceCoolingV1ConfigApplyPayload(
                expectedConfigRevision = INITIAL_CONFIG_REVISION,
                controlMode = DeviceCoolingV1ControlMode.MANUAL
            )
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.ProtocolError)
        assertEquals(
            INITIAL_CONFIG_REVISION,
            repository.currentAuthoritativeState(DEVICE_UID)?.config?.configRevision
        )
    }

    private class ScriptedGateway(
        vararg responses: ScriptedResponse
    ) : DeviceRuntimeCommandGateway {
        private val responses = responses.toList()
        private var responseIndex = 0
        val actions = mutableListOf<String>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            val response = responses[responseIndex++]
            check(response.action == command.action) {
                "Expected ${response.action}, received ${command.action}."
            }
            actions += command.action
            return when (response) {
                is ScriptedResponse.Success -> success(deviceUid, command, response.data)
                is ScriptedResponse.Timeout -> DeviceRuntimeCommandOutcome.Timeout(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = "response-$responseIndex",
                    generation = GENERATION,
                    timeoutMillis = timeoutMillis
                )
            }
        }

        private fun <T> success(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            data: JSONObject
        ): DeviceRuntimeCommandOutcome.Success<T> {
            val messageId = "response-$responseIndex"
            val response = AqlWsIncomingMessage.Response(
                id = messageId,
                type = "res",
                module = command.module,
                action = command.action,
                data = JSONObject(data.toString()),
                ok = true,
                statusCode = SUCCESS_STATUS_CODE
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = messageId,
                generation = GENERATION,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }

    private sealed interface ScriptedResponse {
        val action: String

        data class Success(
            override val action: String,
            val data: JSONObject
        ) : ScriptedResponse

        data class Timeout(override val action: String) : ScriptedResponse
    }

    private companion object {
        const val STATUS_FIXTURE = "aql_cooling_status_v1.json"
        const val CONFIG_FIELD = "config"
        const val CONFIG_REVISION_FIELD = "configRevision"
        const val PREVIOUS_CONFIG_REVISION = 3L
        const val INITIAL_CONFIG_REVISION = 4L
        const val COMMITTED_CONFIG_REVISION = 5L
        const val SUCCESS_STATUS_CODE = 200
        const val MANUAL_TARGET_PERCENT = 65.0
        val DEVICE_UID = DeviceUid("AQL-COOLING-RECONCILIATION")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)

        fun statusJson(): JSONObject = JSONObject(
            requireNotNull(
                DeviceCoolingMutationReconciliationTest::class.java.classLoader
                    ?.getResourceAsStream(STATUS_FIXTURE)
            ) { "Missing fixture resource: $STATUS_FIXTURE" }
                .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        )

        fun configApplyJson(config: JSONObject): JSONObject = JSONObject()
            .put("command", "cooling.config.apply")
            .put("operation", "configApply")
            .put("saved", true)
            .put("event", DeviceCoolingV1Contract.Event.STATUS_CHANGED)
            .put("config", JSONObject(config.toString()))

        fun manualApplyJson(): JSONObject = JSONObject()
            .put("command", "cooling.manual.apply")
            .put("operation", "manualApply")
            .put("saved", true)
            .put(CONFIG_REVISION_FIELD, COMMITTED_CONFIG_REVISION)
            .put("fanKey", DeviceCoolingV1Contract.FAN_KEY)
            .put("manualActive", true)
            .put("manualTargetPercent", MANUAL_TARGET_PERCENT)
            .put("event", DeviceCoolingV1Contract.Event.STATUS_CHANGED)
    }
}
