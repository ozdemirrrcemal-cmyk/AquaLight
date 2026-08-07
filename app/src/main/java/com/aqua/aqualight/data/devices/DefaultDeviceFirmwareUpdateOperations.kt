package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceOtaCoordinator
import com.aqua.aqualight.i18n.AppLanguageController
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared OTA application adapter used by all family-specific Settings screens. */
internal class DefaultDeviceFirmwareUpdateOperations(
    private val devicesRepository: DevicesRepository,
    private val statePublisher: suspend (DeviceOtaState, String) -> Unit = { _, _ -> },
    private val availabilityRefreshPolicy: DeviceFirmwareAvailabilityRefreshPolicy =
        DeviceFirmwareAvailabilityRefreshPolicy()
) : DeviceFirmwareUpdateOperations, AutoCloseable {

    private val operationsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    private val localeRefreshJob = operationsScope.launch {
        AppLanguageController.languageChanges
            .drop(1)
            .collect {
                publisherJobs.keys.toList().forEach { deviceUid ->
                    refreshAvailabilityIfStale(
                        deviceUid = deviceUid.value,
                        manifestUrl = DEVICE_FIRMWARE_MANIFEST_URL,
                        applyNow = true
                    )
                }
            }
    }

    override fun observe(deviceUid: String): StateFlow<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        val states = coordinator.observe(uid)
        observeForNotifications(uid, states)
        if (
            states.value.requiresReleaseContentRelocalization(
                AppLanguageController.current()
            )
        ) {
            operationsScope.launch {
                refreshAvailabilityIfStale(
                    deviceUid = uid.value,
                    manifestUrl = DEVICE_FIRMWARE_MANIFEST_URL,
                    applyNow = true
                )
            }
        }
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
            if (
                !availabilityRefreshPolicy.shouldRefresh(
                    deviceUid = uid,
                    state = currentState,
                    preferredLocaleTag = AppLanguageController.current()
                )
            ) {
                return@withLock Result.success(currentState)
            }
            coordinator.checkAvailability(
                deviceUid = uid,
                manifestUrl = manifestUrl,
                applyNow = applyNow
            ).rethrowFatalOrCancellation().also { result ->
                availabilityRefreshPolicy.recordResult(uid, result)
            }
        }
    }

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        return availabilityLocks.computeIfAbsent(uid) { Mutex() }.withLock {
            coordinator.checkAvailability(
                deviceUid = uid,
                manifestUrl = manifestUrl,
                applyNow = applyNow
            ).rethrowFatalOrCancellation().also { result ->
                availabilityRefreshPolicy.recordResult(uid, result)
            }
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

    override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.clearStatus(requireDeviceUid(deviceUid))

    override fun close() {
        localeRefreshJob.cancel()
        publisherJobs.values.forEach { job -> job.cancel() }
        publisherJobs.clear()
        availabilityLocks.clear()
        availabilityRefreshPolicy.clear()
        operationsScope.cancel()
        coordinator.close()
    }

    private fun observeForNotifications(
        deviceUid: DeviceUid,
        states: StateFlow<DeviceOtaState>
    ) {
        publisherJobs.computeIfAbsent(deviceUid) {
            operationsScope.launch {
                states
                    .map { state -> NotificationEmission(state.notificationKey(), state) }
                    .distinctUntilChangedBy(NotificationEmission::key)
                    .collect { emission ->
                        if (emission.key != null) {
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

    fun shouldRefresh(
        deviceUid: DeviceUid,
        state: DeviceOtaState,
        preferredLocaleTag: String? = null
    ): Boolean {
        if (state.requiresReleaseContentRelocalization(preferredLocaleTag)) {
            return true
        }
        val record = refreshRecords[deviceUid]
        val stale = record == null ||
            nowMillis() - record.completedAtMillis >= record.freshnessMillis
        return state.allowsPassiveAvailabilityRefresh() && stale
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

    fun clear() {
        refreshRecords.clear()
    }
}

private fun DeviceOtaState.requiresReleaseContentRelocalization(
    preferredLocaleTag: String?
): Boolean {
    val preferredLocale = preferredLocaleTag.releaseLocaleOrNull()
    val currentLocale = when (this) {
        is DeviceOtaState.UpToDate -> releaseContent.localeTag
        is DeviceOtaState.UpdateAvailable -> plan.releaseContent.localeTag
        else -> null
    }.releaseLocaleOrNull()

    return preferredLocale != null &&
        currentLocale != null &&
        currentLocale != preferredLocale
}

private fun String?.releaseLocaleOrNull(): String? {
    val normalized = this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.substringBefore('-')
        .orEmpty()
    return normalized.takeIf(String::isNotBlank)
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

private fun DeviceOtaState.notificationKey(): String? = when (this) {
    is DeviceOtaState.UpdateAvailable -> "available:${plan.targetVersion}"
    is DeviceOtaState.Starting -> "starting:${plan.targetVersion}"
    is DeviceOtaState.InProgress ->
        "progress:$targetVersion:$phase:${progressPermille.toNotificationProgressPercent()}"
    is DeviceOtaState.Recovering ->
        "recovering:$targetVersion:${progressPermille.toNotificationProgressPercent()}"
    is DeviceOtaState.RestartRequired -> "restart:$targetVersion:$restartScheduled"
    is DeviceOtaState.Succeeded -> "succeeded:$targetVersion"
    is DeviceOtaState.Failed -> with(failure) {
        "failed:$reason:$code:$field:$httpStatus:$recoverable"
    }
    is DeviceOtaState.Idle,
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.UpToDate -> null
}

private fun Int.toNotificationProgressPercent(): Int {
    val percent = coerceIn(0, COMPLETE_PROGRESS_PERMILLE) / PERMILLE_PER_PERCENT
    return if (percent >= COMPLETE_PROGRESS_PERCENT) {
        COMPLETE_PROGRESS_PERCENT
    } else {
        percent / PROGRESS_NOTIFICATION_STEP_PERCENT * PROGRESS_NOTIFICATION_STEP_PERCENT
    }
}

internal const val DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS = 15L * 60L * 1_000L
internal const val DEVICE_FIRMWARE_AVAILABILITY_FAILURE_RETRY_MILLIS = 30L * 1_000L
private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
private const val COMPLETE_PROGRESS_PERCENT = 100
private const val PROGRESS_NOTIFICATION_STEP_PERCENT = 5
