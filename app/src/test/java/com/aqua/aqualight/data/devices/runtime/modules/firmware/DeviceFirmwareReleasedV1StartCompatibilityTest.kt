package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareReleasedV1StartCompatibilityTest {

    @Test
    fun `released 1_0_0 start echo without model remains upgradeable`() = runTest {
        val result = repository(legacyStartAccepted()).startUpdate(plan(CURRENT_VERSION_V1))

        assertTrue(result is DeviceRuntimeCommandOutcome.Success)
        val accepted = (result as DeviceRuntimeCommandOutcome.Success).value
        assertEquals(MODEL, accepted.request?.model)
        assertEquals(DeviceFirmwareOtaPhase.STARTING, accepted.ota.phase)
    }

    @Test
    fun `newer firmware cannot omit model from exact start echo`() = runTest {
        val result = repository(legacyStartAccepted()).startUpdate(plan(TARGET_VERSION))

        assertTrue(result is DeviceRuntimeCommandOutcome.ProtocolError)
        val failure = result as DeviceRuntimeCommandOutcome.ProtocolError
        assertTrue(failure.reason.contains("keys differ"))
    }

    @Test
    fun `released 1_0_0 compatibility rejects unrelated key drift`() = runTest {
        val response = legacyStartAccepted().apply {
            getJSONObject("request").put("unexpected", true)
        }

        val result = repository(response).startUpdate(plan(CURRENT_VERSION_V1))

        assertTrue(result is DeviceRuntimeCommandOutcome.ProtocolError)
    }

    private fun repository(response: JSONObject): DeviceFirmwareRuntimeRepository =
        DeviceFirmwareRuntimeRepository(StartResponseGateway(response))

    private fun plan(currentVersion: String): DeviceFirmwareUpdatePlan {
        val filename = "AquaLight-$ENV-v$TARGET_VERSION-ota.bin"
        val url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "v$TARGET_VERSION/$filename"
        val firmware = DeviceFirmwareAsset(
            version = TARGET_VERSION,
            filename = filename,
            url = url,
            sha256 = SHA256,
            size = FIRMWARE_SIZE,
            format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
            otaSlotCompatible = true
        )
        val payload = DeviceFirmwareOtaStartPayload(
            url = url,
            version = TARGET_VERSION,
            sha256 = SHA256,
            expectedSize = FIRMWARE_SIZE,
            productKey = PRODUCT_KEY,
            productId = PRODUCT_ID,
            model = MODEL,
            hardwareRevision = HARDWARE_REVISION
        )
        return DeviceFirmwareUpdatePlan(
            deviceUid = DEVICE_UID,
            currentVersion = currentVersion,
            targetVersion = TARGET_VERSION,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            env = ENV,
            productKey = PRODUCT_KEY,
            productId = PRODUCT_ID,
            model = MODEL,
            hardwareRevision = HARDWARE_REVISION,
            displayName = "WRGB Pro Elite 120",
            firmware = firmware,
            payload = payload
        )
    }

    private fun legacyStartAccepted(): JSONObject = JSONObject()
        .put("operation", "otaStart")
        .put("accepted", true)
        .put("runtimeTransport", "websocket")
        .put("command", "firmware.ota.start")
        .put("binaryTransfer", "firmware-download")
        .put("event", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("progressEvent", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("completedEvent", DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
        .put("request", legacyRequestEcho())
        .put("ota", startingSnapshot())

    private fun legacyRequestEcho(): JSONObject = JSONObject()
        .put("urlScheme", "https")
        .put("version", TARGET_VERSION)
        .put("expectedSize", FIRMWARE_SIZE)
        .put("applyNow", true)
        .put("allowInsecureHttp", false)
        .put("productKey", PRODUCT_KEY)
        .put("productId", PRODUCT_ID)
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
        .put("contentLength", FIRMWARE_SIZE.toLong())
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", TARGET_VERSION)
        .put("sha256Expected", SHA256)
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "https")
        .put("httpStatus", 0)

    private class StartResponseGateway(
        private val response: JSONObject
    ) : DeviceRuntimeCommandGateway {
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> = runCatching {
            command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = MESSAGE_ID,
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = JSONObject(response.toString()),
                    ok = true,
                    statusCode = HTTP_ACCEPTED
                )
            )
        }.fold(
            onSuccess = { value ->
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = MESSAGE_ID,
                    generation = GENERATION,
                    statusCode = HTTP_ACCEPTED,
                    value = value
                )
            },
            onFailure = { error ->
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = MESSAGE_ID,
                    generation = GENERATION,
                    reason = error.message.orEmpty()
                )
            }
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-WPE120-RELEASED-V1")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        const val CURRENT_VERSION_V1 = "1.0.0"
        const val TARGET_VERSION = "1.0.1"
        const val PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
        const val PRODUCT_ID = "com.aqualight.light.wrgb_pro_elite"
        const val MODEL = "wrgb_pro_elite_120"
        const val HARDWARE_REVISION = "2.0"
        const val ENV = "light_wrgb_pro_elite"
        const val FIRMWARE_SIZE = 1_048_576
        const val HTTP_ACCEPTED = 202
        const val MESSAGE_ID = "released-v1-start"
        const val SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
