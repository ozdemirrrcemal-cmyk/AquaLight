package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameRuntimeContract
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceSettingsOperationsTest {

    @Test
    fun `name is persisted only after matching successful firmware response`() = runBlocking {
        val fixture = Fixture(responseMode = ResponseMode.SUCCESS)
        val result = fixture.operations.updateCustomName(DEVICE_UID.value, "  Reef Tank  ")

        assertTrue(result.isSuccess)
        assertEquals("Reef Tank", fixture.current().identity.customName)
        val command = requireNotNull(fixture.transport.lastCommand)
        assertEquals(DeviceNameRuntimeContract.MODULE, command.module)
        assertEquals(DeviceNameRuntimeContract.ACTION_SET, command.action)
        assertEquals("Reef Tank", command.data.getString("customName"))
        assertTrue(command.data.getBoolean("save"))
        fixture.close()
    }

    @Test
    fun `firmware error does not mutate durable local name`() = runBlocking {
        val fixture = Fixture(responseMode = ResponseMode.ERROR)
        val result = fixture.operations.updateCustomName(DEVICE_UID.value, "Rejected name")

        assertTrue(result.isFailure)
        assertEquals("Original name", fixture.current().identity.customName)
        fixture.close()
    }

    @Test
    fun `blank input clears firmware override with JSON null`() = runBlocking {
        val fixture = Fixture(responseMode = ResponseMode.CLEAR_SUCCESS)
        val result = fixture.operations.updateCustomName(DEVICE_UID.value, "   ")

        assertTrue(result.isSuccess)
        assertEquals("", fixture.current().identity.customName)
        val command = requireNotNull(fixture.transport.lastCommand)
        assertTrue(command.data.isNull("customName"))
        fixture.close()
    }

    @Test
    fun `more than 64 UTF-8 bytes is rejected before socket write`() = runBlocking {
        val fixture = Fixture(responseMode = ResponseMode.SUCCESS)
        val result = fixture.operations.updateCustomName(
            DEVICE_UID.value,
            "ş".repeat(33)
        )

        assertTrue(result.isFailure)
        assertFalse(fixture.transport.hasSentCommand)
        assertEquals("Original name", fixture.current().identity.customName)
        fixture.close()
    }

    private class Fixture(responseMode: ResponseMode) {
        val transport = DeviceNameTransport(responseMode)
        private val registry = DeviceRegistryStore().apply { upsert(snapshot()) }
        private val runtime = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        private val repository = DevicesRepository(
            registryStore = registry,
            runtimeRepository = runtime
        )
        val operations = DefaultDeviceSettingsOperations(repository)

        init {
            repository.connectRuntime(DEVICE_UID).getOrThrow()
        }

        fun current(): DeviceSnapshot = requireNotNull(repository.currentDevice(DEVICE_UID))

        fun close() {
            runtime.close()
        }
    }

    private class DeviceNameTransport(
        private val responseMode: ResponseMode
    ) : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        private var connectedDeviceUid: DeviceUid? = null
        var lastCommand: AqlWsOutgoingMessage.Command? = null
            private set
        val hasSentCommand: Boolean
            get() = lastCommand != null

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            connectedDeviceUid = deviceUid
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            lastCommand = command
            val deviceUid = requireNotNull(connectedDeviceUid)
            val parsed = when (responseMode) {
                ResponseMode.ERROR -> AqlWsIncomingMessage.Error(
                    id = command.id,
                    type = "err",
                    module = command.module,
                    action = command.action,
                    data = JSONObject(),
                    message = "Name rejected.",
                    statusCode = 422,
                    code = "invalid_field",
                    field = "customName"
                )
                ResponseMode.SUCCESS,
                ResponseMode.CLEAR_SUCCESS -> AqlWsIncomingMessage.Response(
                    id = command.id,
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = successData(
                        customName = if (responseMode == ResponseMode.CLEAR_SUCCESS) {
                            ""
                        } else {
                            "Reef Tank"
                        }
                    ),
                    ok = true,
                    statusCode = 200
                )
            }
            check(_events.tryEmit(AqlWsEvent.Message(deviceUid, parsed)))
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(reason = "closed")
        }

        private fun successData(customName: String): JSONObject = JSONObject()
            .put("operation", "deviceNameSet")
            .put("changed", true)
            .put("saved", true)
            .put("saveRequested", true)
            .put("event", "device.status.changed")
            .put(
                "status",
                JSONObject()
                    .put("productDisplayName", "Dose Pro 2")
                    .put("customName", customName)
                    .put(
                        "effectiveDisplayName",
                        customName.ifBlank { "Dose Pro 2" }
                    )
                    .put("editable", true)
                    .put("maxBytes", 64)
            )
    }

    private enum class ResponseMode {
        SUCCESS,
        CLEAR_SUCCESS,
        ERROR
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-SETTINGS")

        fun snapshot(): DeviceSnapshot = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DEVICE_UID,
                displayName = "Dose Pro 2",
                customName = "Original name"
            ),
            product = DeviceProduct(
                familyRaw = "dosing",
                model = "dose_pro_2",
                displayName = "Dose Pro 2"
            ),
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.44",
                wsPort = 80,
                wsPath = "/aql/v1/ws",
                wsProtocol = "aql.ws.v1",
                wsProtocolVersion = 1
            )
        )
    }
}
