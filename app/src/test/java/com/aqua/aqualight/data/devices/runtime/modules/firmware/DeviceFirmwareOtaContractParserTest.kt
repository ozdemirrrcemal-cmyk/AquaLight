package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `progress parser rejects phase and active flag disagreement`() {
        val invalid = otaSnapshot().put("phase", "downloading").put("active", false)

        assertTrue(DeviceFirmwareStatusParser.parseOtaProgressEventExact(invalid).isFailure)
    }

    @Test
    fun `manifest parser keeps localized content inside signed payload`() {
        val parsed = DeviceFirmwareManifestParser.parse(manifestJson().toString()).getOrThrow()

        assertEquals("en", parsed.releaseNotes.defaultLocale)
        assertEquals(
            "Güvenli güncelleme",
            parsed.releaseNotes.locales.getValue("tr-TR").title
        )
    }

    @Test
    fun `manifest parser rejects incomplete localized content contract`() {
        val invalid = manifestJson().apply {
            getJSONObject("releaseNotes")
                .getJSONObject("locales")
                .getJSONObject("tr-TR")
                .remove("warnings")
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
        val compatibility = JSONObject()
            .put("productKey", "DOSING_DOSE_PRO_2")
            .put("productId", "com.aqualight.dosing.dose_pro_2")
            .put("family", "dosing")
            .put("line", "dose_pro")
            .put("model", "dose_pro_2")
            .put("hardwareRevision", "2.0")
        val firmware = JSONObject()
            .put("filename", filename)
            .put(
                "url",
                DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX + "v2.0.0/$filename"
            )
            .put("sha256", "a".repeat(64))
            .put("size", 1_048_576)
            .put("format", "bin")
            .put("otaSlotCompatible", true)
        val localContent = JSONObject()
            .put("title", "Safe update")
            .put("summary", "Reliability improvements.")
            .put("changes", JSONArray(listOf("Improved calibration checks.")))
            .put("warnings", JSONArray())
        val trContent = JSONObject()
            .put("title", "Güvenli güncelleme")
            .put("summary", "Güvenilirlik iyileştirmeleri.")
            .put("changes", JSONArray(listOf("Kalibrasyon kontrolleri geliştirildi.")))
            .put("warnings", JSONArray())
        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", "AquaLight")
            .put("channel", "stable")
            .put("version", "2.0.0")
            .put("tag", "v2.0.0")
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", "2026-07-30T00:00:00Z")
            .put(
                "releaseNotes",
                JSONObject()
                    .put("defaultLocale", "en")
                    .put("mandatory", false)
                    .put(
                        "locales",
                        JSONObject().put("en", localContent).put("tr-TR", trContent)
                    )
            )
            .put(
                "artifacts",
                JSONArray().put(
                    JSONObject()
                        .put("env", env)
                        .put("product", product)
                        .put("compatibility", compatibility)
                        .put("firmware", firmware)
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
}
