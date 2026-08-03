package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaStaleTerminalStatusCharacterizationTest {

    @Test
    fun `matching terminal failure remains a firmware download failure`() = runTest {
        val gateway = StatusGateway()
        val (coordinator, plan) = coordinatorWithPlan(gateway)
        gateway.statusData = otaStatusData(
            failedSnapshot(
                targetVersion = plan.targetVersion,
                sha256Expected = plan.sha256
            )
        )

        val result = coordinator.requestStatus(DEVICE_UID)

        assertTrue(result.isSuccess)
        val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
        assertEquals(DeviceOtaFailureReason.DOWNLOAD_FAILED, failed.failure.reason)
        assertEquals(FIRMWARE_DOWNLOAD_ERROR, failed.failure.diagnosticMessage)
        coordinator.close()
    }

    @Test
    fun `previous target terminal snapshot reproduces the protocol mismatch`() = runTest {
        val gateway = StatusGateway()
        val (coordinator, plan) = coordinatorWithPlan(gateway)
        gateway.statusData = otaStatusData(
            failedSnapshot(
                targetVersion = PREVIOUS_TARGET_VERSION,
                sha256Expected = plan.sha256
            )
        )

        val result = coordinator.requestStatus(DEVICE_UID)

        assertFalse(result.isSuccess)
        assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, result.failure?.reason)
        assertEquals(TARGET_VERSION_MISMATCH, result.failure?.diagnosticMessage)
        val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
        assertEquals(TARGET_VERSION_MISMATCH, failed.failure.diagnosticMessage)
        coordinator.close()
    }

    @Test
    fun `same target terminal snapshot with stale digest reproduces protocol mismatch`() =
        runTest {
            val gateway = StatusGateway()
            val (coordinator, plan) = coordinatorWithPlan(gateway)
            gateway.statusData = otaStatusData(
                failedSnapshot(
                    targetVersion = plan.targetVersion,
                    sha256Expected = STALE_SHA256
                )
            )

            val result = coordinator.requestStatus(DEVICE_UID)

            assertFalse(result.isSuccess)
            assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, result.failure?.reason)
            assertEquals(SHA256_MISMATCH, result.failure?.diagnosticMessage)
            val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
            assertEquals(SHA256_MISMATCH, failed.failure.diagnosticMessage)
            coordinator.close()
        }

    private suspend fun coordinatorWithPlan(
        gateway: StatusGateway
    ): Pair<DeviceOtaCoordinator, PreparedDeviceFirmwareUpdate> {
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val available = coordinator.checkAvailability(
            deviceUid = DEVICE_UID,
            manifestUrl = MANIFEST_URL,
            applyNow = true
        ).getOrThrow() as DeviceOtaState.UpdateAvailable
        return coordinator to available.plan
    }

    private fun updater(gateway: StatusGateway): DeviceFirmwareUpdateRepository {
        val source = object : DeviceFirmwareManifestHttpSource() {
            override suspend fun load(url: String): Result<DeviceFirmwareManifest> =
                Result.success(manifest())
        }
        return DeviceFirmwareUpdateRepository(
            runtime = DeviceFirmwareRuntimeRepository(gateway),
            manifestSource = source,
            planner = DeviceFirmwareUpdatePlanner { listOf("tr-TR") }
        )
    }

    private fun manifest(): DeviceFirmwareManifest = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = TARGET_VERSION,
        tag = TARGET_TAG,
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = GENERATED_AT,
        platform = OFFICIAL_PLATFORM,
        releaseNotes = releaseNotes(),
        artifacts = listOf(artifact()),
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = PAYLOAD_HASH,
            value = "signed-value"
        )
    )

    private fun releaseNotes(): DeviceFirmwareReleaseNotes = DeviceFirmwareReleaseNotes(
        schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
        defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
        items = listOf(
            DeviceFirmwareReleaseNoteItem(
                tr = "OTA durum uzlaştırması inceleniyor.",
                en = "OTA status reconciliation is under investigation."
            )
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact = DeviceFirmwareManifestArtifact(
        env = ENVIRONMENT,
        product = DeviceFirmwareManifestProduct(
            productKey = PRODUCT_KEY,
            productId = PRODUCT_ID,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            family = FAMILY,
            line = LINE,
            model = MODEL,
            displayName = DISPLAY_NAME,
            skuCode = SKU_CODE,
            hardwareRevision = HARDWARE_REVISION,
            capabilities = CAPABILITIES,
            limits = LIMITS
        ),
        compatibility = DeviceFirmwareCompatibility(
            productKey = PRODUCT_KEY,
            productId = PRODUCT_ID,
            family = FAMILY,
            line = LINE,
            model = MODEL,
            hardwareRevision = HARDWARE_REVISION
        ),
        firmware = DeviceFirmwareAsset(
            version = TARGET_VERSION,
            filename = FIRMWARE_FILENAME,
            url = FIRMWARE_URL,
            sha256 = EXPECTED_SHA256,
            size = FIRMWARE_SIZE,
            format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
            otaSlotCompatible = true
        ),
        factory = null
    )

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = ""),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            productId = PRODUCT_ID,
            productKey = PRODUCT_KEY,
            family = DeviceFamily.DOSING,
            familyRaw = FAMILY,
            line = LINE,
            model = MODEL,
            displayName = DISPLAY_NAME,
            skuCode = SKU_CODE,
            hardwareRevision = HARDWARE_REVISION
        ),
        firmwareVersion = CURRENT_VERSION,
        apiVersion = API_VERSION,
        protocolVersion = PROTOCOL_VERSION,
        capabilities = CAPABILITIES,
        limits = LIMITS,
        runtimeMetadataGeneration = RUNTIME_GENERATION.value
    )

    private fun otaStatusData(snapshot: JSONObject): JSONObject = JSONObject()
        .put("operation", "otaStatus")
        .put("runtimeTransport", "websocket")
        .put("command", "firmware.ota.status")
        .put("binaryTransfer", "firmware-download")
        .put("progressEvent", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("completedEvent", DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
        .put("ota", snapshot)

    private fun failedSnapshot(
        targetVersion: String,
        sha256Expected: String
    ): JSONObject = JSONObject()
        .put("phase", "failed")
        .put("active", false)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", STARTED_AT_MILLIS)
        .put("finishedAtMs", FINISHED_AT_MILLIS)
        .put("bytesWritten", 0L)
        .put("contentLength", FIRMWARE_SIZE.toLong())
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", targetVersion)
        .put("sha256Expected", sha256Expected)
        .put("sha256Actual", "")
        .put("lastError", FIRMWARE_DOWNLOAD_ERROR)
        .put("lastErrorField", DeviceFirmwareRuntimeContract.ErrorField.STREAM)
        .put("urlScheme", "https")
        .put("httpStatus", HTTP_SERVICE_UNAVAILABLE)

    private inner class StatusGateway : DeviceRuntimeCommandGateway {
        var statusData: JSONObject = otaStatusData(
            failedSnapshot(TARGET_VERSION, EXPECTED_SHA256)
        )

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            require(command.action == DeviceFirmwareRuntimeContract.Action.OTA_STATUS)
            val response = AqlWsIncomingMessage.Response(
                id = RESPONSE_ID,
                type = "res",
                module = command.module,
                action = command.action,
                data = JSONObject(statusData.toString()),
                ok = true,
                statusCode = HTTP_OK
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = RESPONSE_ID,
                generation = RUNTIME_GENERATION,
                statusCode = HTTP_OK,
                value = command.parseSuccess(response)
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-STALE-OTA-STATUS")
        val RUNTIME_GENERATION = DeviceRuntimeConnectionGeneration(7L)
        val CAPABILITIES = DeviceCapabilities(dosing = true, timeSync = true, ota = true)
        val LIMITS = DeviceLimits(dosingChannelCount = 2)
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )

        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "1.0.1"
        const val PREVIOUS_TARGET_VERSION = "0.9.9"
        const val TARGET_TAG = "v1.0.1"
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val FAMILY = "dosing"
        const val LINE = "dose_pro"
        const val MODEL = "dose_pro_2"
        const val DISPLAY_NAME = "Dose Pro 2"
        const val SKU_CODE = "AQL-D-DP2-GLB-BLK"
        const val HARDWARE_REVISION = "2.0"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val FIRMWARE_FILENAME = "AquaLight-dosing_dose_pro_2-v1.0.1-ota.bin"
        const val EXPECTED_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val STALE_SHA256 =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val PAYLOAD_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val FIRMWARE_SIZE = 1_048_576
        const val API_VERSION = "1"
        const val PROTOCOL_VERSION = "1"
        const val GENERATED_AT = "2026-08-03T00:00:00+00:00"
        const val STARTED_AT_MILLIS = 1L
        const val FINISHED_AT_MILLIS = 2L
        const val HTTP_OK = 200
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val RESPONSE_ID = "response-stale-ota-status"
        const val FIRMWARE_DOWNLOAD_ERROR = "previous OTA download failed"
        const val TARGET_VERSION_MISMATCH =
            "Firmware OTA targetVersion differs from the selected artifact."
        const val SHA256_MISMATCH =
            "Firmware OTA expected SHA256 differs from the selected artifact."
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/v1.0.1/manifest-stable.json"
        const val FIRMWARE_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/v1.0.1/AquaLight-dosing_dose_pro_2-v1.0.1-ota.bin"
    }
}
