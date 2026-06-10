package com.aqua.aqualight.data.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
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
        val deviceUid: String = "",
        val macAddress: String = "",
        val firmwareSerial: String = "",
        val firmwareBuild: String,
        val lastSeenMillis: Long,
        val tankId: Long? = null,

        val deviceType: AquaDeviceType = AquaDeviceType.UNKNOWN,

        val udpVersion: Int? = null,
        val tabLight: Boolean = false,
        val tabTimer: Boolean = false,
        val tabTemperature: Boolean = false,

        val productId: String = "",
        val productFamily: String = "",
        val productModel: String = "",
        val hardwareRevision: String = "",
        val firmwareVersion: String = "",
        val apiVersion: Int? = null,

        val channelCount: Int? = null,
        val sensorCount: Int? = null,

        val supportedFeatures: Set<String> = emptySet(),
        val supportedScreens: Set<String> = emptySet()
    )

    data class DeviceLastSeenUpdate(
        val id: Long,
        val ip: String,
        val firmwareBuild: String = "",
        val deviceUid: String? = null,
        val macAddress: String? = null,
        val firmwareSerial: String? = null,

        val deviceType: AquaDeviceType? = null,

        val udpVersion: Int? = null,
        val tabLight: Boolean? = null,
        val tabTimer: Boolean? = null,
        val tabTemperature: Boolean? = null,

        val productId: String? = null,
        val productFamily: String? = null,
        val productModel: String? = null,
        val hardwareRevision: String? = null,
        val firmwareVersion: String? = null,
        val apiVersion: Int? = null,

        val channelCount: Int? = null,
        val sensorCount: Int? = null,

        val supportedFeatures: Set<String>? = null,
        val supportedScreens: Set<String>? = null
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
        firmwareBuild: String,
        deviceUid: String = "",
        macAddress: String = "",
        firmwareSerial: String = "",

        deviceType: AquaDeviceType = AquaDeviceType.UNKNOWN,

        udpVersion: Int? = null,
        tabLight: Boolean = false,
        tabTimer: Boolean = false,
        tabTemperature: Boolean = false,

        productId: String = "",
        productFamily: String = "",
        productModel: String = "",
        hardwareRevision: String = "",
        firmwareVersion: String = "",
        apiVersion: Int? = null,

        channelCount: Int? = null,
        sensorCount: Int? = null,

        supportedFeatures: Set<String> = emptySet(),
        supportedScreens: Set<String> = emptySet()
    ) {
        dataStore.updateData { prefs ->
            if (prefs.devicesList.any { device -> device.id == id }) {
                return@updateData prefs
            }

            val now = System.currentTimeMillis()

            val resolvedType = resolveDeviceType(
                explicitType = deviceType,
                aquaName = aquaName,
                name = name,
                productId = productId
            )

            val device = SavedDeviceInfo.newBuilder()
                .setId(id)
                .setAquaName(aquaName)
                .setName(name)
                .setIp(ip)
                .setSerial(serial)
                .setFirmwareBuild(firmwareBuild)
                .setDeviceUid(deviceUid)
                .setMacAddress(macAddress)
                .setFirmwareSerial(firmwareSerial)
                .setLastSeenMillis(now)
                .setTankId(0L)
                .setDeviceType(resolvedType.storageKey)
                .setTabLight(tabLight)
                .setTabTimer(tabTimer)
                .setTabTemperature(tabTemperature)
                .setProductId(productId)
                .setProductFamily(productFamily)
                .setProductModel(productModel)
                .setHardwareRevision(hardwareRevision)
                .setFirmwareVersion(firmwareVersion)
                .addAllSupportedFeatures(supportedFeatures)
                .addAllSupportedScreens(supportedScreens)
                .apply {
                    udpVersion?.let { value ->
                        setUdpVersion(value)
                    }

                    apiVersion?.let { value ->
                        setApiVersion(value)
                    }

                    channelCount?.let { value ->
                        setChannelCount(value)
                    }

                    sensorCount?.let { value ->
                        setSensorCount(value)
                    }
                }
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
        firmwareBuild: String? = null,
        deviceType: AquaDeviceType? = null
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

                    deviceType?.let { value ->
                        setDeviceType(value.storageKey)
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
                    discoveredDevice.id == device.id
                }

                if (match != null) {
                    device.toBuilder().apply {
                        setIp(match.ip)
                        setLastSeenMillis(now)

                        if (match.firmwareBuild.isNotBlank()) {
                            setFirmwareBuild(match.firmwareBuild)
                        }

                        match.deviceUid?.takeIf { value ->
                            value.isNotBlank()
                        }?.let { value ->
                            setDeviceUid(value)
                        }

                        match.macAddress?.takeIf { value ->
                            value.isNotBlank()
                        }?.let { value ->
                            setMacAddress(value)
                        }

                        match.firmwareSerial?.takeIf { value ->
                            value.isNotBlank()
                        }?.let { value ->
                            setFirmwareSerial(value)
                        }

                        match.deviceType?.let { value ->
                            setDeviceType(value.storageKey)
                        }

                        match.udpVersion?.let { value ->
                            setUdpVersion(value)
                        }

                        match.tabLight?.let { value ->
                            setTabLight(value)
                        }

                        match.tabTimer?.let { value ->
                            setTabTimer(value)
                        }

                        match.tabTemperature?.let { value ->
                            setTabTemperature(value)
                        }

                        match.productId?.let { value ->
                            setProductId(value)
                        }

                        match.productFamily?.let { value ->
                            setProductFamily(value)
                        }

                        match.productModel?.let { value ->
                            setProductModel(value)
                        }

                        match.hardwareRevision?.let { value ->
                            setHardwareRevision(value)
                        }

                        match.firmwareVersion?.let { value ->
                            setFirmwareVersion(value)
                        }

                        match.apiVersion?.let { value ->
                            setApiVersion(value)
                        }

                        match.channelCount?.let { value ->
                            setChannelCount(value)
                        }

                        match.sensorCount?.let { value ->
                            setSensorCount(value)
                        }

                        match.supportedFeatures?.let { values ->
                            clearSupportedFeatures()
                            addAllSupportedFeatures(values)
                        }

                        match.supportedScreens?.let { values ->
                            clearSupportedScreens()
                            addAllSupportedScreens(values)
                        }
                    }.build()
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
        val fallbackType = AquaDeviceCatalog.resolveTypeByLegacyIdentity(
            aquaName = aquaName,
            name = name
        )

        val parsedType = AquaDeviceType.fromStorageKey(
            value = deviceType
        )

        val resolvedType = if (parsedType != AquaDeviceType.UNKNOWN) {
            parsedType
        } else {
            fallbackType
        }

        return DeviceInfoUi(
            id = id,
            aquaName = aquaName,
            name = name,
            ip = ip,
            serial = serial,
            deviceUid = deviceUid,
            macAddress = macAddress,
            firmwareSerial = firmwareSerial,
            firmwareBuild = firmwareBuild,
            lastSeenMillis = lastSeenMillis,
            tankId = tankId.takeIf { value ->
                value > 0L
            },

            deviceType = resolvedType,

            udpVersion = udpVersion.takeIf { value ->
                value > 0
            },
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion.takeIf { value ->
                value > 0
            },

            channelCount = channelCount.takeIf { value ->
                value > 0
            },
            sensorCount = sensorCount.takeIf { value ->
                value > 0
            },

            supportedFeatures = supportedFeaturesList.toSet(),
            supportedScreens = supportedScreensList.toSet()
        )
    }

    private fun resolveDeviceType(
        explicitType: AquaDeviceType,
        aquaName: String,
        name: String,
        productId: String
    ): AquaDeviceType {
        if (explicitType != AquaDeviceType.UNKNOWN) {
            return explicitType
        }

        val byProductId = AquaDeviceCatalog.resolveTypeByProductId(
            productId = productId
        )

        if (byProductId != AquaDeviceType.UNKNOWN) {
            return byProductId
        }

        return AquaDeviceCatalog.resolveTypeByLegacyIdentity(
            aquaName = aquaName,
            name = name
        )
    }
}