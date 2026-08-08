package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaState
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "MagicNumber", "MaxLineLength")
class DeviceOtaCommercialHardeningTest {

    @Test
    fun `concurrent start requests dispatch exactly one ota command per device`() = runTest {
        val gateway = RecordingGateway(startDelayMillis = START_SEND_DELAY_MILLIS)
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, applyNow = true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan
        try {
            val successfulStarts = List(WORKER_COUNT) {
                async { coordinator.startUpdate(plan).isSuccess }
            }.awaitAll().count { it }

            assertEquals(1, successfulStarts)
            assertEquals(
                1,
                gateway.commands.count { action ->
                    action == DeviceFirmwareRuntimeContract.Action.OTA_START
                }
            )
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.InProgress)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `multi product manifest fails closed before artifact selection`() {
        val exact = artifact()
        val releaseManifest = manifest(
            artifacts = listOf(
                exact,
                exact.copy(env = "dosing_dose_pro_4")
            )
        )
        val planner = DeviceFirmwareUpdatePlanner { listOf("en") }

        val failure = planner.evaluateUpdate(snapshot(), releaseManifest).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("exactly one artifact"))
    }

    @Test
    fun `manifest parser rejects unknown fields at every signed object boundary`() {
        assertTrue(DeviceFirmwareManifestParser.parse(manifestJson().toString()).isSuccess)

        val invalidManifests = listOf(
            manifestJson().put("legacyRoot", true),
            manifestJson().apply { getJSONObject("platform").put("legacyPlatform", true) },
            manifestJson().apply { artifactJson().put("legacyArtifact", true) },
            manifestJson().apply {
                artifactJson().getJSONObject("product").put("legacyProduct", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("product")
                    .getJSONObject("capabilities")
                    .put("legacyCapability", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("product")
                    .getJSONObject("limits")
                    .put("legacyLimit", 1)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("compatibility").put("legacyCompatibility", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("firmware").put("legacyFirmware", true)
            },
            manifestJson().apply {
                getJSONObject("releaseNotes")
                    .getJSONArray("items")
                    .getJSONObject(0)
                    .put("de", "Nicht unterstützt")
            },
            manifestJson().apply {
                getJSONObject("signature").put("legacySignature", true)
            }
        )

        invalidManifests.forEachIndexed { index, invalid ->
            assertTrue(
                "Expected unknown-field manifest variant $index to fail closed.",
                DeviceFirmwareManifestParser.parse(invalid.toString()).isFailure
            )
        }
    }

    private fun updater(gateway: RecordingGateway): DeviceFirmwareUpdateRepository {
        val source = object : DeviceFirmwareManifestHttpSource() {
            override suspend fun load(url: String): Result<DeviceFirmwareManifest> =
                Result.success(manifest())
        }
        return DeviceFirmwareUpdateRepository(
            runtime = DeviceFirmwareRuntimeRepository(gateway),
            manifestSource = source,
            planner = DeviceFirmwareUpdatePlanner { listOf("en") }
        )
    }

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = ""),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = PRODUCT_ID,
            productKey = PRODUCT_KEY,
            family = DeviceFamily.DOSING,
            familyRaw = "dosing",
            line = "dose_pro",
            model = "dose_pro_2",
            displayName = "Dose Pro 2",
            skuCode = "AQL-D-DP2-GLB-BLK",
            hardwareRevision = "2.0"
        ),
        firmwareVersion = CURRENT_VERSION,
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DOSING_CAPABILITIES,
        limits = DOSING_LIMITS,
        runtimeMetadataGeneration = RUNTIME_GENERATION
    )

    private fun manifest(
        artifacts: List<DeviceFirmwareManifestArtifact> = listOf(artifact())
    ): DeviceFirmwareManifest = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = TARGET_VERSION,
        tag = RELEASE_TAG,
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = GENERATED_AT,
        platform = OFFICIAL_PLATFORM,
        releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
            defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Kalibrasyon doğrulaması geliştirildi.",
                    en = "Calibration validation improved."
                )
            )
        ),
        artifacts = artifacts,
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = "b".repeat(64),
            value = "signed-value"
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val filename = "AquaLight-$RELEASE_TAG-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = ENVIRONMENT,
            product = DeviceFirmwareManifestProduct(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                displayName = "AquaLight Dose Pro 2",
                skuCode = "AQL-D-DP2-GLB-BLK",
                hardwareRevision = "2.0",
                capabilities = DOSING_CAPABILITIES,
                limits = DOSING_LIMITS
            ),
            compatibility = DeviceFirmwareCompatibility(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                hardwareRevision = "2.0"
            ),
            firmware = DeviceFirmwareAsset(
                version = TARGET_VERSION,
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "$RELEASE_TAG/$filename",
                sha256 = "a".repeat(64),
                size = FIRMWARE_SIZE,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = null
        )
    }

    private fun manifestJson(): JSONObject {
        val releaseManifest = manifest()
        val releaseArtifact = releaseManifest.artifacts.single()
        return JSONObject()
            .put("schema", releaseManifest.schema)
            .put("brand", releaseManifest.brand)
            .put("channel", releaseManifest.channel)
            .put("version", releaseManifest.version)
            .put("tag", releaseManifest.tag)
            .put("releaseRepo", releaseManifest.releaseRepo)
            .put("generatedAt", releaseManifest.generatedAt)
            .put(
                "platform",
                JSONObject()
                    .put("framework", releaseManifest.platform.framework)
                    .put("core", releaseManifest.platform.core)
                    .put("platform", releaseManifest.platform.platform)
                    .put("partitionTable", releaseManifest.platform.partitionTable)
                    .put("normalOtaAssetType", releaseManifest.platform.normalOtaAssetType)
            )
            .put(
                "releaseNotes",
                JSONObject()
                    .put("schema", releaseManifest.releaseNotes.schema)
                    .put("defaultLocale", releaseManifest.releaseNotes.defaultLocale)
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("tr", releaseManifest.releaseNotes.items.single().tr)
                                .put("en", releaseManifest.releaseNotes.items.single().en)
                        )
                    )
            )
            .put("artifacts", JSONArray().put(releaseArtifact.toJson()))
            .put(
                "signature",
                JSONObject()
                    .put("scheme", releaseManifest.signature.scheme)
                    .put("keyId", releaseManifest.signature.keyId)
                    .put("payloadHash", releaseManifest.signature.payloadHash)
                    .put("value", releaseManifest.signature.value)
            )
    }

    private fun JSONObject.artifactJson(): JSONObject =
        getJSONArray("artifacts").getJSONObject(0)

    private fun DeviceFirmwareManifestArtifact.toJson(): JSONObject = JSONObject()
        .put("env", env)
        .put(
            "product",
            JSONObject()
                .put("productKey", product.productKey)
                .put("productId", product.productId)
                .put("brand", product.brand)
                .put("family", product.family)
                .put("line", product.line)
                .put("model", product.model)
                .put("displayName", product.displayName)
                .put("skuCode", product.skuCode)
                .put("hardwareRevision", product.hardwareRevision)
                .put("capabilities", product.capabilities.toJson())
                .put("limits", product.limits.toJson())
        )
        .put(
            "compatibility",
            JSONObject()
                .put("productKey", compatibility.productKey)
                .put("productId", compatibility.productId)
                .put("family", compatibility.family)
                .put("line", compatibility.line)
                .put("model", compatibility.model)
                .put("hardwareRevision", compatibility.hardwareRevision)
        )
        .put(
            "firmware",
            JSONObject()
                .put("version", firmware.version)
                .put("filename", firmware.filename)
                .put("url", firmware.url)
                .put("sha256", firmware.sha256)
                .put("size", firmware.size)
                .put("format", firmware.format)
                .put("otaSlotCompatible", firmware.otaSlotCompatible)
        )
        .put("factory", JSONObject.NULL)

    private fun DeviceCapabilities.toJson(): JSONObject = JSONObject()
        .put("light", light)
        .put("manualLight", manualLight)
        .put("lightProgram", lightProgram)
        .put("lightPresets", lightPresets)
        .put("lightSimulation", lightSimulation)
        .put("fan", fan)
        .put("cooling", cooling)
        .put("temperature", temperature)
        .put("standaloneTimer", standaloneTimer)
        .put("dosing", dosing)
        .put("timeSync", timeSync)
        .put("ota", ota)

    private fun DeviceLimits.toJson(): JSONObject = JSONObject()
        .put("lightChannelCount", lightChannelCount)
        .put("fanOutputCount", fanOutputCount)
        .put("temperatureSensorCount", temperatureSensorCount)
        .put("timerChannelCount", timerChannelCount)
        .put("dosingChannelCount", dosingChannelCount)

    private class RecordingGateway(
        private val startDelayMillis: Long
    ) : DeviceRuntimeCommandGateway {
        val commands = CopyOnWriteArrayList<String>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            if (command.action == DeviceFirmwareRuntimeContract.Action.OTA_START) {
                delay(startDelayMillis)
            }
            commands += command.action
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = "ota-${commands.size}",
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = startAcceptedData(),
                    ok = true,
                    statusCode = 202
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "ota-${commands.size}",
                generation = DeviceRuntimeConnectionGeneration(RUNTIME_GENERATION),
                statusCode = 202,
                value = value
            )
        }

        private fun startAcceptedData(): JSONObject = JSONObject()
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
                    .put("version", TARGET_VERSION)
                    .put("expectedSize", FIRMWARE_SIZE)
                    .put("applyNow", true)
                    .put("allowInsecureHttp", false)
                    .put("productKey", PRODUCT_KEY)
                    .put("productId", PRODUCT_ID)
                    .put("model", "dose_pro_2")
                    .put("hardwareRevision", "2.0")
            )
            .put("ota", otaSnapshot())

        private fun otaSnapshot(): JSONObject = JSONObject()
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
            .put("sha256Expected", "a".repeat(64))
            .put("sha256Actual", "")
            .put("lastError", "")
            .put("lastErrorField", "")
            .put("urlScheme", "https")
            .put("httpStatus", 0)
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-HARDENING")
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "2.0.0"
        const val RELEASE_TAG = "dosing_dose_pro_2-v2.0.0"
        const val GENERATED_AT = "2026-08-03T00:00:00+00:00"
        const val FIRMWARE_SIZE = 1_048_576
        const val RUNTIME_GENERATION = 7L
        const val WORKER_COUNT = 8
        const val START_SEND_DELAY_MILLIS = 75L
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/stable-dosing_dose_pro_2/manifest-stable.json"
        val DOSING_CAPABILITIES = DeviceCapabilities(dosing = true, timeSync = true, ota = true)
        val DOSING_LIMITS = DeviceLimits(dosingChannelCount = 2)
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}
