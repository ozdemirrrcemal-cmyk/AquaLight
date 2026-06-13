package com.aqua.aqualight.data.devices.catalog

/**
 * Firmware/app ticari kimlik sözleşmesinin uygulama modeli.
 */
data class AquaDeviceIdentity(
    val productId: String,
    val deviceCategory: AquaDeviceCategory,
    val productFamily: String,
    val productModel: String,
    val displayName: String,

    val deviceUid: String,
    val shortId: String,
    val serialNumber: String? = null,

    val protocolVersion: Int,
    val firmwareVersion: String? = null,
    val hardwareRevision: String? = null,
    val macAddress: String? = null,
    val ip: String? = null,

    val setupMode: Boolean = false,
    val apEnabled: Boolean = false
)
