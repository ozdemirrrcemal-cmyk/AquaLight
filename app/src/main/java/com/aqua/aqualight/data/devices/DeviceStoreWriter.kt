package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice

class DeviceStoreWriter(
    private val devicesStore: DevicesDataStoreManager
) {

    suspend fun saveDiscoveredDevice(
        device: DiscoveredAquaDevice
    ): Long {
        val alreadyExists = devicesStore.deviceExists(
            id = device.id
        )

        if (alreadyExists) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    device.toLastSeenUpdate()
                )
            )

            return device.id
        }

        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        ) ?: error("Unsupported device")

        val savedAquaName = definition.family.displayName
        val savedName = definition.displayName

        devicesStore.addDevice(
            id = device.id,
            aquaName = savedAquaName,
            name = savedName,
            ip = device.ip,
            serial = buildSerial(
                aquaName = savedAquaName,
                name = savedName,
                id = device.id
            ),
            firmwareBuild = device.firmwareBuild,

            deviceType = device.deviceType,

            udpVersion = device.udpVersion,
            tabLight = device.tabLight,
            tabTimer = device.tabTimer,
            tabTemperature = device.tabTemperature,

            productId = device.productId.orEmpty(),
            productFamily = device.productFamily.orEmpty(),
            productModel = device.productModel.orEmpty(),
            hardwareRevision = device.hardwareRevision.orEmpty(),
            firmwareVersion = device.firmwareVersion.orEmpty(),
            apiVersion = device.apiVersion,

            channelCount = device.channelCount,
            sensorCount = device.sensorCount,

            supportedFeatures = device.supportedFeatures,
            supportedScreens = device.supportedScreens
        )

        devicesStore.updateDevicesLastSeen(
            discovered = listOf(
                device.toLastSeenUpdate()
            )
        )

        return device.id
    }

    private fun DiscoveredAquaDevice.toLastSeenUpdate(): DevicesDataStoreManager.DeviceLastSeenUpdate {
        return DevicesDataStoreManager.DeviceLastSeenUpdate(
            id = id,
            ip = ip,
            firmwareBuild = firmwareBuild,

            deviceType = deviceType,

            udpVersion = udpVersion,
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion,

            channelCount = channelCount,
            sensorCount = sensorCount,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
    }

    private fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {
        val aquaInitial = aquaName.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val nameInitial = name.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        return "$aquaInitial$nameInitial-$id"
    }
}