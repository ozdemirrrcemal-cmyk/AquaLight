package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult as AppCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaFailure
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
    private val recoveryStore: DeviceOtaRecoveryStore = NoOpDeviceOtaRecoveryStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val restartWaitMillis: Long = DEFAULT_RESTART_WAIT_MILLIS,
    private val discoverySettleMillis: Long = DEFAULT_DISCOVERY_SETTLE_MILLIS,
    private val postRestartRecoveryTimeoutMillis: Long = DEFAULT_POST_RESTART_RECOVERY_TIMEOUT_MILLIS,
    private val recoveryBackoffMillis: List<Long> = DEFAULT_RECOVERY_BACKOFF_MILLIS,
    private val clockMillis: () -> Long = System::currentTimeMillis
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
        val failure: DeviceOtaFailure,
        val publishFailure: Boolean = false
    ) : IllegalStateException(failure.diagnosticMessage)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val states = ConcurrentHashMap<DeviceUid, MutableStateFlow<DeviceOtaState>>()
    private val selectedPlans = ConcurrentHashMap<DeviceUid, SelectedPlan>()
    private val pendingVersionVerification = ConcurrentHashMap<DeviceUid, DeviceOtaRecoveryRecord>()
    private val verificationReady = ConcurrentHashMap.newKeySet<DeviceUid>()
    private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()
    private val recoveryJobs = ConcurrentHashMap<DeviceUid, Job>()

    init {
        require(restartWaitMillis >= 0L)
        require(discoverySettleMillis >= 0L)
        require(postRestartRecoveryTimeoutMillis > 0L)
        require(recoveryBackoffMillis.isNotEmpty())
        require(recoveryBackoffMillis.all { it >= 0L })
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

    fun observe(deviceUid: DeviceUid): StateFlow<DeviceOtaState> {
        restorePendingRecovery(deviceUid)
        return stateFlow(deviceUid).asStateFlow()
    }

    suspend fun checkAvailability(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        restorePendingRecovery(deviceUid)
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
            state.value = DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                failure = DeviceOtaFailureMapper.availability(error)
            )
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
            return DeviceOtaState.Unsupported(deviceUid.value)
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
            selectedPlans[deviceUid] = SelectedPlan(
                dataPlan = availability.plan,
                applicationPlan = applicationPlan
            )
            DeviceOtaState.UpdateAvailable(applicationPlan)
        }
    }

    suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): AppCommandResult {
        val deviceUid = runCatching { DeviceUid(plan.deviceUid) }.getOrElse { error ->
            return AppCommandResult(
                sent = false,
                failure = DeviceOtaFailureMapper.protocol(error.message.orEmpty())
            )
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
            DeviceOtaFailureMapper.protocol("No prepared OTA plan exists for this device.")
        )
        if (selected.applicationPlan != plan) {
            rejectStart(
                DeviceOtaFailureMapper.protocol(
                    "OTA plan differs from the selected exact artifact."
                )
            )
        }
        if (hasActiveOperation(deviceUid)) {
            rejectStart(
                DeviceOtaFailureMapper.busy(
                    "An OTA operation is already active for this device."
                )
            )
        }
        val snapshot = snapshotProvider(deviceUid) ?: rejectStart(
            DeviceOtaFailureMapper.connection("Device snapshot is not available.")
        )
        DeviceOtaValidator.planAgainstSnapshot(selected.dataPlan, snapshot)?.let { error ->
            rejectStart(
                failure = DeviceOtaFailureMapper.checkFailure(error),
                publishFailure = true
            )
        }
        val updater = updaterProvider() ?: rejectStart(
            DeviceOtaFailureMapper.internal("Firmware update runtime is not configured.")
        )
        connectRuntime(deviceUid).exceptionOrNull()?.let { error ->
            rejectStart(
                failure = DeviceOtaFailureMapper.connection(error.message.orEmpty()),
                publishFailure = true
            )
        }
        return StartContext(selected, updater)
    }

    private fun rejectStart(
        failure: DeviceOtaFailure,
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
                val echo = accepted.request
                if (
                    !accepted.accepted ||
                    echo == null ||
                    !echo.matches(context.selected.dataPlan.payload)
                ) {
                    val failure = DeviceOtaFailureMapper.protocol(
                        "Firmware OTA request echo differs from the selected plan."
                    )
                    fail(deviceUid, failure)
                    AppCommandResult(
                        sent = false,
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
                            sent = false,
                            messageId = outcome.messageId,
                            failure = failure
                        )
                    } ?: outcome.toApplicationResult()
                }
            }
            else -> handleCommandFailure(deviceUid, outcome, preserveRecovery = false)
        }

    private fun handleStartPreparationFailure(
        deviceUid: DeviceUid,
        error: Throwable
    ): AppCommandResult {
        val preparationFailure = error as? StartPreparationFailure
        val failure = preparationFailure?.failure
            ?: DeviceOtaFailureMapper.internal(error.message.orEmpty())
        if (preparationFailure?.publishFailure == true) {
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
                    failure = DeviceOtaFailureMapper.connection(error.message.orEmpty())
                )
            }
        )

    suspend fun retryPostRestartRecovery(deviceUid: DeviceUid): AppCommandResult {
        restorePendingRecovery(deviceUid)
        val current = pendingVersionVerification[deviceUid]
            ?: return AppCommandResult(
                sent = false,
                failure = DeviceOtaFailureMapper.protocol(
                    "No pending post-restart OTA verification exists for this device."
                )
            )
        val restarted = current.copy(recoveryStartedAtMillis = clockMillis())
        pendingVersionVerification[deviceUid] = restarted
        recoveryStore.save(restarted)
        verificationReady.remove(deviceUid)
        recoveryJobs.remove(deviceUid)?.cancel()
        stateFlow(deviceUid).value = DeviceOtaState.Recovering(
            deviceUid = deviceUid.value,
            targetVersion = restarted.targetVersion,
            progressPermille = COMPLETE_PROGRESS_PERMILLE
        )
        scheduleRecovery(deviceUid)
        return AppCommandResult(sent = true)
    }

    private fun applyStatusOutcome(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot>
    ): AppCommandResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            val selected = selectedPlans[deviceUid]?.copy(runtimeGeneration = outcome.generation)
            if (selected != null) selectedPlans[deviceUid] = selected
            applySnapshot(deviceUid, outcome.value, selected, outcome.generation)?.let { failure ->
                AppCommandResult(
                    sent = false,
                    messageId = outcome.messageId,
                    failure = failure
                )
            } ?: outcome.toApplicationResult()
        }
        else -> {
            val failure = DeviceOtaFailureMapper.command(outcome)
            if (stateFlow(deviceUid).value is DeviceOtaState.UpdateAvailable) {
                outcome.toApplicationResult(failure)
            } else {
                handleCommandFailure(
                    deviceUid = deviceUid,
                    outcome = outcome,
                    preserveRecovery = stateFlow(deviceUid).value is DeviceOtaState.Recovering
                )
            }
        }
    }

    suspend fun clearStatus(deviceUid: DeviceUid): AppCommandResult =
        connectedUpdater(deviceUid).fold(
            onSuccess = { updater -> applyClearOutcome(deviceUid, updater.clearOtaStatus(deviceUid)) },
            onFailure = { error ->
                AppCommandResult(
                    sent = false,
                    failure = DeviceOtaFailureMapper.connection(error.message.orEmpty())
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
        else -> handleCommandFailure(deviceUid, outcome, preserveRecovery = false)
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
            is DeviceRuntimeLifecycleEvent.Authenticated -> recoverAfterAuthentication(event.deviceUid)
            is DeviceRuntimeLifecycleEvent.Unavailable -> markRuntimeUnavailable(event.deviceUid)
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
                DeviceOtaFailureMapper.protocol(
                    "Firmware OTA event payload is not a snapshot."
                )
            )
            selected == null || selected.runtimeGeneration != event.generation -> Unit
            else -> DeviceFirmwareStatusParser.parseOtaProgressEventExact(payload.data).fold(
                onSuccess = { snapshot ->
                    applySnapshot(event.deviceUid, snapshot, selected, event.generation)
                },
                onFailure = { error ->
                    fail(
                        event.deviceUid,
                        DeviceOtaFailureMapper.protocol(error.message.orEmpty())
                    )
                }
            )
        }
    }

    private fun processSnapshotUpdates(snapshots: Map<DeviceUid, DeviceSnapshot>) {
        pendingVersionVerification.keys.toList().forEach { deviceUid ->
            if (verificationReady.contains(deviceUid)) {
                snapshots[deviceUid]?.let { snapshot -> verifyInstalledFirmware(deviceUid, snapshot) }
            }
        }
    }

    private fun applySnapshot(
        deviceUid: DeviceUid,
        snapshot: DeviceFirmwareOtaSnapshot,
        selected: SelectedPlan?,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceOtaFailure? {
        val state = stateFlow(deviceUid)
        if (state.value.preservesPreparedUpdateFor(snapshot) && selected != null) {
            val prepared = selected.copy(runtimeGeneration = generation)
            selectedPlans[deviceUid] = prepared
            state.value = DeviceOtaState.UpdateAvailable(prepared.applicationPlan)
        } else {
            DeviceOtaValidator.snapshotAgainstPlan(snapshot, selected?.dataPlan)?.let { error ->
                val failure = DeviceOtaFailureMapper.protocol(error)
                fail(deviceUid, failure)
                return failure
            }
            val activeSelection = selected?.copy(runtimeGeneration = generation)
            if (activeSelection != null) selectedPlans[deviceUid] = activeSelection
            val releaseContent = activeSelection?.applicationPlan?.releaseContent
                ?: pendingVersionVerification[deviceUid]?.releaseContent
                ?: DeviceFirmwareReleaseContent.EMPTY
            val targetVersion = snapshot.targetVersion.ifBlank {
                activeSelection?.dataPlan?.targetVersion
                    ?: pendingVersionVerification[deviceUid]?.targetVersion
                    .orEmpty()
            }
            armRestartVerification(deviceUid, snapshot, activeSelection)
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
        }
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
            val existing = pendingVersionVerification[deviceUid]
            val record = existing ?: selected.toRecoveryRecord(deviceUid, clockMillis())
            pendingVersionVerification[deviceUid] = record
            recoveryStore.save(record)
            verificationReady.remove(deviceUid)
            if (snapshot.restartScheduled) scheduleRecovery(deviceUid)
        }
    }

    private fun recoverAfterAuthentication(deviceUid: DeviceUid) {
        restorePendingRecovery(deviceUid)
        pendingVersionVerification[deviceUid]?.let {
            verificationReady.add(deviceUid)
            snapshotProvider(deviceUid)?.let { snapshot -> verifyInstalledFirmware(deviceUid, snapshot) }
            if (pendingVersionVerification.containsKey(deviceUid)) scheduleRecovery(deviceUid)
            return
        }
        recoveryJobs.remove(deviceUid)?.cancel()
        if (stateFlow(deviceUid).value.requiresOtaStatusRecovery) {
            scope.launch { requestStatus(deviceUid) }
        }
    }

    private fun markRuntimeUnavailable(deviceUid: DeviceUid) {
        restorePendingRecovery(deviceUid)
        val current = stateFlow(deviceUid).value
        if (
            !current.requiresRuntimeRecovery &&
            !pendingVersionVerification.containsKey(deviceUid)
        ) {
            return
        }
        verificationReady.remove(deviceUid)
        stateFlow(deviceUid).value = DeviceOtaState.Recovering(
            deviceUid = deviceUid.value,
            targetVersion = pendingVersionVerification[deviceUid]?.targetVersion
                ?: current.targetVersionOrEmpty,
            progressPermille = current.progressPermilleOrZero
        )
        scheduleRecovery(deviceUid)
    }

    private fun scheduleRecovery(deviceUid: DeviceUid) {
        synchronized(recoveryJobs) {
            if (recoveryJobs[deviceUid]?.isActive == true) return
            val job = scope.launch {
                val pending = pendingVersionVerification[deviceUid]
                if (pending == null) {
                    runSingleRuntimeRecovery(deviceUid)
                    return@launch
                }

                var attempt = 0
                while (pendingVersionVerification.containsKey(deviceUid)) {
                    val record = pendingVersionVerification[deviceUid] ?: return@launch
                    val elapsed = (clockMillis() - record.recoveryStartedAtMillis).coerceAtLeast(0L)
                    val remaining = postRestartRecoveryTimeoutMillis - elapsed
                    if (remaining <= 0L) {
                        publishPostRestartTimeout(deviceUid, record)
                        return@launch
                    }

                    if (attempt == 0 && restartWaitMillis > 0L) {
                        delay(minOf(restartWaitMillis, remaining))
                    }
                    runCatching { refreshDiscovery() }
                    val afterDiscoveryRemaining = remainingRecoveryMillis(record)
                    if (afterDiscoveryRemaining <= 0L) {
                        publishPostRestartTimeout(deviceUid, record)
                        return@launch
                    }
                    if (discoverySettleMillis > 0L) {
                        delay(minOf(discoverySettleMillis, afterDiscoveryRemaining))
                    }

                    val recovered = recoverRuntime(deviceUid)
                    if (recovered.isSuccess) {
                        verificationReady.add(deviceUid)
                        snapshotProvider(deviceUid)?.let { snapshot ->
                            verifyInstalledFirmware(deviceUid, snapshot)
                        }
                        if (!pendingVersionVerification.containsKey(deviceUid)) return@launch
                    }

                    val current = pendingVersionVerification[deviceUid] ?: return@launch
                    val remainingAfterAttempt = remainingRecoveryMillis(current)
                    if (remainingAfterAttempt <= 0L) {
                        publishPostRestartTimeout(deviceUid, current)
                        return@launch
                    }
                    val backoff = recoveryBackoffMillis[
                        attempt.coerceAtMost(recoveryBackoffMillis.lastIndex)
                    ]
                    attempt++
                    if (backoff > 0L) delay(minOf(backoff, remainingAfterAttempt))
                }
            }
            recoveryJobs[deviceUid] = job
            job.invokeOnCompletion { recoveryJobs.remove(deviceUid, job) }
        }
    }

    private suspend fun runSingleRuntimeRecovery(deviceUid: DeviceUid) {
        if (restartWaitMillis > 0L) delay(restartWaitMillis)
        runCatching { refreshDiscovery() }
        if (discoverySettleMillis > 0L) delay(discoverySettleMillis)
        recoverRuntime(deviceUid)
    }

    private fun remainingRecoveryMillis(record: DeviceOtaRecoveryRecord): Long =
        postRestartRecoveryTimeoutMillis -
            (clockMillis() - record.recoveryStartedAtMillis).coerceAtLeast(0L)

    private fun publishPostRestartTimeout(
        deviceUid: DeviceUid,
        record: DeviceOtaRecoveryRecord
    ) {
        verificationReady.remove(deviceUid)
        stateFlow(deviceUid).value = DeviceOtaState.PostRestartTimeout(
            deviceUid = deviceUid.value,
            targetVersion = record.targetVersion,
            previousVersion = record.previousVersion,
            releaseContent = record.releaseContent
        )
    }

    private fun verifyInstalledFirmware(deviceUid: DeviceUid, snapshot: DeviceSnapshot) {
        val record = pendingVersionVerification[deviceUid] ?: return
        if (!verificationReady.contains(deviceUid) || !snapshot.hasValidatedRuntimeMetadata) return

        val identityError = installedIdentityError(snapshot, record)
        if (identityError != null) {
            fail(deviceUid, DeviceOtaFailureMapper.incompatible(identityError))
            return
        }

        when (snapshot.firmwareVersion) {
            record.targetVersion -> completeInstalledFirmwareVerification(deviceUid, record)
            record.previousVersion -> completeRollbackVerification(deviceUid, record)
            else -> fail(
                deviceUid,
                DeviceOtaFailureMapper.incompatible(
                    "Reconnected firmware is neither the OTA target nor the previous known-good version."
                )
            )
        }
    }

    private fun installedIdentityError(
        snapshot: DeviceSnapshot,
        record: DeviceOtaRecoveryRecord
    ): String? = when {
        snapshot.product.productKey != record.productKey ->
            "Reconnected device productKey differs after OTA restart."
        snapshot.product.productId != record.productId ->
            "Reconnected device productId differs after OTA restart."
        snapshot.product.model != record.model ->
            "Reconnected device model differs after OTA restart."
        snapshot.product.hardwareRevision != record.hardwareRevision ->
            "Reconnected device hardwareRevision differs after OTA restart."
        else -> null
    }

    private fun completeInstalledFirmwareVerification(
        deviceUid: DeviceUid,
        record: DeviceOtaRecoveryRecord
    ) {
        clearRecoveryRecord(deviceUid)
        selectedPlans.remove(deviceUid)
        stateFlow(deviceUid).value = DeviceOtaState.Succeeded(
            deviceUid = deviceUid.value,
            targetVersion = record.targetVersion,
            releaseContent = record.releaseContent
        )
    }

    private fun completeRollbackVerification(
        deviceUid: DeviceUid,
        record: DeviceOtaRecoveryRecord
    ) {
        clearRecoveryRecord(deviceUid)
        selectedPlans.remove(deviceUid)
        stateFlow(deviceUid).value = DeviceOtaState.RolledBack(
            deviceUid = deviceUid.value,
            targetVersion = record.targetVersion,
            restoredVersion = record.previousVersion,
            releaseContent = record.releaseContent
        )
    }

    private fun restorePendingRecovery(deviceUid: DeviceUid) {
        if (pendingVersionVerification.containsKey(deviceUid)) return
        val record = recoveryStore.load(deviceUid.value) ?: return
        pendingVersionVerification[deviceUid] = record
        verificationReady.remove(deviceUid)
        val current = stateFlow(deviceUid).value
        if (current is DeviceOtaState.Idle || current is DeviceOtaState.PostRestartTimeout) {
            stateFlow(deviceUid).value = DeviceOtaState.Recovering(
                deviceUid = deviceUid.value,
                targetVersion = record.targetVersion,
                progressPermille = COMPLETE_PROGRESS_PERMILLE
            )
        }
        scheduleRecovery(deviceUid)
    }

    private fun handleCommandFailure(
        deviceUid: DeviceUid,
        outcome: DeviceRuntimeCommandOutcome<*>,
        preserveRecovery: Boolean
    ): AppCommandResult {
        val failure = DeviceOtaFailureMapper.command(outcome)
        if (!(preserveRecovery && failure.recoverable)) {
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

    private fun clearRecoveryRecord(deviceUid: DeviceUid) {
        pendingVersionVerification.remove(deviceUid)
        verificationReady.remove(deviceUid)
        recoveryJobs.remove(deviceUid)?.cancel()
        recoveryStore.remove(deviceUid.value)
    }

    private fun clearPlanState(deviceUid: DeviceUid) {
        selectedPlans.remove(deviceUid)
        clearRecoveryRecord(deviceUid)
    }

    private fun fail(
        deviceUid: DeviceUid,
        failure: DeviceOtaFailure
    ) {
        clearRecoveryRecord(deviceUid)
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
        verificationReady.clear()
        startLocks.clear()
        scope.cancel()
    }

    private fun SelectedPlan.toRecoveryRecord(
        deviceUid: DeviceUid,
        startedAtMillis: Long
    ): DeviceOtaRecoveryRecord = DeviceOtaRecoveryRecord(
        deviceUid = deviceUid.value,
        previousVersion = dataPlan.currentVersion,
        targetVersion = dataPlan.targetVersion,
        productKey = dataPlan.productKey,
        productId = dataPlan.productId,
        model = dataPlan.model,
        hardwareRevision = dataPlan.hardwareRevision,
        runtimeMetadataGeneration = dataPlan.runtimeMetadataGeneration,
        manifestTag = applicationPlan.manifestTag,
        firmwareSha256 = dataPlan.firmware.sha256,
        releaseContent = applicationPlan.releaseContent,
        recoveryStartedAtMillis = startedAtMillis
    )

    private companion object {
        const val DEFAULT_RESTART_WAIT_MILLIS = 1_000L
        const val DEFAULT_DISCOVERY_SETTLE_MILLIS = 750L
        const val DEFAULT_POST_RESTART_RECOVERY_TIMEOUT_MILLIS = 120_000L
        const val COMPLETE_PROGRESS_PERMILLE = 1_000
        val DEFAULT_RECOVERY_BACKOFF_MILLIS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
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

internal fun DeviceOtaState.preservesPreparedUpdateFor(
    snapshot: DeviceFirmwareOtaSnapshot
): Boolean = this is DeviceOtaState.UpdateAvailable &&
    snapshot.phase == DeviceFirmwareOtaPhase.FAILED

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
        is DeviceOtaState.Succeeded -> targetVersion
        is DeviceOtaState.RolledBack -> targetVersion
        is DeviceOtaState.PostRestartTimeout -> targetVersion
        else -> ""
    }

private val DeviceOtaState.progressPermilleOrZero: Int
    get() = when (this) {
        is DeviceOtaState.InProgress -> progressPermille
        is DeviceOtaState.Recovering -> progressPermille
        else -> 0
    }

private fun DeviceRuntimeCommandOutcome<*>.toApplicationResult(
    failure: DeviceOtaFailure? = null
): AppCommandResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> -> AppCommandResult(
        sent = true,
        messageId = messageId
    )
    is DeviceRuntimeCommandOutcome.FirmwareError -> AppCommandResult(
        sent = true,
        messageId = messageId,
        failure = failure
    )
    is DeviceRuntimeCommandOutcome.ProtocolError -> AppCommandResult(
        sent = messageId.isNotBlank(),
        messageId = messageId,
        failure = failure
    )
    is DeviceRuntimeCommandOutcome.Timeout -> AppCommandResult(
        sent = true,
        messageId = messageId,
        failure = failure
    )
    is DeviceRuntimeCommandOutcome.Cancelled -> AppCommandResult(
        sent = messageId.isNotBlank(),
        messageId = messageId,
        failure = failure
    )
    is DeviceRuntimeCommandOutcome.SendFailed -> AppCommandResult(
        sent = false,
        messageId = messageId,
        failure = failure
    )
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> AppCommandResult(
        sent = false,
        failure = failure
    )
}