package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareOperationResult as AppOperationResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One shared OTA state machine used by every family-specific Settings update screen. */
@Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)
internal class DeviceOtaCoordinator(
    private val snapshotProvider: (DeviceUid) -> DeviceSnapshot?,
    private val connectRuntime: (DeviceUid) -> Result<Unit>,
    private val updaterProvider: () -> DeviceFirmwareUpdateRepository?,
    runtimeEvents: SharedFlow<AqlWsEvent>?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Closeable {

    private data class SelectedPlan(
        val dataPlan: DeviceFirmwareUpdatePlan,
        val applicationPlan: PreparedDeviceFirmwareUpdate
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val states = ConcurrentHashMap<DeviceUid, MutableStateFlow<DeviceOtaState>>()
    private val selectedPlans = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()
    private val versionVerificationLocks = ConcurrentHashMap<DeviceUid, Mutex>()

    init {
        if (runtimeEvents != null) {
            scope.launch {
                runtimeEvents.collect { event -> processEvent(event) }
            }
        }
    }

    fun observe(deviceUid: DeviceUid): StateFlow<DeviceOtaState> = stateFlow(deviceUid).asStateFlow()

    suspend fun checkAvailability(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val state = stateFlow(deviceUid)
        val initial = snapshotProvider(deviceUid)
        state.value = DeviceOtaState.Checking(
            deviceUid = deviceUid.value,
            currentVersion = initial?.firmwareVersion.orEmpty()
        )
        return runCatching {
            val snapshot = requireNotNull(initial) { "Device snapshot is not available." }
            require(snapshot.hasValidatedRuntimeMetadata) {
                "OTA requires current authenticated runtime metadata."
            }
            if (!snapshot.capabilities.ota) {
                val unsupported = DeviceOtaState.Unsupported(
                    deviceUid = deviceUid.value,
                    reason = "This exact device profile does not support OTA."
                )
                state.value = unsupported
                return Result.success(unsupported)
            }
            connectRuntime(deviceUid).getOrThrow()
            val current = requireNotNull(snapshotProvider(deviceUid)) {
                "Device snapshot disappeared during OTA availability check."
            }
            require(current.runtimeMetadataGeneration == snapshot.runtimeMetadataGeneration) {
                "Runtime metadata changed during OTA availability check."
            }
            val updater = requireNotNull(updaterProvider()) {
                "Firmware update runtime is not configured."
            }
            when (
                val availability = updater.fetchAndEvaluateUpdate(
                    snapshot = current,
                    manifestUrl = manifestUrl,
                    applyNow = applyNow
                ).getOrThrow()
            ) {
                is DeviceFirmwareAvailability.UpToDate -> DeviceOtaState.UpToDate(
                    deviceUid = deviceUid.value,
                    currentVersion = availability.currentVersion,
                    latestVersion = availability.latestVersion,
                    releaseContent = availability.releaseContent
                ).also { upToDate ->
                    selectedPlans.remove(deviceUid)
                    state.value = upToDate
                }
                is DeviceFirmwareAvailability.UpdateAvailable -> {
                    val applicationPlan = availability.plan.toApplicationPlan()
                    selectedPlans[deviceUid] = SelectedPlan(availability.plan, applicationPlan)
                    DeviceOtaState.UpdateAvailable(applicationPlan).also { available ->
                        state.value = available
                    }
                }
            }
        }.onFailure { error ->
            state.value = DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                message = error.safeMessage("OTA availability check failed."),
                recoverable = true
            )
        }
    }

    suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): AppOperationResult {
        val deviceUid = runCatching { DeviceUid(plan.deviceUid) }.getOrElse { error ->
            return AppOperationResult(
                successful = false,
                errorMessage = error.safeMessage("OTA plan contains an invalid device uid.")
            )
        }
        return startLock(deviceUid).withLock {
            startUpdateLocked(deviceUid, plan)
        }
    }

    private suspend fun startUpdateLocked(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate
    ): AppOperationResult {
        val selected = selectedPlans[deviceUid]
            ?: return AppOperationResult(
                successful = false,
                errorMessage = "No prepared OTA plan exists for this device."
            )
        if (selected.applicationPlan != plan) {
            return AppOperationResult(
                successful = false,
                errorMessage = "OTA plan differs from the selected exact artifact."
            )
        }
        if (stateFlow(deviceUid).value.isActiveOtaState) {
            return AppOperationResult(
                successful = false,
                errorMessage = "An OTA operation is already active for this device."
            )
        }
        val snapshot = snapshotProvider(deviceUid)
            ?: return AppOperationResult(
                successful = false,
                errorMessage = "Device snapshot is not available."
            )
        val validationError = validatePlanAgainstCurrentSnapshot(selected, snapshot)
        if (validationError != null) {
            selectedPlans.remove(deviceUid)
            stateFlow(deviceUid).value = DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                message = validationError,
                recoverable = true
            )
            return AppOperationResult(successful = false, errorMessage = validationError)
        }
        val updater = updaterProvider()
            ?: return AppOperationResult(
                successful = false,
                errorMessage = "Firmware update runtime is not configured."
            )

        val outcome = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.startUpdate(selected.dataPlan)
        }.getOrElse { error ->
            return AppOperationResult(
                successful = false,
                errorMessage = error.safeMessage("Firmware OTA start failed.")
            )
        }

        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            val echo = outcome.value.request
            if (echo == null || !echo.matches(selected.dataPlan.payload)) {
                fail(deviceUid, "Firmware OTA request echo differs from the selected plan.")
                return AppOperationResult(
                    successful = false,
                    errorMessage = "Firmware OTA request echo differs from the selected plan."
                )
            }
            stateFlow(deviceUid).value = DeviceOtaState.Starting(plan, outcome.messageId)
            applySnapshot(deviceUid, outcome.value.ota, selected)
        }
        return outcome.toApplicationResult()
    }

    suspend fun requestStatus(deviceUid: DeviceUid): AppOperationResult {
        val updater = updaterProvider()
            ?: return AppOperationResult(
                successful = false,
                errorMessage = "Firmware update runtime is not configured."
            )
        val outcome = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.requestOtaStatus(deviceUid)
        }.getOrElse { error ->
            return AppOperationResult(
                successful = false,
                errorMessage = error.safeMessage("Firmware OTA status request failed.")
            )
        }
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            val current = stateFlow(deviceUid).value
            if (
                outcome.value.phase == DeviceFirmwareOtaPhase.IDLE &&
                current is DeviceOtaState.Recovering &&
                current.targetVersion.isNotBlank()
            ) {
                verifyInstalledVersion(
                    deviceUid = deviceUid,
                    expectedVersion = current.targetVersion,
                    releaseContent = selectedPlans[deviceUid]?.applicationPlan?.releaseContent
                        ?: DeviceFirmwareReleaseContent.EMPTY
                )
            } else {
                applySnapshot(deviceUid, outcome.value, selectedPlans[deviceUid])
            }
        }
        return outcome.toApplicationResult()
    }

    suspend fun clearStatus(deviceUid: DeviceUid): AppOperationResult {
        val updater = updaterProvider()
            ?: return AppOperationResult(
                successful = false,
                errorMessage = "Firmware update runtime is not configured."
            )
        val outcome = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.clearOtaStatus(deviceUid)
        }.getOrElse { error ->
            return AppOperationResult(
                successful = false,
                errorMessage = error.safeMessage("Firmware OTA clear failed.")
            )
        }
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            selectedPlans.remove(deviceUid)
            stateFlow(deviceUid).value = DeviceOtaState.Idle(deviceUid.value)
        }
        return outcome.toApplicationResult()
    }

    private suspend fun processEvent(event: AqlWsEvent) {
        when (event) {
            is AqlWsEvent.Authenticated -> recoverAfterAuthentication(event.deviceUid)
            is AqlWsEvent.Message -> processMessage(event.deviceUid, event.parsed)
            is AqlWsEvent.Closed -> markRecovering(event.deviceUid)
            is AqlWsEvent.Failure -> markRecovering(event.deviceUid)
            is AqlWsEvent.Opened -> Unit
        }
    }

    private fun processMessage(deviceUid: DeviceUid, message: AqlWsIncomingMessage) {
        if (message.module != DeviceFirmwareRuntimeContract.MODULE) return
        if (message is AqlWsIncomingMessage.Event) {
            processFirmwareEvent(deviceUid, message)
        }
    }

    private fun processFirmwareEvent(
        deviceUid: DeviceUid,
        event: AqlWsIncomingMessage.Event
    ) {
        if (
            event.action != DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS &&
            event.action != DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
        ) {
            return
        }
        runCatching { DeviceFirmwareCommandParsers.parseOtaEvent(event.data) }.fold(
            onSuccess = { snapshot -> applySnapshot(deviceUid, snapshot, selectedPlans[deviceUid]) },
            onFailure = { error -> fail(deviceUid, error.safeMessage("Invalid firmware OTA event.")) }
        )
    }

    private fun applySnapshot(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?
    ) {
        val plan = selected?.dataPlan
        if (plan != null) {
            if (snapshot.targetVersion.isNotBlank() && snapshot.targetVersion != plan.targetVersion) {
                fail(deviceUid, "Firmware OTA targetVersion differs from the selected artifact.")
                return
            }
            if (
                snapshot.sha256Expected.isNotBlank() &&
                !snapshot.sha256Expected.equals(plan.firmware.sha256, ignoreCase = true)
            ) {
                fail(deviceUid, "Firmware OTA expected SHA256 differs from the selected artifact.")
                return
            }
            if (snapshot.contentLength > 0L && snapshot.contentLength != plan.firmware.size.toLong()) {
                fail(deviceUid, "Firmware OTA content length differs from the selected artifact.")
                return
            }
        }
        val releaseContent = selected?.applicationPlan?.releaseContent
            ?: DeviceFirmwareReleaseContent.EMPTY
        val targetVersion = snapshot.targetVersion.ifBlank { plan?.targetVersion.orEmpty() }
        stateFlow(deviceUid).value = when (snapshot.phase) {
            DeviceFirmwareOtaPhase.IDLE -> DeviceOtaState.Idle(deviceUid.value)
            DeviceFirmwareOtaPhase.STARTING,
            DeviceFirmwareOtaPhase.SAFE_MODE,
            DeviceFirmwareOtaPhase.DOWNLOADING,
            DeviceFirmwareOtaPhase.WRITING,
            DeviceFirmwareOtaPhase.VERIFYING -> DeviceOtaState.InProgress(
                deviceUid = deviceUid.value,
                targetVersion = targetVersion,
                phase = snapshot.phase.toApplicationPhase(),
                progressPermille = snapshot.progressPermille,
                bytesWritten = snapshot.bytesWritten,
                contentLength = snapshot.contentLength,
                releaseContent = releaseContent
            )
            DeviceFirmwareOtaPhase.SUCCEEDED -> if (snapshot.restartRequired) {
                DeviceOtaState.RestartRequired(
                    deviceUid = deviceUid.value,
                    targetVersion = targetVersion,
                    restartScheduled = snapshot.restartScheduled,
                    releaseContent = releaseContent
                )
            } else {
                selectedPlans.remove(deviceUid)
                DeviceOtaState.Succeeded(
                    deviceUid = deviceUid.value,
                    targetVersion = targetVersion,
                    releaseContent = releaseContent
                )
            }
            DeviceFirmwareOtaPhase.FAILED -> DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                message = snapshot.lastError.ifBlank { "Firmware OTA failed." },
                field = snapshot.lastErrorField,
                recoverable = false
            )
        }
    }

    private suspend fun recoverAfterAuthentication(deviceUid: DeviceUid) {
        when (val current = stateFlow(deviceUid).value) {
            is DeviceOtaState.RestartRequired -> verifyInstalledVersion(
                deviceUid = deviceUid,
                expectedVersion = current.targetVersion,
                releaseContent = current.releaseContent
            )
            is DeviceOtaState.Starting,
            is DeviceOtaState.InProgress,
            is DeviceOtaState.Recovering -> requestStatus(deviceUid)
            else -> Unit
        }
    }

    private suspend fun verifyInstalledVersion(
        deviceUid: DeviceUid,
        expectedVersion: String,
        releaseContent: DeviceFirmwareReleaseContent
    ) {
        versionVerificationLock(deviceUid).withLock {
            val current = stateFlow(deviceUid).value
            val stillExpected = when (current) {
                is DeviceOtaState.RestartRequired -> current.targetVersion == expectedVersion
                is DeviceOtaState.Recovering -> current.targetVersion == expectedVersion
                else -> false
            }
            if (!stillExpected) return@withLock

            val updater = updaterProvider()
            if (updater == null) {
                fail(deviceUid, "Firmware update runtime is not configured.", recoverable = true)
                return@withLock
            }
            val outcome = runCatching {
                connectRuntime(deviceUid).getOrThrow()
                updater.requestFirmwareStatus(deviceUid)
            }.getOrElse { error ->
                fail(
                    deviceUid,
                    error.safeMessage("Installed firmware verification failed."),
                    recoverable = true
                )
                return@withLock
            }
            when (outcome) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    val status = outcome.value
                    val selected = selectedPlans[deviceUid]
                    if (selected != null && !status.matches(selected.dataPlan)) {
                        fail(
                            deviceUid,
                            "Firmware identity changed while verifying the installed OTA version."
                        )
                    } else if (status.version != expectedVersion) {
                        fail(
                            deviceUid,
                            "Firmware rebooted with version ${status.version}; expected $expectedVersion."
                        )
                    } else {
                        selectedPlans.remove(deviceUid)
                        stateFlow(deviceUid).value = DeviceOtaState.Succeeded(
                            deviceUid = deviceUid.value,
                            targetVersion = expectedVersion,
                            releaseContent = releaseContent
                        )
                    }
                }
                else -> fail(
                    deviceUid = deviceUid,
                    message = outcome.errorMessage(),
                    recoverable = true
                )
            }
        }
    }

    private fun markRecovering(deviceUid: DeviceUid) {
        val current = stateFlow(deviceUid).value
        if (!current.isActiveOtaState) return
        stateFlow(deviceUid).value = DeviceOtaState.Recovering(
            deviceUid = deviceUid.value,
            targetVersion = current.targetVersionOrEmpty,
            progressPermille = current.progressPermilleOrZero
        )
    }

    private fun validatePlanAgainstCurrentSnapshot(
        selected: SelectedPlan,
        snapshot: DeviceSnapshot
    ): String? = when {
        !snapshot.hasValidatedRuntimeMetadata -> "Current runtime metadata is not validated."
        snapshot.runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration ->
            "OTA plan expired because runtime metadata generation changed."
        snapshot.product.productKey != selected.dataPlan.productKey -> "OTA plan productKey changed."
        snapshot.product.productId != selected.dataPlan.productId -> "OTA plan productId changed."
        snapshot.product.model != selected.dataPlan.model -> "OTA plan model changed."
        snapshot.product.hardwareRevision != selected.dataPlan.hardwareRevision ->
            "OTA plan hardwareRevision changed."
        else -> null
    }

    private fun fail(
        deviceUid: DeviceUid,
        message: String,
        field: String = "",
        recoverable: Boolean = false
    ) {
        stateFlow(deviceUid).value = DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            message = message.ifBlank { "OTA operation failed." },
            field = field,
            recoverable = recoverable
        )
    }

    private fun startLock(deviceUid: DeviceUid): Mutex =
        startLocks.getOrPut(deviceUid) { Mutex() }

    private fun versionVerificationLock(deviceUid: DeviceUid): Mutex =
        versionVerificationLocks.getOrPut(deviceUid) { Mutex() }

    private fun stateFlow(deviceUid: DeviceUid): MutableStateFlow<DeviceOtaState> =
        states.getOrPut(deviceUid) { MutableStateFlow(DeviceOtaState.Idle(deviceUid.value)) }

    override fun close() {
        selectedPlans.clear()
        startLocks.clear()
        versionVerificationLocks.clear()
        states.clear()
        scope.cancel()
    }
}

private fun DeviceFirmwareUpdatePlan.toApplicationPlan(): PreparedDeviceFirmwareUpdate =
    PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid.value,
        currentVersion = currentVersion,
        targetVersion = targetVersion,
        channel = channel,
        environment = env,
        productKey = productKey,
        productId = productId,
        model = model,
        hardwareRevision = hardwareRevision,
        displayName = displayName,
        filename = firmware.filename,
        downloadUrl = firmware.url,
        sha256 = firmware.sha256,
        sizeBytes = firmware.size,
        applyNow = payload.applyNow,
        runtimeMetadataGeneration = runtimeMetadataGeneration,
        manifestTag = manifestTag,
        releaseContent = releaseContent
    )

private fun DeviceRuntimeCommandOutcome<*>.toApplicationResult(): AppOperationResult =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> AppOperationResult(
            successful = true,
            correlationId = messageId
        )
        else -> AppOperationResult(
            successful = false,
            errorMessage = errorMessage()
        )
    }

private fun DeviceRuntimeCommandOutcome<*>.errorMessage(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> ""
    is DeviceRuntimeCommandOutcome.NotConnected -> "Device runtime is not connected."
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> "Device runtime is not authenticated."
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        "Firmware command is not supported by this device."
    is DeviceRuntimeCommandOutcome.SendFailed -> "WebSocket send failed."
    is DeviceRuntimeCommandOutcome.Timeout -> "Firmware command timed out."
    is DeviceRuntimeCommandOutcome.FirmwareError -> message.ifBlank { code.ifBlank { "Firmware rejected the operation." } }
    is DeviceRuntimeCommandOutcome.ProtocolError -> reason.ifBlank { "Firmware response violated the runtime contract." }
    is DeviceRuntimeCommandOutcome.LocalStateError -> reason.ifBlank { "Local OTA state update failed." }
    is DeviceRuntimeCommandOutcome.Cancelled -> reason.ifBlank { "Firmware command was cancelled." }
}

private fun Throwable.safeMessage(fallback: String): String =
    message?.takeIf(String::isNotBlank) ?: fallback

private fun DeviceFirmwareOtaStartRequestEcho.matches(
    payload: DeviceFirmwareOtaStartPayload
): Boolean = urlScheme == "https" &&
    version == payload.version &&
    expectedSize == payload.expectedSize &&
    applyNow == payload.applyNow &&
    !allowInsecureHttp &&
    productKey == payload.productKey &&
    productId == payload.productId &&
    model == payload.model &&
    hardwareRevision == payload.hardwareRevision

private fun DeviceFirmwareStatus.matches(plan: DeviceFirmwareUpdatePlan): Boolean =
    productKey == plan.productKey &&
        productId == plan.productId &&
        model == plan.model &&
        hardwareRevision == plan.hardwareRevision

private fun DeviceFirmwareOtaPhase.toApplicationPhase(): DeviceOtaProgressPhase = when (this) {
    DeviceFirmwareOtaPhase.STARTING -> DeviceOtaProgressPhase.STARTING
    DeviceFirmwareOtaPhase.SAFE_MODE -> DeviceOtaProgressPhase.SAFE_MODE
    DeviceFirmwareOtaPhase.DOWNLOADING -> DeviceOtaProgressPhase.DOWNLOADING
    DeviceFirmwareOtaPhase.WRITING -> DeviceOtaProgressPhase.WRITING
    DeviceFirmwareOtaPhase.VERIFYING -> DeviceOtaProgressPhase.VERIFYING
    DeviceFirmwareOtaPhase.IDLE,
    DeviceFirmwareOtaPhase.SUCCEEDED,
    DeviceFirmwareOtaPhase.FAILED -> error("Non-progress OTA phase cannot map to progress.")
}

private val DeviceOtaState.isActiveOtaState: Boolean
    get() = this is DeviceOtaState.Starting ||
        this is DeviceOtaState.InProgress ||
        this is DeviceOtaState.Recovering

private val DeviceOtaState.targetVersionOrEmpty: String
    get() = when (this) {
        is DeviceOtaState.Starting -> plan.targetVersion
        is DeviceOtaState.InProgress -> targetVersion
        is DeviceOtaState.Recovering -> targetVersion
        else -> ""
    }

private val DeviceOtaState.progressPermilleOrZero: Int
    get() = when (this) {
        is DeviceOtaState.InProgress -> progressPermille
        is DeviceOtaState.Recovering -> progressPermille
        else -> 0
    }
