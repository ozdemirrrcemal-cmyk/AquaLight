package com.aqua.aqualight.data.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DevicesDataStoreManager private constructor(
    private val dataStore: DataStore<DevicesPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: DevicesDataStoreManager? = null

        fun create(
            context: Context
        ): DevicesDataStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(
                    appContext = context.applicationContext
                ).also { manager ->
                    INSTANCE = manager
                }
            }
        }

        private fun buildDataStore(
            appContext: Context
        ): DevicesDataStoreManager {
            val delegate = DevicesPreferencesSerializer

            val encryptedSerializer = EncryptedDevicesPreferencesSerializer(
                context = appContext,
                delegate = delegate
            )

            val dataStore = DataStoreFactory.create(
                serializer = encryptedSerializer,
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                ),
                produceFile = {
                    appContext.dataStoreFile("devices_prefs.pb")
                }
            )

            return DevicesDataStoreManager(dataStore)
        }
    }

    data class DeviceInfoUi(
        val id: Long,
        val aquaName: String,
        val name: String,
        val ip: String,
        val serial: String,
        val firmwareBuild: String,
        val lastSeenMillis: Long,
        val tankId: Long? = null
    )

    data class DeviceLastSeenUpdate(
        val id: Long,
        val ip: String,
        val firmwareBuild: String = ""
    )

    private val devicesPrefsFlow: Flow<DevicesPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(DevicesPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    val devicesFlow: Flow<List<DeviceInfoUi>> = devicesPrefsFlow.map { prefs ->
        prefs.devicesList.map { device ->
            device.toUi()
        }
    }

    val unassignedDevicesFlow: Flow<List<DeviceInfoUi>> = devicesFlow.map { devices ->
        devices.filter { device ->
            device.tankId == null
        }
    }

    fun devicesForTankFlow(
        tankId: Long
    ): Flow<List<DeviceInfoUi>> {
        return devicesFlow.map { devices ->
            devices.filter { device ->
                device.tankId == tankId
            }
        }
    }

    suspend fun deviceExists(
        id: Long
    ): Boolean {
        return devicesPrefsFlow.first()
            .devicesList
            .any { device ->
                device.id == id
            }
    }

    suspend fun addDevice(
        id: Long,
        aquaName: String,
        name: String,
        ip: String,
        serial: String,
        firmwareBuild: String
    ) {
        dataStore.updateData { prefs ->
            if (prefs.devicesList.any { device -> device.id == id }) {
                return@updateData prefs
            }

            val now = System.currentTimeMillis()

            val device = SavedDeviceInfo.newBuilder()
                .setId(id)
                .setAquaName(aquaName)
                .setName(name)
                .setIp(ip)
                .setSerial(serial)
                .setFirmwareBuild(firmwareBuild)
                .setLastSeenMillis(now)
                .setTankId(0L)
                .build()

            prefs.toBuilder()
                .addDevices(device)
                .build()
        }
    }

    suspend fun updateDevice(
        id: Long,
        aquaName: String? = null,
        name: String? = null,
        ip: String? = null,
        serial: String? = null,
        firmwareBuild: String? = null
    ) {
        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.id != id) {
                    return@map device
                }

                device.toBuilder().apply {
                    aquaName?.let { value ->
                        setAquaName(value)
                    }

                    name?.let { value ->
                        setName(value)
                    }

                    ip?.let { value ->
                        setIp(value)
                    }

                    serial?.let { value ->
                        setSerial(value)
                    }

                    firmwareBuild?.let { value ->
                        setFirmwareBuild(value)
                    }
                }.build()
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    suspend fun assignDeviceToTank(
        deviceId: Long,
        tankId: Long
    ) {
        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.id == deviceId) {
                    device.toBuilder()
                        .setTankId(tankId)
                        .build()
                } else {
                    device
                }
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    suspend fun removeDeviceFromTank(
        deviceId: Long
    ) {
        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.id == deviceId) {
                    device.toBuilder()
                        .setTankId(0L)
                        .build()
                } else {
                    device
                }
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    suspend fun unassignDevicesFromTank(
        tankId: Long
    ) {
        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.tankId == tankId) {
                    device.toBuilder()
                        .setTankId(0L)
                        .build()
                } else {
                    device
                }
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    suspend fun deleteDevices(
        ids: Set<Long>
    ) {
        if (ids.isEmpty()) {
            return
        }

        dataStore.updateData { prefs ->
            val filteredDevices = prefs.devicesList.filter { device ->
                device.id !in ids
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(filteredDevices)
                .build()
        }
    }

    suspend fun clearAllDevices() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearDevices()
                .build()
        }
    }
	
    suspend fun updateDevicesLastSeen(
        discovered: List<DeviceLastSeenUpdate>
    ) {
        if (discovered.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()

        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                val match = discovered.firstOrNull { discoveredDevice ->
                    discoveredDevice.id == device.id ||
                        discoveredDevice.ip == device.ip
                }

                if (match != null) {
                    device.toBuilder()
                        .setLastSeenMillis(now)
                        .setFirmwareBuild(match.firmwareBuild)
                        .build()
                } else {
                    device
                }
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    private fun SavedDeviceInfo.toUi(): DeviceInfoUi {
        return DeviceInfoUi(
            id = id,
            aquaName = aquaName,
            name = name,
            ip = ip,
            serial = serial,
            firmwareBuild = firmwareBuild,
            lastSeenMillis = lastSeenMillis,
            tankId = tankId.takeIf { value ->
                value > 0L
            }
        )
    }
}