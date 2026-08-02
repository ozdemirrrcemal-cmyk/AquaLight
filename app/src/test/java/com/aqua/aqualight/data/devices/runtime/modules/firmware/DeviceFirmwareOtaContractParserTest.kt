package com.aqua.aqualight.data.devices.runtime.modules.firmware

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
class DeviceFirmwareOtaContractParserTest {

    @Test
    fun `firmware publisher v2 fixture parses without compatibility normalization`() {
        val raw = checkNotNull(
            javaClass.getResource("/ota/firmware-channel-manifest-v2.json")
        ).readText()

        val manifest = DeviceFirmwareManifestParser.parse(raw).getOrThrow()
        val artifact = manifest.artifacts.single()

        assertEquals("light_wrgb_pro_elite", artifact.env)
        assertEquals("1.0.1", artifact.release.version)
        assertEquals("WRGB Pro Elite 120", artifact.product.displayName)
        assertEquals(4, artifact.product.limits.lightChannelCount)
        assertTrue(artifact.product.capabilities.ota)
        assertEquals("esp32-app-bin", artifact.firmware.format)
        assertEquals(
            "AquaLight-light_wrgb_pro_elite-v1.0.1-factory.zip",
            artifact.factory?.filename
        )
    }

    @Test
    fun `android canonical payload matches firmware python signer bytes`() {
        val raw = checkNotNull(
            javaClass.getResource("/ota/firmware-channel-manifest-v2.json")
        ).readText()
        val payload = DeviceFirmwareManifestSignatureVerifier.canonicalManifestPayload(
            JSONObject(raw)
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString(separator = "") { value ->
                "%02x".format(value.toInt() and 0xff)
            }

        assertEquals(FIRMWARE_PYTHON_PAYLOAD_HASH, digest)
    }

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
    fun `manifest parser keeps localized content inside signed payload`() {
        val parsed = DeviceFirmwareManifestParser.parse(manifestJson().toString()).getOrThrow()

        val releaseNotes = parsed.artifacts.single().release.releaseNotes
        assertEquals("tr", releaseNotes.defaultLocale)
        assertEquals(
            listOf("Kalibrasyon kontrolleri geliştirildi."),
            releaseNotes.locales.getValue("tr").changes
        )
    }

    @Test
    fun `manifest parser rejects incomplete localized content contract`() {
        val invalid = manifestJson().apply {
            getJSONArray("artifacts")
                .getJSONObject(0)
                .getJSONObject("release")
                .getJSONObject("releaseNotes")
                .getJSONArray("items")
                .getJSONObject(0)
                .remove("en")
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
        val product = JSONObject()
            .put("productKey", "DOSING_DOSE_PRO_2")
            .put("productId", "com.aqualight.dosing.dose_pro_2")
            .put("brand", "AquaLight")
            .put("family", "dosing")
            .put("line", "dose_pro")
            .put("model", "dose_pro_2")
            .put("displayName", "Dose Pro 2")
            .put("skuCode", "AQL-D-DP2-GLB-BLK")
            .put("hardwareRevision", "2.0")
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
        val compatibility = JSONObject()
            .put("productKey", "DOSING_DOSE_PRO_2")
            .put("productId", "com.aqualight.dosing.dose_pro_2")
            .put("family", "dosing")
            .put("line", "dose_pro")
            .put("model", "dose_pro_2")
            .put("hardwareRevision", "2.0")
        val platform = JSONObject()
            .put("framework", DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK)
            .put("core", "3.3.9")
            .put("platform", "pioarduino/platform-espressif32#55.03.39")
            .put(
                "partitionTable",
                DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PARTITION_TABLE
            )
            .put(
                "normalOtaAssetType",
                DeviceFirmwareRuntimeContract.Manifest.PLATFORM_OTA_ASSET_TYPE
            )
        val release = JSONObject()
            .put("version", "2.0.0")
            .put("tag", "v2.0.0")
            .put("generatedAt", "2026-07-30T00:00:00Z")
            .put(
                "releaseNotes",
                JSONObject()
                    .put(
                        "schema",
                        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA
                    )
                    .put("defaultLocale", "tr")
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("tr", "Kalibrasyon kontrolleri geliştirildi.")
                                .put("en", "Calibration checks were improved.")
                        )
                    )
            )
        val firmware = JSONObject()
            .put("filename", filename)
            .put(
                "url",
                DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX + "v2.0.0/$filename"
            )
            .put("sha256", "a".repeat(64))
            .put("size", 1_048_576)
            .put("format", DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT)
            .put("otaSlotCompatible", true)
        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", "AquaLight")
            .put("channel", "stable")
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", "2026-07-30T00:00:00Z")
            .put(
                "artifacts",
                JSONArray().put(
                    JSONObject()
                        .put("env", env)
                        .put("product", product)
                        .put("compatibility", compatibility)
                        .put("platform", platform)
                        .put("release", release)
                        .put("firmware", firmware)
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

    private companion object {
        const val FIRMWARE_PYTHON_PAYLOAD_HASH =
            "acb62ebf6bb7e90bde2ff1afae183b2622a95da9c95b3c752c6179ccae3b1fe6"
    }
}
