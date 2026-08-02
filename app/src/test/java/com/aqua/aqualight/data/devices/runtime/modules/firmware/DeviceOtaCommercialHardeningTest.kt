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
                async {
                    coordinator.startUpdate(plan).isSuccess
                }
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
                artifactJson().put("legacyArtifact", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("product").put("legacyProduct", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("compatibility").put("legacyCompatibility", true)
            },
            manifestJson().apply {
                artifactJson().getJSONObject("firmware").put("legacyFirmware", true)
            },
            manifestJson().apply {
                getJSONObject("signature").put("legacySignature", true)
            },
            manifestJson().apply {
                val firmware = artifactJson().getJSONObject("firmware")
                artifactJson().put(
                    "factory",
                    JSONObject(firmware.toString()).put("legacyFactory", true)
                )
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
            brand = "AquaLight",
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
        artifacts = artifacts,
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = "b".repeat(64),
            value = "signed-value"
        ),
        releaseNotes = DeviceFirmwareReleaseNotes(
            defaultLocale = "en",
            mandatory = false,
            locales = mapOf(
                "en" to DeviceFirmwareLocalizedReleaseNotes(
                    title = "Dosing reliability",
                    summary = "Safer dosing update.",
                    changes = listOf("Calibration validation improved."),
                    warnings = emptyList()
                )
            )
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val product = product()
        val filename = "AquaLight-$ENVIRONMENT-$RELEASE_TAG-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = ENVIRONMENT,
            product = DeviceFirmwareManifestProduct(
                productKey = product.productKey.value,
                productId = product.productId.value,
                brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                displayName = product.displayName,
                skuCode = product.skuCode.value,
                hardwareRevision = product.hardwareRevision.value
            ),
            compatibility = DeviceFirmwareCompatibility(
                productKey = product.productKey.value,
                productId = product.productId.value,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                hardwareRevision = product.hardwareRevision.value
            ),
            firmware = DeviceFirmwareAsset(
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "$RELEASE_TAG/$filename",
                sha256 = "a".repeat(64),
                size = FIRMWARE_SIZE,
                format = "bin",
                otaSlotCompatible = true
            )
        )
    }

    private fun manifestJson(): JSONObject {
        val artifact = artifact()
        val releaseContent = JSONObject()
            .put("title", "Safe update")
            .put("summary", "Reliability improvements.")
            .put("changes", JSONArray(listOf("Improved calibration checks.")))
            .put("warnings", JSONArray())

        return JSONObject()
            .put("schema", DeviceFirmwareRuntimeContract.Manifest.SCHEMA)
            .put("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND)
            .put("channel", DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL)
            .put("version", TARGET_VERSION)
            .put("tag", RELEASE_TAG)
            .put("releaseRepo", DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY)
            .put("generatedAt", GENERATED_AT)
            .put(
                "releaseNotes",
                JSONObject()
                    .put("defaultLocale", "en")
                    .put("mandatory", false)
                    .put("locales", JSONObject().put("en", releaseContent))
            )
            .put("artifacts", JSONArray().put(artifact.toJson()))
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
                .put("filename", firmware.filename)
                .put("url", firmware.url)
                .put("sha256", firmware.sha256)
                .put("size", firmware.size)
                .put("format", firmware.format)
                .put("otaSlotCompatible", firmware.otaSlotCompatible)
        )

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
        const val RUNTIME_GENERATION = 7L
        const val WORKER_COUNT = 8
        const val START_SEND_DELAY_MILLIS = 75L
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v2.0.0/manifest-stable.json"
    }
}
