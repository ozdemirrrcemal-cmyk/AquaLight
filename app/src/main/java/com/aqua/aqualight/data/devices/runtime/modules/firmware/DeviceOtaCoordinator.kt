package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult as AppCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareFailure
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureKind
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureSource
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureStage
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One shared, device-isolated OTA state machine used by every commercial product family. */
@Suppress("TooManyFunctions", "LongParameterList")
internal class DeviceOtaCoordinator(
    private val snapshotProvider: (DeviceUid) -> DeviceSnapshot?,
    private val connectRuntime: (DeviceUid) -> Result<Unit>,
    private val updaterProvider: () -> DeviceFirmwareUpdateRepository?,
    runtimeLifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>?,
    runtimeTypedEvents: SharedFlow<DeviceRuntimeTypedEvent>? = null,
    snapshotUpdates: StateFlow<Map<DeviceUid, DeviceSnapshot>>? = null,
    private val recoverRuntime: (DeviceUid) -> Result<Unit> = connectRuntime,
    private val refreshDiscovery: suspend () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val restartWaitMillis: Long = DEFAULT_RESTART_WAIT_MILLIS,
    private val discoverySettleMillis: Long = DEFAULT_DISCOVERY_SETTLE_MILLIS
) : Closeable {

    private data class SelectedPlan(
        val dataPlan: DeviceFirmwareUpdatePlan,
        val applicationPlan: PreparedDeviceFirmwareUpdate,
        val runtimeGeneration: DeviceRuntimeConnectionGeneration? = null
    )

    private data class StartContext(
        val selected: SelectedPlan,
        val updater: DeviceFirmwareUpdateRepository
    )

    private class StartPreparationFailure(
        val failure: DeviceFirmwareFailure,
        val publishFailure: Boolean
    ) : IllegalStateException(failure.technicalMessage)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val states = ConcurrentHashMap<DeviceUid, MutableStateFlow<DeviceOtaState>>()
    private val selectedPlans = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val pendingVersionVerification = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()
    private val recoveryJobs = ConcurrentHashMap<DeviceUid, Job>()

    init {
        require(restartWaitMillis >= 0L)
        require(discoverySettleMillis >= 0L)
        runtimeLifecycleEvents?.let { events ->
            scope.launch { events.collect(::processLifecycleEvent) }
        }
        runtimeTypedEvents?.let { events ->
            scope.launch { events.collect(::processTypedEvent) }
        }
        snapshotUpdates?.let { updates ->
            scope.launch { updates.collect(::processSnapshotUpdates) }
        }
    }

    fun observe(deviceUid: DeviceUid): StateFlow<DeviceOtaState> = stateFlow(deviceUid).asStateFlow()

    suspend fun checkAvailability(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val state = stateFlow(deviceUid)
        if (hasActiveOperation(deviceUid)) {
            return Result.failure(
                IllegalStateException("An OTA operation is already active for this device.")
            )
        }
        val initial = snapshotProvider(deviceUid)
        state.value = DeviceOtaState.Checking(
            deviceUid = deviceUid.value,
            currentVersion = initial?.firmwareVersion.orEmpty()
        )
        return runCatching {
            resolveAvailability(deviceUid, initial, manifestUrl, applyNow)
        }.onSuccess { availability ->
            state.value = availability
        }.onFailure { error ->
            val failure = DeviceFirmwareFailureMapper.fromThrowable(
                error = error,
                source = DeviceFirmwareFailureSource.MANIFEST,
                stage = DeviceFirmwareFailureStage.AVAILABILITY,
                code = "availability_failed",
                recoverable = true
            )
            state.value = DeviceOtaState.Failed(deviceUid.value, failure)
        }
    }

    private suspend fun resolveAvailability(
        deviceUid: DeviceUid,
        initial: DeviceSnapshot?,
        manifestUrl: String,
        applyNow: Boolean
    ): DeviceOtaState {
        val snapshot = requireNotNull(initial) { "Device snapshot is not available." }
        require(snapshot.hasValidatedRuntimeMetadata) {
            "OTA requires current authenticated runtime metadata."
        }
        if (!snapshot.capabilities.ota) {
            return DeviceOtaState.Unsupported(
                deviceUid = deviceUid.value,
                reason = "This exact device profile does not support OTA."
            )
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
        val availability = updater.fetchAndEvaluateUpdate(
            snapshot = current,
            manifestUrl = manifestUrl,
            applyNow = applyNow
        ).getOrThrow()
        return applyAvailability(deviceUid, availability)
    }

    private fun applyAvailability(
        deviceUid: DeviceUid,
        availability: DeviceFirmwareAvailability
    ): DeviceOtaState = when (availability) {
        is DeviceFirmwareAvailability.UpToDate -> DeviceOtaState.UpToDate(
            deviceUid = deviceUid.value,
            currentVersion = availability.currentVersion,
            latestVersion = availability.latestVersion,
            releaseContent = availability.releaseContent
        ).also { clearPlanState(deviceUid) }
        is DeviceFirmwareAvailability.UpdateAvailable -> {
            val applicationPlan = availability.plan.toApplicationPlan()
            pendingVersionVerification.remove(deviceUid)
            selectedPlans[deviceUid] = SelectedPlan(
                dataPlan = availability.plan,
                applicationPlan = applicationPlan
            )
            DeviceOtaState.UpdateAvailable(applicationPlan)
        }
    }

    suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): AppCommandResult {
        val deviceUid = runCatching { DeviceUid(plan.deviceUid) }.getOrElse { error ->
            val failure = DeviceFirmwareFailureMapper.fromThrowable(
                error = error,
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "invalid_device_uid",
                field = "deviceUid",
                recoverable = false
            )
            return AppCommandResult(sent = false, failure = failure)
        }
        return startLock(deviceUid).withLock {
            startUpdateLocked(deviceUid, plan)
        }
    }

    private suspend fun startUpdateLocked(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate
    ): AppCommandResult = runCatching {
        prepareStartContext(deviceUid, plan)
    }.fold(
        onSuccess = { context -> dispatchStart(deviceUid, plan, context) },
        onFailure = { error -> handleStartPreparationFailure(deviceUid, error) }
    )

    private fun prepareStartContext(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate
    ): StartContext {
        val selected = selectedPlans[deviceUid] ?: rejectStart(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "No prepared OTA plan exists for this device.",
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "prepared_plan_missing",
                field = "plan",
                recoverable = false,
                kind = DeviceFirmwareFailureKind.INVALID_REQUEST
            )
        )
        if (selected.applicationPlan != plan) {
            rejectStart(
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = "OTA plan differs from the selected exact artifact.",
                    source = DeviceFirmwareFailureSource.ANDROID,
                    stage = DeviceFirmwareFailureStage.PREPARATION,
                    code = "prepared_plan_mismatch",
                    field = "plan",
                    recoverable = false,
                    kind = DeviceFirmwareFailureKind.INVALID_REQUEST
                )
            )
        }
        if (hasActiveOperation(deviceUid)) {
            rejectStart(
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = "An OTA operation is already active for this device.",
                    source = DeviceFirmwareFailureSource.ANDROID,
                    stage = DeviceFirmwareFailureStage.PREPARATION,
                    code = "operation_already_active",
                    field = "state",
                    recoverable = true,
                    kind = DeviceFirmwareFailureKind.INVALID_REQUEST
                )
            )
        }
        val snapshot = snapshotProvider(deviceUid) ?: rejectStart(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Device snapshot is not available.",
                source = DeviceFirmwareFailureSource.RUNTIME,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "device_snapshot_missing",
                recoverable = true,
                kind = DeviceFirmwareFailureKind.CONNECTION
            ),
            publishFailure = true
        )
        DeviceOtaValidator.planAgainstSnapshot(selected.dataPlan, snapshot)?.let { error ->
            rejectStart(
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = error,
                    source = DeviceFirmwareFailureSource.ANDROID,
                    stage = DeviceFirmwareFailureStage.PREPARATION,
                    code = "prepared_plan_expired",
                    recoverable = true
                ),
                publishFailure = true
            )
        }
        val updater = updaterProvider() ?: rejectStart(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Firmware update runtime is not configured.",
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "update_runtime_missing",
                recoverable = false,
                kind = DeviceFirmwareFailureKind.INTERNAL
            )
        )
        connectRuntime(deviceUid).exceptionOrNull()?.let { error ->
            rejectStart(
                DeviceFirmwareFailureMapper.fromThrowable(
                    error = error,
                    source = DeviceFirmwareFailureSource.RUNTIME,
                    stage = DeviceFirmwareFailureStage.PREPARATION,
                    code = "runtime_connect_failed",
                    recoverable = true
                ),
                publishFailure = true
            )
        }
        return StartContext(selected, updater)
    }

    private fun rejectStart(
        failure: DeviceFirmwareFailure,
        publishFailure: Boolean = false
    ): Nothing = throw StartPreparationFailure(failure, publishFailure)

    private suspend fun dispatchStart(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate,
        context: StartContext
    ): AppCommandResult =
        when (val outcome = context.updater.startUpdate(context.selected.dataPlan)) {
            is DeviceRuntimeCommandOutcome.Success -> {
                val accepted = outcome.value
                if (!accepted.accepted || !accepted.request.matches(context.selected.dataPlan.payload)) {
                    val failure = DeviceFirmwareFailureMapper.local(
                        technicalMessage =
                            "Firmware OTA request echo differs from the selected plan.",
                        source = DeviceFirmwareFailureSource.RUNTIME,
                        stage = DeviceFirmwareFailureStage.START,
                        code = "request_echo_mismatch",
                        requestId = outcome.messageId,
                        recoverable = false,
                        kind = DeviceFirmwareFailureKind.PROTOCOL
                    )
                    fail(deviceUid, failure)
                    AppCommandResult(
                        sent = true,
                        messageId = outcome.messageId,
                        failure = failure
                    )
                } else {
                    val active = context.selected.copy(runtimeGeneration = outcome.generation)
                    selectedPlans[deviceUid] = active
                    stateFlow(deviceUid).value = DeviceOtaState.Starting(
                        plan = plan,
                        requestId = outcome.messageId
                    )
                    applySnapshot(deviceUid, accepted.ota, active, outcome.generation)?.let { failure ->
                        AppCommandResult(
                            sent = true,
                            messageId = outcome.messageId,
                            failure = failure
                        )
                    } ?: outcome.toApplicationResult()
                }
            }
            else -> handleCommandFailure(
                deviceUid = deviceUid,
                outcome = outcome,
                stage = DeviceFirmwareFailureStage.START,
                preserveRecovery = false
            )
        }

    private fun handleStartPreparationFailure(
        deviceUid: DeviceUid,
        error: Throwable
    ): AppCommandResult {
        val failure = (error as? StartPreparationFailure)?.failure
            ?: DeviceFirmwareFailureMapper.fromThrowable(
                error = error,
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "start_preparation_failed",
                recoverable = false
            )
        if ((error as? StartPreparationFailure)?.publishFailure == true) {
            fail(deviceUid, failure)
        }
        return AppCommandResult(sent = false, failure = failure)
    }

    suspend fun requestStatus(deviceUid: DeviceUid): AppCommandResult =
        connectedUpdater(deviceUid).fold(
            onSuccess = { updater -> applyStatusOutcome(deviceUid, updater.requestOtaStatus(deviceUid)) },
            onFailure = { error ->
                AppCommandResult(
                    sent = false,
                    failure = DeviceFirmwareFailureMapper.fromThrowable(
                        error = error,
                        source = DeviceFirmwareFailureSource.RUNTIME,
                        stage = DeviceFirmwareFailureStage.STATUS,
                        code = "status_connection_failed",
                        recoverable = true
                    )
                )
            }
        )

    private fun applyStatusOutcome(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStatusResponse>
    ): AppCommandResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            val selected = selectedPlans[deviceUid]?.copy(runtimeGeneration = outcome.generation)
            if (selected != null) selectedPlans[deviceUid] = selected
            applySnapshot(deviceUid, outcome.value.ota, selected, outcome.generation)?.let { failure ->
                AppCommandResult(
                    sent = true,
                    messageId = outcome.messageId,
                    failure = failure
                )
            } ?: outcome.toApplicationResult()
        }
        else -> handleCommandFailure(
            deviceUid = deviceUid,
            outcome = outcome,
            stage = DeviceFirmwareFailureStage.STATUS,
            preserveRecovery = stateFlow(deviceUid).value is DeviceOtaState.Recovering
        )
    }

    suspend fun clearStatus(deviceUid: DeviceUid): AppCommandResult =
        connectedUpdater(deviceUid).fold(
            onSuccess = { updater -> applyClearOutcome(deviceUid, updater.clearOtaStatus(deviceUid)) },
            onFailure = { error ->
                AppCommandResult(
                    sent = false,
                    failure = DeviceFirmwareFailureMapper.fromThrowable(
                        error = error,
                        source = DeviceFirmwareFailureSource.RUNTIME,
                        stage = DeviceFirmwareFailureStage.CLEAR,
                        code = "clear_connection_failed",
                        recoverable = true
                    )
                )
            }
        )

    private fun applyClearOutcome(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult>
    ): AppCommandResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            clearPlanState(deviceUid)
            stateFlow(deviceUid).value = DeviceOtaState.Idle(deviceUid.value)
            outcome.toApplicationResult()
        }
        else -> handleCommandFailure(
            deviceUid = deviceUid,
            outcome = outcome,
            stage = DeviceFirmwareFailureStage.CLEAR,
            preserveRecovery = false
        )
    }

    private fun connectedUpdater(deviceUid: DeviceUid): Result<DeviceFirmwareUpdateRepository> =
        runCatching {
            val updater = requireNotNull(updaterProvider()) {
                "Firmware update runtime is not configured."
            }
            connectRuntime(deviceUid).getOrThrow()
            updater
        }

    private fun processLifecycleEvent(event: DeviceRuntimeLifecycleEvent) {
        when (event) {
            is DeviceRuntimeLifecycleEvent.Authenticated ->
                recoverAfterAuthentication(event.deviceUid)
            is DeviceRuntimeLifecycleEvent.Unavailable ->
                markRuntimeUnavailable(event.deviceUid)
        }
    }

    private fun processTypedEvent(event: DeviceRuntimeTypedEvent) {
        when (event.type) {
            DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_PROGRESS,
            DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_COMPLETED -> processOtaEvent(event)
            DeviceRuntimeTypedEvent.Type.SYSTEM_RESTARTING -> {
                if (
                    hasActiveOperation(event.deviceUid) &&
                    selectedPlans[event.deviceUid]?.runtimeGeneration == event.generation
                ) {
                    markRuntimeUnavailable(event.deviceUid)
                }
            }
            else -> Unit
        }
    }

    private fun processOtaEvent(event: DeviceRuntimeTypedEvent) {
        val payload = event.payload as? DeviceRuntimeEventPayload.Snapshot
        val selected = selectedPlans[event.deviceUid]
        when {
            payload == null -> fail(
                event.deviceUid,
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = "Firmware OTA event payload is not a snapshot.",
                    source = DeviceFirmwareFailureSource.RUNTIME,
                    stage = DeviceFirmwareFailureStage.STATUS,
                    code = "event_payload_invalid",
                    requestId = event.messageId,
                    recoverable = false,
                    kind = DeviceFirmwareFailureKind.PROTOCOL
                )
            )
            selected == null || selected.runtimeGeneration != event.generation -> Unit
            else -> DeviceFirmwareStatusParser.parseOtaProgressEventExact(payload.data).fold(
                onSuccess = { otaEvent ->
                    applySnapshot(event.deviceUid, otaEvent.ota, selected, event.generation)
                },
                onFailure = { error ->
                    fail(
                        event.deviceUid,
                        DeviceFirmwareFailureMapper.fromThrowable(
                            error = error,
                            source = DeviceFirmwareFailureSource.RUNTIME,
                            stage = DeviceFirmwareFailureStage.STATUS,
                            code = "event_contract_invalid",
                            recoverable = false
                        ).copy(requestId = event.messageId)
                    )
                }
            )
        }
    }

    private fun processSnapshotUpdates(snapshots: Map<DeviceUid, DeviceSnapshot>) {
        pendingVersionVerification.keys.toList().forEach { deviceUid ->
            snapshots[deviceUid]?.let { snapshot -> verifyInstalledFirmware(deviceUid, snapshot) }
        }
    }

    private fun applySnapshot(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceFirmwareFailure? {
        DeviceOtaValidator.snapshotAgainstPlan(snapshot, selected?.dataPlan)?.let { error ->
            val failure = DeviceFirmwareFailureMapper.local(
                technicalMessage = error,
                source = DeviceFirmwareFailureSource.RUNTIME,
                stage = snapshot.failureStage(),
                code = "snapshot_plan_mismatch",
                httpStatus = snapshot.httpStatus,
                firmwarePhase = snapshot.phaseRaw,
                recoverable = false
            )
            fail(deviceUid, failure)
            return failure
        }
        val activeSelection = selected?.copy(runtimeGeneration = generation)
        if (activeSelection != null) selectedPlans[deviceUid] = activeSelection
        val releaseContent = activeSelection?.applicationPlan?.releaseContent
            ?: DeviceFirmwareReleaseContent.EMPTY
        val targetVersion = snapshot.targetVersion.ifBlank {
            activeSelection?.dataPlan?.targetVersion.orEmpty()
        }
        armRestartVerification(deviceUid, snapshot, activeSelection)
        val state = stateFlow(deviceUid)
        state.value = when {
            snapshot.phase == DeviceFirmwareOtaPhase.IDLE &&
                activeSelection != null &&
                state.value is DeviceOtaState.UpdateAvailable -> {
                DeviceOtaState.UpdateAvailable(activeSelection.applicationPlan)
            }
            snapshot.phase == DeviceFirmwareOtaPhase.IDLE &&
                state.value is DeviceOtaState.Starting -> state.value
            else -> DeviceOtaStateMapper.map(
                snapshot = snapshot,
                deviceUid = deviceUid,
                targetVersion = targetVersion,
                releaseContent = releaseContent
            )
        }
        verifyCurrentFirmwareIfReady(deviceUid, snapshot, activeSelection)
        return null
    }

    private fun armRestartVerification(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?
    ) {
        if (
            snapshot.phase == DeviceFirmwareOtaPhase.SUCCEEDED &&
            snapshot.restartRequired &&
            selected != null
        ) {
            pendingVersionVerification[deviceUid] = selected
            if (snapshot.restartScheduled) scheduleRecovery(deviceUid)
        }
    }

    private fun verifyCurrentFirmwareIfReady(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?
    ) {
        if (
            snapshot.phase == DeviceFirmwareOtaPhase.SUCCEEDED &&
            snapshot.restartRequired &&
            selected != null
        ) {
            snapshotProvider(deviceUid)?.let { current ->
                verifyInstalledFirmware(deviceUid, current)
            }
        }
    }

    private fun recoverAfterAuthentication(deviceUid: DeviceUid) {
        recoveryJobs.remove(deviceUid)?.cancel()
        pendingVersionVerification[deviceUid]?.let {
            snapshotProvider(deviceUid)?.let { snapshot ->
                verifyInstalledFirmware(deviceUid, snapshot)
            }
            return
        }
        if (stateFlow(deviceUid).value.requiresOtaStatusRecovery) {
            scope.launch { requestStatus(deviceUid) }
        }
    }

    private fun markRuntimeUnavailable(deviceUid: DeviceUid) {
        val current = stateFlow(deviceUid).value
        if (
            !current.requiresRuntimeRecovery &&
            !pendingVersionVerification.containsKey(deviceUid)
        ) {
            return
        }
        stateFlow(deviceUid).value = DeviceOtaState.Recovering(
            deviceUid = deviceUid.value,
            targetVersion = current.targetVersionOrEmpty,
            progressPermille = current.progressPermilleOrZero
        )
        scheduleRecovery(deviceUid)
    }

    private fun scheduleRecovery(deviceUid: DeviceUid) {
        synchronized(recoveryJobs) {
            if (recoveryJobs[deviceUid]?.isActive == true) return
            val job = scope.launch {
                if (restartWaitMillis > 0L) delay(restartWaitMillis)
                if (pendingVersionVerification.containsKey(deviceUid)) {
                    val current = stateFlow(deviceUid).value
                    stateFlow(deviceUid).value = DeviceOtaState.Recovering(
                        deviceUid = deviceUid.value,
                        targetVersion = current.targetVersionOrEmpty,
                        progressPermille = current.progressPermilleOrZero
                    )
                }
                runCatching { refreshDiscovery() }
                if (discoverySettleMillis > 0L) delay(discoverySettleMillis)
                recoverRuntime(deviceUid)
            }
            recoveryJobs[deviceUid] = job
            job.invokeOnCompletion { recoveryJobs.remove(deviceUid, job) }
        }
    }

    private fun verifyInstalledFirmware(deviceUid: DeviceUid, snapshot: DeviceSnapshot) {
        val selected = pendingVersionVerification[deviceUid]
        if (
            selected != null &&
            snapshot.hasValidatedRuntimeMetadata &&
            snapshot.runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration
        ) {
            val error = DeviceOtaValidator.installedFirmwareError(snapshot, selected.dataPlan)
            if (error == null) {
                completeInstalledFirmwareVerification(deviceUid, selected)
            } else {
                fail(
                    deviceUid,
                    DeviceFirmwareFailureMapper.local(
                        technicalMessage = error,
                        source = DeviceFirmwareFailureSource.ANDROID,
                        stage = DeviceFirmwareFailureStage.RESTART_VERIFICATION,
                        code = "installed_firmware_mismatch",
                        recoverable = false,
                        kind = DeviceFirmwareFailureKind.COMPATIBILITY
                    )
                )
            }
        }
    }

    private fun completeInstalledFirmwareVerification(
        deviceUid: DeviceUid,
        selected: SelectedPlan
    ) {
        pendingVersionVerification.remove(deviceUid)
        selectedPlans.remove(deviceUid)
        recoveryJobs.remove(deviceUid)?.cancel()
        stateFlow(deviceUid).value = DeviceOtaState.Succeeded(
            deviceUid = deviceUid.value,
            targetVersion = selected.dataPlan.targetVersion,
            releaseContent = selected.applicationPlan.releaseContent
        )
    }

    private fun handleCommandFailure(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<*>,
        stage: DeviceFirmwareFailureStage,
        preserveRecovery: Boolean
    ): AppCommandResult {
        val failure = DeviceFirmwareFailureMapper.fromOutcome(outcome, stage)
        if (!(preserveRecovery && outcome.isTransientFailure)) {
            fail(deviceUid, failure)
        }
        return outcome.toApplicationResult(failure)
    }

    private fun hasActiveOperation(deviceUid: DeviceUid): Boolean =
        stateFlow(deviceUid).value.isActiveOtaState ||
            pendingVersionVerification.containsKey(deviceUid)

    private fun startLock(deviceUid: DeviceUid): Mutex {
        val candidate = Mutex()
        return startLocks.putIfAbsent(deviceUid, candidate) ?: candidate
    }

    private fun stateFlow(deviceUid: DeviceUid): MutableStateFlow<DeviceOtaState> =
        states.getOrPut(deviceUid) { MutableStateFlow(DeviceOtaState.Idle(deviceUid.value)) }

    private fun clearPlanState(deviceUid: DeviceUid) {
        selectedPlans.remove(deviceUid)
        pendingVersionVerification.remove(deviceUid)
        recoveryJobs.remove(deviceUid)?.cancel()
    }

    private fun fail(deviceUid: DeviceUid, failure: DeviceFirmwareFailure) {
        pendingVersionVerification.remove(deviceUid)
        recoveryJobs.remove(deviceUid)?.cancel()
        stateFlow(deviceUid).value = DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            failure = failure
        )
    }

    override fun close() {
        recoveryJobs.values.forEach(Job::cancel)
        recoveryJobs.clear()
        selectedPlans.clear()
        pendingVersionVerification.clear()
        startLocks.clear()
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_RESTART_WAIT_MILLIS = 1_000L
        const val DEFAULT_DISCOVERY_SETTLE_MILLIS = 750L
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
        filename = firmware.filename,
        downloadUrl = firmware.url,
        sha256 = firmware.sha256,
        sizeBytes = firmware.size,
        applyNow = payload.applyNow,
        runtimeMetadataGeneration = runtimeMetadataGeneration,
        manifestTag = manifestTag,
        releaseContent = releaseContent
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

private val DeviceOtaState.isActiveOtaState: Boolean
    get() = this is DeviceOtaState.Starting ||
        this is DeviceOtaState.InProgress ||
        this is DeviceOtaState.Recovering ||
        this is DeviceOtaState.RestartRequired

private val DeviceOtaState.requiresRuntimeRecovery: Boolean
    get() = isActiveOtaState

private val DeviceOtaState.requiresOtaStatusRecovery: Boolean
    get() = this is DeviceOtaState.Starting ||
        this is DeviceOtaState.InProgress ||
        this is DeviceOtaState.Recovering

private val DeviceOtaState.targetVersionOrEmpty: String
    get() = when (this) {
        is DeviceOtaState.Starting -> plan.targetVersion
        is DeviceOtaState.InProgress -> targetVersion
        is DeviceOtaState.Recovering -> targetVersion
        is DeviceOtaState.RestartRequired -> targetVersion
        else -> ""
    }

private val DeviceOtaState.progressPermilleOrZero: Int
    get() = when (this) {
        is DeviceOtaState.InProgress -> progressPermille
        is DeviceOtaState.Recovering -> progressPermille
        else -> 0
    }

private val DeviceRuntimeCommandOutcome<*>.isTransientFailure: Boolean
    get() = this is DeviceRuntimeCommandOutcome.NotConnected ||
        this is DeviceRuntimeCommandOutcome.NotAuthenticated ||
        this is DeviceRuntimeCommandOutcome.SendFailed ||
        this is DeviceRuntimeCommandOutcome.Timeout ||
        this is DeviceRuntimeCommandOutcome.Cancelled

private fun DeviceRuntimeCommandOutcome<*>.toApplicationResult(
    failure: DeviceFirmwareFailure? = null
): AppCommandResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> -> AppCommandResult(
        sent = true,
        messageId = messageId
    )
    is DeviceRuntimeCommandOutcome.FirmwareError -> AppCommandResult(
        sent = true,
        messageId = messageId,
        failure = requireNotNull(failure)
    )
    is DeviceRuntimeCommandOutcome.ProtocolError -> AppCommandResult(
        sent = messageId.isNotBlank(),
        messageId = messageId,
        failure = requireNotNull(failure)
    )
    is DeviceRuntimeCommandOutcome.Timeout -> AppCommandResult(
        sent = true,
        messageId = messageId,
        failure = requireNotNull(failure)
    )
    is DeviceRuntimeCommandOutcome.Cancelled -> AppCommandResult(
        sent = messageId.isNotBlank(),
        messageId = messageId,
        failure = requireNotNull(failure)
    )
    is DeviceRuntimeCommandOutcome.SendFailed -> AppCommandResult(
        sent = false,
        messageId = messageId,
        failure = requireNotNull(failure)
    )
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> AppCommandResult(
        sent = false,
        failure = requireNotNull(failure)
    )
}

private fun DeviceFirmwareOtaSnapshot.failureStage(): DeviceFirmwareFailureStage = when (phase) {
    DeviceFirmwareOtaPhase.STARTING -> DeviceFirmwareFailureStage.START
    DeviceFirmwareOtaPhase.SAFE_MODE,
    DeviceFirmwareOtaPhase.DOWNLOADING,
    DeviceFirmwareOtaPhase.WRITING -> DeviceFirmwareFailureStage.TRANSFER
    DeviceFirmwareOtaPhase.VERIFYING,
    DeviceFirmwareOtaPhase.SUCCEEDED -> DeviceFirmwareFailureStage.VERIFICATION
    DeviceFirmwareOtaPhase.IDLE,
    DeviceFirmwareOtaPhase.FAILED,
    DeviceFirmwareOtaPhase.UNKNOWN -> DeviceFirmwareFailureStage.STATUS
}
