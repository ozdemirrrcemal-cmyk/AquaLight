package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult as AppCommandResult
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
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
import kotlinx.coroutines.launch

/** One shared OTA state machine used by every family-specific Settings update screen. */
internal class DeviceOtaCoordinator(
    private val snapshotProvider: (DeviceUid) -> DeviceSnapshot?,
    private val connectRuntime: (DeviceUid) -> Result<Unit>,
    private val updaterProvider: () -> DeviceFirmwareUpdateRepository?,
    runtimeEvents: SharedFlow<AqlWsEvent>?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Closeable {

    private enum class PendingKind { START, STATUS, CLEAR }

    private data class SelectedPlan(
        val dataPlan: DeviceFirmwareUpdatePlan,
        val applicationPlan: PreparedDeviceFirmwareUpdate
    )

    private data class PendingRequest(
        val deviceUid: DeviceUid,
        val kind: PendingKind,
        val selectedPlan: SelectedPlan?
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val states = ConcurrentHashMap<DeviceUid, MutableStateFlow<DeviceOtaState>>()
    private val selectedPlans = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    init {
        if (runtimeEvents != null) {
            scope.launch {
                runtimeEvents.collect(::processEvent)
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
                message = error.message ?: error::class.java.simpleName,
                recoverable = true
            )
        }
    }

    fun startUpdate(plan: PreparedDeviceFirmwareUpdate): AppCommandResult {
        val deviceUid = runCatching { DeviceUid(plan.deviceUid) }.getOrElse { error ->
            return AppCommandResult(false, errorMessage = error.message.orEmpty())
        }
        val selected = selectedPlans[deviceUid]
            ?: return AppCommandResult(false, errorMessage = "No prepared OTA plan exists for this device.")
        if (selected.applicationPlan != plan) {
            return AppCommandResult(false, errorMessage = "OTA plan differs from the selected exact artifact.")
        }
        val currentState = stateFlow(deviceUid).value
        if (currentState.isActiveOtaState) {
            return AppCommandResult(false, errorMessage = "An OTA operation is already active for this device.")
        }
        val snapshot = snapshotProvider(deviceUid)
            ?: return AppCommandResult(false, errorMessage = "Device snapshot is not available.")
        val validationError = validatePlanAgainstCurrentSnapshot(selected, snapshot)
        if (validationError != null) {
            selectedPlans.remove(deviceUid)
            stateFlow(deviceUid).value = DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                message = validationError,
                recoverable = true
            )
            return AppCommandResult(false, errorMessage = validationError)
        }
        val updater = updaterProvider()
            ?: return AppCommandResult(false, errorMessage = "Firmware update runtime is not configured.")
        val command = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.startUpdate(selected.dataPlan)
        }.getOrElse { error ->
            return AppCommandResult(false, errorMessage = error.message.orEmpty())
        }
        if (command.isSuccess && command.messageId.isNotBlank()) {
            pendingRequests[command.messageId] = PendingRequest(
                deviceUid = deviceUid,
                kind = PendingKind.START,
                selectedPlan = selected
            )
            stateFlow(deviceUid).value = DeviceOtaState.Starting(plan, command.messageId)
        }
        return command.toApplicationResult()
    }

    fun requestStatus(deviceUid: DeviceUid): AppCommandResult {
        val updater = updaterProvider()
            ?: return AppCommandResult(false, errorMessage = "Firmware update runtime is not configured.")
        val command = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.requestOtaStatus(deviceUid)
        }.getOrElse { error ->
            return AppCommandResult(false, errorMessage = error.message.orEmpty())
        }
        if (command.isSuccess && command.messageId.isNotBlank()) {
            pendingRequests[command.messageId] = PendingRequest(
                deviceUid = deviceUid,
                kind = PendingKind.STATUS,
                selectedPlan = selectedPlans[deviceUid]
            )
        }
        return command.toApplicationResult()
    }

    fun clearStatus(deviceUid: DeviceUid): AppCommandResult {
        val updater = updaterProvider()
            ?: return AppCommandResult(false, errorMessage = "Firmware update runtime is not configured.")
        val command = runCatching {
            connectRuntime(deviceUid).getOrThrow()
            updater.clearOtaStatus(deviceUid)
        }.getOrElse { error ->
            return AppCommandResult(false, errorMessage = error.message.orEmpty())
        }
        if (command.isSuccess && command.messageId.isNotBlank()) {
            pendingRequests[command.messageId] = PendingRequest(
                deviceUid = deviceUid,
                kind = PendingKind.CLEAR,
                selectedPlan = selectedPlans[deviceUid]
            )
        }
        return command.toApplicationResult()
    }

    private fun processEvent(event: AqlWsEvent) {
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
        when (message) {
            is AqlWsIncomingMessage.Response -> processResponse(deviceUid, message)
            is AqlWsIncomingMessage.Event -> processFirmwareEvent(deviceUid, message)
            is AqlWsIncomingMessage.Error -> processError(deviceUid, message)
        }
    }

    private fun processResponse(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ) {
        val pending = pendingRequests.remove(response.id) ?: return
        if (pending.deviceUid != deviceUid || !response.ok || response.statusCode !in 200..299) {
            fail(deviceUid, "OTA response correlation or success status mismatch.")
            return
        }
        val expectedAction = when (pending.kind) {
            PendingKind.START -> DeviceFirmwareRuntimeContract.Action.OTA_START
            PendingKind.STATUS -> DeviceFirmwareRuntimeContract.Action.OTA_STATUS
            PendingKind.CLEAR -> DeviceFirmwareRuntimeContract.Action.OTA_CLEAR
        }
        if (response.action != expectedAction) {
            fail(deviceUid, "OTA response action mismatch.")
            return
        }
        when (pending.kind) {
            PendingKind.START -> processStartAccepted(pending, response)
            PendingKind.STATUS -> DeviceFirmwareStatusParser.parseOtaStatusResponseExact(response.data)
                .fold(
                    onSuccess = { snapshot -> applySnapshot(deviceUid, snapshot, pending.selectedPlan) },
                    onFailure = { error -> fail(deviceUid, error.message.orEmpty()) }
                )
            PendingKind.CLEAR -> DeviceFirmwareStatusParser.parseOtaClearResultExact(response.data)
                .fold(
                    onSuccess = {
                        selectedPlans.remove(deviceUid)
                        stateFlow(deviceUid).value = DeviceOtaState.Idle(deviceUid.value)
                    },
                    onFailure = { error -> fail(deviceUid, error.message.orEmpty()) }
                )
        }
    }

    private fun processStartAccepted(
        pending: PendingRequest,
        response: AqlWsIncomingMessage.Response
    ) {
        val selected = pending.selectedPlan
        if (selected == null) {
            fail(pending.deviceUid, "OTA start response has no selected plan.")
            return
        }
        DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(response.data).fold(
            onSuccess = { accepted ->
                val echo = accepted.request
                if (echo == null || !echo.matches(selected.dataPlan.payload)) {
                    fail(pending.deviceUid, "Firmware OTA request echo differs from the selected plan.")
                } else {
                    applySnapshot(pending.deviceUid, accepted.ota, selected)
                }
            },
            onFailure = { error -> fail(pending.deviceUid, error.message.orEmpty()) }
        )
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
        DeviceFirmwareStatusParser.parseOtaProgressEventExact(event.data).fold(
            onSuccess = { snapshot -> applySnapshot(deviceUid, snapshot, selectedPlans[deviceUid]) },
            onFailure = { error -> fail(deviceUid, error.message.orEmpty()) }
        )
    }

    private fun processError(deviceUid: DeviceUid, error: AqlWsIncomingMessage.Error) {
        val pending = pendingRequests.remove(error.id) ?: return
        if (pending.deviceUid == deviceUid) {
            fail(
                deviceUid = deviceUid,
                message = error.message.ifBlank { "Firmware rejected the OTA command." },
                field = error.field
            )
        }
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
            ?: com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent.EMPTY
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
            DeviceFirmwareOtaPhase.UNKNOWN -> DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                message = "Firmware reported an unknown OTA phase.",
                recoverable = false
            )
        }
    }

    private fun recoverAfterAuthentication(deviceUid: DeviceUid) {
        if (stateFlow(deviceUid).value.isActiveOtaState) {
            requestStatus(deviceUid)
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

    private fun fail(deviceUid: DeviceUid, message: String, field: String = "") {
        stateFlow(deviceUid).value = DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            message = message.ifBlank { "OTA operation failed." },
            field = field,
            recoverable = false
        )
    }

    private fun stateFlow(deviceUid: DeviceUid): MutableStateFlow<DeviceOtaState> =
        states.getOrPut(deviceUid) { MutableStateFlow(DeviceOtaState.Idle(deviceUid.value)) }

    override fun close() {
        pendingRequests.clear()
        selectedPlans.clear()
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

private fun DeviceFirmwareCommandResult.toApplicationResult(): AppCommandResult = AppCommandResult(
    sent = sent,
    messageId = messageId,
    errorMessage = errorMessage
)

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

private fun DeviceFirmwareOtaPhase.toApplicationPhase(): DeviceOtaProgressPhase = when (this) {
    DeviceFirmwareOtaPhase.STARTING -> DeviceOtaProgressPhase.STARTING
    DeviceFirmwareOtaPhase.SAFE_MODE -> DeviceOtaProgressPhase.SAFE_MODE
    DeviceFirmwareOtaPhase.DOWNLOADING -> DeviceOtaProgressPhase.DOWNLOADING
    DeviceFirmwareOtaPhase.WRITING -> DeviceOtaProgressPhase.WRITING
    DeviceFirmwareOtaPhase.VERIFYING -> DeviceOtaProgressPhase.VERIFYING
    else -> error("Terminal/unknown OTA phase cannot map to progress.")
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
