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

/** Shared OTA application adapter used by all family-specific Settings screens. */
internal class DefaultDeviceFirmwareUpdateOperations(
    private val devicesRepository: DevicesRepository,
    private val statePublisher: suspend (DeviceOtaState, String) -> Unit = { _, _ -> }
) : DeviceFirmwareUpdateOperations, AutoCloseable {

    private val publisherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publisherJobs = ConcurrentHashMap<DeviceUid, Job>()

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

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> = coordinator.checkAvailability(
        deviceUid = requireDeviceUid(deviceUid),
        manifestUrl = manifestUrl,
        applyNow = applyNow
    )

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

private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
