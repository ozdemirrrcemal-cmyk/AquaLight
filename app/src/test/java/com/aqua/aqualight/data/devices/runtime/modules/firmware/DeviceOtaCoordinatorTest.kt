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
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "MaxLineLength")
class DeviceOtaCoordinatorTest {

    @Test
    fun `typed ota flow verifies installed version after reboot before success`() = runTest {
        val gateway = FakeFirmwareGateway()
        val events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        var snapshot = product().toSnapshot()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeEvents = events.asSharedFlow(),
            dispatcher = Dispatchers.Unconfined
        )

        val availability = coordinator.checkAvailability(
            DEVICE_UID,
            MANIFEST_URL,
            applyNow = false
        ).getOrThrow() as DeviceOtaState.UpdateAvailable
        assertEquals("2.0.0", availability.plan.targetVersion)

        val startResult = coordinator.startUpdate(availability.plan)
        assertTrue(startResult.isSuccess)
        assertEquals(DeviceFirmwareRuntimeContract.Action.OTA_START, gateway.actions.last())
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.InProgress)

        events.emit(
            AqlWsEvent.Message(
                DEVICE_UID,
                otaEvent(
                    action = DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS,
                    snapshot = otaSnapshot(
                        phase = DeviceFirmwareOtaPhase.WRITING,
                        active = true,
                        progressPermille = 500
                    )
                )
            )
        )
        assertEquals(
            500,
            (coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress).progressPermille
        )

        events.emit(AqlWsEvent.Closed(DEVICE_UID, 1006, "network changed"))
        assertTrue(coordinator.observe(DEVICE_UID).value is DeviceOtaState.Recovering)

        gateway.otaStatus = otaSnapshot(
            phase = DeviceFirmwareOtaPhase.WRITING,
            active = true,
            progressPermille = 600
        )
        events.emit(AqlWsEvent.Authenticated(DEVICE_UID))
        assertEquals(
            600,
            (coordinator.observe(DEVICE_UID).value as DeviceOtaState.InProgress).progressPermille
        )

        events.emit(
            AqlWsEvent.Message(
                DEVICE_UID,
                otaEvent(
                    action = DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED,
                    snapshot = otaSnapshot(
                        phase = DeviceFirmwareOtaPhase.SUCCEEDED,
                        active = false,
                        progressPermille = 1_000,
                        restartRequired = true
                    )
                )
            )
        )
        val restartRequired = coordinator.observe(DEVICE_UID).value
            as DeviceOtaState.RestartRequired
        assertEquals("2.0.0", restartRequired.targetVersion)
        assertFalse(restartRequired.restartScheduled)

        events.emit(AqlWsEvent.Authenticated(DEVICE_UID))
        val succeeded = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Succeeded
        assertEquals("2.0.0", succeeded.targetVersion)
        assertTrue(DeviceFirmwareRuntimeContract.Action.STATUS_GET in gateway.actions)
        coordinator.close()
    }

    @Test
    fun `rebooting into a different firmware version fails verification`() = runTest {
        val gateway = FakeFirmwareGateway().apply { installedVersion = "1.9.9" }
        val events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 8)
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { product().toSnapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
            runtimeEvents = events.asSharedFlow(),
            dispatcher = Dispatchers.Unconfined
        )
        val plan = (
            coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, false).getOrThrow()
                as DeviceOtaState.UpdateAvailable
            ).plan
        assertTrue(coordinator.startUpdate(plan).isSuccess)

        events.emit(
            AqlWsEvent.Message(
                DEVICE_UID,
                otaEvent(
                    action = DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED,
                    snapshot = otaSnapshot(
                        phase = DeviceFirmwareOtaPhase.SUCCEEDED,
                        active = false,
                        progressPermille = 1_000,
                        restartRequired = true
                    )
                )
            )
        )
        events.emit(AqlWsEvent.Authenticated(DEVICE_UID))

        val failed = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed
        assertTrue(failed.message.contains("expected 2.0.0"))
        assertFalse(failed.recoverable)
        coordinator.close()
    }

    @Test
    fun `metadata generation change expires a prepared plan before start`() = runTest {
        val gateway = FakeFirmwareGateway()
        var snapshot = product().toSnapshot()
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { updater(gateway) },
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

    private fun updater(gateway: DeviceRuntimeCommandGateway): DeviceFirmwareUpdateRepository {
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

    private inner class FakeFirmwareGateway : DeviceRuntimeCommandGateway {
        val actions = CopyOnWriteArrayList<String>()
        var installedVersion: String = "2.0.0"
        var otaStatus: DeviceFirmwareOtaSnapshot = otaSnapshot(
            phase = DeviceFirmwareOtaPhase.WRITING,
            active = true,
            progressPermille = 600
        )

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val value: Any = when (command.action) {
                DeviceFirmwareRuntimeContract.Action.STATUS_GET -> firmwareStatus(installedVersion)
                DeviceFirmwareRuntimeContract.Action.OTA_STATUS -> otaStatus
                DeviceFirmwareRuntimeContract.Action.OTA_START -> DeviceFirmwareOtaStartAccepted(
                    accepted = true,
                    request = DeviceFirmwareOtaStartRequestEcho(
                        urlScheme = "https",
                        version = "2.0.0",
                        expectedSize = FIRMWARE_SIZE,
                        applyNow = false,
                        allowInsecureHttp = false,
                        productKey = "DOSING_DOSE_PRO_2",
                        productId = "com.aqualight.dosing.dose_pro_2",
                        model = "dose_pro_2",
                        hardwareRevision = "2.0"
                    ),
                    ota = otaSnapshot(
                        phase = DeviceFirmwareOtaPhase.STARTING,
                        active = true,
                        progressPermille = 0
                    )
                )
                DeviceFirmwareRuntimeContract.Action.OTA_CLEAR -> DeviceFirmwareOtaClearTypedResult(
                    cleared = true,
                    previous = DeviceFirmwareOtaClearPrevious(
                        phase = DeviceFirmwareOtaPhase.SUCCEEDED,
                        phaseRaw = DeviceFirmwareOtaPhase.SUCCEEDED.wireValue,
                        restartRequired = true,
                        restartScheduled = false,
                        targetVersion = "2.0.0",
                        lastError = "",
                        lastErrorField = ""
                    ),
                    ota = otaSnapshot(
                        phase = DeviceFirmwareOtaPhase.IDLE,
                        active = false,
                        progressPermille = 0,
                        targetVersion = ""
                    )
                )
                else -> error("Unexpected firmware action ${command.action}")
            }
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "${command.action}-${actions.size}",
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = if (command.action == DeviceFirmwareRuntimeContract.Action.OTA_START) {
                    202
                } else {
                    200
                },
                value = value as T
            )
        }
    }

    private fun otaEvent(
        action: String,
        snapshot: DeviceFirmwareOtaSnapshot
    ): AqlWsIncomingMessage.Event = AqlWsIncomingMessage.Event(
        id = "event-${snapshot.progressPermille}",
        type = "event",
        module = DeviceFirmwareRuntimeContract.MODULE,
        action = action,
        data = JSONObject()
            .put("phase", snapshot.phaseRaw)
            .put("active", snapshot.active)
            .put("completed", snapshot.completed)
            .put("success", snapshot.success)
            .put("failed", snapshot.failed)
            .put("restartRequired", snapshot.restartRequired)
            .put("restartScheduled", snapshot.restartScheduled)
            .put("allowInsecureHttp", snapshot.allowInsecureHttp)
            .put("startedAtMs", snapshot.startedAtMs)
            .put("finishedAtMs", snapshot.finishedAtMs)
            .put("bytesWritten", snapshot.bytesWritten)
            .put("contentLength", snapshot.contentLength)
            .put("progressPermille", snapshot.progressPermille)
            .put("progressPercent", snapshot.progressPercent)
            .put("targetVersion", snapshot.targetVersion)
            .put("sha256Expected", snapshot.sha256Expected)
            .put("sha256Actual", snapshot.sha256Actual)
            .put("lastError", snapshot.lastError)
            .put("lastErrorField", snapshot.lastErrorField)
            .put("urlScheme", snapshot.urlScheme)
            .put("httpStatus", snapshot.httpStatus)
            .put("runtimeTransport", "websocket")
            .put("binaryTransfer", "firmware-download")
    )

    private fun otaSnapshot(
        phase: DeviceFirmwareOtaPhase,
        active: Boolean,
        progressPermille: Int,
        restartRequired: Boolean = false,
        targetVersion: String = "2.0.0"
    ): DeviceFirmwareOtaSnapshot = DeviceFirmwareOtaSnapshot(
        phase = phase,
        phaseRaw = phase.wireValue,
        active = active,
        completed = phase.isTerminal,
        success = phase == DeviceFirmwareOtaPhase.SUCCEEDED,
        failed = phase == DeviceFirmwareOtaPhase.FAILED,
        restartRequired = restartRequired,
        restartScheduled = false,
        allowInsecureHttp = false,
        startedAtMs = if (phase == DeviceFirmwareOtaPhase.IDLE) 0L else 1L,
        finishedAtMs = if (phase.isTerminal) 2L else 0L,
        bytesWritten = FIRMWARE_SIZE.toLong() * progressPermille / 1_000L,
        contentLength = if (phase == DeviceFirmwareOtaPhase.IDLE) 0L else FIRMWARE_SIZE.toLong(),
        progressPermille = progressPermille,
        progressPercent = progressPermille / 10.0,
        targetVersion = targetVersion,
        sha256Expected = if (phase == DeviceFirmwareOtaPhase.IDLE) "" else "a".repeat(64),
        sha256Actual = if (phase == DeviceFirmwareOtaPhase.SUCCEEDED) "a".repeat(64) else "",
        lastError = "",
        lastErrorField = "",
        urlScheme = if (phase == DeviceFirmwareOtaPhase.IDLE) "" else "https",
        httpStatus = if (phase == DeviceFirmwareOtaPhase.IDLE) 0 else 200
    )

    private fun firmwareStatus(version: String): DeviceFirmwareStatus = DeviceFirmwareStatus(
        version = version,
        productKey = "DOSING_DOSE_PRO_2",
        productId = "com.aqualight.dosing.dose_pro_2",
        model = "dose_pro_2",
        hardwareRevision = "2.0",
        otaSupported = true
    )

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

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-COORDINATOR")
        const val FIRMWARE_SIZE = 1_048_576
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v2.0.0/manifest-stable.json"
    }
}
