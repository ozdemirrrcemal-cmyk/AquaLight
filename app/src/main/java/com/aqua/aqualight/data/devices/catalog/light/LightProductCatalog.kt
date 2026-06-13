package com.aqua.aqualight.data.devices.catalog.light

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

object LightProductCatalog {

    val wrgbProElite120 = LightDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.LIGHT_WRGB_PRO_ELITE,
            productId = AquaProductKey.LIGHT_WRGB_PRO_ELITE.productId,
            category = AquaDeviceCategory.LIGHT,

            productFamily = "AquaLight",
            productLine = "WRGB",
            productModel = "WRGB Pro Elite",
            displayName = "WRGB Pro Elite",
            setupCode = AquaProductKey.LIGHT_WRGB_PRO_ELITE.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.light.wrgb_pro_elite.120.global.black",
                    skuCode = "AQL-WPE-120-GLB-BLK",
                    displayName = "WRGB Pro Elite 120cm Global Black",
                    sizeMm = 1200,
                    channelCount = 4,
                    fanCount = 2,
                    sensorCount = 1,
                    maxPowerWatt = 120,
                    region = AquaProductRegion.GLOBAL,
                    color = AquaProductColor.BLACK,
                    hardwareRevision = null
                )
            ),

            mainModule = AquaDeviceModule.LIGHT,
            controllerType = AquaDeviceControllerType.GENERIC_LIGHT,
            firmwareProtocol = FirmwareProtocol.AQUA_V1,

            moduleVisibility = mapOf(
                AquaDeviceModule.LIGHT to ModuleVisibility.TOP_LEVEL,
                AquaDeviceModule.TEMPERATURE to ModuleVisibility.EMBEDDED,
                AquaDeviceModule.TIMER to ModuleVisibility.HIDDEN,
                AquaDeviceModule.COOLING to ModuleVisibility.HIDDEN
            ),

            screens = setOf(
                AquaDeviceScreen.OVERVIEW,
                AquaDeviceScreen.LIGHT_CONTROL,
                AquaDeviceScreen.LIGHT_CHANNELS,
                AquaDeviceScreen.LIGHT_SCHEDULE,
                AquaDeviceScreen.LIGHT_PRESETS,
                AquaDeviceScreen.LIGHT_QUICK_SETUP,
                AquaDeviceScreen.LIGHT_MOONLIGHT,
                AquaDeviceScreen.LIGHT_ACCLIMATION,
                AquaDeviceScreen.LIGHT_TEMPERATURE_PROTECTION,
                AquaDeviceScreen.LIGHT_FAN_CONTROL,
                AquaDeviceScreen.ADVANCED
            ),

            features = setOf(
                AquaDeviceFeature.WIFI_SETUP,
                AquaDeviceFeature.LAN_DISCOVERY,
                AquaDeviceFeature.LIGHT_CONTROL,
                AquaDeviceFeature.LIGHT_QUICK_SETUP,
                AquaDeviceFeature.LIGHT_PRESETS,
                AquaDeviceFeature.LIGHT_MOONLIGHT,
                AquaDeviceFeature.LIGHT_ACCLIMATION,
                AquaDeviceFeature.LIGHT_TEMPERATURE_PROTECTION,
                AquaDeviceFeature.LIGHT_FAN_CONTROL,
                AquaDeviceFeature.TEMPERATURE_READ,
                AquaDeviceFeature.OTA_UPDATE
            )
        ),

        lightFeatures = setOf(
            LightFeature.MANUAL_POWER,
            LightFeature.GLOBAL_BRIGHTNESS,
            LightFeature.CHANNEL_CONTROL,
            LightFeature.LIGHT_SCHEDULE,
            LightFeature.PRESETS,
            LightFeature.MOONLIGHT,
            LightFeature.ACCLIMATION_MODE,
            LightFeature.TEMPERATURE_PROTECTION,
            LightFeature.FAN_CONTROL
        ),

        channels = listOf(
            LightChannelDefinition(
                id = "white",
                displayName = "White",
                color = LightChannelColor.WHITE,
                order = 0,
                firmwareChannelIndex = 0
            ),
            LightChannelDefinition(
                id = "red",
                displayName = "Red",
                color = LightChannelColor.RED,
                order = 1,
                firmwareChannelIndex = 1
            ),
            LightChannelDefinition(
                id = "green",
                displayName = "Green",
                color = LightChannelColor.GREEN,
                order = 2,
                firmwareChannelIndex = 2
            ),
            LightChannelDefinition(
                id = "blue",
                displayName = "Blue",
                color = LightChannelColor.BLUE,
                order = 3,
                firmwareChannelIndex = 3
            )
        ),

        defaultSchedulePointCount = 5,
        maxSchedulePointCount = 24,
        supportsIndependentChannelSchedule = true
    )

    val all: List<LightDeviceDefinition> = listOf(
        wrgbProElite120
    )

    fun findByProductKey(
        productKey: AquaProductKey
    ): LightDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productKey == productKey
        }
    }


    fun findByProductId(
        productId: String
    ): LightDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productId.equals(productId.trim(), ignoreCase = true)
        }
    }
}
