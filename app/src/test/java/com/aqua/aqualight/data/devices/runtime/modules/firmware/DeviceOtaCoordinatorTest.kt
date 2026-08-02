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
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "MaxLineLength")
class DeviceOtaCoordinatorTest {

    @Test
    fun `correlated start typed progress restart recovery and version proof share one state`() =
        runTest {
            val lifecycle = MutableSharedFlow<DeviceRuntimeLifecycleEvent>(
                extraBufferCapacity = 16
            )
            val typedEvents = MutableSharedFlow<DeviceRuntimeTypedEvent>(extraBufferCapacity = 16)
            val snapshots = MutableStateFlow(mapOf(DEVICE_UID to product().toSnapshot()))
            val gateway = RecordingGateway()
            var discoveryRefreshes = 0
            var reconnects = 0
            val coordinator = DeviceOtaCoordinator(
                snapshotProvider = { deviceUid -> snapshots.value[deviceUid] },
                connectRuntime = { Result.success(Unit) },
                updaterProvider = { updater(gateway) },
                runtimeLifecycleEvents = lifecycle,
                runtimeTypedEvents = typedEvents,
                snapshotUpdates = snapshots,
                recoverRuntime = {
                    reconnects += 1
                    Result.success(Unit)
                },
                refreshDiscovery = { discoveryRefreshes += 1 },
                dispatcher = StandardTestDispatcher(testScheduler),
                restartWaitMillis = 100L,
                discoverySettleMillis = 0L
            )
            runCurrent()

            val availability = coordinator.checkAvailability(
                DEVICE_UID,
                MANIFEST_URL,
                applyNow = true
            ).getOrThrow() as DeviceOtaState.UpdateAvailable
            assertEquals("2.0.0", availability.plan.targetVersion)
            assertEquals(
                listOf("Kalibrasyon kontrolleri geliştirildi."),
                availability.plan.releaseContent.changes
            )

            val startResult = coordinator.startUpdate(availability.plan)
            assertTrue(startResult.isSuccess)
            assertEquals(DeviceFirmwareRuntimeContract.Action.OTA_START, gateway.commands.last().action)
            assertEquals("dose_pro_2", gateway.commands.last().data.getString("model"))
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.InProgress)

            typedEvents.tryEmit(
                otaEvent(
                    type = DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_PROGRESS,
                    id = "progress-1",
                    snapshot = otaEventData("writing", active = true, progressPermille = 500)
                )
            )
            runCurrent()
            val progress = coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress
            assertEquals(500, progress.progressPermille)

            lifecycle.tryEmit(DeviceRuntimeLifecycleEvent.Unavailable(DEVICE_UID))
            runCurrent()
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)

            gateway.statusData = otaStatusData(
                otaSnapshot("writing", active = true, progressPermille = 600)
            )
            lifecycle.tryEmit(DeviceRuntimeLifecycleEvent.Authenticated(DEVICE_UID))
            runCurrent()
            assertEquals(
                600,
                (coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress)
                    .progressPermille
            )

            typedEvents.tryEmit(
                otaEvent(
                    type = DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_COMPLETED,
                    id = "completed-1",
                    snapshot = otaEventData(
                        phase = "succeeded",
                        active = false,
                        progressPermille = 1_000,
                        restartRequired = true,
                        restartScheduled = true
                    )
                )
            )
            runCurrent()
            val completed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.RestartRequired
            assertEquals("2.0.0", completed.targetVersion)
            assertTrue(completed.restartScheduled)

            lifecycle.tryEmit(DeviceRuntimeLifecycleEvent.Unavailable(DEVICE_UID))
            runCurrent()
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)

            snapshots.value = mapOf(
                DEVICE_UID to product().toSnapshot().copy(
                    firmwareVersion = "2.0.0",
                    runtimeMetadataGeneration = 8L
                )
            )
            runCurrent()
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Succeeded)
            assertEquals(0, discoveryRefreshes)
            assertEquals(0, reconnects)
            coordinator.close()
        }

    @Test
    fun `scheduled restart invokes UDP refresh and device scoped reconnect`() = runTest {
        val lifecycle = MutableSharedFlow<DeviceRuntimeLifecycleEvent>(extraBufferCapacity = 8)
        val typedEvents = MutableSharedFlow<DeviceRuntimeTypedEvent>(extraBufferCapacity = 8)
        val snapshots = MutableStateFlow(mapOf(DEVICE_UID to product().toSnapshot()))
        val gateway = RecordingGateway()
        var discoveryRefreshes = 0
        var reconnects = 0
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { deviceUid -> snapshots.value[deviceUid] },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = lifecycle,
            runtimeTypedEvents = typedEvents,
            snapshotUpdates = snapshots,
            recoverRuntime = {
                reconnects += 1
                Result.success(Unit)
            },
            refreshDiscovery = { discoveryRefreshes += 1 },
            dispatcher = StandardTestDispatcher(testScheduler),
            restartWaitMillis = 0L,
            discoverySettleMillis = 0L
        )
        runCurrent()
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan
        coordinator.startUpdate(plan)

        typedEvents.tryEmit(
            otaEvent(
                type = DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_COMPLETED,
                id = "completed-recovery",
                snapshot = otaEventData(
                    phase = "succeeded",
                    active = false,
                    progressPermille = 1_000,
                    restartRequired = true,
                    restartScheduled = true
                )
            )
        )
        runCurrent()

        assertEquals(1, discoveryRefreshes)
        assertEquals(1, reconnects)
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)
        coordinator.close()
    }

    @Test
    fun `reconnected old firmware fails final installed version proof`() = runTest {
        val typedEvents = MutableSharedFlow<DeviceRuntimeTypedEvent>(extraBufferCapacity = 8)
        val snapshots = MutableStateFlow(mapOf(DEVICE_UID to product().toSnapshot()))
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { deviceUid -> snapshots.value[deviceUid] },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(RecordingGateway()) },
            runtimeLifecycleEvents = null,
            runtimeTypedEvents = typedEvents,
            snapshotUpdates = snapshots,
            dispatcher = StandardTestDispatcher(testScheduler),
            restartWaitMillis = 1_000L
        )
        runCurrent()
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan
        coordinator.startUpdate(plan)
        typedEvents.tryEmit(
            otaEvent(
                DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_COMPLETED,
                "completed-old-version",
                otaEventData(
                    "succeeded",
                    active = false,
                    progressPermille = 1_000,
                    restartRequired = true,
                    restartScheduled = true
                )
            )
        )
        runCurrent()

        snapshots.value = mapOf(
            DEVICE_UID to product().toSnapshot().copy(runtimeMetadataGeneration = 8L)
        )
        runCurrent()

        val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
        assertTrue(failed.message.contains("firmware version"))
        assertFalse(failed.recoverable)
        coordinator.close()
    }

    @Test
    fun `metadata generation change expires a prepared plan before start`() = runTest {
        var snapshot = product().toSnapshot()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(RecordingGateway()) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan

        snapshot = snapshot.copy(runtimeMetadataGeneration = 8L)
        val result = coordinator.startUpdate(plan)

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage.contains("generation changed"))
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Failed)
        coordinator.close()
    }

    @Test
    fun `idle recovery status preserves the exact prepared update plan`() = runTest {
        val gateway = RecordingGateway().apply {
            statusData = otaStatusData(
                otaSnapshot("idle", active = false, progressPermille = 0)
            )
        }
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { product().toSnapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan

        val result = coordinator.requestStatus(DEVICE_UID)

        assertTrue(result.isSuccess)
        assertEquals(
            DeviceOtaState.UpdateAvailable(plan),
            coordinator.observe(DEVICE_UID).value
        )
        coordinator.close()
    }

    @Test
    fun `idle start acknowledgement cannot re-enable the install action`() = runTest {
        val gateway = RecordingGateway().apply {
            startData = startAcceptedData().put(
                "ota",
                otaSnapshot("idle", active = false, progressPermille = 0)
            )
        }
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { product().toSnapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan

        val result = coordinator.startUpdate(plan)

        assertTrue(result.isSuccess)
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Starting)
        coordinator.close()
    }

    private fun updater(gateway: RecordingGateway): DeviceFirmwareUpdateRepository {
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

    private fun otaEvent(
        type: DeviceRuntimeTypedEvent.Type,
        id: String,
        snapshot: JSONObject
    ) = DeviceRuntimeTypedEvent(
        deviceUid = DEVICE_UID,
        generation = RUNTIME_GENERATION,
        messageId = id,
        type = type,
        payload = DeviceRuntimeEventPayload.Snapshot(snapshot)
    )

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
                .put("version", "2.0.0")
                .put("expectedSize", FIRMWARE_SIZE)
                .put("applyNow", true)
                .put("allowInsecureHttp", false)
                .put("productKey", "DOSING_DOSE_PRO_2")
                .put("productId", "com.aqualight.dosing.dose_pro_2")
                .put("model", "dose_pro_2")
                .put("hardwareRevision", "2.0")
        )
        .put("ota", otaSnapshot("starting", active = true, progressPermille = 0))

    private fun otaStatusData(snapshot: JSONObject): JSONObject = JSONObject()
        .put("operation", "otaStatus")
        .put("runtimeTransport", "websocket")
        .put("command", "firmware.ota.status")
        .put("binaryTransfer", "firmware-download")
        .put("progressEvent", DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
        .put("completedEvent", DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED)
        .put("ota", snapshot)

    private fun otaEventData(
        phase: String,
        active: Boolean,
        progressPermille: Int,
        restartRequired: Boolean = false,
        restartScheduled: Boolean = false
    ): JSONObject = otaSnapshot(
        phase = phase,
        active = active,
        progressPermille = progressPermille,
        restartRequired = restartRequired,
        restartScheduled = restartScheduled
    )
        .put("completed", phase == "succeeded" || phase == "failed")
        .put("success", phase == "succeeded")
        .put("failed", phase == "failed")
        .put("runtimeTransport", "websocket")
        .put("binaryTransfer", "firmware-download")

    private fun otaSnapshot(
        phase: String,
        active: Boolean,
        progressPermille: Int,
        restartRequired: Boolean = false,
        restartScheduled: Boolean = false
    ): JSONObject = JSONObject()
        .put("phase", phase)
        .put("active", active)
        .put("restartRequired", restartRequired)
        .put("restartScheduled", restartScheduled)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 1L)
        .put("finishedAtMs", if (phase == "succeeded" || phase == "failed") 2L else 0L)
        .put("bytesWritten", FIRMWARE_SIZE.toLong() * progressPermille / 1_000L)
        .put("contentLength", FIRMWARE_SIZE.toLong())
        .put("progressPermille", progressPermille)
        .put("progressPercent", progressPermille / 10.0)
        .put("targetVersion", "2.0.0")
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", if (phase == "succeeded") "a".repeat(64) else "")
        .put("lastError", if (phase == "failed") "download failed" else "")
        .put("lastErrorField", if (phase == "failed") "download" else "")
        .put("urlScheme", "https")
        .put("httpStatus", 200)

    private fun product(): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == "DOSING_DOSE_PRO_2"
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
        firmwareVersion = "1.0.0",
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
        supportedFeatures = profile.supportedFeatures.map { it.wireValue },
        supportedScreens = profile.supportedScreens.map { it.wireValue },
        runtimeMetadataGeneration = 7L
    )

    private fun manifest(): DeviceFirmwareManifest {
        val product = product()
        val env = "dosing_dose_pro_2"
        val tag = "v2.0.0"
        val otaFilename = "AquaLight-$env-$tag-ota.bin"
        val factoryFilename = "AquaLight-$env-$tag-factory.zip"
        val releaseUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX + "$tag/"
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = "2.0.0",
            tag = tag,
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-07-30T00:00:00Z",
            platform = DeviceFirmwareManifestPlatform(
                framework = "arduino-esp32",
                core = "3.3.9",
                platform = "pioarduino/platform-espressif32#55.03.39",
                partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
                normalOtaAssetType =
                    DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
            ),
            artifacts = listOf(
                DeviceFirmwareManifestArtifact(
                    env = env,
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
            ),
            signature = DeviceFirmwareManifestSignature(
                scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
                keyId = "release-key-1",
                payloadHash = "b".repeat(64),
                value = "signed-value"
            ),
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA,
                defaultLocale = "tr",
                items = listOf(
                    DeviceFirmwareReleaseNoteItem(
                        tr = "Kalibrasyon kontrolleri geliştirildi.",
                        en = "Calibration checks improved."
                    )
                )
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

    private inner class RecordingGateway : DeviceRuntimeCommandGateway {
        val commands = CopyOnWriteArrayList<RecordedCommand>()
        var startData: JSONObject = startAcceptedData()
        var statusData: JSONObject = otaStatusData(
            otaSnapshot("starting", active = true, progressPermille = 0)
        )

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            commands += RecordedCommand(command.action, command.encodeData())
            val data = when (command.action) {
                DeviceFirmwareRuntimeContract.Action.OTA_START -> startData
                DeviceFirmwareRuntimeContract.Action.OTA_STATUS -> statusData
                else -> error("Unexpected OTA command: ${command.action}")
            }
            val responseStatusCode = if (
                command.action == DeviceFirmwareRuntimeContract.Action.OTA_START
            ) {
                202
            } else {
                200
            }
            return runCatching {
                command.parseSuccess(
                    AqlWsIncomingMessage.Response(
                        id = "response-${commands.size}",
                        type = "res",
                        module = command.module,
                        action = command.action,
                        data = JSONObject(data.toString()),
                        ok = true,
                        statusCode = responseStatusCode
                    )
                )
            }.fold(
                onSuccess = { value ->
                    DeviceRuntimeCommandOutcome.Success(
                        deviceUid = deviceUid,
                        module = command.module,
                        action = command.action,
                        messageId = "response-${commands.size}",
                        generation = RUNTIME_GENERATION,
                        statusCode = responseStatusCode,
                        value = value
                    )
                },
                onFailure = { error ->
                    DeviceRuntimeCommandOutcome.ProtocolError(
                        deviceUid = deviceUid,
                        module = command.module,
                        action = command.action,
                        messageId = "response-${commands.size}",
                        generation = RUNTIME_GENERATION,
                        reason = error.message.orEmpty()
                    )
                }
            )
        }
    }

    private data class RecordedCommand(
        val action: String,
        val data: JSONObject
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-COORDINATOR")
        val RUNTIME_GENERATION = DeviceRuntimeConnectionGeneration(7L)
        const val FIRMWARE_SIZE = 1_048_576
        const val FACTORY_SIZE = 2_097_152
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v2.0.0/manifest-stable.json"
    }
}
