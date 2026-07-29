package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataLivePipelineTest {

    @Test
    fun `authenticated responses publish one catalog validated dosing generation and exact routes`() {
        val transport = RecordingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val initial = snapshot()
        repository.connect(initial).getOrThrow()
        transport.emit(AqlWsEvent.Authenticated(DEVICE_UID))

        val commands = transport.commands().associateBy { it.action }
        assertEquals(EXPECTED_ACTIONS, commands.keys)

        val statusUpdate = repository.processMetadataResponse(
            DEVICE_UID,
            response(commands.getValue("status.get"), statusJson())
        )
        val identityUpdate = repository.processMetadataResponse(
            DEVICE_UID,
            response(commands.getValue("identity.get"), identityJson())
        )
        val readyUpdate = repository.processMetadataResponse(
            DEVICE_UID,
            response(commands.getValue("capabilities.get"), capabilitiesJson())
        )

        assertTrue(statusUpdate is DeviceRuntimeMetadataUpdate.Collecting)
        assertTrue(identityUpdate is DeviceRuntimeMetadataUpdate.Collecting)
        val ready = (readyUpdate as DeviceRuntimeMetadataUpdate.Ready).state
        assertTrue(AqlCommercialDeviceCatalog.validate(ready.metadata) is AqlCommercialCatalogValidation.Valid)

        val projected = DeviceRuntimeMetadataProjector.applyReady(initial, ready)
        assertTrue(projected.hasValidatedRuntimeMetadata)
        assertEquals(2, projected.limits.dosingChannelCount)
        assertFalse(projected.modules.contains("timerApi"))
        assertTrue(projected.modules.contains("timerEngine"))
        assertTrue(projected.modules.contains("dosing"))

        val root = projected.toDeviceRootSnapshot()
        assertEquals(DeviceRootCatalogState.VALID, root.catalogState)
        assertTrue(DeviceRootRoute.DOSING_CHANNELS in root.allowedRoutes)
        assertTrue(DeviceRootRoute.DOSING_CALIBRATION in root.allowedRoutes)
        assertTrue(DeviceRootRoute.DOSING_SCHEDULES in root.allowedRoutes)
        assertFalse(DeviceRootRoute.TIMER_CHANNELS in root.allowedRoutes)
        assertFalse(DeviceRootRoute.TIMER_SCHEDULES in root.allowedRoutes)
        repository.close()
    }

    @Test
    fun `catalog module mismatch rejects generation and closes only current socket`() {
        val transport = RecordingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        repository.connect(snapshot()).getOrThrow()
        transport.emit(AqlWsEvent.Authenticated(DEVICE_UID))
        val commands = transport.commands().associateBy { it.action }

        repository.processMetadataResponse(
            DEVICE_UID,
            response(commands.getValue("identity.get"), identityJson())
        )
        repository.processMetadataResponse(
            DEVICE_UID,
            response(commands.getValue("capabilities.get"), capabilitiesJson())
        )
        val rejected = repository.processMetadataResponse(
            DEVICE_UID,
            response(
                commands.getValue("status.get"),
                statusJson().apply {
                    getJSONObject("modules").put("timerApi", true)
                }
            )
        )

        assertTrue(rejected is DeviceRuntimeMetadataUpdate.Rejected)
        assertEquals("metadata bootstrap failed", transport.lastDisconnectReason)
        assertFalse(repository.metadataBootstrapCoordinator.currentState(DEVICE_UID)!!.publishedMetadata != null)
        repository.close()
    }

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = "My Dose Pro"),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20", wsPort = 80)
    )

    private fun response(
        command: AqlWsOutgoingMessage.Command,
        data: JSONObject
    ): AqlWsIncomingMessage.Response = AqlWsIncomingMessage.Response(
        id = command.id,
        type = "res",
        module = command.module,
        action = command.action,
        data = data,
        ok = true,
        statusCode = 200
    )

    private fun identityJson(): JSONObject = JSONObject()
        .put("productKey", "DOSING_DOSE_PRO_2")
        .put("productId", "com.aqualight.dosing.dose_pro_2")
        .put("setupCode", "DP2")
        .put("deviceUid", DEVICE_UID.value)
        .put("shortId", "DP2001")
        .put("serialNumber", "AQL-D-DP2-000001")
        .put("firmwareSerial", "FW-DP2-000001")
        .put("macAddress", "AA:BB:CC:DD:EE:01")
        .put("brand", "AquaLight")
        .put("family", "dosing")
        .put("line", "dose_pro")
        .put("model", "dose_pro_2")
        .put("displayName", "Dose Pro 2")
        .put("skuId", "com.aqualight.dosing.dose_pro_2.global.black")
        .put("skuCode", "AQL-D-DP2-GLB-BLK")
        .put("firmwareVersion", "6.0.0")
        .put("hardwareRevision", "2.0")
        .put("apiVersion", 1)
        .put("protocolVersion", 1)
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsSchema", "aql.ws.v1")
                .put("wsPath", "/aql/v1/ws")
                .put("wsPort", 80)
                .put("wsProtocolVersion", 1)
        )

    private fun capabilitiesJson(): JSONObject = JSONObject()
        .put(
            "capabilities",
            JSONObject()
                .put("light", false)
                .put("manualLight", false)
                .put("lightProgram", false)
                .put("lightPresets", false)
                .put("lightSimulation", false)
                .put("fan", false)
                .put("cooling", false)
                .put("temperature", false)
                .put("standaloneTimer", false)
                .put("dosing", true)
                .put("timeSync", true)
                .put("ota", true)
        )
        .put(
            "limits",
            JSONObject()
                .put("lightChannelCount", 0)
                .put("fanOutputCount", 0)
                .put("temperatureSensorCount", 0)
                .put("timerChannelCount", 0)
                .put("dosingChannelCount", 2)
        )
        .put(
            "supportedFeatures",
            JSONArray(
                listOf(
                    "WIFI_SETUP",
                    "LAN_DISCOVERY",
                    "DOSING_CONTROL",
                    "DOSING_CALIBRATION",
                    "DOSING_RESERVOIR_TRACKING",
                    "DOSING_CHANNEL_DISPLAY_NAME",
                    "OTA_UPDATE"
                )
            )
        )
        .put(
            "supportedScreens",
            JSONArray(
                listOf(
                    "OVERVIEW",
                    "DOSING_CONTROL",
                    "DOSING_CHANNELS",
                    "DOSING_SCHEDULES",
                    "DOSING_CALIBRATION",
                    "DOSING_RESERVOIR",
                    "DOSING_MANUAL_RUN",
                    "ADVANCED"
                )
            )
        )

    private fun statusJson(): JSONObject = JSONObject()
        .put("state", "booted")
        .put("authenticated", true)
        .put("uptimeMs", 123_456L)
        .put(
            "product",
            JSONObject()
                .put("productKey", "DOSING_DOSE_PRO_2")
                .put("family", "dosing")
                .put("model", "dose_pro_2")
                .put("displayName", "Dose Pro 2")
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsSchema", "aql.ws.v1")
                .put("wsPath", "/aql/v1/ws")
                .put("wsPort", 80)
        )
        .put(
            "modules",
            JSONObject()
                .put("light", false)
                .put("cooling", false)
                .put("temperature", false)
                .put("timerApi", false)
                .put("timerEngine", true)
                .put("dosing", true)
                .put("network", true)
                .put("discovery", true)
                .put("firmware", true)
                .put("system", true)
        )

    private class RecordingWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        private val sent = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()
        var lastDisconnectReason: String? = null
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://${endpoint.ip}:${endpoint.wsPort}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            sent += command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            lastDisconnectReason = reason
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(reason = "closed")
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }

        fun commands(): List<AqlWsOutgoingMessage.Command> = sent.toList()
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-000001")
        val EXPECTED_ACTIONS = setOf("identity.get", "capabilities.get", "status.get")
    }
}
