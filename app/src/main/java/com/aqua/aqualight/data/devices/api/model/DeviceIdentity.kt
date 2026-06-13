package com.aqua.aqualight.data.devices.api.model

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey

data class DeviceIdentity(
    val deviceId: Long,
    val deviceUid: String = "",
    val macAddress: String = "",
    val serialNumber: String = "",
    val shortId: String = "",
    val productId: String = "",
    val productKey: AquaProductKey = AquaProductKey.UNKNOWN,
    val category: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN,
    val productFamily: String = "",
    val productLine: String = "",
    val productModel: String = "",
    val displayName: String = "",
    val customName: String = "",
    val skuId: String = "",
    val skuCode: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val firmwareBuild: String = "",
    val apiVersion: Int? = null,
    val protocolVersion: Int? = null,
    val supportedFeatures: Set<String> = emptySet(),
    val supportedScreens: Set<String> = emptySet()
) {
    val resolvedTitle: String
        get() = customName.ifBlank {
            displayName.ifBlank {
                productModel.ifBlank {
                    productId.ifBlank {
                        "Device"
                    }
                }
            }
        }
}
