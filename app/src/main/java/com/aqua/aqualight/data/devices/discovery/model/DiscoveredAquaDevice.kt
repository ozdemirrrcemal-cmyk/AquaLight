package com.aqua.aqualight.data.devices.discovery.model

import com.aqua.aqualight.data.devices.catalog.AquaDeviceType

data class DiscoveredAquaDevice(
    val id: Long,
    val ip: String,

    /**
     * Mevcut ESP32 firmware kimliği.
     *
     * Şimdiki firmware:
     * AquaName
     * Name
     */
    val aquaName: String,
    val name: String,

    /**
     * Ticari cihaz kimliği.
     * IP değişebilir; bu alanlar cihazın kalıcı kimliğini taşır.
     */
    val deviceUid: String? = null,
    val macAddress: String? = null,
    val firmwareSerial: String? = null,

    /**
     * ESP32 firmware tarafındaki profesyonel ürün/capability alanları.
     */
    val productId: String? = null,
    val productFamily: String? = null,
    val productModel: String? = null,
    val hardwareRevision: String? = null,
    val firmwareVersion: String? = null,
    val apiVersion: Int? = null,

    /**
     * Mevcut firmware alanı.
     */
    val firmwareBuild: String,

    val udpVersion: Int?,

    /**
     * Mevcut ESP32 UDP response içindeki eski tab bilgileri.
     * Bunları ekran açma ana kaynağı yapmayacağız.
     * Katalog ana kaynaktır.
     */
    val tabLight: Boolean,
    val tabTimer: Boolean,
    val tabTemperature: Boolean,

    /**
     * İleride firmware tarafında gelecek profesyonel capability alanları.
     */
    val supportedFeatures: Set<String> = emptySet(),
    val supportedScreens: Set<String> = emptySet(),

    val channelCount: Int? = null,
    val sensorCount: Int? = null,

    /**
     * Android katalog çözüm sonucu.
     *
     * UNKNOWN ise cihaz desteklenmiyor demektir.
     */
    val deviceType: AquaDeviceType
) {

    val isSupported: Boolean
        get() = deviceType != AquaDeviceType.UNKNOWN
}