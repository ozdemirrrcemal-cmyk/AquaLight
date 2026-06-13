package com.aqua.aqualight.data.devices.discovery.model

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey

/**
 * Device returned by LAN discovery.
 *
 * Commercial discovery requires ProductId. AquaName/Name are retained only as
 * display/fallback fields while firmware is being moved to the new contract.
 */
data class DiscoveredAquaDevice(
    val id: Long,
    val ip: String,

    val productId: String,
    val productKey: AquaProductKey,
    val category: AquaDeviceCategory,
    val setupCode: String,

    val productFamily: String,
    val productLine: String,
    val productModel: String,
    val displayName: String,
    val skuId: String? = null,
    val skuCode: String? = null,

    val deviceUid: String? = null,
    val macAddress: String? = null,
    val serialNumber: String? = null,
    val shortId: String? = null,
    val firmwareSerial: String? = null,

    val hardwareRevision: String? = null,
    val firmwareVersion: String? = null,
    val protocolVersion: Int? = null,

    /** Existing firmware/build field. */
    val firmwareBuild: String,

    val udpVersion: Int?,

    /** Legacy ESP32 tab fields. Not used as commercial routing source. */
    val tabLight: Boolean,
    val tabTimer: Boolean,
    val tabTemperature: Boolean,

    val supportedFeatures: Set<String> = emptySet(),
    val supportedScreens: Set<String> = emptySet(),

    val channelCount: Int? = null,
    val sensorCount: Int? = null,

    /** Firmware display fields. They must not drive routing. */
    val aquaName: String = productFamily,
    val name: String = productModel
) {

    val isSupported: Boolean
        get() = productKey != AquaProductKey.UNKNOWN &&
            category != AquaDeviceCategory.UNKNOWN
}
