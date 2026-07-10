package com.aqua.aqualight.data.devices.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.first

private const val KNOWN_DEVICES_FILE_NAME = "known_devices_v1.pb"

private val Context.knownDevicesDataStore: DataStore<KnownDevicesStore> by dataStore(
    fileName = KNOWN_DEVICES_FILE_NAME,
    serializer = KnownDevicesSerializer
)

/**
 * Durable non-secret device metadata partitioned by authenticated Firebase owner.
 *
 * Runtime credentials stay in [DeviceCredentialStore]. The store is a strict Proto DataStore V1:
 * malformed or duplicate records are treated as corruption instead of being silently skipped.
 */
class DeviceKnownStore(
    context: Context
) {
    private val dataStore = context.applicationContext.knownDevicesDataStore

    suspend fun loadSnapshots(
        ownerUid: String = UserDataScope.currentUid()
    ): List<DeviceSnapshot> {
        val normalizedOwnerUid = normalizedOwnerUidOrNull(ownerUid)
            ?: return emptyList()

        return KnownDevicesStoreReducer.devicesForOwner(
            store = dataStore.data.first(),
            ownerUid = normalizedOwnerUid
        )
    }

    suspend fun saveSnapshot(
        snapshot: DeviceSnapshot,
        ownerUid: String = UserDataScope.requireCurrentUid()
    ) {
        saveSnapshots(
            snapshots = listOf(snapshot),
            ownerUid = ownerUid
        )
    }

    suspend fun saveSnapshots(
        snapshots: Iterable<DeviceSnapshot>,
        ownerUid: String = UserDataScope.requireCurrentUid()
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val snapshotsToSave = snapshots.toList()

        if (snapshotsToSave.isEmpty()) {
            return
        }

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.upsertDevices(
                store = currentStore,
                ownerUid = normalizedOwnerUid,
                snapshots = snapshotsToSave
            )
        }
    }

    suspend fun remove(
        deviceUid: DeviceUid,
        ownerUid: String = UserDataScope.requireCurrentUid()
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.removeDevice(
                store = currentStore,
                ownerUid = normalizedOwnerUid,
                deviceUid = deviceUid
            )
        }
    }

    suspend fun ignoreDevice(
        deviceUid: DeviceUid,
        ownerUid: String = UserDataScope.requireCurrentUid()
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.ignoreDevice(
                store = currentStore,
                ownerUid = normalizedOwnerUid,
                deviceUid = deviceUid
            )
        }
    }

    suspend fun allowDevice(
        deviceUid: DeviceUid,
        ownerUid: String = UserDataScope.requireCurrentUid()
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.allowDevice(
                store = currentStore,
                ownerUid = normalizedOwnerUid,
                deviceUid = deviceUid
            )
        }
    }

    suspend fun isIgnored(
        deviceUid: DeviceUid,
        ownerUid: String = UserDataScope.currentUid()
    ): Boolean {
        val normalizedOwnerUid = normalizedOwnerUidOrNull(ownerUid)
            ?: return false

        return deviceUid.value.trim() in KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
            store = dataStore.data.first(),
            ownerUid = normalizedOwnerUid
        )
    }

    suspend fun ignoredDeviceUidValues(
        ownerUid: String = UserDataScope.currentUid()
    ): Set<String> {
        val normalizedOwnerUid = normalizedOwnerUidOrNull(ownerUid)
            ?: return emptySet()

        return KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
            store = dataStore.data.first(),
            ownerUid = normalizedOwnerUid
        )
    }

    suspend fun clearIgnoredDevices(
        ownerUid: String = UserDataScope.currentUid()
    ) {
        val normalizedOwnerUid = normalizedOwnerUidOrNull(ownerUid)
            ?: return

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.clearOwnerIgnoredDevices(
                store = currentStore,
                ownerUid = normalizedOwnerUid
            )
        }
    }

    suspend fun clear(
        ownerUid: String = UserDataScope.currentUid()
    ) {
        val normalizedOwnerUid = normalizedOwnerUidOrNull(ownerUid)
            ?: return

        dataStore.updateData { currentStore ->
            KnownDevicesStoreReducer.clearOwnerDevices(
                store = currentStore,
                ownerUid = normalizedOwnerUid
            )
        }
    }

    private fun normalizedOwnerUidOrNull(
        ownerUid: String
    ): String? {
        return UserDataScope.normalizeOwnerUid(ownerUid)
            .takeIf { normalized -> normalized.isNotBlank() }
    }

    private fun requireOwnerUid(
        ownerUid: String
    ): String {
        return requireNotNull(normalizedOwnerUidOrNull(ownerUid)) {
            "Known-device storage requires an authenticated owner."
        }
    }
}
