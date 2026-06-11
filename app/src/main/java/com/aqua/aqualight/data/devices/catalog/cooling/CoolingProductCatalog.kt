package com.aqua.aqualight.data.devices.catalog.cooling

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object CoolingProductCatalog {

    val aquaCool001 = CoolingDeviceDefinition(
        base = AquaDeviceDefinition(
            type = AquaDeviceType.AQUA_COOL_001,
            family = AquaDeviceFamily.AQUA_COOL,

            legacyAquaName = "AquaCool",
            legacyName = "CoolPro",

            productId = "aquacool.001",
            productFamily = "AquaCool",
            productModel = "CoolPro",

            displayName = "CoolPro",

            mainModule = AquaDeviceModule.COOLING,
            controllerType = AquaDeviceControllerType.GENERIC_COOLING,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

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

    fun findByType(
        type: AquaDeviceType
    ): CoolingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.type == type
        }
    }

    fun findByLegacyIdentity(
        aquaName: String,
        name: String
    ): CoolingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.legacyAquaName.equals(aquaName.trim(), ignoreCase = true) &&
                definition.base.legacyName.equals(name.trim(), ignoreCase = true)
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