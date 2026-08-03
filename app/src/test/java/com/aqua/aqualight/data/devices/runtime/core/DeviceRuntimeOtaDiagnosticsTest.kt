package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeOtaDiagnosticsTest {

    @Test
    fun `ota start report redacts url and digest while retaining contract evidence`() {
        val deviceUid = DeviceUid("AQL-DIAGNOSTIC")
        val generation = DeviceRuntimeConnectionGeneration(3L)
        val outgoing = AqlWsOutgoingMessage.Command(
            module = "firmware",
            action = "ota.start",
            data = JSONObject()
                .put("url", "https://example.invalid/private/path/firmware.bin?token=secret")
                .put("version", "1.0.1")
                .put("sha256", "a".repeat(64))
                .put("expectedSize", 1024)
                .put("applyNow", true)
                .put("allowInsecureHttp", false)
                .put("productKey", "LIGHT_WRGB_PRO_ELITE")
                .put("productId", "com.aqualight.light.wrgb_pro_elite")
                .put("model", "wrgb_pro_elite_120")
                .put("hardwareRevision", "2.0")
        )

        DeviceRuntimeOtaDiagnostics.recordOutgoing(deviceUid, generation, outgoing)
        DeviceRuntimeOtaDiagnostics.recordIncoming(
            deviceUid,
            generation,
            AqlWsIncomingMessage.Response(
                id = outgoing.id,
                type = "res",
                module = outgoing.module,
                action = outgoing.action,
                data = JSONObject()
                    .put("operation", "otaStart")
                    .put("request", JSONObject().put("model", "wrgb_pro_elite_120"))
                    .put("ota", JSONObject().put("phase", "starting")),
                ok = true,
                statusCode = 202
            )
        )
        DeviceRuntimeOtaDiagnostics.recordParserFailure(
            deviceUid,
            generation,
            outgoing.id,
            IllegalArgumentException("request keys differ")
        )

        val report = DeviceRuntimeOtaDiagnostics.report(deviceUid.value)

        assertTrue(report.contains("url=https:firmware.bin"))
        assertTrue(report.contains("sha256=aaaaaaaa…aaaaaaaa"))
        assertTrue(report.contains("request.model=wrgb_pro_elite_120"))
        assertTrue(report.contains("parserError=request keys differ"))
        assertFalse(report.contains("token=secret"))
        assertFalse(report.contains("private/path"))
        assertFalse(report.contains("a".repeat(64)))
    }
}
