package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeContract
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceRuntimeBootstrapDispatchTest {

    @Test
    fun `authenticated dosing bootstrap waits for metadata and never queries timer`() {
        val transport = RecordingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val deviceUid = DeviceUid("fixture-dose-pro-2")
        repository.connect(snapshot(deviceUid)).getOrThrow()

        transport.emit(AqlWsEvent.Authenticated(deviceUid))

        assertEquals(
            listOf(
                AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
                AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
            ),
            transport.commandKeys()
        )

        transport.emit(
            messageEvent(
                deviceUid = deviceUid,
                action = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET,
                data = JSONObject().put(
                    "capabilities",
                    JSONObject()
                        .put("dosing", true)
                        .put("standaloneTimer", false)
                        .put("timeSync", true)
                        .put("ota", true)
                )
            )
        )
        assertEquals(2, transport.commandKeys().size)

        transport.emit(
            messageEvent(
                deviceUid = deviceUid,
                action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
                data = JSONObject().put("family", "dosing")
            )
        )

        val commandKeys = transport.commandKeys()
        assertEquals(
            listOf(
                AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
                AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET,
                AqlWsContract.MODULE_SECURITY to AqlWsContract.ACTION_SECURITY_STATUS_GET,
                AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_STATUS_GET,
                AqlWsContract.MODULE_NETWORK to AqlWsContract.ACTION_NETWORK_STATUS_GET,
                AqlWsContract.MODULE_TIME to AqlWsContract.ACTION_TIME_STATUS_GET,
                AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_STATUS_GET,
                AqlWsContract.MODULE_DOSING to AqlWsContract.ACTION_DOSING_STATUS_GET,
                AqlWsContract.MODULE_TIME to DeviceTimeRuntimeContract.Action.PHONE_SYNC
            ),
            commandKeys
        )
        assertFalse(commandKeys.any { (module, _) -> module == AqlWsContract.MODULE_TIMER })
        assertFalse(commandKeys.any { (module, _) -> module == AqlWsContract.MODULE_LIGHT })
        assertFalse(commandKeys.any { (module, _) -> module == AqlWsContract.MODULE_COOLING })

        repository.close()
    }

    private fun snapshot(deviceUid: DeviceUid): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = deviceUid),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.20",
                wsPort = 80
            )
        )
    }

    private fun messageEvent(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject
    ): AqlWsEvent.Message {
        return AqlWsEvent.Message(
            deviceUid = deviceUid,
            parsed = AqlWsIncomingMessage.Response(
                id = "fixture-$action",
                type = AqlWsContract.TYPE_RESPONSE,
                module = AqlWsContract.MODULE_DEVICE,
                action = action,
                data = data,
                ok = true,
                statusCode = 200
            )
        )
    }

    private class RecordingWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        private val sent = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            if (message is AqlWsOutgoingMessage.Command) sent += message
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(code = 1000, reason = "closed")
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }

        fun commandKeys(): List<Pair<String, String>> =
            sent.map { message -> message.module to message.action }
    }
}
