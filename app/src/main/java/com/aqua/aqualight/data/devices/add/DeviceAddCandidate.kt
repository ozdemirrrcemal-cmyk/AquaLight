package com.aqua.aqualight.data.devices.add

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice

data class DeviceAddCandidate(
    val key: String,
    val source: DeviceAddSource,

    val displayName: String,
    val familyName: String,

    val productId: String,
    val productKey: AquaProductKey,
    val category: AquaDeviceCategory,
    val setupCode: String,


    val stateText: String,
    val actionText: String,

    val setupSsid: String? = null,
    val setupShortId: String? = null,

    val localDevice: DiscoveredAquaDevice? = null
) {
    val isSetupCandidate: Boolean
        get() = source == DeviceAddSource.SETUP_AP

    val isLocalNetworkCandidate: Boolean
        get() = source == DeviceAddSource.LOCAL_NETWORK
}
