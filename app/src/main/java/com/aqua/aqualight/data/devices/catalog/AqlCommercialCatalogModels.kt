package com.aqua.aqualight.data.devices.catalog

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceCompatibilityIdentity
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceHardwareRevision
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceProductId
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceProductLine
import com.aqua.aqualight.data.devices.model.DeviceProductModel
import com.aqua.aqualight.data.devices.model.DeviceSkuCode
import com.aqua.aqualight.data.devices.model.DeviceSkuId

internal data class AqlCommercialCatalogProfile(
    val capabilities: DeviceCapabilitySet,
    val supportedFeatures: Set<AqlDeviceFeatureKey>,
    val supportedScreens: Set<AqlDeviceScreenKey>,
    val expectedMenuFeatureNames: Set<String>
)

internal data class AqlCommercialCatalogProduct(
    val productKey: DeviceProductKey,
    val productId: DeviceProductId,
    val family: DeviceFamily,
    val line: DeviceProductLine,
    val model: DeviceProductModel,
    val displayName: String,
    val skuId: DeviceSkuId,
    val skuCode: DeviceSkuCode,
    val hardwareRevision: DeviceHardwareRevision,
    val limits: DeviceLimitSet,
    val profile: AqlCommercialCatalogProfile
) {
    init {
        require(family != DeviceFamily.UNKNOWN) {
            "Commercial catalog products must use an exact supported family."
        }
        require(displayName.isNotEmpty()) { "Commercial catalog displayName must not be empty." }
        require(!displayName.first().isWhitespace() && !displayName.last().isWhitespace()) {
            "Commercial catalog displayName must not contain surrounding whitespace."
        }
    }

    val compatibilityIdentity: DeviceCompatibilityIdentity
        get() = DeviceCompatibilityIdentity(
            productKey = productKey,
            productId = productId,
            model = model,
            hardwareRevision = hardwareRevision
        )
}
