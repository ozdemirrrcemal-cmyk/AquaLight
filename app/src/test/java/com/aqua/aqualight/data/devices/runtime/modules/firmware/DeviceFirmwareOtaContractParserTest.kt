package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
class DeviceFirmwareOtaContractParserTest {

    @Test
    fun `start acceptance requires and parses exact model echo`() {
        val parsed = DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(
            startAcceptedJson()
        ).getOrThrow()

        assertTrue(parsed.accepted)
        assertEquals("dose_pro_2", parsed.request?.model)
        assertEquals(DeviceFirmwareOtaPhase.STARTING, parsed.ota.phase)
    }

    @Test
    fun `start acceptance rejects missing model echo`() {
        val invalid = startAcceptedJson().apply {
            getJSONObject("request").remove("model")
        }

        assertTrue(DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(invalid).isFailure)
    }

    @Test
    fun `progress parser accepts exact firmware event envelope`() {
        val parsed = DeviceFirmwareStatusParser.parseOtaProgressEventExact(
            otaEventJson().put("phase", "writing").put("active", true)
        ).getOrThrow()

        assertEquals(DeviceFirmwareOtaPhase.WRITING, parsed.phase)
    }

    @Test
    fun `progress parser rejects phase and active flag disagreement`() {
        val invalid = otaEventJson().put("phase", "downloading").put("active", false)

        assertTrue(DeviceFirmwareStatusParser.parseOtaProgressEventExact(invalid).isFailure)
    }

    @Test
    fun `clear parser accepts compact previous state emitted by firmware`() {
        val parsed = DeviceFirmwareStatusParser.parseOtaClearResultExact(
            JSONObject()
                .put("operation", "otaClear")
                .put("cleared", true)
                .put("runtimeTransport", "websocket")
                .put("command", "firmware.ota.clear")
                .put(
                    "previous",
                    JSONObject()
                        .put("phase", "failed")
                        .put("restartRequired", false)
                        .put("restartScheduled", false)
                        .put("targetVersion", "2.0.0")
                        .put("lastError", "download failed")
                        .put("lastErrorField", "download")
                )
                .put("ota", otaSnapshot().put("targetVersion", "").put("sha256Expected", ""))
        ).getOrThrow()

        assertTrue(parsed.cleared)
        assertEquals(DeviceFirmwareOtaPhase.FAILED, parsed.previous.phase)
        assertEquals(DeviceFirmwareOtaPhase.IDLE, parsed.ota.phase)
    }

    @Test
    fun `manifest parser accepts the exact firmware release pipeline document`() {
        val parsed = DeviceFirmwareManifestParser.parse(manifestJson().toString()).getOrThrow()

        assertEquals(
            DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            parsed.platform.partitionTable
        )
        assertTrue(parsed.artifacts.single().product.capabilities.ota)
        assertEquals(2, parsed.artifacts.single().product.limits.dosingChannelCount)
        assertEquals(
            "AquaLight-dosing_dose_pro_2-v2.0.0-factory.zip",
            parsed.artifacts.single().factory?.filename
        )
        assertEquals("tr", parsed.releaseNotes.defaultLocale)
        assertEquals(
            listOf("Kalibrasyon kontrolleri geliştirildi."),
            parsed.releaseNotes.resolve(listOf("tr-TR")).changes
        )
        assertFalse(manifestJson().has("deviceName"))
    }

    @Test
    fun `manifest parser rejects a document without platform metadata`() {
        val invalid = manifestJson().apply { remove("platform") }

        assertTrue(DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure)
    }

    @Test
    fun `manifest parser rejects the removed localized-content contract`() {
        val invalid = manifestJson().apply {
            put(
                "releaseNotes",
                JSONObject()
                    .put("defaultLocale", "en")
                    .put("mandatory", false)
                    .put("locales", JSONObject())
            )
        }

        assertTrue(DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure)
    }

    @Test
    fun `factory asset cannot use firmware-only compatibility fields`() {
        val invalid = manifestJson().apply {
            artifactJson().getJSONObject("factory")
                .put("format", DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT)
                .put("otaSlotCompatible", true)
        }

        assertTrue(DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure)
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
                .put("productKey", "DOSING_DOSE_PRO_2")
                .put("productId", "com.aqualight.dosing.dose_pro_2")
                .put("model", "dose_pro_2")
                .put("hardwareRevision", "2.0")
        )
        .put("ota", otaSnapshot().put("phase", "starting").put("active", true))

    private fun otaSnapshot(): JSONObject = JSONObject()
        .put("phase", "idle")
        .put("active", false)
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

    private fun otaEventJson(): JSONObject = otaSnapshot()
        .put("completed", false)
        .put("success", false)
        .put("failed", false)
        .put("runtimeTransport", "websocket")
        .put("binaryTransfer", "firmware-download")

    private fun manifestJson(): JSONObject {
        val env = "dosing_dose_pro_2"
        val tag = "v2.0.0"
        val releaseUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX + "$tag/"
        val otaFilename = "AquaLight-$env-$tag-ota.bin"
        val factoryFilename = "AquaLight-$env-$tag-factory.zip"
        val product = JSONObject()
            .put("productKey", "DOSING_DOSE_PRO_2")
            .put("productId", "com.aqualight.dosing.dose_pro_2")
            .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
            .put("family", "dosing")
            .put("line", "dose_pro")
            .put("model", "dose_pro_2")
            .put("displayName", "Dose Pro 2")
            .put("skuCode", "AQL-D-DP2-GLB-BLK")
            .put("hardwareRevision", "2.0")
            .put("capabilities", capabilitiesJson())
            .put("limits", limitsJson())
        val compatibility = JSONObject()
            .put("productKey", "DOSING_DOSE_PRO_2")
            .put("productId", "com.aqualight.dosing.dose_pro_2")
            .put("family", "dosing")
            .put("line", "dose_pro")
            .put("model", "dose_pro_2")
            .put("hardwareRevision", "2.0")
        val firmware = JSONObject()
            .put("filename", otaFilename)
            .put("url", releaseUrl + otaFilename)
            .put("sha256", "a".repeat(64))
            .put("size", 1_048_576)
            .put("format", DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT)
            .put("otaSlotCompatible", true)
        val factory = JSONObject()
            .put("filename", factoryFilename)
            .put("url", releaseUrl + factoryFilename)
            .put("sha256", "c".repeat(64))
            .put("size", 2_097_152)
        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
            .put("channel", DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL)
            .put("version", "2.0.0")
            .put("tag", tag)
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", "2026-07-30T00:00:00+00:00")
            .put("platform", platformJson())
            .put("artifacts", JSONArray().put(
                JSONObject()
                    .put("env", env)
                    .put("product", product)
                    .put("compatibility", compatibility)
                    .put("firmware", firmware)
                    .put("factory", factory)
            ))
            .put(
                "releaseNotes",
                JSONObject()
                    .put("schema", DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA)
                    .put("defaultLocale", "tr")
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("tr", "Kalibrasyon kontrolleri geliştirildi.")
                                .put("en", "Calibration checks improved.")
                        )
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

    private fun JSONObject.artifactJson(): JSONObject =
        getJSONArray("artifacts").getJSONObject(0)

    private fun platformJson(): JSONObject = JSONObject()
        .put("framework", "arduino-esp32")
        .put("core", "3.3.9")
        .put("platform", "pioarduino/platform-espressif32#55.03.39")
        .put("partitionTable", DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE)
        .put(
            "normalOtaAssetType",
            DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )

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
}
