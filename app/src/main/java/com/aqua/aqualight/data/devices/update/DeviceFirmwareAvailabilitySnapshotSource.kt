package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.store.DeviceKnownStore

internal sealed interface DeviceFirmwareAvailabilitySnapshotResult {
    data class Ready(
        val currentDeviceUids: Set<String>,
        val eligibleSnapshots: List<DeviceSnapshot>
    ) : DeviceFirmwareAvailabilitySnapshotResult

    data object Retryable : DeviceFirmwareAvailabilitySnapshotResult
}

internal fun interface DeviceFirmwareAvailabilitySnapshotReader {
    suspend fun load(ownerUid: String): DeviceFirmwareAvailabilitySnapshotResult
}

internal data class ActiveOwnerDeviceSnapshotState(
    val ready: Boolean,
    val snapshots: List<DeviceSnapshot>
)

internal class DeviceFirmwareAvailabilitySnapshotSource(
    private val trust: DeviceFirmwareAvailabilityTrust,
    private val activeStateProvider: (String) -> ActiveOwnerDeviceSnapshotState?,
    private val durableSnapshotLoader: suspend (String) -> List<DeviceSnapshot>
) : DeviceFirmwareAvailabilitySnapshotReader {

    override suspend fun load(
        ownerUid: String
    ): DeviceFirmwareAvailabilitySnapshotResult {
        val owner = DeviceFirmwareAvailabilityTrustCodec.normalizeOwnerUid(ownerUid)
        val activeState = activeStateProvider(owner)
        return if (activeState == null) {
            loadDurable(owner)
        } else {
            loadActive(owner, activeState)
        }
    }

    private suspend fun loadActive(
        ownerUid: String,
        state: ActiveOwnerDeviceSnapshotState
    ): DeviceFirmwareAvailabilitySnapshotResult {
        if (!state.ready) {
            return DeviceFirmwareAvailabilitySnapshotResult.Retryable
        }
        val eligible = mutableListOf<DeviceSnapshot>()
        state.snapshots.forEach { snapshot ->
            if (trust.recordValidated(ownerUid, snapshot)) {
                eligible += snapshot
            } else {
                trust.clearDevice(ownerUid, snapshot.deviceUid.value)
            }
        }
        return readyResult(state.snapshots, eligible)
    }

    private suspend fun loadDurable(
        ownerUid: String
    ): DeviceFirmwareAvailabilitySnapshotResult {
        val snapshots = durableSnapshotLoader(ownerUid)
        val eligible = mutableListOf<DeviceSnapshot>()
        snapshots.forEach { snapshot ->
            if (trust.isFresh(ownerUid, snapshot)) {
                eligible += snapshot
            }
        }
        return readyResult(snapshots, eligible)
    }

    private fun readyResult(
        snapshots: List<DeviceSnapshot>,
        eligible: List<DeviceSnapshot>
    ): DeviceFirmwareAvailabilitySnapshotResult.Ready {
        val deviceUids = snapshots.mapTo(linkedSetOf()) { snapshot ->
            snapshot.deviceUid.value
        }
        return DeviceFirmwareAvailabilitySnapshotResult.Ready(
            currentDeviceUids = deviceUids,
            eligibleSnapshots = eligible
        )
    }

    companion object {
        fun create(context: Context): DeviceFirmwareAvailabilitySnapshotSource {
            val appContext = context.applicationContext
            return DeviceFirmwareAvailabilitySnapshotSource(
                trust = DeviceFirmwareAvailabilityTrustStore.create(appContext),
                activeStateProvider = ::activeOwnerDeviceSnapshotState,
                durableSnapshotLoader = { ownerUid ->
                    DeviceKnownStore(appContext, ownerUid).loadSnapshots()
                }
            )
        }
    }
}

private fun activeOwnerDeviceSnapshotState(
    ownerUid: String
): ActiveOwnerDeviceSnapshotState? {
    val repository = DevicesRepositoryProvider.currentRepository(ownerUid)
        ?: return null
    return ActiveOwnerDeviceSnapshotState(
        ready = repository.ready.value,
        snapshots = repository.currentDevices()
    )
}
