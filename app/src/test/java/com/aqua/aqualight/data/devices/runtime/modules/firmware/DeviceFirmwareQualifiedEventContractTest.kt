package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.contract.AqlWsEventContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareQualifiedEventContractTest {

    @Test
    fun `ota payload metadata uses qualified firmware event names`() {
        val progress = AqlWsEventContract.Definition(
            module = AqlWsContract.MODULE_FIRMWARE,
            action = AqlWsEventContract.ACTION_OTA_PROGRESS
        ).qualifiedName
        val completed = AqlWsEventContract.Definition(
            module = AqlWsContract.MODULE_FIRMWARE,
            action = AqlWsEventContract.ACTION_OTA_COMPLETED
        ).qualifiedName

        assertEquals("firmware.ota.progress", progress)
        assertEquals("firmware.ota.completed", completed)
        assertEquals(progress, DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        assertEquals(completed, DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
    }

    @Test
    fun `accepted start parses firmware qualified event metadata`() {
        val result = DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(
            startAccepted(
                progressEvent = "firmware.ota.progress",
                completedEvent = "firmware.ota.completed"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(DeviceFirmwareOtaPhase.STARTING, result.getOrThrow().ota.phase)
    }

    @Test
    fun `accepted start rejects action-only event metadata`() {
        val result = DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(
            startAccepted(
                progressEvent = "ota.progress",
                completedEvent = "ota.completed"
            )
        )

        assertTrue(result.isFailure)
    }

    private fun startAccepted(
        progressEvent: String,
        completedEvent: String
    ): JSONObject = JSONObject()
        .put("operation", "otaStart")
        .put("accepted", true)
        .put("runtimeTransport", "websocket")
        .put("command", "firmware.ota.start")
        .put("binaryTransfer", "firmware-download")
        .put("event", progressEvent)
        .put("progressEvent", progressEvent)
        .put("completedEvent", completedEvent)
        .put("request", requestEcho())
        .put("ota", startingSnapshot())

    private fun requestEcho(): JSONObject = JSONObject()
        .put("urlScheme", "https")
        .put("version", TARGET_VERSION)
        .put("expectedSize", EXPECTED_SIZE)
        .put("applyNow", true)
        .put("allowInsecureHttp", false)
        .put("productKey", PRODUCT_KEY)
        .put("productId", PRODUCT_ID)
        .put("model", MODEL)
        .put("hardwareRevision", HARDWARE_REVISION)

    private fun startingSnapshot(): JSONObject = JSONObject()
        .put("phase", "starting")
        .put("active", true)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 1L)
        .put("finishedAtMs", 0L)
        .put("bytesWritten", 0L)
        .put("contentLength", EXPECTED_SIZE.toLong())
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", TARGET_VERSION)
        .put("sha256Expected", SHA256)
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "https")
        .put("httpStatus", 0)

    private companion object {
        const val TARGET_VERSION = "1.0.1"
        const val EXPECTED_SIZE = 1_660_816
        const val PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
        const val PRODUCT_ID = "com.aqualight.light.wrgb_pro_elite"
        const val MODEL = "wrgb_pro_elite_120"
        const val HARDWARE_REVISION = "2.0"
        const val SHA256 =
            "1c38cde21c38cde21c38cde21c38cde21c38cde21c38cde21c38cde2fc89bd3c"
    }
}
