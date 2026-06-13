package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice

class DeviceStoreWriter(
    private val devicesStore: DevicesDataStoreManager
) {

    suspend fun saveDiscoveredDevice(
        device: DiscoveredAquaDevice
    ): Long {
        val existingDeviceId = devicesStore.findStoredDeviceIdForIdentity(
            id = device.id,
            deviceUid = device.deviceUid,
            macAddress = device.macAddress,
            firmwareSerial = device.firmwareSerial,
            serialNumber = device.serialNumber,
            shortId = device.shortId,
            productId = device.productId
        )

        if (existingDeviceId != null) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    device.toLastSeenUpdate(
                        storedDeviceId = existingDeviceId
                    )
                )
            )

            return existingDeviceId
        }

        val definition = AquaDeviceCatalog.findByProductId(
            productId = device.productId
        ) ?: error("Unsupported device productId=${device.productId}")

        val savedDisplayName = device.displayName.ifBlank {
            definition.displayName
        }

        val savedProductFamily = device.productFamily.ifBlank {
            definition.productFamily
        }

        val savedProductLine = device.productLine.ifBlank {
            definition.productLine
        }

        val savedProductModel = device.productModel.ifBlank {
            definition.productModel
        }

        val defaultVariant = definition.variants.firstOrNull()
        val savedSkuId = device.skuId.orEmpty().ifBlank {
            defaultVariant?.skuId.orEmpty()
        }
        val savedSkuCode = device.skuCode.orEmpty().ifBlank {
            defaultVariant?.skuCode.orEmpty()
        }

        val identifier = DeviceSerialFormatter.buildCommercialIdentifier(
            setupCode = definition.setupCode,
            serialNumber = device.serialNumber,
            shortId = device.shortId,
            deviceUid = device.deviceUid,
            macAddress = device.macAddress,
            firmwareSerial = device.firmwareSerial,
            fallbackNumericId = device.id
        )

        devicesStore.addDevice(
            id = device.id,
            aquaName = savedProductFamily,
            name = savedDisplayName,
            ip = device.ip,
            serial = identifier,
            firmwareBuild = device.firmwareBuild,
            deviceUid = device.deviceUid.orEmpty(),
            macAddress = device.macAddress.orEmpty(),
            firmwareSerial = device.firmwareSerial.orEmpty(),

            productId = definition.productId,
            productKey = definition.productKey,
            category = definition.category,
            setupCode = definition.setupCode,

            productFamily = savedProductFamily,
            productLine = savedProductLine,
            productModel = savedProductModel,
            displayName = savedDisplayName,
            skuId = savedSkuId,
            skuCode = savedSkuCode,
            serialNumber = device.serialNumber.orEmpty(),
            shortId = device.shortId.orEmpty(),

            udpVersion = device.udpVersion,
            tabLight = device.tabLight,
            tabTimer = device.tabTimer,
            tabTemperature = device.tabTemperature,

            hardwareRevision = device.hardwareRevision.orEmpty(),
            firmwareVersion = device.firmwareVersion.orEmpty(),
            protocolVersion = device.protocolVersion,
            apiVersion = device.protocolVersion,

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

    private fun DiscoveredAquaDevice.toLastSeenUpdate(
        storedDeviceId: Long = id
    ): DevicesDataStoreManager.DeviceLastSeenUpdate {
        return DevicesDataStoreManager.DeviceLastSeenUpdate(
            id = storedDeviceId,
            ip = ip,
            firmwareBuild = firmwareBuild,
            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            shortId = shortId,
            firmwareSerial = firmwareSerial,

            productId = productId,
            productKey = productKey,
            category = category,
            setupCode = setupCode,

            productFamily = productFamily,
            productLine = productLine,
            productModel = productModel,
            displayName = displayName,
            skuId = skuId,
            skuCode = skuCode,

            udpVersion = udpVersion,
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            protocolVersion = protocolVersion,
            apiVersion = protocolVersion,

            channelCount = channelCount,
            sensorCount = sensorCount,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
    }
}
