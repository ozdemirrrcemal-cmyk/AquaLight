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
@Suppress("LongParameterList")
internal class DeviceOtaCoordinator(
    snapshotProvider: (DeviceUid) -> DeviceSnapshot?,
    connectRuntime: (DeviceUid) -> Result<Unit>,
    updaterProvider: () -> DeviceFirmwareUpdateRepository?,
    runtimeLifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>?,
    runtimeTypedEvents: SharedFlow<DeviceRuntimeTypedEvent>? = null,
    snapshotUpdates: StateFlow<Map<DeviceUid, DeviceSnapshot>>? = null,
    recoverRuntime: (DeviceUid) -> Result<Unit> = connectRuntime,
    refreshDiscovery: suspend () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    restartWaitMillis: Long = DEFAULT_RESTART_WAIT_MILLIS,
    discoverySettleMillis: Long = DEFAULT_DISCOVERY_SETTLE_MILLIS
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateStore = DeviceOtaStateStore()
    private val planStore = DeviceOtaPlanStore()
    private val jobStore = DeviceOtaJobStore()
    private val availabilityController: DeviceOtaAvailabilityController
    private val startController: DeviceOtaStartController
    private val commandController: DeviceOtaCommandController
    private val eventController: DeviceOtaEventController
    private val recoveryController: DeviceOtaRecoveryController

    init {
        require(restartWaitMillis >= 0L)
        require(discoverySettleMillis >= 0L)
        val runtime = DeviceOtaRuntimeAccess(
            snapshotProvider = snapshotProvider,
            connectRuntime = connectRuntime,
            updaterProvider = updaterProvider,
            recoverRuntimeAction = recoverRuntime,
            refreshDiscoveryAction = refreshDiscovery
        )
        val failureHandler = DeviceOtaFailureHandler(stateStore, planStore, jobStore)
        val statusDelegate = DeviceOtaStatusRequestDelegate()
        recoveryController = DeviceOtaRecoveryController(
            environment = DeviceOtaRecoveryEnvironment(
                runtime = runtime,
                scope = scope,
                timing = DeviceOtaRecoveryTiming(restartWaitMillis, discoverySettleMillis),
                requestStatus = statusDelegate::request
            ),
            stateStore = stateStore,
            planStore = planStore,
            jobStore = jobStore,
            failureHandler = failureHandler
        )
        val snapshotController = DeviceOtaSnapshotController(
            stateStore = stateStore,
            planStore = planStore,
            recoveryController = recoveryController,
            failureHandler = failureHandler
        )
        commandController = DeviceOtaCommandController(
            runtime = runtime,
            stateStore = stateStore,
            planStore = planStore,
            jobStore = jobStore,
            snapshotController = snapshotController,
            failureHandler = failureHandler
        )
        statusDelegate.bind(commandController::requestStatus)
        availabilityController = DeviceOtaAvailabilityController(
            runtime = runtime,
            stateStore = stateStore,
            planStore = planStore,
            jobStore = jobStore
        )
        startController = DeviceOtaStartController(
            runtime = runtime,
            stateStore = stateStore,
            planStore = planStore,
            jobStore = jobStore,
            snapshotController = snapshotController,
            failureHandler = failureHandler
        )
        eventController = DeviceOtaEventController(
            stateStore = stateStore,
            planStore = planStore,
            snapshotController = snapshotController,
            recoveryController = recoveryController,
            failureHandler = failureHandler
        )
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

    fun observe(deviceUid: DeviceUid): StateFlow<DeviceOtaState> = stateStore.observe(deviceUid)

    suspend fun checkAvailability(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> = availabilityController.checkAvailability(
        deviceUid = deviceUid,
        manifestUrl = manifestUrl,
        applyNow = applyNow
    )

    suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): AppCommandResult =
        startController.startUpdate(plan)

    suspend fun requestStatus(deviceUid: DeviceUid): AppCommandResult =
        commandController.requestStatus(deviceUid)

    suspend fun clearStatus(deviceUid: DeviceUid): AppCommandResult =
        commandController.clearStatus(deviceUid)

    private fun processLifecycleEvent(event: DeviceRuntimeLifecycleEvent) {
        recoveryController.processLifecycleEvent(event)
    }

    private fun processTypedEvent(event: DeviceRuntimeTypedEvent) {
        eventController.processTypedEvent(event)
    }

    private fun processSnapshotUpdates(snapshots: Map<DeviceUid, DeviceSnapshot>) {
        recoveryController.processSnapshotUpdates(snapshots)
    }

    override fun close() {
        planStore.close()
        jobStore.close()
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_RESTART_WAIT_MILLIS = 1_000L
        const val DEFAULT_DISCOVERY_SETTLE_MILLIS = 750L
    }
}

private data class DeviceOtaRuntimeAccess(
    val snapshotProvider: (DeviceUid) -> DeviceSnapshot?,
    val connectRuntime: (DeviceUid) -> Result<Unit>,
    val updaterProvider: () -> DeviceFirmwareUpdateRepository?,
    private val recoverRuntimeAction: (DeviceUid) -> Result<Unit>,
    private val refreshDiscoveryAction: suspend () -> Unit
) {
    fun recoverRuntime(deviceUid: DeviceUid): Result<Unit> = recoverRuntimeAction(deviceUid)

    suspend fun refreshDiscovery() {
        refreshDiscoveryAction()
    }
}

private data class DeviceOtaRecoveryTiming(
    val restartWaitMillis: Long,
    val discoverySettleMillis: Long
)

private data class DeviceOtaRecoveryEnvironment(
    val runtime: DeviceOtaRuntimeAccess,
    val scope: CoroutineScope,
    val timing: DeviceOtaRecoveryTiming,
    val requestStatus: suspend (DeviceUid) -> AppCommandResult
)

private data class SelectedPlan(
    val dataPlan: DeviceFirmwareUpdatePlan,
    val applicationPlan: PreparedDeviceFirmwareUpdate,
    val runtimeGeneration: DeviceRuntimeConnectionGeneration? = null
)

private data class StartContext(
    val selected: SelectedPlan,
    val updater: DeviceFirmwareUpdateRepository
)

private class AvailabilityFailure(
    val failure: DeviceFirmwareFailure
) : IllegalStateException(failure.technicalMessage)

private class StartPreparationFailure(
    val failure: DeviceFirmwareFailure,
    val publishFailure: Boolean
) : IllegalStateException(failure.technicalMessage)

private class DeviceOtaStatusRequestDelegate {
    private var requester: (suspend (DeviceUid) -> AppCommandResult)? = null

    fun bind(value: suspend (DeviceUid) -> AppCommandResult) {
        check(requester == null) { "OTA status requester is already bound." }
        requester = value
    }

    suspend fun request(deviceUid: DeviceUid): AppCommandResult =
        checkNotNull(requester) { "OTA status requester is not bound." }(deviceUid)
}

private class DeviceOtaStateStore {
    private val states = ConcurrentHashMap<DeviceUid, MutableStateFlow<DeviceOtaState>>()

    fun observe(deviceUid: DeviceUid): StateFlow<DeviceOtaState> =
        stateFlow(deviceUid).asStateFlow()

    fun current(deviceUid: DeviceUid): DeviceOtaState = stateFlow(deviceUid).value

    fun set(deviceUid: DeviceUid, state: DeviceOtaState) {
        stateFlow(deviceUid).value = state
    }

    private fun stateFlow(deviceUid: DeviceUid): MutableStateFlow<DeviceOtaState> =
        states.getOrPut(deviceUid) { MutableStateFlow(DeviceOtaState.Idle(deviceUid.value)) }
}

private class DeviceOtaPlanStore {
    private val selectedPlans = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val pendingVersionVerification = ConcurrentHashMap<DeviceUid, SelectedPlan>()

    fun selected(deviceUid: DeviceUid): SelectedPlan? = selectedPlans[deviceUid]

    fun select(deviceUid: DeviceUid, selected: SelectedPlan) {
        selectedPlans[deviceUid] = selected
    }

    fun updateGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): SelectedPlan? = selected(deviceUid)?.copy(runtimeGeneration = generation)?.also { selected ->
        select(deviceUid, selected)
    }

    fun removeSelected(deviceUid: DeviceUid) {
        selectedPlans.remove(deviceUid)
    }

    fun pending(deviceUid: DeviceUid): SelectedPlan? = pendingVersionVerification[deviceUid]

    fun armVerification(deviceUid: DeviceUid, selected: SelectedPlan) {
        pendingVersionVerification[deviceUid] = selected
    }

    fun removeVerification(deviceUid: DeviceUid) {
        pendingVersionVerification.remove(deviceUid)
    }

    fun hasPendingVerification(deviceUid: DeviceUid): Boolean =
        pendingVersionVerification.containsKey(deviceUid)

    fun pendingDeviceUids(): List<DeviceUid> = pendingVersionVerification.keys.toList()

    fun clear(deviceUid: DeviceUid) {
        removeSelected(deviceUid)
        removeVerification(deviceUid)
    }

    fun close() {
        selectedPlans.clear()
        pendingVersionVerification.clear()
    }
}

private class DeviceOtaJobStore {
    private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()
    private val recoveryJobs = ConcurrentHashMap<DeviceUid, Job>()

    fun startLock(deviceUid: DeviceUid): Mutex {
        val candidate = Mutex()
        return startLocks.putIfAbsent(deviceUid, candidate) ?: candidate
    }

    fun recoveryJob(deviceUid: DeviceUid): Job? = recoveryJobs[deviceUid]

    fun putRecoveryJob(deviceUid: DeviceUid, job: Job) {
        recoveryJobs[deviceUid] = job
    }

    fun removeRecoveryJob(deviceUid: DeviceUid, job: Job) {
        recoveryJobs.remove(deviceUid, job)
    }

    fun cancelRecovery(deviceUid: DeviceUid) {
        recoveryJobs.remove(deviceUid)?.cancel()
    }

    fun close() {
        recoveryJobs.values.forEach(Job::cancel)
        recoveryJobs.clear()
        startLocks.clear()
    }
}

private class DeviceOtaFailureHandler(
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val jobStore: DeviceOtaJobStore
) {
    fun fail(deviceUid: DeviceUid, failure: DeviceFirmwareFailure) {
        planStore.removeVerification(deviceUid)
        jobStore.cancelRecovery(deviceUid)
        stateStore.set(
            deviceUid,
            DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                failure = failure
            )
        )
    }

    fun handleCommandFailure(
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
}

private class DeviceOtaAvailabilityController(
    private val runtime: DeviceOtaRuntimeAccess,
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val jobStore: DeviceOtaJobStore
) {
    suspend fun checkAvailability(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        if (hasActiveOperation(deviceUid, stateStore, planStore)) {
            return Result.failure(
                IllegalStateException("An OTA operation is already active for this device.")
            )
        }
        val initial = runtime.snapshotProvider(deviceUid)
        stateStore.set(
            deviceUid,
            DeviceOtaState.Checking(
                deviceUid = deviceUid.value,
                currentVersion = initial?.firmwareVersion.orEmpty()
            )
        )
        return runCatching {
            resolveAvailability(deviceUid, initial, manifestUrl, applyNow)
        }.onSuccess { availability ->
            stateStore.set(deviceUid, availability)
        }.onFailure { error ->
            stateStore.set(
                deviceUid,
                DeviceOtaState.Failed(deviceUid.value, error.toAvailabilityFailure())
            )
        }
    }

    private suspend fun resolveAvailability(
        deviceUid: DeviceUid,
        initial: DeviceSnapshot?,
        manifestUrl: String,
        applyNow: Boolean
    ): DeviceOtaState {
        val snapshot = requireInitialSnapshot(initial)
        validateRuntimeMetadata(snapshot)
        if (!snapshot.capabilities.ota) return unsupported(deviceUid)
        connectRuntime(deviceUid)
        val current = requireCurrentSnapshot(deviceUid)
        validateMetadataGeneration(snapshot, current)
        val updater = requireUpdater()
        val availability = evaluateUpdate(updater, current, manifestUrl, applyNow)
        return applyAvailability(deviceUid, availability)
    }

    private fun requireInitialSnapshot(snapshot: DeviceSnapshot?): DeviceSnapshot =
        snapshot ?: rejectAvailability(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Device snapshot is not available.",
                source = DeviceFirmwareFailureSource.RUNTIME,
                stage = DeviceFirmwareFailureStage.AVAILABILITY,
                code = "device_snapshot_missing",
                recoverable = true,
                kind = DeviceFirmwareFailureKind.CONNECTION
            )
        )

    private fun validateRuntimeMetadata(snapshot: DeviceSnapshot) {
        if (!snapshot.hasValidatedRuntimeMetadata) {
            rejectAvailability(
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = "OTA requires current authenticated runtime metadata.",
                    source = DeviceFirmwareFailureSource.RUNTIME,
                    stage = DeviceFirmwareFailureStage.AVAILABILITY,
                    code = "runtime_metadata_unvalidated",
                    recoverable = true,
                    kind = DeviceFirmwareFailureKind.AUTHENTICATION
                )
            )
        }
    }

    private fun unsupported(deviceUid: DeviceUid): DeviceOtaState.Unsupported =
        DeviceOtaState.Unsupported(
            deviceUid = deviceUid.value,
            reason = "This exact device profile does not support OTA."
        )

    private fun connectRuntime(deviceUid: DeviceUid) {
        runtime.connectRuntime(deviceUid).exceptionOrNull()?.let { error ->
            rejectAvailability(
                DeviceFirmwareFailureMapper.fromThrowable(
                    error = error,
                    source = DeviceFirmwareFailureSource.RUNTIME,
                    stage = DeviceFirmwareFailureStage.AVAILABILITY,
                    code = "runtime_connect_failed",
                    recoverable = true
                )
            )
        }
    }

    private fun requireCurrentSnapshot(deviceUid: DeviceUid): DeviceSnapshot =
        runtime.snapshotProvider(deviceUid) ?: rejectAvailability(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Device snapshot disappeared during OTA availability check.",
                source = DeviceFirmwareFailureSource.RUNTIME,
                stage = DeviceFirmwareFailureStage.AVAILABILITY,
                code = "device_snapshot_disappeared",
                recoverable = true,
                kind = DeviceFirmwareFailureKind.CONNECTION
            )
        )

    private fun validateMetadataGeneration(initial: DeviceSnapshot, current: DeviceSnapshot) {
        if (current.runtimeMetadataGeneration != initial.runtimeMetadataGeneration) {
            rejectAvailability(
                DeviceFirmwareFailureMapper.local(
                    technicalMessage = "Runtime metadata changed during OTA availability check.",
                    source = DeviceFirmwareFailureSource.RUNTIME,
                    stage = DeviceFirmwareFailureStage.AVAILABILITY,
                    code = "runtime_metadata_changed",
                    recoverable = true,
                    kind = DeviceFirmwareFailureKind.INVALID_REQUEST
                )
            )
        }
    }

    private fun requireUpdater(): DeviceFirmwareUpdateRepository =
        runtime.updaterProvider() ?: rejectAvailability(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Firmware update runtime is not configured.",
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.AVAILABILITY,
                code = "update_runtime_missing",
                recoverable = false,
                kind = DeviceFirmwareFailureKind.INTERNAL
            )
        )

    private suspend fun evaluateUpdate(
        updater: DeviceFirmwareUpdateRepository,
        snapshot: DeviceSnapshot,
        manifestUrl: String,
        applyNow: Boolean
    ): DeviceFirmwareAvailability = updater.fetchAndEvaluateUpdate(
        snapshot = snapshot,
        manifestUrl = manifestUrl,
        applyNow = applyNow
    ).getOrElse { error ->
        rejectAvailability(
            DeviceFirmwareFailureMapper.fromThrowable(
                error = error,
                source = DeviceFirmwareFailureSource.MANIFEST,
                stage = DeviceFirmwareFailureStage.AVAILABILITY,
                code = "manifest_evaluation_failed"
            )
        )
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
        ).also { clearPlanState(deviceUid, planStore, jobStore) }
        is DeviceFirmwareAvailability.UpdateAvailable -> {
            val applicationPlan = availability.plan.toApplicationPlan()
            planStore.removeVerification(deviceUid)
            planStore.select(
                deviceUid,
                SelectedPlan(
                    dataPlan = availability.plan,
                    applicationPlan = applicationPlan
                )
            )
            DeviceOtaState.UpdateAvailable(applicationPlan)
        }
    }
}

private class DeviceOtaStartController(
    private val runtime: DeviceOtaRuntimeAccess,
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val jobStore: DeviceOtaJobStore,
    private val snapshotController: DeviceOtaSnapshotController,
    private val failureHandler: DeviceOtaFailureHandler
) {
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
        return jobStore.startLock(deviceUid).withLock {
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
        val selected = requirePreparedSelection(deviceUid, plan)
        val snapshot = requireSnapshot(deviceUid)
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
        val updater = requireUpdater()
        connectRuntime(deviceUid)
        return StartContext(selected, updater)
    }

    private fun requirePreparedSelection(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate
    ): SelectedPlan {
        val selected = planStore.selected(deviceUid) ?: rejectStart(
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
        if (hasActiveOperation(deviceUid, stateStore, planStore)) {
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
        return selected
    }

    private fun requireSnapshot(deviceUid: DeviceUid): DeviceSnapshot =
        runtime.snapshotProvider(deviceUid) ?: rejectStart(
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

    private fun requireUpdater(): DeviceFirmwareUpdateRepository =
        runtime.updaterProvider() ?: rejectStart(
            DeviceFirmwareFailureMapper.local(
                technicalMessage = "Firmware update runtime is not configured.",
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "update_runtime_missing",
                recoverable = false,
                kind = DeviceFirmwareFailureKind.INTERNAL
            )
        )

    private fun connectRuntime(deviceUid: DeviceUid) {
        runtime.connectRuntime(deviceUid).exceptionOrNull()?.let { error ->
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
    }

    private suspend fun dispatchStart(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate,
        context: StartContext
    ): AppCommandResult = when (
        val outcome = context.updater.startUpdate(context.selected.dataPlan)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> handleStartSuccess(
            deviceUid = deviceUid,
            plan = plan,
            context = context,
            outcome = outcome
        )
        else -> failureHandler.handleCommandFailure(
            deviceUid = deviceUid,
            outcome = outcome,
            stage = DeviceFirmwareFailureStage.START,
            preserveRecovery = false
        )
    }

    private fun handleStartSuccess(
        deviceUid: DeviceUid,
        plan: PreparedDeviceFirmwareUpdate,
        context: StartContext,
        outcome: DeviceRuntimeCommandOutcome.Success<DeviceFirmwareOtaStartAccepted>
    ): AppCommandResult {
        val accepted = outcome.value
        if (!accepted.accepted || !accepted.request.matches(context.selected.dataPlan.payload)) {
            val failure = DeviceFirmwareFailureMapper.local(
                technicalMessage = "Firmware OTA request echo differs from the selected plan.",
                source = DeviceFirmwareFailureSource.RUNTIME,
                stage = DeviceFirmwareFailureStage.START,
                code = "request_echo_mismatch",
                requestId = outcome.messageId,
                recoverable = false,
                kind = DeviceFirmwareFailureKind.PROTOCOL
            )
            failureHandler.fail(deviceUid, failure)
            return AppCommandResult(
                sent = true,
                messageId = outcome.messageId,
                failure = failure
            )
        }
        val active = context.selected.copy(runtimeGeneration = outcome.generation)
        planStore.select(deviceUid, active)
        stateStore.set(
            deviceUid,
            DeviceOtaState.Starting(plan = plan, requestId = outcome.messageId)
        )
        val failure = snapshotController.applySnapshot(
            deviceUid = deviceUid,
            snapshot = accepted.ota,
            selected = active,
            requestId = outcome.messageId,
            generation = outcome.generation
        )
        return failure?.let { snapshotFailure ->
            AppCommandResult(
                sent = true,
                messageId = outcome.messageId,
                failure = snapshotFailure
            )
        } ?: outcome.toApplicationResult()
    }

    private fun handleStartPreparationFailure(
        deviceUid: DeviceUid,
        error: Throwable
    ): AppCommandResult {
        val preparationFailure = error as? StartPreparationFailure
        val failure = preparationFailure?.failure
            ?: DeviceFirmwareFailureMapper.fromThrowable(
                error = error,
                source = DeviceFirmwareFailureSource.ANDROID,
                stage = DeviceFirmwareFailureStage.PREPARATION,
                code = "start_preparation_failed",
                recoverable = false
            )
        if (preparationFailure?.publishFailure == true) {
            failureHandler.fail(deviceUid, failure)
        }
        return AppCommandResult(sent = false, failure = failure)
    }

    private fun rejectStart(
        failure: DeviceFirmwareFailure,
        publishFailure: Boolean = false
    ): Nothing = throw StartPreparationFailure(failure, publishFailure)
}

private class DeviceOtaCommandController(
    private val runtime: DeviceOtaRuntimeAccess,
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val jobStore: DeviceOtaJobStore,
    private val snapshotController: DeviceOtaSnapshotController,
    private val failureHandler: DeviceOtaFailureHandler
) {
    suspend fun requestStatus(deviceUid: DeviceUid): AppCommandResult =
        connectedUpdater(deviceUid).fold(
            onSuccess = { updater ->
                applyStatusOutcome(deviceUid, updater.requestOtaStatus(deviceUid))
            },
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

    suspend fun clearStatus(deviceUid: DeviceUid): AppCommandResult =
        connectedUpdater(deviceUid).fold(
            onSuccess = { updater ->
                applyClearOutcome(deviceUid, updater.clearOtaStatus(deviceUid))
            },
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

    private fun applyStatusOutcome(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStatusResponse>
    ): AppCommandResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            val selected = planStore.updateGeneration(deviceUid, outcome.generation)
            val failure = snapshotController.applySnapshot(
                deviceUid = deviceUid,
                snapshot = outcome.value.ota,
                selected = selected,
                requestId = outcome.messageId,
                generation = outcome.generation
            )
            failure?.let { snapshotFailure ->
                AppCommandResult(
                    sent = true,
                    messageId = outcome.messageId,
                    failure = snapshotFailure
                )
            } ?: outcome.toApplicationResult()
        }
        else -> failureHandler.handleCommandFailure(
            deviceUid = deviceUid,
            outcome = outcome,
            stage = DeviceFirmwareFailureStage.STATUS,
            preserveRecovery = stateStore.current(deviceUid) is DeviceOtaState.Recovering
        )
    }

    private fun applyClearOutcome(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult>
    ): AppCommandResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            clearPlanState(deviceUid, planStore, jobStore)
            stateStore.set(deviceUid, DeviceOtaState.Idle(deviceUid.value))
            outcome.toApplicationResult()
        }
        else -> failureHandler.handleCommandFailure(
            deviceUid = deviceUid,
            outcome = outcome,
            stage = DeviceFirmwareFailureStage.CLEAR,
            preserveRecovery = false
        )
    }

    private fun connectedUpdater(deviceUid: DeviceUid): Result<DeviceFirmwareUpdateRepository> =
        runCatching {
            val updater = requireNotNull(runtime.updaterProvider()) {
                "Firmware update runtime is not configured."
            }
            runtime.connectRuntime(deviceUid).getOrThrow()
            updater
        }
}

private class DeviceOtaSnapshotController(
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val recoveryController: DeviceOtaRecoveryController,
    private val failureHandler: DeviceOtaFailureHandler
) {
    fun applySnapshot(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?,
        requestId: String,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceFirmwareFailure? {
        snapshotMismatchFailure(snapshot, selected, requestId)?.let { failure ->
            failureHandler.fail(deviceUid, failure)
            return failure
        }
        val activeSelection = selected?.copy(runtimeGeneration = generation)
        if (activeSelection != null) planStore.select(deviceUid, activeSelection)
        val releaseContent = activeSelection?.applicationPlan?.releaseContent
            ?: DeviceFirmwareReleaseContent.EMPTY
        val targetVersion = snapshot.targetVersion.ifBlank {
            activeSelection?.dataPlan?.targetVersion.orEmpty()
        }
        recoveryController.armRestartVerification(deviceUid, snapshot, activeSelection)
        stateStore.set(
            deviceUid,
            mapSnapshotState(
                deviceUid = deviceUid,
                snapshot = snapshot,
                activeSelection = activeSelection,
                targetVersion = targetVersion,
                releaseContent = releaseContent,
                requestId = requestId
            )
        )
        recoveryController.verifyCurrentFirmwareIfReady(deviceUid, snapshot, activeSelection)
        return null
    }

    private fun snapshotMismatchFailure(
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?,
        requestId: String
    ): DeviceFirmwareFailure? {
        val error = DeviceOtaValidator.snapshotAgainstPlan(snapshot, selected?.dataPlan)
            ?: return null
        return DeviceFirmwareFailureMapper.local(
            technicalMessage = error,
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = snapshot.failureStage(),
            code = "snapshot_plan_mismatch",
            httpStatus = snapshot.httpStatus,
            requestId = requestId,
            firmwarePhase = snapshot.phaseRaw,
            recoverable = false
        )
    }

    private fun mapSnapshotState(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        activeSelection: SelectedPlan?,
        targetVersion: String,
        releaseContent: DeviceFirmwareReleaseContent,
        requestId: String
    ): DeviceOtaState {
        val current = stateStore.current(deviceUid)
        return when {
            snapshot.phase == DeviceFirmwareOtaPhase.IDLE &&
                activeSelection != null &&
                current is DeviceOtaState.UpdateAvailable ->
                DeviceOtaState.UpdateAvailable(activeSelection.applicationPlan)
            snapshot.phase == DeviceFirmwareOtaPhase.IDLE &&
                current is DeviceOtaState.Starting -> current
            else -> DeviceOtaStateMapper.map(
                snapshot = snapshot,
                deviceUid = deviceUid,
                targetVersion = targetVersion,
                releaseContent = releaseContent,
                requestId = requestId
            )
        }
    }
}

private class DeviceOtaEventController(
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val snapshotController: DeviceOtaSnapshotController,
    private val recoveryController: DeviceOtaRecoveryController,
    private val failureHandler: DeviceOtaFailureHandler
) {
    fun processTypedEvent(event: DeviceRuntimeTypedEvent) {
        when (event.type) {
            DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_PROGRESS,
            DeviceRuntimeTypedEvent.Type.FIRMWARE_OTA_COMPLETED -> processOtaEvent(event)
            DeviceRuntimeTypedEvent.Type.SYSTEM_RESTARTING -> processRestarting(event)
            else -> Unit
        }
    }

    private fun processOtaEvent(event: DeviceRuntimeTypedEvent) {
        val payload = event.payload as? DeviceRuntimeEventPayload.Snapshot
        val selected = planStore.selected(event.deviceUid)
        when {
            payload == null -> failureHandler.fail(event.deviceUid, invalidPayloadFailure(event))
            selected == null || selected.runtimeGeneration != event.generation -> Unit
            else -> DeviceFirmwareStatusParser.parseOtaProgressEventExact(payload.data).fold(
                onSuccess = { otaEvent ->
                    snapshotController.applySnapshot(
                        deviceUid = event.deviceUid,
                        snapshot = otaEvent.ota,
                        selected = selected,
                        requestId = event.messageId,
                        generation = event.generation
                    )
                },
                onFailure = { error ->
                    failureHandler.fail(event.deviceUid, contractFailure(event, error))
                }
            )
        }
    }

    private fun processRestarting(event: DeviceRuntimeTypedEvent) {
        val active = hasActiveOperation(event.deviceUid, stateStore, planStore)
        val sameGeneration = planStore.selected(event.deviceUid)?.runtimeGeneration == event.generation
        if (active && sameGeneration) {
            recoveryController.markRuntimeUnavailable(event.deviceUid)
        }
    }

    private fun invalidPayloadFailure(event: DeviceRuntimeTypedEvent): DeviceFirmwareFailure =
        DeviceFirmwareFailureMapper.local(
            technicalMessage = "Firmware OTA event payload is not a snapshot.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = DeviceFirmwareFailureStage.STATUS,
            code = "event_payload_invalid",
            requestId = event.messageId,
            recoverable = false,
            kind = DeviceFirmwareFailureKind.PROTOCOL
        )

    private fun contractFailure(
        event: DeviceRuntimeTypedEvent,
        error: Throwable
    ): DeviceFirmwareFailure = DeviceFirmwareFailureMapper.fromThrowable(
        error = error,
        source = DeviceFirmwareFailureSource.RUNTIME,
        stage = DeviceFirmwareFailureStage.STATUS,
        code = "event_contract_invalid",
        recoverable = false
    ).copy(requestId = event.messageId)
}

private class DeviceOtaRecoveryController(
    private val environment: DeviceOtaRecoveryEnvironment,
    private val stateStore: DeviceOtaStateStore,
    private val planStore: DeviceOtaPlanStore,
    private val jobStore: DeviceOtaJobStore,
    private val failureHandler: DeviceOtaFailureHandler
) {
    fun processLifecycleEvent(event: DeviceRuntimeLifecycleEvent) {
        when (event) {
            is DeviceRuntimeLifecycleEvent.Authenticated ->
                recoverAfterAuthentication(event.deviceUid)
            is DeviceRuntimeLifecycleEvent.Unavailable ->
                markRuntimeUnavailable(event.deviceUid)
        }
    }

    fun processSnapshotUpdates(snapshots: Map<DeviceUid, DeviceSnapshot>) {
        planStore.pendingDeviceUids().forEach { deviceUid ->
            snapshots[deviceUid]?.let { snapshot -> verifyInstalledFirmware(deviceUid, snapshot) }
        }
    }

    fun armRestartVerification(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?
    ) {
        if (snapshot.requiresRestartVerification && selected != null) {
            planStore.armVerification(deviceUid, selected)
            if (snapshot.restartScheduled) scheduleRecovery(deviceUid)
        }
    }

    fun verifyCurrentFirmwareIfReady(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?
    ) {
        if (snapshot.requiresRestartVerification && selected != null) {
            environment.runtime.snapshotProvider(deviceUid)?.let { current ->
                verifyInstalledFirmware(deviceUid, current)
            }
        }
    }

    fun markRuntimeUnavailable(deviceUid: DeviceUid) {
        val current = stateStore.current(deviceUid)
        if (!current.requiresRuntimeRecovery && !planStore.hasPendingVerification(deviceUid)) return
        stateStore.set(
            deviceUid,
            DeviceOtaState.Recovering(
                deviceUid = deviceUid.value,
                targetVersion = current.targetVersionOrEmpty,
                progressPermille = current.progressPermilleOrZero
            )
        )
        scheduleRecovery(deviceUid)
    }

    private fun recoverAfterAuthentication(deviceUid: DeviceUid) {
        jobStore.cancelRecovery(deviceUid)
        planStore.pending(deviceUid)?.let {
            environment.runtime.snapshotProvider(deviceUid)?.let { snapshot ->
                verifyInstalledFirmware(deviceUid, snapshot)
            }
            return
        }
        if (stateStore.current(deviceUid).requiresOtaStatusRecovery) {
            environment.scope.launch { environment.requestStatus(deviceUid) }
        }
    }

    private fun scheduleRecovery(deviceUid: DeviceUid) {
        synchronized(jobStore) {
            if (jobStore.recoveryJob(deviceUid)?.isActive == true) return
            val job = environment.scope.launch { runRecovery(deviceUid) }
            jobStore.putRecoveryJob(deviceUid, job)
            job.invokeOnCompletion { jobStore.removeRecoveryJob(deviceUid, job) }
        }
    }

    private suspend fun runRecovery(deviceUid: DeviceUid) {
        val timing = environment.timing
        if (timing.restartWaitMillis > 0L) delay(timing.restartWaitMillis)
        if (planStore.hasPendingVerification(deviceUid)) {
            val current = stateStore.current(deviceUid)
            stateStore.set(
                deviceUid,
                DeviceOtaState.Recovering(
                    deviceUid = deviceUid.value,
                    targetVersion = current.targetVersionOrEmpty,
                    progressPermille = current.progressPermilleOrZero
                )
            )
        }
        with(environment.runtime) {
            runCatching { refreshDiscovery() }
            if (timing.discoverySettleMillis > 0L) delay(timing.discoverySettleMillis)
            recoverRuntime(deviceUid)
        }
    }

    private fun verifyInstalledFirmware(deviceUid: DeviceUid, snapshot: DeviceSnapshot) {
        val selected = planStore.pending(deviceUid) ?: return
        val metadataIsCurrent = snapshot.hasValidatedRuntimeMetadata &&
            snapshot.runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration
        if (!metadataIsCurrent) return
        val error = DeviceOtaValidator.installedFirmwareError(snapshot, selected.dataPlan)
        if (error == null) {
            completeInstalledFirmwareVerification(deviceUid, selected)
        } else {
            failureHandler.fail(
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

    private fun completeInstalledFirmwareVerification(
        deviceUid: DeviceUid,
        selected: SelectedPlan
    ) {
        planStore.removeVerification(deviceUid)
        planStore.removeSelected(deviceUid)
        jobStore.cancelRecovery(deviceUid)
        stateStore.set(
            deviceUid,
            DeviceOtaState.Succeeded(
                deviceUid = deviceUid.value,
                targetVersion = selected.dataPlan.targetVersion,
                releaseContent = selected.applicationPlan.releaseContent
            )
        )
    }
}

private fun rejectAvailability(failure: DeviceFirmwareFailure): Nothing =
    throw AvailabilityFailure(failure)

private fun Throwable.toAvailabilityFailure(): DeviceFirmwareFailure =
    (this as? AvailabilityFailure)?.failure
        ?: DeviceFirmwareFailureMapper.fromThrowable(
            error = this,
            source = DeviceFirmwareFailureSource.ANDROID,
            stage = DeviceFirmwareFailureStage.AVAILABILITY,
            code = "availability_internal_error",
            recoverable = false
        )

private fun clearPlanState(
    deviceUid: DeviceUid,
    planStore: DeviceOtaPlanStore,
    jobStore: DeviceOtaJobStore
) {
    planStore.clear(deviceUid)
    jobStore.cancelRecovery(deviceUid)
}

private fun hasActiveOperation(
    deviceUid: DeviceUid,
    stateStore: DeviceOtaStateStore,
    planStore: DeviceOtaPlanStore
): Boolean = stateStore.current(deviceUid).isActiveOtaState ||
    planStore.hasPendingVerification(deviceUid)

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

private val DeviceFirmwareOtaSnapshot.requiresRestartVerification: Boolean
    get() = phase == DeviceFirmwareOtaPhase.SUCCEEDED && restartRequired

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
    DeviceFirmwareOtaPhase.FAILED -> DeviceFirmwareFailureStage.STATUS
}
