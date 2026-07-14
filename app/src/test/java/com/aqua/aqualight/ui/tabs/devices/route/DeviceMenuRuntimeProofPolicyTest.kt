package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuRuntimeProofPolicyTest {

    private val requestedUid = DeviceUid("AQL-WPE-336172")
    private val requestId = "android-menu-proof-1"

    @Test
    fun `matching successful network status response proves current liveness`() {
        assertTrue(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = networkStatusResponse(
                    deviceUid = requestedUid,
                    id = requestId,
                    ok = true
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
    }

    @Test
    fun `current firmware response without echoed command fields proves liveness`() {
        assertTrue(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = responseEvent(
                    deviceUid = requestedUid,
                    id = requestId,
                    ok = true,
                    module = "",
                    action = ""
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
    }

    @Test
    fun `cached or unrelated response cannot open device menu`() {
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = networkStatusResponse(
                    deviceUid = requestedUid,
                    id = "older-request",
                    ok = true
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = networkStatusResponse(
                    deviceUid = DeviceUid("AQL-OTHER"),
                    id = requestId,
                    ok = true
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
    }

    @Test
    fun `failed or explicitly contradictory response cannot open device menu`() {
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = networkStatusResponse(
                    deviceUid = requestedUid,
                    id = requestId,
                    ok = false
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = responseEvent(
                    deviceUid = requestedUid,
                    id = requestId,
                    ok = true,
                    module = AqlWsContract.MODULE_DEVICE,
                    action = AqlWsContract.ACTION_DEVICE_STATUS_GET
                ),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
    }

    @Test
    fun `socket lifecycle events are not proof of device liveness`() {
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = AqlWsEvent.Opened(deviceUid = requestedUid),
                requestedDeviceUid = requestedUid,
                expectedRequestId = requestId
            )
        )
    }

    private fun networkStatusResponse(
        deviceUid: DeviceUid,
        id: String,
        ok: Boolean
    ): AqlWsEvent = responseEvent(
        deviceUid = deviceUid,
        id = id,
        ok = ok,
        module = AqlWsContract.MODULE_NETWORK,
        action = AqlWsContract.ACTION_NETWORK_STATUS_GET
    )

    private fun responseEvent(
        deviceUid: DeviceUid,
        id: String,
        ok: Boolean,
        module: String,
        action: String
    ): AqlWsEvent {
        // Android's JVM stub throws from JSONObject.put(). The policy only consumes the typed
        // response fields, so an empty object keeps this a plain unit test without Robolectric.
        val json = JSONObject()

        return AqlWsEvent.Message(
            deviceUid = deviceUid,
            parsed = AqlWsIncomingMessage.Response(
                raw = "",
                id = id,
                type = AqlWsContract.TYPE_RESPONSE,
                json = json,
                ok = ok,
                module = module,
                action = action,
                statusCode = if (ok) 200 else 500
            )
        )
    }
}
