package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareOtaStartCompatibilityTest {

    @Test
    fun `firmware 1_0_0 response receives model from the verified command payload`() {
        val payload = otaPayload()
        val normalized = DeviceFirmwareOtaStartCompatibility.normalizeAcceptedResponse(
            data = startAcceptedResponse(),
            payload = payload
        )

        val parsed = DeviceFirmwareStatusParser
            .parseOtaStartAcceptedExact(normalized)
            .getOrThrow()

        assertEquals(payload.model, parsed.request.model)
        assertEquals(payload.productKey, parsed.request.productKey)
        assertEquals(payload.productId, parsed.request.productId)
        assertEquals(payload.hardwareRevision, parsed.request.hardwareRevision)
        assertEquals(DeviceFirmwareOtaPhase.STARTING, parsed.ota.phase)
    }

    @Test
    fun `legacy echo mismatch fails closed instead of receiving a model`() {
        val invalid = startAcceptedResponse().apply {
            getJSONObject("request").put("productId", "com.aqualight.other")
        }

        assertTrue(
            runCatching {
                DeviceFirmwareOtaStartCompatibility.normalizeAcceptedResponse(
                    data = invalid,
                    payload = otaPayload()
                )
            }.isFailure
        )
    }

    @Test
    fun `unknown legacy response field remains rejected by the exact parser`() {
        val invalid = startAcceptedResponse().apply {
            getJSONObject("request").put("unexpected", true)
        }
        val normalized = DeviceFirmwareOtaStartCompatibility.normalizeAcceptedResponse(
            data = invalid,
            payload = otaPayload()
        )

        assertFalse(normalized.getJSONObject("request").has("model"))
        assertTrue(DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(normalized).isFailure)
    }

    private fun otaPayload() = DeviceFirmwareOtaStartPayload(
        url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "v1.0.1/AquaLight-light_wrgb_pro_elite-v1.0.1-ota.bin",
        version = "1.0.1",
        sha256 = "a".repeat(64),
        expectedSize = 1_048_576,
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        hardwareRevision = "2.0"
    )

    private fun startAcceptedResponse(): JSONObject = JSONObject()
        .put("operation", "otaStart")
        .put("accepted", true)
        .put("runtimeTransport", "websocket")
        .put("command", "firmware.ota.start")
        .put("binaryTransfer", "firmware-download")
        .put("event", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("progressEvent", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("completedEvent", DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
        .put(
            "request",
            JSONObject()
                .put("urlScheme", "https")
                .put("version", "1.0.1")
                .put("expectedSize", 1_048_576)
                .put("applyNow", true)
                .put("allowInsecureHttp", false)
                .put("productKey", "LIGHT_WRGB_PRO_ELITE")
                .put("productId", "com.aqualight.light.wrgb_pro_elite")
                .put("hardwareRevision", "2.0")
        )
        .put("ota", startingSnapshot())

    private fun startingSnapshot(): JSONObject = JSONObject()
        .put("phase", "starting")
        .put("active", true)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 1L)
        .put("finishedAtMs", 0L)
        .put("bytesWritten", 0L)
        .put("contentLength", 1_048_576L)
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", "1.0.1")
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "https")
        .put("httpStatus", 0)
}
