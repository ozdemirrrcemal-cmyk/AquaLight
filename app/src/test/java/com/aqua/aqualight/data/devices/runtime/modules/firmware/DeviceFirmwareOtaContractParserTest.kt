package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `manifest parser mirrors exact firmware main release contract`() {
        val parsed = DeviceFirmwareManifestParser.parse(manifestJson().toString()).getOrThrow()
        val artifact = parsed.artifacts.single()

        assertEquals(DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE, parsed.platform.core)
        assertEquals(DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA, parsed.releaseNotes.schema)
        assertEquals("tr", parsed.releaseNotes.defaultLocale)
        assertEquals("Güvenli güncelleme", parsed.releaseNotes.items.single().tr)
        assertEquals("Safe update", parsed.releaseNotes.items.single().en)
        assertEquals("2.0.0", artifact.firmware.version)
        assertEquals(DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT, artifact.firmware.format)
        assertTrue(artifact.product.capabilities.dosing)
        assertEquals(2, artifact.product.limits.dosingChannelCount)
        assertNull(artifact.factory)
    }

    @Test
    fun `manifest parser rejects Android invented localized object contract`() {
        val invalid = manifestJson().apply {
            put(
                "releaseNotes",
                JSONObject()
                    .put("defaultLocale", "tr-TR")
                    .put("mandatory", false)
                    .put("locales", JSONObject())
            )
        }

        assertTrue(DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure)
    }

    @Test
    fun `manifest parser rejects omitted firmware owned product capabilities`() {
        val invalid = manifestJson().apply {
            artifactJson().getJSONObject("product").remove("capabilities")
        }

        assertTrue(DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure)
    }

    @Test
    fun `manifest parser rejects factory encoded as OTA firmware asset`() {
        val invalid = manifestJson().apply {
            artifactJson().put(
                "factory",
                JSONObject()
                    .put("version", "2.0.0")
                    .put("filename", "AquaLight-dosing_dose_pro_2-v2.0.0-factory.zip")
                    .put(
                        "url",
                        DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                            "v2.0.0/AquaLight-dosing_dose_pro_2-v2.0.0-factory.zip"
                    )
                    .put("sha256", "c".repeat(64))
                    .put("size", 2_048)
                    .put("format", "esp32-app-bin")
                    .put("otaSlotCompatible", false)
            )
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
        val filename = "AquaLight-$env-v2.0.0-ota.bin"
        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
            .put("channel", DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL)
            .put("version", "2.0.0")
            .put("tag", "v2.0.0")
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", "2026-08-03T00:00:00+00:00")
            .put("platform", platformJson())
            .put(
                "releaseNotes",
                JSONObject()
                    .put("schema", DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA)
                    .put("defaultLocale", DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE)
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("tr", "Güvenli güncelleme")
                                .put("en", "Safe update")
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
                                        "v2.0.0/$filename"
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

    private fun JSONObject.artifactJson(): JSONObject =
        getJSONArray("artifacts").getJSONObject(0)

    private fun platformJson(): JSONObject = JSONObject()
        .put("framework", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK)
        .put("core", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE)
        .put("platform", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE)
        .put("partitionTable", DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE)
        .put("normalOtaAssetType", DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE)

    private fun productJson(): JSONObject = JSONObject()
        .put("productKey", "DOSING_DOSE_PRO_2")
        .put("productId", "com.aqualight.dosing.dose_pro_2")
        .put("brand", "AquaLight")
        .put("family", "dosing")
        .put("line", "dose_pro")
        .put("model", "dose_pro_2")
        .put("displayName", "AquaLight Dose Pro 2")
        .put("skuCode", "AQL-D-DP2-GLB-BLK")
        .put("hardwareRevision", "2.0")
        .put("capabilities", capabilitiesJson())
        .put("limits", limitsJson())

    private fun compatibilityJson(): JSONObject = JSONObject()
        .put("productKey", "DOSING_DOSE_PRO_2")
        .put("productId", "com.aqualight.dosing.dose_pro_2")
        .put("family", "dosing")
        .put("line", "dose_pro")
        .put("model", "dose_pro_2")
        .put("hardwareRevision", "2.0")

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
