package com.aqua.aqualight.data.devices.catalog.cooling

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaProductColor
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.catalog.AquaProductRegion
import com.aqua.aqualight.data.devices.catalog.AquaProductVariant
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object CoolingProductCatalog {

    val coolPro1f = coolDefinition(
        productKey = AquaProductKey.COOLING_COOL_PRO_1F,
        productModel = "cool_pro_1f",
        displayName = "AquaLight Cool Pro 1 Fan",
        skuId = "com.aqualight.cooling.cool_pro_1f.global.black",
        skuCode = "AQL-C-CP1F-GLB-BLK",
        fanCount = 1
    )

    val coolPro2f = coolDefinition(
        productKey = AquaProductKey.COOLING_COOL_PRO_2F,
        productModel = "cool_pro_2f",
        displayName = "AquaLight Cool Pro 2 Fan",
        skuId = "com.aqualight.cooling.cool_pro_2f.global.black",
        skuCode = "AQL-C-CP2F-GLB-BLK",
        fanCount = 2
    )

    val coolPro3f = coolDefinition(
        productKey = AquaProductKey.COOLING_COOL_PRO_3F,
        productModel = "cool_pro_3f",
        displayName = "AquaLight Cool Pro 3 Fan",
        skuId = "com.aqualight.cooling.cool_pro_3f.global.black",
        skuCode = "AQL-C-CP3F-GLB-BLK",
        fanCount = 3
    )

    val all: List<CoolingDeviceDefinition> = listOf(
        coolPro1f,
        coolPro2f,
        coolPro3f
    )

    fun findByProductKey(productKey: AquaProductKey): CoolingDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productKey == productKey }

    fun findByProductId(productId: String): CoolingDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productId.equals(productId.trim(), ignoreCase = true) }

    private fun coolDefinition(
        productKey: AquaProductKey,
        productModel: String,
        displayName: String,
        skuId: String,
        skuCode: String,
        fanCount: Int
    ): CoolingDeviceDefinition {
        return CoolingDeviceDefinition(
            base = AquaDeviceDefinition(
                productKey = productKey,
                productId = productKey.productId,
                category = AquaDeviceCategory.COOLING,
                productFamily = "cooling",
                productLine = "cool_pro",
                productModel = productModel,
                displayName = displayName,
                setupCode = productKey.setupCode,
                variants = listOf(
                    AquaProductVariant(
                        skuId = skuId,
                        skuCode = skuCode,
                        displayName = "$displayName Global Black",
                        fanCount = fanCount,
                        sensorCount = 1,
                        region = AquaProductRegion.GLOBAL,
                        color = AquaProductColor.BLACK,
                        hardwareRevision = "1.0"
                    )
                ),
                mainModule = AquaDeviceModule.COOLING,
                controllerType = AquaDeviceControllerType.GENERIC_COOLING,
                firmwareProtocol = FirmwareProtocol.AQUA_V1,
                moduleVisibility = mapOf(
                    AquaDeviceModule.LIGHT to ModuleVisibility.HIDDEN,
                    AquaDeviceModule.TEMPERATURE to ModuleVisibility.EMBEDDED,
                    AquaDeviceModule.TIMER to ModuleVisibility.HIDDEN,
                    AquaDeviceModule.COOLING to ModuleVisibility.TOP_LEVEL,
                    AquaDeviceModule.DOSING to ModuleVisibility.HIDDEN
                ),
                screens = setOf(
                    AquaDeviceScreen.OVERVIEW,
                    AquaDeviceScreen.COOLING_CONTROL,
                    AquaDeviceScreen.COOLING_RULES,
                    AquaDeviceScreen.COOLING_FANS,
                    AquaDeviceScreen.COOLING_SENSOR_STATUS,
                    AquaDeviceScreen.ADVANCED
                ),
                features = setOf(
                    AquaDeviceFeature.WIFI_SETUP,
                    AquaDeviceFeature.LAN_DISCOVERY,
                    AquaDeviceFeature.COOLING_CONTROL,
                    AquaDeviceFeature.TEMPERATURE_READ,
                    AquaDeviceFeature.OTA_UPDATE
                )
            ),
            coolingFeatures = setOf(
                CoolingFeature.FAN_CONTROL,
                CoolingFeature.AUTO_TEMPERATURE_RULE,
                CoolingFeature.MANUAL_FAN_SPEED,
                CoolingFeature.SENSOR_STATUS
            ),
            maxFanChannelCount = fanCount,
            maxSensorCount = 1
        )
    }
}
