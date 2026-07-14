package com.aqua.aqualight.data.devices.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.knownDevicesDataStore: DataStore<KnownDevicesStore> by dataStore(
    fileName = "known_devices.pb",
    serializer = KnownDevicesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.KNOWN_DEVICES
        )
        KnownDevicesStore.getDefaultInstance()
    }
)

/**
 * Durable owner-scoped non-secret device metadata.
 *
 * This is the only known-device persistence source. Runtime credentials are not
 * stored here. The application is unreleased, so no legacy JSON migration or
 * fallback path exists by design.
 */
class DeviceKnownStore(
    context: Context,
    ownerUid: String
) {

    private val dataStore = context.applicationContext.knownDevicesDataStore
    private val mutationMutex = Mutex()
    private val ownerUid = ownerUid.trim().also { normalizedOwnerUid ->
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }
    }

    suspend fun loadSnapshots(): List<DeviceSnapshot> {
        return dataStore.data.first()
            .getDevicesList()
            .asSequence()
            .filter { stored ->
                stored.ownerUid == ownerUid
            }
            .map(KnownDeviceProtoMapper::toSnapshot)
            .sortedWith(
                compareBy<DeviceSnapshot> { snapshot ->
                    snapshot.title.lowercase()
                }.thenBy { snapshot ->
                    snapshot.deviceUid.value
                }
            )
            .toList()
    }

    suspend fun saveSnapshot(
        snapshot: DeviceSnapshot
    ) {
        saveSnapshots(listOf(snapshot))
    }

    suspend fun saveSnapshots(
        snapshots: Iterable<DeviceSnapshot>
    ) {
        val snapshotList = snapshots.toList()
        if (snapshotList.isEmpty()) {
            return
        }

        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.saveSnapshots(
                    store = currentStore,
                    ownerUid = ownerUid,
                    snapshots = snapshotList
                )
            }
        }
    }

    suspend fun remove(
        deviceUid: DeviceUid
    ) {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.removeKnownDevice(
                    store = currentStore,
                    ownerUid = ownerUid,
                    deviceUid = deviceUid
                )
            }
        }
    }

    suspend fun forgetDevice(
        deviceUid: DeviceUid
    ) {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.forgetDevice(
                    store = currentStore,
                    ownerUid = ownerUid,
                    deviceUid = deviceUid
                )
            }
        }
    }

    suspend fun ignoreDevice(
        deviceUid: DeviceUid
    ) {
        forgetDevice(deviceUid)
    }

    suspend fun allowDevice(
        deviceUid: DeviceUid
    ) {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.allowDevice(
                    store = currentStore,
                    ownerUid = ownerUid,
                    deviceUid = deviceUid
                )
            }
        }
    }

    suspend fun isIgnored(
        deviceUid: DeviceUid
    ): Boolean {
        return deviceUid.value in ignoredDeviceUidValues()
    }

    suspend fun ignoredDeviceUidValues(): Set<String> {
        return dataStore.data.first()
            .getIgnoredDevicesList()
            .asSequence()
            .filter { ignored ->
                ignored.ownerUid == ownerUid
            }
            .map { ignored ->
                ignored.deviceUid
            }
            .toSet()
    }

    suspend fun clearIgnoredDevices() {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.clearIgnoredDevices(
                    store = currentStore,
                    ownerUid = ownerUid
                )
            }
        }
    }

    suspend fun clear() {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.clearKnownDevices(
                    store = currentStore,
                    ownerUid = ownerUid
                )
            }
        }
    }

    suspend fun clearOwnerData() {
        mutationMutex.withLock {
            dataStore.updateData { currentStore ->
                KnownDevicesStoreReducer.clearOwner(
                    store = currentStore,
                    ownerUid = ownerUid
                )
            }
        }
    }
}
