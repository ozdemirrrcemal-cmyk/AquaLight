package com.aqua.aqualight.data.devices.catalog.cooling

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.catalog.AquaProductVariant
import com.aqua.aqualight.data.devices.catalog.AquaProductRegion
import com.aqua.aqualight.data.devices.catalog.AquaProductColor
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object CoolingProductCatalog {

    val aquaCool001 = CoolingDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.COOLING_COOL_PRO,
            productId = AquaProductKey.COOLING_COOL_PRO.productId,
            category = AquaDeviceCategory.COOLING,

            productFamily = "AquaCool",
            productLine = "Cooling",
            productModel = "CoolPro",
            displayName = "CoolPro",
            setupCode = AquaProductKey.COOLING_COOL_PRO.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.cooling.cool_pro.global.black",
                    skuCode = "AQL-CPR-GLOBAL-BLK",
                    displayName = "CoolPro Global Black",
                    sensorCount = 2,
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
                AquaDeviceModule.COOLING to ModuleVisibility.TOP_LEVEL
            ),

            screens = setOf(
                AquaDeviceScreen.OVERVIEW,
                AquaDeviceScreen.COOLING_CONTROL,
                AquaDeviceScreen.COOLING_RULES,
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

        maxFanChannelCount = 2,
        maxSensorCount = 2
    )

    val all: List<CoolingDeviceDefinition> = listOf(
        aquaCool001
    )

    fun findByProductKey(
        productKey: AquaProductKey
    ): CoolingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productKey == productKey
        }
    }


    fun findByProductId(
        productId: String
    ): CoolingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productId.equals(productId.trim(), ignoreCase = true)
        }
    }
}