package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.data.devices.contract.AqlWsEventContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
class DeviceFirmwareOtaMainParityTest {

    @Test
    fun `qualified firmware event names remain distinct from websocket actions`() {
        assertEquals("firmware.ota.progress", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        assertEquals("firmware.ota.completed", DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
        assertEquals("ota.progress", AqlWsEventContract.ACTION_OTA_PROGRESS)
        assertEquals("ota.completed", AqlWsEventContract.ACTION_OTA_COMPLETED)
    }

    @Test
    fun `start response accepts firmware qualified names and rejects short actions`() {
        val valid = startAcceptedJson()

        assertTrue(DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(valid).isSuccess)

        val invalid = startAcceptedJson()
            .put("event", AqlWsEventContract.ACTION_OTA_PROGRESS)
            .put("progressEvent", AqlWsEventContract.ACTION_OTA_PROGRESS)
            .put("completedEvent", AqlWsEventContract.ACTION_OTA_COMPLETED)

        assertTrue(DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(invalid).isFailure)
    }

    @Test
    fun `failed snapshot preserves negative firmware http transport diagnostic`() {
        val parsed = DeviceFirmwareStatusParser.parseOtaSnapshotExact(
            failedSnapshot(httpStatus = -1, finishedAtMs = 0L)
        ).getOrThrow()

        assertEquals(DeviceFirmwareOtaPhase.FAILED, parsed.phase)
        assertEquals(-1, parsed.httpStatus)
        assertEquals(0L, parsed.finishedAtMs)
    }

    @Test
    fun `negative firmware http diagnostic maps to retryable download failure`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            DeviceFirmwareOtaSnapshot(
                phase = DeviceFirmwareOtaPhase.FAILED,
                phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
                active = false,
                completed = true,
                failed = true,
                targetVersion = "2.0.0",
                lastError = "connection refused",
                lastErrorField = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                httpStatus = -1
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_FAILED, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(-1, failure.httpStatus)
    }

    @Test
    fun `manifest release notes mirror firmware C0 control policy`() {
        val signedButNonC0Text = "Güvenli\u007Fsürüm"
        val parsed = DeviceFirmwareManifestParser.parse(
            manifestJson(signedButNonC0Text).toString()
        )

        assertTrue(parsed.isSuccess)
        assertEquals(signedButNonC0Text, parsed.getOrThrow().releaseNotes.items.single().tr)

        val c0Text = "Geçersiz\u001Fsürüm"
        assertFalse(DeviceFirmwareManifestParser.parse(manifestJson(c0Text).toString()).isSuccess)
    }

    private fun startAcceptedJson(): JSONObject = JSONObject()
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
                .put("version", "2.0.0")
                .put("expectedSize", 1_048_576)
                .put("applyNow", true)
                .put("allowInsecureHttp", false)
                .put("productKey", PRODUCT_KEY)
                .put("productId", PRODUCT_ID)
                .put("model", MODEL)
                .put("hardwareRevision", HARDWARE_REVISION)
        )
        .put("ota", activeSnapshot())

    private fun activeSnapshot(): JSONObject = JSONObject()
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
        .put("targetVersion", "2.0.0")
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "https")
        .put("httpStatus", 0)

    private fun failedSnapshot(httpStatus: Int, finishedAtMs: Long): JSONObject = JSONObject()
        .put("phase", "failed")
        .put("active", false)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 0L)
        .put("finishedAtMs", finishedAtMs)
        .put("bytesWritten", 0L)
        .put("contentLength", 1_048_576L)
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", "2.0.0")
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", "")
        .put("lastError", "connection refused")
        .put("lastErrorField", DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS)
        .put("urlScheme", "https")
        .put("httpStatus", httpStatus)

    private fun manifestJson(releaseNote: String): JSONObject {
        val env = "dosing_dose_pro_2"
        val tag = "v2.0.0"
        val filename = "AquaLight-$env-$tag-ota.bin"
        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
            .put("channel", DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL)
            .put("version", "2.0.0")
            .put("tag", tag)
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", "2026-08-04T00:00:00+00:00")
            .put(
                "platform",
                JSONObject()
                    .put("framework", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK)
                    .put("core", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE)
                    .put("platform", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE)
                    .put("partitionTable", DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE)
                    .put(
                        "normalOtaAssetType",
                        DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
                    )
            )
            .put(
                "releaseNotes",
                JSONObject()
                    .put("schema", DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA)
                    .put("defaultLocale", DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE)
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("tr", releaseNote)
                                .put("en", "Safe release")
                        )
                    )
            )
            .put(
                "artifacts",
                JSONArray().put(
                    JSONObject()
                        .put("env", env)
                        .put("product", productJson())
                        .put("compatibility", compatibilityJson())
                        .put(
                            "firmware",
                            JSONObject()
                                .put("version", "2.0.0")
                                .put("filename", filename)
                                .put(
                                    "url",
                                    DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                                        "$tag/$filename"
                                )
                                .put("sha256", "a".repeat(64))
                                .put("size", 1_048_576)
                                .put(
                                    "format",
                                    DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT
                                )
                                .put("otaSlotCompatible", true)
                        )
                        .put("factory", JSONObject.NULL)
                )
            )
            .put(
                "signature",
                JSONObject()
                    .put(
                        "scheme",
                        DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256
                    )
                    .put("keyId", "release-key-1")
                    .put("payloadHash", "b".repeat(64))
                    .put("value", "signed-value")
            )
    }

    private fun productJson(): JSONObject = JSONObject()
        .put("productKey", PRODUCT_KEY)
        .put("productId", PRODUCT_ID)
        .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
        .put("family", "dosing")
        .put("line", "dose_pro")
        .put("model", MODEL)
        .put("displayName", "AquaLight Dose Pro 2")
        .put("skuCode", "AQL-D-DP2-GLB-BLK")
        .put("hardwareRevision", HARDWARE_REVISION)
        .put("capabilities", capabilitiesJson())
        .put("limits", limitsJson())

    private fun compatibilityJson(): JSONObject = JSONObject()
        .put("productKey", PRODUCT_KEY)
        .put("productId", PRODUCT_ID)
        .put("family", "dosing")
        .put("line", "dose_pro")
        .put("model", MODEL)
        .put("hardwareRevision", HARDWARE_REVISION)

    private fun capabilitiesJson(): JSONObject = JSONObject()
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

    private fun limitsJson(): JSONObject = JSONObject()
        .put("lightChannelCount", 0)
        .put("fanOutputCount", 0)
        .put("temperatureSensorCount", 0)
        .put("timerChannelCount", 0)
        .put("dosingChannelCount", 2)

    private companion object {
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val MODEL = "dose_pro_2"
        const val HARDWARE_REVISION = "2.0"
    }
}
