package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
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
            snapshotProvider = { product().toSnapshot() },
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
    fun `exact artifact predicate ignores nonmatching environment family and line`() {
        val exact = artifact()
        val manifest = manifest(
            artifacts = listOf(
                exact,
                exact.copy(env = "dosing_dose_pro_4"),
                exact.copy(
                    compatibility = exact.compatibility.copy(family = "timer")
                ),
                exact.copy(
                    compatibility = exact.compatibility.copy(line = "legacy_dose")
                )
            )
        )
        val planner = DeviceFirmwareUpdatePlanner { listOf("en") }
        val snapshot = product().toSnapshot()

        val matching = planner.compatibleArtifacts(snapshot, manifest)
        val availability = planner.evaluateUpdate(snapshot, manifest).getOrThrow()
            as DeviceFirmwareAvailability.UpdateAvailable

        assertEquals(listOf(exact), matching)
        assertEquals(exact.env, availability.plan.env)
        assertEquals(exact.firmware.sha256, availability.plan.firmware.sha256)
    }

    @Test
    fun `manifest parser rejects unknown fields at every signed object boundary`() {
        assertTrue(DeviceFirmwareManifestParser.parse(manifestJson().toString()).isSuccess)

        val invalidManifests = listOf(
            manifestJson().put("legacyRoot", true),
            manifestJson().apply {
                getJSONObject("platform").put("legacyPlatform", true)
            },
            manifestJson().apply {
                artifactJson().put("legacyArtifact", true)
            },
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
                artifactJson().getJSONObject("compatibility")
                    .put("legacyCompatibility", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("firmware").put("legacyFirmware", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("factory").put("legacyFactory", true)
            },
            manifestJson().apply {
                getJSONObject("releaseNotes").put("mandatory", false)
            },
            manifestJson().apply {
                getJSONObject("releaseNotes").getJSONArray("items").getJSONObject(0)
                    .put("title", "legacy")
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
        val runtime = DeviceFirmwareRuntimeRepository(gateway)
        val source = object : DeviceFirmwareManifestHttpSource() {
            override suspend fun load(url: String): Result<DeviceFirmwareManifest> =
                Result.success(manifest())
        }
        return DeviceFirmwareUpdateRepository(
            runtime = runtime,
            manifestSource = source,
            planner = DeviceFirmwareUpdatePlanner { listOf("en") }
        )
    }

    private fun product(): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == PRODUCT_KEY
        }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = "Salon Dozaj"),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            productId = productId.value,
            productKey = productKey.value,
            family = family,
            familyRaw = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuId = skuId.value,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value
        ),
        firmwareVersion = CURRENT_VERSION,
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DeviceCapabilities(
            light = profile.capabilities.light,
            manualLight = profile.capabilities.manualLight,
            lightProgram = profile.capabilities.lightProgram,
            lightPresets = profile.capabilities.lightPresets,
            lightSimulation = profile.capabilities.lightSimulation,
            fan = profile.capabilities.fan,
            cooling = profile.capabilities.cooling,
            temperature = profile.capabilities.temperature,
            standaloneTimer = profile.capabilities.standaloneTimer,
            dosing = profile.capabilities.dosing,
            timeSync = profile.capabilities.timeSync,
            ota = profile.capabilities.ota
        ),
        limits = DeviceLimits(
            lightChannelCount = limits.lightChannelCount,
            fanOutputCount = limits.fanOutputCount,
            temperatureSensorCount = limits.temperatureSensorCount,
            timerChannelCount = limits.timerChannelCount,
            dosingChannelCount = limits.dosingChannelCount
        ),
        supportedFeatures = profile.supportedFeatures.map { feature -> feature.wireValue },
        supportedScreens = profile.supportedScreens.map { screen -> screen.wireValue },
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
        platform = manifestPlatform(),
        artifacts = artifacts,
        signature = manifestSignature(),
        releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA,
            defaultLocale = "en",
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Kalibrasyon doğrulaması geliştirildi.",
                    en = "Calibration validation improved."
                )
            )
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val product = product()
        val otaFilename = "AquaLight-$ENVIRONMENT-$RELEASE_TAG-ota.bin"
        val factoryFilename = "AquaLight-$ENVIRONMENT-$RELEASE_TAG-factory.zip"
        val releaseUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "$RELEASE_TAG/"
        return DeviceFirmwareManifestArtifact(
            env = ENVIRONMENT,
            product = product.toManifestProduct(),
            compatibility = DeviceFirmwareCompatibility(
                productKey = product.productKey.value,
                productId = product.productId.value,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                hardwareRevision = product.hardwareRevision.value
            ),
            firmware = DeviceFirmwareAsset(
                filename = otaFilename,
                url = releaseUrl + otaFilename,
                sha256 = "a".repeat(64),
                size = FIRMWARE_SIZE,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = DeviceFirmwareFactoryAsset(
                filename = factoryFilename,
                url = releaseUrl + factoryFilename,
                sha256 = "c".repeat(64),
                size = FACTORY_SIZE
            )
        )
    }

    private fun AqlCommercialCatalogProduct.toManifestProduct() =
        DeviceFirmwareManifestProduct(
            productKey = productKey.value,
            productId = productId.value,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            family = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value,
            capabilities = DeviceFirmwareManifestCapabilities(
                light = profile.capabilities.light,
                manualLight = profile.capabilities.manualLight,
                lightProgram = profile.capabilities.lightProgram,
                lightPresets = profile.capabilities.lightPresets,
                lightSimulation = profile.capabilities.lightSimulation,
                fan = profile.capabilities.fan,
                cooling = profile.capabilities.cooling,
                temperature = profile.capabilities.temperature,
                standaloneTimer = profile.capabilities.standaloneTimer,
                dosing = profile.capabilities.dosing,
                timeSync = profile.capabilities.timeSync,
                ota = profile.capabilities.ota
            ),
            limits = DeviceFirmwareManifestLimits(
                lightChannelCount = limits.lightChannelCount,
                fanOutputCount = limits.fanOutputCount,
                temperatureSensorCount = limits.temperatureSensorCount,
                timerChannelCount = limits.timerChannelCount,
                dosingChannelCount = limits.dosingChannelCount
            )
        )

    private fun manifestJson(): JSONObject = JSONObject()
        .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
        .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
        .put("channel", DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL)
        .put("version", TARGET_VERSION)
        .put("tag", RELEASE_TAG)
        .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
        .put("generatedAt", GENERATED_AT)
        .put("platform", manifestPlatform().toJson())
        .put("artifacts", JSONArray().put(artifact().toJson()))
        .put(
            "releaseNotes",
            JSONObject()
                .put("schema", DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA)
                .put("defaultLocale", "en")
                .put(
                    "items",
                    JSONArray().put(
                        JSONObject()
                            .put("tr", "Kalibrasyon doğrulaması geliştirildi.")
                            .put("en", "Calibration validation improved.")
                    )
                )
        )
        .put("signature", manifestSignature().toJson())

    private fun JSONObject.artifactJson(): JSONObject =
        getJSONArray("artifacts").getJSONObject(0)

    private fun DeviceFirmwareManifestArtifact.toJson(): JSONObject = JSONObject()
        .put("env", env)
        .put("product", product.toJson())
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
                .put("filename", firmware.filename)
                .put("url", firmware.url)
                .put("sha256", firmware.sha256)
                .put("size", firmware.size)
                .put("format", firmware.format)
                .put("otaSlotCompatible", firmware.otaSlotCompatible)
        )
        .put(
            "factory",
            factory?.let { asset ->
                JSONObject()
                    .put("filename", asset.filename)
                    .put("url", asset.url)
                    .put("sha256", asset.sha256)
                    .put("size", asset.size)
            } ?: JSONObject.NULL
        )

    private fun DeviceFirmwareManifestProduct.toJson(): JSONObject = JSONObject()
        .put("productKey", productKey)
        .put("productId", productId)
        .put("brand", brand)
        .put("family", family)
        .put("line", line)
        .put("model", model)
        .put("displayName", displayName)
        .put("skuCode", skuCode)
        .put("hardwareRevision", hardwareRevision)
        .put(
            "capabilities",
            JSONObject()
                .put("light", capabilities.light)
                .put("manualLight", capabilities.manualLight)
                .put("lightProgram", capabilities.lightProgram)
                .put("lightPresets", capabilities.lightPresets)
                .put("lightSimulation", capabilities.lightSimulation)
                .put("fan", capabilities.fan)
                .put("cooling", capabilities.cooling)
                .put("temperature", capabilities.temperature)
                .put("standaloneTimer", capabilities.standaloneTimer)
                .put("dosing", capabilities.dosing)
                .put("timeSync", capabilities.timeSync)
                .put("ota", capabilities.ota)
        )
        .put(
            "limits",
            JSONObject()
                .put("lightChannelCount", limits.lightChannelCount)
                .put("fanOutputCount", limits.fanOutputCount)
                .put("temperatureSensorCount", limits.temperatureSensorCount)
                .put("timerChannelCount", limits.timerChannelCount)
                .put("dosingChannelCount", limits.dosingChannelCount)
        )

    private fun manifestPlatform() = DeviceFirmwareManifestPlatform(
        framework = "arduino-esp32",
        core = "3.3.9",
        platform = "pioarduino/platform-espressif32#55.03.39",
        partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
        normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
    )

    private fun DeviceFirmwareManifestPlatform.toJson(): JSONObject = JSONObject()
        .put("framework", framework)
        .put("core", core)
        .put("platform", platform)
        .put("partitionTable", partitionTable)
        .put("normalOtaAssetType", normalOtaAssetType)

    private fun manifestSignature() = DeviceFirmwareManifestSignature(
        scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
        keyId = "release-key-1",
        payloadHash = "b".repeat(64),
        value = "signed-value"
    )

    private fun DeviceFirmwareManifestSignature.toJson(): JSONObject = JSONObject()
        .put("scheme", scheme)
        .put("keyId", keyId)
        .put("payloadHash", payloadHash)
        .put("value", value)

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
                    .put("productId", "com.aqualight.dosing.dose_pro_2")
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
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "2.0.0"
        const val RELEASE_TAG = "v2.0.0"
        const val GENERATED_AT = "2026-07-30T00:00:00Z"
        const val FIRMWARE_SIZE = 1_048_576
        const val FACTORY_SIZE = 2_097_152
        const val RUNTIME_GENERATION = 7L
        const val WORKER_COUNT = 8
        const val START_SEND_DELAY_MILLIS = 75L
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v2.0.0/manifest-stable.json"
    }
}
