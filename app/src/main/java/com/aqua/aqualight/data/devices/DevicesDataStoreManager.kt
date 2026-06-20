package com.aqua.aqualight.data.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.user.UserDataScope
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

    data class DeviceInfo(
        val id: Long,
        val ownerUid: String = "",

        val deviceUid: String = "",
        val macAddress: String = "",
        val serialNumber: String = "",
        val shortId: String = "",
        val firmwareSerial: String = "",

        val productId: String = "",
        val productKey: AquaProductKey = AquaProductKey.UNKNOWN,
        val category: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN,
        val setupCode: String = "",

        val productFamily: String = "",
        val productLine: String = "",
        val productModel: String = "",
        val displayName: String = "",
        val customName: String = "",
        val skuId: String = "",
        val skuCode: String = "",

        val aquaName: String,
        val name: String,
        val ip: String,
        val serial: String,
        val firmwareBuild: String,
        val lastSeenMillis: Long,
        val tankId: Long? = null,


        val udpVersion: Int? = null,
        val tabLight: Boolean = false,
        val tabTimer: Boolean = false,
        val tabTemperature: Boolean = false,

        val hardwareRevision: String = "",
        val firmwareVersion: String = "",
        val protocolVersion: Int? = null,
        val apiVersion: Int? = protocolVersion,

        val channelCount: Int? = null,
        val sensorCount: Int? = null,

        val supportedFeatures: Set<String> = emptySet(),
        val supportedScreens: Set<String> = emptySet(),

        val deviceApiToken: String = ""
    ) {
        val resolvedTitle: String
            get() = customName.ifBlank {
                displayName.ifBlank {
                    name.ifBlank {
                        productModel.ifBlank {
                            "Device"
                        }
                    }
                }
            }
    }

    data class DeviceLastSeenUpdate(
        val id: Long,
        val ip: String,
        val firmwareBuild: String = "",
        val deviceUid: String? = null,
        val macAddress: String? = null,
        val serialNumber: String? = null,
        val shortId: String? = null,
        val firmwareSerial: String? = null,

        val productId: String? = null,
        val productKey: AquaProductKey? = null,
        val category: AquaDeviceCategory? = null,
        val setupCode: String? = null,

        val productFamily: String? = null,
        val productLine: String? = null,
        val productModel: String? = null,
        val displayName: String? = null,
        val skuId: String? = null,
        val skuCode: String? = null,


        val udpVersion: Int? = null,
        val tabLight: Boolean? = null,
        val tabTimer: Boolean? = null,
        val tabTemperature: Boolean? = null,

        val hardwareRevision: String? = null,
        val firmwareVersion: String? = null,
        val protocolVersion: Int? = null,
        val apiVersion: Int? = protocolVersion,

        val channelCount: Int? = null,
        val sensorCount: Int? = null,

        val supportedFeatures: Set<String>? = null,
        val supportedScreens: Set<String>? = null,

        val deviceApiToken: String? = null
    )

    private val devicesPrefsFlow: Flow<DevicesPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(DevicesPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    val devicesFlow: Flow<List<DeviceInfo>> = devicesPrefsFlow.map { prefs ->
        prefs.devicesList
            .filter { device ->
                device.belongsToCurrentUser()
            }
            .map { device ->
                device.toDeviceInfo()
            }
    }

    val unassignedDevicesFlow: Flow<List<DeviceInfo>> = devicesFlow.map { devices ->
        devices.filter { device ->
            device.tankId == null
        }
    }

    fun devicesForTankFlow(
        tankId: Long
    ): Flow<List<DeviceInfo>> {
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
                device.id == id && device.belongsToCurrentUser()
            }
    }

    suspend fun findStoredDeviceIdForIdentity(
        id: Long,
        deviceUid: String?,
        macAddress: String?,
        firmwareSerial: String?,
        serialNumber: String? = null,
        shortId: String? = null,
        productId: String? = null
    ): Long? {
        return devicesPrefsFlow.first()
            .devicesList
            .filter { device ->
                device.belongsToCurrentUser()
            }
            .map { device ->
                device.toDeviceInfo()
            }
            .firstOrNull { savedDevice ->
                DeviceIdentityMatcher.matchesStoredIdentity(
                    savedDevice = savedDevice,
                    id = id,
                    deviceUid = deviceUid,
                    macAddress = macAddress,
                    firmwareSerial = firmwareSerial,
                    serialNumber = serialNumber,
                    shortId = shortId,
                    productId = productId
                )
            }
            ?.id
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

        productId: String = "",
        productKey: AquaProductKey = AquaProductKey.UNKNOWN,
        category: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN,
        setupCode: String = "",

        productFamily: String = "",
        productLine: String = "",
        productModel: String = "",
        displayName: String = "",
        customName: String = "",
        skuId: String = "",
        skuCode: String = "",
        serialNumber: String = "",
        shortId: String = "",

        udpVersion: Int? = null,
        tabLight: Boolean = false,
        tabTimer: Boolean = false,
        tabTemperature: Boolean = false,

        hardwareRevision: String = "",
        firmwareVersion: String = "",
        protocolVersion: Int? = null,
        apiVersion: Int? = protocolVersion,

        channelCount: Int? = null,
        sensorCount: Int? = null,

        supportedFeatures: Set<String> = emptySet(),
        supportedScreens: Set<String> = emptySet(),
        deviceApiToken: String = ""
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()

        dataStore.updateData { prefs ->
            if (
                prefs.devicesList.any { device ->
                    device.belongsToOwner(ownerUid) &&
                        DeviceIdentityMatcher.matchesStoredIdentity(
                            savedDevice = device.toDeviceInfo(),
                            id = id,
                            deviceUid = deviceUid,
                            macAddress = macAddress,
                            firmwareSerial = firmwareSerial,
                            serialNumber = serialNumber,
                            shortId = shortId,
                            productId = productId
                        )
                }
            ) {
                return@updateData prefs
            }

            val now = System.currentTimeMillis()

            val definition = resolveDefinition(
                productId = productId,
                productKey = productKey,
                category = category
            )

            val resolvedProductKey = definition?.productKey
                ?: productKey.takeIf { key -> key != AquaProductKey.UNKNOWN }
                ?: AquaProductKey.fromProductId(productId)

            val resolvedCategory = definition?.category
                ?: category.takeIf { value -> value != AquaDeviceCategory.UNKNOWN }
                ?: resolvedProductKey.category

            val resolvedProductId = definition?.productId
                ?: productId.ifBlank {
                    resolvedProductKey.productId.takeUnless { value ->
                        resolvedProductKey == AquaProductKey.UNKNOWN || value == AquaProductKey.UNKNOWN.productId
                    }.orEmpty()
                }

            val resolvedSetupCode = setupCode.ifBlank {
                definition?.setupCode ?: resolvedProductKey.setupCode.takeUnless { value ->
                    resolvedProductKey == AquaProductKey.UNKNOWN || value == AquaProductKey.UNKNOWN.setupCode
                }.orEmpty()
            }

            val resolvedProductFamily = productFamily.ifBlank {
                definition?.productFamily ?: aquaName
            }

            val resolvedProductLine = productLine.ifBlank {
                definition?.productLine.orEmpty()
            }

            val resolvedProductModel = productModel.ifBlank {
                definition?.productModel ?: name
            }

            val resolvedDisplayName = displayName.ifBlank {
                definition?.displayName ?: name.ifBlank {
                    resolvedProductModel
                }
            }

            val device = SavedDeviceInfo.newBuilder()
                .setId(id)
                .setOwnerUid(ownerUid)
                .setAquaName(resolvedProductFamily)
                .setName(resolvedDisplayName)
                .setIp(ip)
                .setSerial(serial)
                .setFirmwareBuild(firmwareBuild)
                .setDeviceUid(deviceUid)
                .setMacAddress(macAddress)
                .setFirmwareSerial(firmwareSerial)
                .setLastSeenMillis(now)
                .setTankId(0L)
                .setProductId(resolvedProductId)
                .setProductKey(resolvedProductKey.storageKey)
                .setCategory(resolvedCategory.storageKey)
                .setSetupCode(resolvedSetupCode)
                .setProductFamily(resolvedProductFamily)
                .setProductLine(resolvedProductLine)
                .setProductModel(resolvedProductModel)
                .setDisplayName(resolvedDisplayName)
                .setCustomName(customName)
                .setSkuId(skuId)
                .setSkuCode(skuCode)
                .setSerialNumber(serialNumber)
                .setShortId(shortId)
                .setHardwareRevision(hardwareRevision)
                .setFirmwareVersion(firmwareVersion)
                .setTabLight(tabLight)
                .setTabTimer(tabTimer)
                .setTabTemperature(tabTemperature)
                .addAllSupportedFeatures(supportedFeatures)
                .addAllSupportedScreens(supportedScreens)
                .setDeviceApiToken(deviceApiToken)
                .apply {
                    udpVersion?.let { value ->
                        setUdpVersion(value)
                    }

                    protocolVersion?.let { value ->
                        setProtocolVersion(value)
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

    suspend fun updateDeviceApiToken(
        id: Long,
        deviceApiToken: String
    ) {
        val token = deviceApiToken.trim()
        if (id <= 0L || token.isBlank()) {
            return
        }

        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.id == id && device.belongsToCurrentUser()) {
                    device.toBuilder()
                        .setDeviceApiToken(token)
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
                if (device.id != id || !device.belongsToCurrentUser()) {
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
                if (device.id == deviceId && device.belongsToCurrentUser()) {
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
                if (device.id == deviceId && device.belongsToCurrentUser()) {
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
                if (device.tankId == tankId && device.belongsToCurrentUser()) {
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
            val filteredDevices = prefs.devicesList.filterNot { device ->
                device.id in ids && device.belongsToCurrentUser()
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(filteredDevices)
                .build()
        }
    }

    suspend fun clearAllDevices(
        ownerUid: String? = null
    ) {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

        dataStore.updateData { prefs ->
            val remainingDevices = prefs.devicesList.filterNot { device ->
                device.belongsToOwner(targetOwnerUid)
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(remainingDevices)
                .build()
        }
    }

    suspend fun assignLegacyDevicesToOwner(
        ownerUid: String
    ) {
        val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (targetOwnerUid.isBlank()) {
            return
        }

        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (device.ownerUid.isBlank()) {
                    device.toBuilder()
                        .setOwnerUid(targetOwnerUid)
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

    suspend fun updateDevicesLastSeen(
        discovered: List<DeviceLastSeenUpdate>
    ) {
        if (discovered.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()

        dataStore.updateData { prefs ->
            val updatedDevices = prefs.devicesList.map { device ->
                if (!device.belongsToCurrentUser()) {
                    return@map device
                }

                val savedDevice = device.toDeviceInfo()

                val match = discovered.firstOrNull { discoveredDevice ->
                    DeviceIdentityMatcher.samePhysicalDevice(
                        savedDevice = savedDevice,
                        update = discoveredDevice
                    )
                }

                if (match != null) {
                    device.toBuilder().apply {
                        setIp(match.ip)
                        setLastSeenMillis(now)

                        if (match.firmwareBuild.isNotBlank()) {
                            setFirmwareBuild(match.firmwareBuild)
                        }

                        match.deviceUid?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setDeviceUid(value)
                        }

                        match.macAddress?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setMacAddress(value)
                        }

                        match.serialNumber?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setSerialNumber(value)
                        }

                        match.shortId?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setShortId(value)
                        }

                        match.firmwareSerial?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setFirmwareSerial(value)
                        }

                        match.productId?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setProductId(value)
                        }

                        match.productKey?.takeIf { value -> value != AquaProductKey.UNKNOWN }?.let { value ->
                            setProductKey(value.storageKey)
                        }

                        match.category?.takeIf { value -> value != AquaDeviceCategory.UNKNOWN }?.let { value ->
                            setCategory(value.storageKey)
                        }

                        match.setupCode?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setSetupCode(value)
                        }

                        match.productFamily?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setProductFamily(value)
                            setAquaName(value)
                        }

                        match.productLine?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setProductLine(value)
                        }

                        match.productModel?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setProductModel(value)
                        }

                        match.displayName?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setDisplayName(value)
                            setName(value)
                        }

                        match.skuId?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setSkuId(value)
                        }

                        match.skuCode?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setSkuCode(value)
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

                        match.hardwareRevision?.let { value ->
                            setHardwareRevision(value)
                        }

                        match.firmwareVersion?.let { value ->
                            setFirmwareVersion(value)
                        }

                        match.protocolVersion?.let { value ->
                            setProtocolVersion(value)
                            setApiVersion(value)
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

                        match.deviceApiToken?.takeIf { value -> value.isNotBlank() }?.let { value ->
                            setDeviceApiToken(value)
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

    private fun SavedDeviceInfo.toDeviceInfo(): DeviceInfo {
        val parsedProductKey = AquaProductKey.fromStorageKey(
            value = productKey
        ).takeIf { key ->
            key != AquaProductKey.UNKNOWN
        } ?: AquaProductKey.fromProductId(
            value = productId
        )

        val parsedCategory = AquaDeviceCategory.fromStorageKey(
            value = category
        )


        val definition = resolveDefinition(
            productId = productId,
            productKey = parsedProductKey,
            category = parsedCategory
        )

        val resolvedProductKey = definition?.productKey
            ?: parsedProductKey

        val resolvedCategory = definition?.category
            ?: parsedCategory.takeIf { value -> value != AquaDeviceCategory.UNKNOWN }
            ?: resolvedProductKey.category


        val resolvedProductId = productId.ifBlank {
            definition?.productId ?: resolvedProductKey.productId.takeUnless { value ->
                resolvedProductKey == AquaProductKey.UNKNOWN || value == AquaProductKey.UNKNOWN.productId
            }.orEmpty()
        }

        val resolvedSetupCode = setupCode.ifBlank {
            definition?.setupCode ?: resolvedProductKey.setupCode.takeUnless { value ->
                resolvedProductKey == AquaProductKey.UNKNOWN || value == AquaProductKey.UNKNOWN.setupCode
            }.orEmpty()
        }

        val resolvedProductFamily = productFamily.ifBlank {
            definition?.productFamily ?: aquaName
        }

        val resolvedProductLine = productLine.ifBlank {
            definition?.productLine.orEmpty()
        }

        val resolvedProductModel = productModel.ifBlank {
            definition?.productModel ?: name
        }

        val resolvedDisplayName = displayName.ifBlank {
            definition?.displayName ?: name.ifBlank {
                resolvedProductModel
            }
        }

        val resolvedProtocolVersion = protocolVersion.takeIf { value -> value > 0 }
            ?: apiVersion.takeIf { value -> value > 0 }

        return DeviceInfo(
            id = id,
            ownerUid = ownerUid,

            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            shortId = shortId,
            firmwareSerial = firmwareSerial,

            productId = resolvedProductId,
            productKey = resolvedProductKey,
            category = resolvedCategory,
            setupCode = resolvedSetupCode,

            productFamily = resolvedProductFamily,
            productLine = resolvedProductLine,
            productModel = resolvedProductModel,
            displayName = resolvedDisplayName,
            customName = customName,
            skuId = skuId,
            skuCode = skuCode,

            aquaName = resolvedProductFamily,
            name = resolvedDisplayName,
            ip = ip,
            serial = serial,
            firmwareBuild = firmwareBuild,
            lastSeenMillis = lastSeenMillis,
            tankId = tankId.takeIf { value ->
                value > 0L
            },

            udpVersion = udpVersion.takeIf { value ->
                value > 0
            },
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            protocolVersion = resolvedProtocolVersion,
            apiVersion = apiVersion.takeIf { value -> value > 0 },

            channelCount = channelCount.takeIf { value ->
                value > 0
            },
            sensorCount = sensorCount.takeIf { value ->
                value > 0
            },

            supportedFeatures = supportedFeaturesList.toSet(),
            supportedScreens = supportedScreensList.toSet(),
            deviceApiToken = deviceApiToken
        )
    }

    private fun String?.orCurrentOwnerUidOrReturn(): String {
        val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

        if (explicitOwnerUid.isNotBlank()) {
            return explicitOwnerUid
        }

        return UserDataScope.currentUid()
    }

    private fun SavedDeviceInfo.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun SavedDeviceInfo.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
    }

    private fun resolveDefinition(
        productId: String,
        productKey: AquaProductKey,
        category: AquaDeviceCategory
    ): AquaDeviceDefinition? {
        return AquaDeviceCatalog.findDefinition(
            productId = productId,
            productKey = productKey,
            category = category
        )
    }
}
