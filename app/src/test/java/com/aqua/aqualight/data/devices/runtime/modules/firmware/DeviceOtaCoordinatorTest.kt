package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
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
            val snapshots = MutableStateFlow(mapOf(DEVICE_UID to snapshot()))
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
                DeviceFirmwareChannel.STABLE,
                applyNow = true
            ).getOrThrow() as DeviceOtaState.UpdateAvailable
            assertEquals(TARGET_VERSION, availability.plan.targetVersion)
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
            assertEquals(TARGET_VERSION, completed.targetVersion)
            assertTrue(completed.restartScheduled)

            lifecycle.tryEmit(DeviceRuntimeLifecycleEvent.Unavailable(DEVICE_UID))
            runCurrent()
            assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)

            snapshots.value = mapOf(
                DEVICE_UID to snapshot().copy(
                    firmwareVersion = TARGET_VERSION,
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
        val snapshots = MutableStateFlow(mapOf(DEVICE_UID to snapshot()))
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
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
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
        val snapshots = MutableStateFlow(mapOf(DEVICE_UID to snapshot()))
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
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
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
            DEVICE_UID to snapshot().copy(runtimeMetadataGeneration = 8L)
        )
        runCurrent()

        val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
        assertTrue(failed.failure.diagnosticMessage.contains("firmware version"))
        assertEquals(DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE, failed.failure.reason)
        assertFalse(failed.failure.recoverable)
        coordinator.close()
    }

    @Test
    fun `metadata generation change expires a prepared plan before start`() = runTest {
        var currentSnapshot = snapshot()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { currentSnapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(RecordingGateway()) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan

        currentSnapshot = currentSnapshot.copy(runtimeMetadataGeneration = 8L)
        val result = coordinator.startUpdate(plan)

        assertFalse(result.isSuccess)
        assertTrue(result.failure?.diagnosticMessage.orEmpty().contains("generation changed"))
        assertEquals(DeviceOtaFailureReason.CHECK_FAILED, result.failure?.reason)
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
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
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
    fun `failed recovery status probe preserves signed update availability`() = runTest {
        val gateway = RecordingGateway()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan
        gateway.statusData = JSONObject()

        val result = coordinator.requestStatus(DEVICE_UID)

        assertFalse(result.isSuccess)
        assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, result.failure?.reason)
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
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeLifecycleEvents = null
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, DeviceFirmwareChannel.STABLE, true).getOrThrow()
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
                .put("version", TARGET_VERSION)
                .put("expectedSize", FIRMWARE_SIZE)
                .put("applyNow", true)
                .put("productKey", PRODUCT_KEY)
                .put("productId", PRODUCT_ID)
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
        .put("startedAtMs", 1L)
        .put("finishedAtMs", if (phase == "succeeded" || phase == "failed") 2L else 0L)
        .put("bytesWritten", FIRMWARE_SIZE.toLong() * progressPermille / 1_000L)
        .put("contentLength", FIRMWARE_SIZE.toLong())
        .put("progressPermille", progressPermille)
        .put("progressPercent", progressPermille / 10.0)
        .put("targetVersion", TARGET_VERSION)
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", if (phase == "succeeded") "a".repeat(64) else "")
        .put("lastError", if (phase == "failed") "download failed" else "")
        .put("lastErrorField", if (phase == "failed") "stream" else "")
        .put("urlScheme", "https")
        .put("httpStatus", 200)

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
        firmwareVersion = "1.0.0",
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DOSING_CAPABILITIES,
        limits = DOSING_LIMITS,
        runtimeMetadataGeneration = 7L
    )

    private fun manifest(): DeviceFirmwareManifest {
        val filename = "AquaLight-$RELEASE_TAG-ota.bin"
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = TARGET_VERSION,
            tag = RELEASE_TAG,
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-08-03T00:00:00+00:00",
            platform = OFFICIAL_PLATFORM,
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
                defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
                items = listOf(
                    DeviceFirmwareReleaseNoteItem(
                        tr = "Kalibrasyon kontrolleri geliştirildi.",
                        en = "Calibration checks improved."
                    )
                )
            ),
            artifacts = listOf(
                DeviceFirmwareManifestArtifact(
                    env = ENVIRONMENT,
                    product = DeviceFirmwareManifestProduct(
                        productKey = PRODUCT_KEY,
                        productId = PRODUCT_ID,
                        brand = "AquaLight",
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
            ),
            signature = DeviceFirmwareManifestSignature(
                scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
                keyId = "release-key-1",
                payloadHash = "b".repeat(64),
                value = "signed-value"
            )
        )
    }

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
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val TARGET_VERSION = "2.0.0"
        const val RELEASE_TAG = "dosing_dose_pro_2-v2.0.0"
        const val FIRMWARE_SIZE = 1_048_576
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "main/channels/stable/dosing_dose_pro_2.json"
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
