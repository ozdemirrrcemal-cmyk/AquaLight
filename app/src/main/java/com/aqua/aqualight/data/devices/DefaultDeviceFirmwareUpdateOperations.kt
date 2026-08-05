package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceOtaCoordinator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
            availabilityRefreshPolicy.recordAttempt(uid)
            coordinator.checkAvailability(
                deviceUid = uid,
                manifestUrl = manifestUrl,
                applyNow = applyNow
            )
        }
    }

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val uid = requireDeviceUid(deviceUid)
        return availabilityLocks.computeIfAbsent(uid) { Mutex() }.withLock {
            availabilityRefreshPolicy.recordAttempt(uid)
            coordinator.checkAvailability(
                deviceUid = uid,
                manifestUrl = manifestUrl,
                applyNow = applyNow
            )
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
    ).mapCatching { state ->
        when (state) {
            is DeviceOtaState.UpdateAvailable -> state.plan
            is DeviceOtaState.UpToDate -> error(
                "Device is already up to date: ${state.currentVersion}."
            )
            is DeviceOtaState.Unsupported -> error("OTA is unsupported for this device.")
            is DeviceOtaState.Failed -> error(
                state.failure.diagnosticMessage.ifBlank { state.failure.reason.name }
            )
            else -> error("OTA availability did not produce a prepared update plan.")
        }
    }

    override suspend fun startUpdate(
        plan: PreparedDeviceFirmwareUpdate
    ): DeviceFirmwareCommandResult = coordinator.startUpdate(plan)

    override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.requestStatus(requireDeviceUid(deviceUid))

    override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.clearStatus(requireDeviceUid(deviceUid))

    override fun close() {
        publisherJobs.values.forEach { job -> job.cancel() }
        publisherJobs.clear()
        availabilityLocks.clear()
        availabilityRefreshPolicy.clear()
        publisherScope.cancel()
        coordinator.close()
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

internal class DeviceFirmwareAvailabilityRefreshPolicy(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val freshnessMillis: Long = DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS
) {
    private val lastAttemptAtMillis = ConcurrentHashMap<DeviceUid, Long>()

    fun shouldRefresh(deviceUid: DeviceUid, state: DeviceOtaState): Boolean {
        val lastAttempt = lastAttemptAtMillis[deviceUid]
        val stale = lastAttempt == null || nowMillis() - lastAttempt >= freshnessMillis
        return state.allowsPassiveAvailabilityRefresh() && stale
    }

    fun recordAttempt(deviceUid: DeviceUid) {
        lastAttemptAtMillis[deviceUid] = nowMillis()
    }

    fun clear() {
        lastAttemptAtMillis.clear()
    }
}

private fun DeviceOtaState.allowsPassiveAvailabilityRefresh(): Boolean = when (this) {
    is DeviceOtaState.Idle,
    is DeviceOtaState.UpToDate -> true
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.UpdateAvailable,
    is DeviceOtaState.Starting,
    is DeviceOtaState.InProgress,
    is DeviceOtaState.Recovering,
    is DeviceOtaState.RestartRequired,
    is DeviceOtaState.Succeeded,
    is DeviceOtaState.Failed -> false
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
    is DeviceOtaState.Idle,
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.UpToDate -> null
}

private fun Int.toProgressPercent(): Int =
    coerceIn(0, COMPLETE_PROGRESS_PERMILLE) / PERMILLE_PER_PERCENT

internal const val DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS = 15L * 60L * 1_000L
private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
