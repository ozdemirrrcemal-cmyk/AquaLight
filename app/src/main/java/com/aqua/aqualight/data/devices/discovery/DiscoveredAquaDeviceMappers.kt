package com.aqua.aqualight.data.devices.discovery

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice

fun DiscoveredAquaDevice.toDeviceLastSeenUpdate(
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
