package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareReadParserTest {
    @Test
    fun `parses exact read only firmware status`() {
        val parsed = DeviceFirmwareReadParser.parseStatus(statusJson())

        assertEquals("1.0.0", parsed.version)
        assertEquals("light_prime", parsed.model)
        assertEquals(DeviceFirmwareOtaPhase.IDLE, parsed.ota.phase)
    }

    @Test
    fun `rejects writable runtime declaration`() {
        val invalid = statusJson().apply {
            getJSONObject("runtime").put("readOnly", false)
        }

        assertTrue(runCatching { DeviceFirmwareReadParser.parseStatus(invalid) }.isFailure)
    }

    private fun statusJson(): JSONObject = JSONObject()
        .put("version", "1.0.0")
        .put("build", "2026.08.01")
        .put("hardwareRevision", "1.0")
        .put("sdkVersion", "5.4.0")
        .put("uptimeMs", 10_000L)
        .put(
            "product",
            JSONObject()
                .put("productKey", "LIGHT_LIGHT_PRIME")
                .put("productId", "com.aqualight.light.light_prime")
                .put("family", "light")
                .put("model", "light_prime")
                .put("displayName", "Light Prime")
                .put("skuCode", "AQL-L-LP-GLB-BLK")
        )
        .put(
            "flash",
            JSONObject()
                .put("chipSize", 4_194_304L)
                .put("sketchSize", 1_000_000L)
                .put("freeSketchSpace", 1_500_000L)
        )
        .put(
            "partition",
            JSONObject()
                .put("running", JSONObject().put("present", false))
                .put("boot", JSONObject().put("present", false))
                .put("nextUpdate", JSONObject().put("present", false))
                .put("bootMatchesRunning", false)
                .put("runningState", "undefined")
                .put("runningStateCode", 0)
                .put("stateReadOk", false)
                .put("stateReadError", 0)
        )
        .put(
            "ota",
            JSONObject()
                .put("supported", true)
                .put("transport", "websocket-control")
                .put("binaryTransfer", "firmware-download")
                .put("progressEvent", "ota.progress")
                .put("completedEvent", "ota.completed")
                .put("startCommand", "firmware.ota.start")
                .put("statusCommand", "firmware.ota.status")
                .put("status", idleOta())
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsSchema", "aql.ws.v1")
                .put("wsProtocolVersion", 1)
                .put("readOnly", true)
        )

    private fun idleOta(): JSONObject = JSONObject()
        .put("phase", "idle")
        .put("active", false)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 0L)
        .put("finishedAtMs", 0L)
        .put("bytesWritten", 0L)
        .put("contentLength", 0L)
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", "")
        .put("sha256Expected", "")
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "")
        .put("httpStatus", 0)
}
