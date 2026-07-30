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
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "MaxLineLength")
class DeviceOtaCoordinatorTest {

    @Test
    fun `availability start progress reconnect and completion use one device state machine`() = runTest {
        val transport = RecordingWsTransport()
        val events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        var snapshot = product().toSnapshot()
        val updater = updater(transport)
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater },
            runtimeEvents = events.asSharedFlow(),
            dispatcher = Dispatchers.Unconfined
        )

        val availability = coordinator.checkAvailability(
            DEVICE_UID,
            MANIFEST_URL,
            applyNow = false
        ).getOrThrow() as DeviceOtaState.UpdateAvailable
        assertEquals("2.0.0", availability.plan.targetVersion)
        assertEquals("Güvenli güncelleme", availability.plan.releaseContent.title)

        val startResult = coordinator.startUpdate(availability.plan)
        assertTrue(startResult.isSuccess)
        val startCommand = transport.commands.last()
        assertEquals("dose_pro_2", startCommand.data.getString("model"))

        events.tryEmit(
            AqlWsEvent.Message(
                DEVICE_UID,
                response(startCommand, startAcceptedData())
            )
        )
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.InProgress)

        events.tryEmit(
            AqlWsEvent.Message(
                DEVICE_UID,
                AqlWsIncomingMessage.Event(
                    id = "progress-1",
                    type = "event",
                    module = DeviceFirmwareRuntimeContract.MODULE,
                    action = DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS,
                    data = otaSnapshot("writing", active = true, progressPermille = 500)
                )
            )
        )
        val progress = coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress
        assertEquals(500, progress.progressPermille)

        events.tryEmit(AqlWsEvent.Closed(DEVICE_UID, 1006, "network changed"))
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)

        events.tryEmit(AqlWsEvent.Authenticated(DEVICE_UID))
        val statusCommand = transport.commands.last()
        assertEquals(DeviceFirmwareRuntimeContract.Action.OTA_STATUS, statusCommand.action)
        events.tryEmit(
            AqlWsEvent.Message(
                DEVICE_UID,
                response(
                    statusCommand,
                    otaStatusData(otaSnapshot("writing", active = true, progressPermille = 600))
                )
            )
        )
        assertEquals(
            600,
            (coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress).progressPermille
        )

        events.tryEmit(
            AqlWsEvent.Message(
                DEVICE_UID,
                AqlWsIncomingMessage.Event(
                    id = "completed-1",
                    type = "event",
                    module = DeviceFirmwareRuntimeContract.MODULE,
                    action = DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED,
                    data = otaSnapshot(
                        phase = "succeeded",
                        active = false,
                        progressPermille = 1_000,
                        restartRequired = true
                    )
                )
            )
        )
        val completed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.RestartRequired
        assertEquals("2.0.0", completed.targetVersion)
        assertFalse(completed.restartScheduled)
        coordinator.close()
    }

    @Test
    fun `metadata generation change expires a prepared plan before start`() = runTest {
        val transport = RecordingWsTransport()
        var snapshot = product().toSnapshot()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(transport) },
            runtimeEvents = null,
            dispatcher = Dispatchers.Unconfined
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

    private fun updater(transport: RecordingWsTransport): DeviceFirmwareUpdateRepository {
        val runtime = DeviceFirmwareRuntimeRepository {
            AqlWsCommandClient(transport)
        }
        val source = object : DeviceFirmwareManifestHttpSource() {
            override suspend fun load(url: String): Result<DeviceFirmwareManifest> =
                Result.success(manifest())
        }
        return DeviceFirmwareUpdateRepository(
            runtime = runtime,
            manifestSource = source,
            planner = DeviceFirmwareUpdatePlanner { listOf("tr-TR") }
        )
    }

    private fun response(
        command: AqlWsOutgoingMessage.Command,
        data: JSONObject
    ) = AqlWsIncomingMessage.Response(
        id = command.id,
        type = "res",
        module = command.module,
        action = command.action,
        data = data,
        ok = true,
        statusCode = if (command.action == DeviceFirmwareRuntimeContract.Action.OTA_START) 202 else 200
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
                .put("applyNow", false)
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

    private fun otaSnapshot(
        phase: String,
        active: Boolean,
        progressPermille: Int,
        restartRequired: Boolean = false
    ): JSONObject = JSONObject()
        .put("phase", phase)
        .put("active", active)
        .put("restartRequired", restartRequired)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 1L)
        .put("finishedAtMs", if (phase == "succeeded") 2L else 0L)
        .put("bytesWritten", FIRMWARE_SIZE.toLong() * progressPermille / 1_000L)
        .put("contentLength", FIRMWARE_SIZE.toLong())
        .put("progressPermille", progressPermille)
        .put("progressPercent", progressPermille / 10.0)
        .put("targetVersion", "2.0.0")
        .put("sha256Expected", "a".repeat(64))
        .put("sha256Actual", if (phase == "succeeded") "a".repeat(64) else "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "https")
        .put("httpStatus", 200)

    private fun product(): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == "DOSING_DOSE_PRO_2"
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
        val filename = "AquaLight-$env-v2.0.0-ota.bin"
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = "AquaLight",
            channel = "stable",
            version = "2.0.0",
            tag = "v2.0.0",
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-07-30T00:00:00Z",
            artifacts = listOf(
                DeviceFirmwareManifestArtifact(
                    env = env,
                    product = DeviceFirmwareManifestProduct(
                        productKey = product.productKey.value,
                        productId = product.productId.value,
                        brand = "AquaLight",
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
                            "v2.0.0/$filename",
                        sha256 = "a".repeat(64),
                        size = FIRMWARE_SIZE,
                        format = "bin",
                        otaSlotCompatible = true
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
                defaultLocale = "tr-TR",
                mandatory = false,
                locales = mapOf(
                    "tr-TR" to DeviceFirmwareLocalizedReleaseNotes(
                        title = "Güvenli güncelleme",
                        summary = "Dozaj güvenilirliği geliştirildi.",
                        changes = listOf("Kalibrasyon kontrolleri geliştirildi."),
                        warnings = emptyList()
                    )
                )
            )
        )
    }

    private class RecordingWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 1)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val commands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
        ): Result<Unit> = Result.success(Unit)

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            commands += command
            return true
        }

        override fun disconnect(code: Int, reason: String) = Unit

        override fun close() = Unit
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-COORDINATOR")
        const val FIRMWARE_SIZE = 1_048_576
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v2.0.0/manifest-stable.json"
    }
}
