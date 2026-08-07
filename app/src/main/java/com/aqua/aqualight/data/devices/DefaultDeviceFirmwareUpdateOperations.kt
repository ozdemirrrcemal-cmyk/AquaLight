package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareBackgroundOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareBackgroundRefreshResult
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceOtaCoordinator
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Shared OTA application adapter used by all family-specific Settings screens and owner work. */
@Suppress("TooManyFunctions", "LongParameterList")
internal class DefaultDeviceFirmwareUpdateOperations(
    private val devicesRepository: DevicesRepository,
    private val ownerUid: String = "",
    private val statePublisher: suspend (DeviceOtaState, String) -> Unit = { _, _ -> },
    private val dismissNotificationState: suspend (String) -> Unit = {},
    private val releaseNotificationState: suspend (String) -> Unit = {},
    private val notificationLedger: DeviceUpdateNotificationLedger =
        DeviceUpdateNotificationLedger.noOp(),
    private val ownerIsActive: () -> Boolean = { true },
    private val availabilityRefreshPolicy: DeviceFirmwareAvailabilityRefreshPolicy =
        DeviceFirmwareAvailabilityRefreshPolicy()
) : DeviceFirmwareUpdateOperations, DeviceFirmwareBackgroundOperations, AutoCloseable {

    private val publisherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publisherJobs = ConcurrentHashMap<DeviceUid, Job>()
    private val availabilityLocks = ConcurrentHashMap<DeviceUid, Mutex>()

    private val coordinator = DeviceOtaCoordinator(
        snapshotProvider = devicesRepository::currentDevice,
        connectRuntime = devicesRepository::connectRuntime,
        recoverRuntime = devicesRepository::replaceRuntimeAfterControlFailure,
        refreshDiscovery = {
            devicesRepository.refreshForegroundBurst()
            Unit
        },
        updaterProvider = { devicesRepository.runtimeModules()?.firmwareUpdate },
        runtimeLifecycleEvents = devicesRepository.runtimeLifecycleEvents(),
        runtimeTypedEvents = devicesRepository.typedRuntimeEvents(),
        snapshotUpdates = devicesRepository.snapshots
    )

    override fun observe(deviceUid: String): StateFlow<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        val states = coordinator.observe(uid)
        observeForNotifications(uid, states)
        return states
    }

    override suspend fun refreshAvailabilityIfStale(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        return availabilityLocks.computeIfAbsent(uid) { Mutex() }.withLock {
            val currentState = coordinator.observe(uid).value
            if (!availabilityRefreshPolicy.shouldRefresh(uid, currentState)) {
                return@withLock Result.success(currentState)
            }
            checkAvailabilityLocked(uid, manifestUrl, applyNow)
        }
    }

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        return availabilityLocks.computeIfAbsent(uid) { Mutex() }.withLock {
            checkAvailabilityLocked(uid, manifestUrl, applyNow)
        }
    }

    override suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<PreparedDeviceFirmwareUpdate> = checkAvailability(
        deviceUid = deviceUid,
        manifestUrl = manifestUrl,
        applyNow = applyNow
    ).toPreparedUpdateResult()

    override suspend fun startUpdate(
        plan: PreparedDeviceFirmwareUpdate
    ): DeviceFirmwareCommandResult = coordinator.startUpdate(plan)

    override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.requestStatus(requireDeviceUid(deviceUid))

    override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult {
        val uid = requireDeviceUid(deviceUid)
        return coordinator.clearStatus(uid).also { result ->
            if (result.isSuccess) {
                dismissNotificationState(uid.value)
            }
        }
    }

    override suspend fun releaseDevice(deviceUid: String) {
        val uid = requireDeviceUid(deviceUid)
        publisherJobs.remove(uid)?.cancelAndJoin()
        availabilityLocks.remove(uid)
        availabilityRefreshPolicy.remove(uid)
        coordinator.releaseDevice(uid)
        releaseNotificationState(uid.value)
    }

    override suspend fun reconcileNotificationState() {
        if (ownerUid.isBlank() || !ownerIsActive()) return
        val registeredDeviceUids = devicesRepository.currentDevices()
            .mapTo(linkedSetOf()) { snapshot -> snapshot.deviceUid.value }
        val staleDeviceUids = notificationLedger.recordedDeviceUids(ownerUid) -
            registeredDeviceUids
        staleDeviceUids.sorted().forEach { deviceUid ->
            releaseNotificationState(deviceUid)
        }
    }

    override suspend fun refreshRegisteredDevices(
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceFirmwareBackgroundRefreshResult> = runCatching {
        require(manifestUrl.isNotBlank()) { "Firmware manifest URL is missing." }
        if (!ownerIsActive()) return@runCatching emptyBackgroundResult()

        reconcileNotificationState()
        val liveRefresh = refreshLiveBackgroundSnapshots()
        if (!ownerIsActive()) return@runCatching emptyBackgroundResult()
        refreshBackgroundSnapshots(liveRefresh, manifestUrl, applyNow)
    }.rethrowFatalOrCancellation()

    private suspend fun refreshBackgroundSnapshots(
        liveRefresh: BackgroundSnapshotRefresh,
        manifestUrl: String,
        applyNow: Boolean
    ): DeviceFirmwareBackgroundRefreshResult {
        val snapshots = liveRefresh.snapshots.sortedBy { snapshot -> snapshot.deviceUid.value }
        val counts = BackgroundRefreshCounts()
        for (snapshot in snapshots) {
            if (!ownerIsActive()) return emptyBackgroundResult()
            counts.record(
                refreshRegisteredDevice(
                    snapshot = snapshot,
                    liveDeviceUids = liveRefresh.liveDeviceUids,
                    manifestUrl = manifestUrl,
                    applyNow = applyNow
                )
            )
        }
        return counts.toResult(snapshots.size)
    }

    private suspend fun refreshLiveBackgroundSnapshots(): BackgroundSnapshotRefresh {
        val initial = devicesRepository.currentDevices()
        if (initial.isEmpty() || !devicesRepository.isLocalNetworkAvailable()) {
            return BackgroundSnapshotRefresh(initial, emptySet())
        }

        val refreshStartedAtMillis = System.currentTimeMillis()
        runCatching { devicesRepository.refreshForegroundBurst() }
            .rethrowFatalOrCancellation()
            .getOrNull()
        delay(BACKGROUND_DISCOVERY_SETTLE_MILLIS)

        val liveDeviceUids = devicesRepository.currentDevices()
            .asSequence()
            .filter { snapshot -> snapshot.lastSeenAtMillis >= refreshStartedAtMillis }
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .mapTo(linkedSetOf()) { snapshot -> snapshot.deviceUid }

        liveDeviceUids.forEach { deviceUid ->
            devicesRepository.connectRuntime(deviceUid)
                .rethrowFatalOrCancellation()
                .getOrNull()
        }
        awaitLiveRuntimeMetadata(liveDeviceUids)
        return BackgroundSnapshotRefresh(
            snapshots = devicesRepository.currentDevices(),
            liveDeviceUids = liveDeviceUids
        )
    }

    private suspend fun awaitLiveRuntimeMetadata(liveDeviceUids: Set<DeviceUid>) {
        if (liveDeviceUids.isEmpty()) return
        withTimeoutOrNull(BACKGROUND_RUNTIME_METADATA_TIMEOUT_MILLIS) {
            devicesRepository.snapshots.first { snapshots ->
                liveDeviceUids.all { deviceUid ->
                    snapshots[deviceUid]?.hasValidatedRuntimeMetadata == true ||
                        deviceUid !in snapshots
                }
            }
        }
    }

    private suspend fun refreshRegisteredDevice(
        snapshot: DeviceSnapshot,
        liveDeviceUids: Set<DeviceUid>,
        manifestUrl: String,
        applyNow: Boolean
    ): BackgroundRefreshOutcome {
        val precheck = DeviceFirmwareBackgroundProbePolicy.decide(
            freshlyDiscovered = snapshot.deviceUid in liveDeviceUids,
            hasValidatedRuntimeMetadata = snapshot.hasValidatedRuntimeMetadata,
            supportsOta = snapshot.capabilities.ota
        )
        if (precheck != DeviceFirmwareBackgroundProbePolicy.Decision.ELIGIBLE) {
            return when (precheck) {
                DeviceFirmwareBackgroundProbePolicy.Decision.METADATA_UNVALIDATED ->
                    BackgroundRefreshOutcome.LIVE_VALIDATION_FAILED
                DeviceFirmwareBackgroundProbePolicy.Decision.NOT_LIVE,
                DeviceFirmwareBackgroundProbePolicy.Decision.OTA_UNSUPPORTED ->
                    BackgroundRefreshOutcome.PRECHECK_SKIPPED
                DeviceFirmwareBackgroundProbePolicy.Decision.ELIGIBLE ->
                    error("Eligible background device escaped the precheck gate.")
            }
        }

        val uid = snapshot.deviceUid
        val result = availabilityLocks.computeIfAbsent(uid) { Mutex() }.withLock {
            val currentState = coordinator.observe(uid).value
            if (!availabilityRefreshPolicy.shouldRefreshForBackground(uid, currentState)) {
                Result.success(currentState)
            } else {
                checkAvailabilityLocked(uid, manifestUrl, applyNow)
            }
        }

        return result.fold(
            onSuccess = { state -> backgroundOutcome(uid, state) },
            onFailure = { BackgroundRefreshOutcome.FAILED }
        )
    }

    private suspend fun backgroundOutcome(
        deviceUid: DeviceUid,
        state: DeviceOtaState
    ): BackgroundRefreshOutcome = when (state) {
        is DeviceOtaState.UpdateAvailable -> {
            publishBackgroundState(deviceUid, state)
            BackgroundRefreshOutcome.UPDATE_AVAILABLE
        }
        is DeviceOtaState.UpToDate -> {
            publishBackgroundState(deviceUid, state)
            BackgroundRefreshOutcome.UP_TO_DATE
        }
        is DeviceOtaState.Unsupported -> {
            publishBackgroundState(deviceUid, state)
            BackgroundRefreshOutcome.UNSUPPORTED
        }
        else -> BackgroundRefreshOutcome.UNCHANGED
    }

    override fun close() {
        publisherJobs.values.forEach { job -> job.cancel() }
        publisherJobs.clear()
        availabilityLocks.clear()
        availabilityRefreshPolicy.clear()
        publisherScope.cancel()
        coordinator.close()
    }

    private suspend fun checkAvailabilityLocked(
        deviceUid: DeviceUid,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        return coordinator.checkAvailability(
            deviceUid = deviceUid,
            manifestUrl = manifestUrl,
            applyNow = applyNow
        ).rethrowFatalOrCancellation().also { result ->
            availabilityRefreshPolicy.recordResult(deviceUid, result)
        }
    }

    private suspend fun publishBackgroundState(
        deviceUid: DeviceUid,
        state: DeviceOtaState
    ) {
        if (!ownerIsActive()) return
        val snapshot = devicesRepository.currentDevice(deviceUid) ?: return
        statePublisher(state, snapshot.title)
    }

    private fun observeForNotifications(
        deviceUid: DeviceUid,
        states: StateFlow<DeviceOtaState>
    ) {
        publisherJobs.computeIfAbsent(deviceUid) {
            publisherScope.launch {
                states
                    .map { state -> NotificationEmission(state.notificationKey(), state) }
                    .distinctUntilChangedBy(NotificationEmission::key)
                    .collect { emission ->
                        if (emission.key != null && ownerIsActive()) {
                            val deviceName = devicesRepository.currentDevice(deviceUid)?.title.orEmpty()
                            statePublisher(emission.state, deviceName)
                        }
                    }
            }
        }
    }

    private fun requireDeviceUid(value: String): DeviceUid {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "Device uid is missing." }
        return DeviceUid(normalized)
    }

    private enum class BackgroundRefreshOutcome {
        PRECHECK_SKIPPED,
        LIVE_VALIDATION_FAILED,
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        UNSUPPORTED,
        UNCHANGED,
        FAILED
    }

    private data class BackgroundSnapshotRefresh(
        val snapshots: List<DeviceSnapshot>,
        val liveDeviceUids: Set<DeviceUid>
    )

    private class BackgroundRefreshCounts {
        private var eligible = 0
        private var updates = 0
        private var upToDate = 0
        private var skipped = 0
        private var failed = 0

        fun record(outcome: BackgroundRefreshOutcome) {
            when (outcome) {
                BackgroundRefreshOutcome.PRECHECK_SKIPPED -> skipped += 1
                BackgroundRefreshOutcome.LIVE_VALIDATION_FAILED -> failed += 1
                BackgroundRefreshOutcome.UPDATE_AVAILABLE -> {
                    eligible += 1
                    updates += 1
                }
                BackgroundRefreshOutcome.UP_TO_DATE -> {
                    eligible += 1
                    upToDate += 1
                }
                BackgroundRefreshOutcome.UNSUPPORTED -> {
                    eligible += 1
                    skipped += 1
                }
                BackgroundRefreshOutcome.UNCHANGED -> eligible += 1
                BackgroundRefreshOutcome.FAILED -> {
                    eligible += 1
                    failed += 1
                }
            }
        }

        fun toResult(inspected: Int) = DeviceFirmwareBackgroundRefreshResult(
            inspectedDeviceCount = inspected,
            eligibleDeviceCount = eligible,
            updateAvailableCount = updates,
            upToDateCount = upToDate,
            skippedDeviceCount = skipped,
            failedDeviceCount = failed
        )
    }

    private data class NotificationEmission(
        val key: String?,
        val state: DeviceOtaState
    )
}

internal fun <T> Result<T>.rethrowFatalOrCancellation(): Result<T> {
    val error = exceptionOrNull()
    when {
        error is CancellationException -> throw error
        error != null && error !is Exception -> throw error
    }
    return this
}

internal fun Result<DeviceOtaState>.toPreparedUpdateResult():
    Result<PreparedDeviceFirmwareUpdate> = fold(
    onSuccess = { state ->
        when (state) {
            is DeviceOtaState.UpdateAvailable -> Result.success(state.plan)
            is DeviceOtaState.UpToDate -> Result.failure(
                IllegalStateException(
                    "Device is already up to date: ${state.currentVersion}."
                )
            )
            is DeviceOtaState.Unsupported -> Result.failure(
                IllegalStateException("OTA is unsupported for this device.")
            )
            is DeviceOtaState.Failed -> Result.failure(
                IllegalStateException(
                    state.failure.diagnosticMessage.ifBlank { state.failure.reason.name }
                )
            )
            else -> Result.failure(
                IllegalStateException(
                    "OTA availability did not produce a prepared update plan."
                )
            )
        }
    },
    onFailure = { error -> Result.failure(error) }
)

internal class DeviceFirmwareAvailabilityRefreshPolicy(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val freshnessMillis: Long = DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS,
    private val failureRetryMillis: Long =
        DEVICE_FIRMWARE_AVAILABILITY_FAILURE_RETRY_MILLIS
) {
    private data class RefreshRecord(
        val completedAtMillis: Long,
        val freshnessMillis: Long
    )

    private val refreshRecords = ConcurrentHashMap<DeviceUid, RefreshRecord>()

    init {
        require(freshnessMillis >= 0L)
        require(failureRetryMillis >= 0L)
    }

    fun shouldRefresh(deviceUid: DeviceUid, state: DeviceOtaState): Boolean {
        return state.allowsPassiveAvailabilityRefresh() && isStale(deviceUid)
    }

    fun shouldRefreshForBackground(deviceUid: DeviceUid, state: DeviceOtaState): Boolean {
        return state.allowsBackgroundAvailabilityRefresh() && isStale(deviceUid)
    }

    fun recordResult(deviceUid: DeviceUid, result: Result<DeviceOtaState>) {
        val resultFreshnessMillis = if (result.isSuccess) {
            freshnessMillis
        } else {
            failureRetryMillis
        }
        refreshRecords[deviceUid] = RefreshRecord(
            completedAtMillis = nowMillis(),
            freshnessMillis = resultFreshnessMillis
        )
    }

    private fun isStale(deviceUid: DeviceUid): Boolean {
        val record = refreshRecords[deviceUid] ?: return true
        return nowMillis() - record.completedAtMillis >= record.freshnessMillis
    }

    fun remove(deviceUid: DeviceUid) {
        refreshRecords.remove(deviceUid)
    }

    fun clear() {
        refreshRecords.clear()
    }
}

private fun DeviceOtaState.allowsPassiveAvailabilityRefresh(): Boolean = when (this) {
    is DeviceOtaState.Idle,
    is DeviceOtaState.UpToDate -> true
    is DeviceOtaState.Failed -> failure.recoverable
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.UpdateAvailable,
    is DeviceOtaState.Starting,
    is DeviceOtaState.InProgress,
    is DeviceOtaState.Recovering,
    is DeviceOtaState.RestartRequired,
    is DeviceOtaState.Succeeded -> false
}

private fun DeviceOtaState.allowsBackgroundAvailabilityRefresh(): Boolean = when (this) {
    is DeviceOtaState.Idle,
    is DeviceOtaState.UpToDate,
    is DeviceOtaState.UpdateAvailable,
    is DeviceOtaState.Succeeded -> true
    is DeviceOtaState.Failed -> failure.recoverable
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.Starting,
    is DeviceOtaState.InProgress,
    is DeviceOtaState.Recovering,
    is DeviceOtaState.RestartRequired -> false
}

private fun DeviceOtaState.notificationKey(): String? = when (this) {
    is DeviceOtaState.UpdateAvailable -> "available:${plan.targetVersion}"
    is DeviceOtaState.Starting -> "starting:${plan.targetVersion}"
    is DeviceOtaState.InProgress ->
        "progress:$targetVersion:$phase:${progressPermille.toProgressPercent()}"
    is DeviceOtaState.Recovering ->
        "recovering:$targetVersion:${progressPermille.toProgressPercent()}"
    is DeviceOtaState.RestartRequired -> "restart:$targetVersion:$restartScheduled"
    is DeviceOtaState.Succeeded -> "succeeded:$targetVersion"
    is DeviceOtaState.Failed -> with(failure) {
        "failed:$reason:$code:$field:$httpStatus:$recoverable"
    }
    is DeviceOtaState.Unsupported -> "unsupported"
    is DeviceOtaState.UpToDate -> "up-to-date:$currentVersion:$latestVersion"
    is DeviceOtaState.Idle,
    is DeviceOtaState.Checking -> null
}

private fun Int.toProgressPercent(): Int =
    coerceIn(0, COMPLETE_PROGRESS_PERMILLE) / PERMILLE_PER_PERCENT

private fun emptyBackgroundResult() = DeviceFirmwareBackgroundRefreshResult(
    inspectedDeviceCount = 0,
    eligibleDeviceCount = 0,
    updateAvailableCount = 0,
    upToDateCount = 0,
    skippedDeviceCount = 0,
    failedDeviceCount = 0
)

internal const val DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS = 15L * 60L * 1_000L
internal const val DEVICE_FIRMWARE_AVAILABILITY_FAILURE_RETRY_MILLIS = 30L * 1_000L
private const val BACKGROUND_DISCOVERY_SETTLE_MILLIS = 750L
private const val BACKGROUND_RUNTIME_METADATA_TIMEOUT_MILLIS = 8_000L
private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
