package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope

internal object KnownDevicesStoreReducer {

    fun upsertDevices(
        store: KnownDevicesStore,
        ownerUid: String,
        snapshots: Iterable<DeviceSnapshot>
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val records = store.devicesList
            .associateBy { record ->
                record.ownerUid to record.snapshot.identity.uid
            }
            .toMutableMap()

        snapshots.forEach { snapshot ->
            val normalizedDeviceUid = requireDeviceUid(snapshot.deviceUid)
            records[normalizedOwnerUid to normalizedDeviceUid] =
                OwnedKnownDeviceRecord.newBuilder()
                    .setOwnerUid(normalizedOwnerUid)
                    .setSnapshot(snapshot.toStoredSnapshot())
                    .build()
        }

        return store.toBuilder()
            .clearDevices()
            .addAllDevices(records.values.sortedWith(DEVICE_RECORD_ORDER))
            .build()
            .validated()
    }

    fun removeDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val normalizedDeviceUid = requireDeviceUid(deviceUid)

        return store.toBuilder()
            .clearDevices()
            .addAllDevices(
                store.devicesList.filterNot { record ->
                    record.ownerUid == normalizedOwnerUid &&
                        record.snapshot.identity.uid == normalizedDeviceUid
                }
            )
            .build()
            .validated()
    }

    fun ignoreDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val normalizedDeviceUid = requireDeviceUid(deviceUid)
        val records = store.ignoredDevicesList
            .associateBy { record -> record.ownerUid to record.deviceUid }
            .toMutableMap()

        records[normalizedOwnerUid to normalizedDeviceUid] =
            IgnoredKnownDeviceRecord.newBuilder()
                .setOwnerUid(normalizedOwnerUid)
                .setDeviceUid(normalizedDeviceUid)
                .build()

        return store.toBuilder()
            .clearIgnoredDevices()
            .addAllIgnoredDevices(records.values.sortedWith(IGNORED_RECORD_ORDER))
            .build()
            .validated()
    }

    fun allowDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val normalizedDeviceUid = requireDeviceUid(deviceUid)

        return store.toBuilder()
            .clearIgnoredDevices()
            .addAllIgnoredDevices(
                store.ignoredDevicesList.filterNot { record ->
                    record.ownerUid == normalizedOwnerUid &&
                        record.deviceUid == normalizedDeviceUid
                }
            )
            .build()
            .validated()
    }

    fun clearOwnerDevices(
        store: KnownDevicesStore,
        ownerUid: String
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        return store.toBuilder()
            .clearDevices()
            .addAllDevices(
                store.devicesList.filterNot { record ->
                    record.ownerUid == normalizedOwnerUid
                }
            )
            .build()
            .validated()
    }

    fun clearOwnerIgnoredDevices(
        store: KnownDevicesStore,
        ownerUid: String
    ): KnownDevicesStore {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        return store.toBuilder()
            .clearIgnoredDevices()
            .addAllIgnoredDevices(
                store.ignoredDevicesList.filterNot { record ->
                    record.ownerUid == normalizedOwnerUid
                }
            )
            .build()
            .validated()
    }

    fun devicesForOwner(
        store: KnownDevicesStore,
        ownerUid: String
    ): List<DeviceSnapshot> {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        return store.devicesList
            .asSequence()
            .filter { record -> record.ownerUid == normalizedOwnerUid }
            .map { record -> record.snapshot.toDomainSnapshot() }
            .sortedWith(SNAPSHOT_ORDER)
            .toList()
    }

    fun ignoredDeviceUidsForOwner(
        store: KnownDevicesStore,
        ownerUid: String
    ): Set<String> {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        return store.ignoredDevicesList
            .asSequence()
            .filter { record -> record.ownerUid == normalizedOwnerUid }
            .map { record -> record.deviceUid }
            .toSet()
    }

    private fun DeviceSnapshot.toStoredSnapshot(): StoredKnownDeviceSnapshot {
        return StoredKnownDeviceSnapshot.newBuilder()
            .setIdentity(
                StoredDeviceIdentity.newBuilder()
                    .setUid(requireDeviceUid(deviceUid))
                    .setShortId(identity.shortId)
                    .setChipId(identity.chipId)
                    .setEspChipId(identity.espChipId)
                    .setEfuseMac(identity.efuseMac)
                    .setMacAddress(identity.macAddress)
                    .setSerialNumber(identity.serialNumber)
                    .setFirmwareSerial(identity.firmwareSerial)
                    .setDisplayName(identity.displayName)
                    .setCustomName(identity.customName)
                    .setSetupCode(identity.setupCode)
                    .setSetupSsid(identity.setupSsid)
            )
            .setProduct(
                StoredDeviceProduct.newBuilder()
                    .setBrand(product.brand)
                    .setProductId(product.productId)
                    .setProductKey(product.productKey)
                    .setFamily(product.family.wireValue)
                    .setFamilyRaw(product.familyRaw)
                    .setLine(product.line)
                    .setModel(product.model)
                    .setDisplayName(product.displayName)
                    .setSkuId(product.skuId)
                    .setSkuCode(product.skuCode)
                    .setSetupCode(product.setupCode)
                    .setHardwareRevision(product.hardwareRevision)
            )
            .setFirmwareVersion(firmwareVersion)
            .setFirmwareBuild(firmwareBuild)
            .setApiVersion(apiVersion)
            .setProtocolVersion(protocolVersion)
            .setEndpoint(
                StoredDeviceRuntimeEndpoint.newBuilder()
                    .setIp(endpoint.ip)
                    .setWifiMode(endpoint.wifiMode)
                    .setWifiConnected(endpoint.wifiConnected)
                    .setSetupApActive(endpoint.setupApActive)
                    .setRuntimeTransport(endpoint.runtimeTransport)
                    .setWsPort(endpoint.wsPort)
                    .setWsPath(endpoint.wsPath)
                    .setWsProtocol(endpoint.wsProtocol)
                    .setWsProtocolVersion(endpoint.wsProtocolVersion)
                    .setDiscoveryPort(endpoint.discoveryPort)
            )
            .setCapabilities(
                StoredDeviceCapabilities.newBuilder()
                    .setLight(capabilities.light)
                    .setManualLight(capabilities.manualLight)
                    .setLightProgram(capabilities.lightProgram)
                    .setLightPresets(capabilities.lightPresets)
                    .setLightSimulation(capabilities.lightSimulation)
                    .setFan(capabilities.fan)
                    .setCooling(capabilities.cooling)
                    .setTemperature(capabilities.temperature)
                    .setStandaloneTimer(capabilities.standaloneTimer)
                    .setDosing(capabilities.dosing)
                    .setTimeSync(capabilities.timeSync)
                    .setOta(capabilities.ota)
            )
            .setLimits(
                StoredDeviceLimits.newBuilder()
                    .setLightChannelCount(limits.lightChannelCount)
                    .setFanOutputCount(limits.fanOutputCount)
                    .setTemperatureSensorCount(limits.temperatureSensorCount)
                    .setTimerChannelCount(limits.timerChannelCount)
                    .setDosingChannelCount(limits.dosingChannelCount)
            )
            .addAllSupportedFeatures(supportedFeatures)
            .addAllSupportedScreens(supportedScreens)
            .addAllModules(modules)
            .setLastSeenAtMillis(lastSeenAtMillis)
            .build()
    }

    private fun StoredKnownDeviceSnapshot.toDomainSnapshot(): DeviceSnapshot {
        val familyWireValue = product.familyRaw.ifBlank { product.family }

        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(identity.uid),
                shortId = identity.shortId,
                chipId = identity.chipId,
                espChipId = identity.espChipId,
                efuseMac = identity.efuseMac,
                macAddress = identity.macAddress,
                serialNumber = identity.serialNumber,
                firmwareSerial = identity.firmwareSerial,
                displayName = identity.displayName,
                customName = identity.customName,
                setupCode = identity.setupCode,
                setupSsid = identity.setupSsid
            ),
            product = DeviceProduct(
                brand = product.brand,
                productId = product.productId,
                productKey = product.productKey,
                family = DeviceFamily.fromWire(familyWireValue),
                familyRaw = familyWireValue,
                line = product.line,
                model = product.model,
                displayName = product.displayName,
                skuId = product.skuId,
                skuCode = product.skuCode,
                setupCode = product.setupCode,
                hardwareRevision = product.hardwareRevision
            ),
            firmwareVersion = firmwareVersion,
            firmwareBuild = firmwareBuild,
            apiVersion = apiVersion,
            protocolVersion = protocolVersion,
            endpoint = DeviceRuntimeEndpoint(
                ip = endpoint.ip,
                wifiMode = endpoint.wifiMode,
                wifiConnected = endpoint.wifiConnected,
                setupApActive = endpoint.setupApActive,
                runtimeTransport = endpoint.runtimeTransport,
                wsPort = endpoint.wsPort,
                wsPath = endpoint.wsPath.ifBlank { AqlWsContract.DEFAULT_PATH },
                wsProtocol = endpoint.wsProtocol.ifBlank { AqlWsContract.DEFAULT_PROTOCOL },
                wsProtocolVersion = endpoint.wsProtocolVersion,
                discoveryPort = endpoint.discoveryPort
            ),
            capabilities = DeviceCapabilities(
                light = capabilities.light,
                manualLight = capabilities.manualLight,
                lightProgram = capabilities.lightProgram,
                lightPresets = capabilities.lightPresets,
                lightSimulation = capabilities.lightSimulation,
                fan = capabilities.fan,
                cooling = capabilities.cooling,
                temperature = capabilities.temperature,
                standaloneTimer = capabilities.standaloneTimer,
                dosing = capabilities.dosing,
                timeSync = capabilities.timeSync,
                ota = capabilities.ota
            ),
            limits = DeviceLimits(
                lightChannelCount = limits.lightChannelCount,
                fanOutputCount = limits.fanOutputCount,
                temperatureSensorCount = limits.temperatureSensorCount,
                timerChannelCount = limits.timerChannelCount,
                dosingChannelCount = limits.dosingChannelCount
            ),
            supportedFeatures = supportedFeaturesList,
            supportedScreens = supportedScreensList,
            modules = modulesList,
            connectionState = DeviceConnectionState(
                lastErrorMessage = null
            ),
            lastSeenAtMillis = lastSeenAtMillis
        )
    }

    private fun requireOwnerUid(
        ownerUid: String
    ): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "Known-device storage requires an authenticated owner."
            }
        }
    }

    private fun requireDeviceUid(
        deviceUid: DeviceUid
    ): String {
        return deviceUid.value.trim().also { normalized ->
            require(normalized.isNotBlank()) {
                "Known-device UID must not be blank."
            }
        }
    }

    private fun KnownDevicesStore.validated(): KnownDevicesStore {
        KnownDevicesStoreValidator.validate(this)
        return this
    }

    private val DEVICE_RECORD_ORDER =
        compareBy<OwnedKnownDeviceRecord> { record -> record.ownerUid }
            .thenBy { record -> record.snapshot.identity.uid }

    private val IGNORED_RECORD_ORDER =
        compareBy<IgnoredKnownDeviceRecord> { record -> record.ownerUid }
            .thenBy { record -> record.deviceUid }

    private val SNAPSHOT_ORDER =
        compareBy<DeviceSnapshot> { snapshot -> snapshot.title.lowercase() }
            .thenBy { snapshot -> snapshot.deviceUid.value }
}
