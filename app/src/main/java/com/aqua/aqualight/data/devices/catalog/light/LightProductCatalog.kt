package com.aqua.aqualight.data.devices.catalog.light

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.catalog.AquaProductVariant
import com.aqua.aqualight.data.devices.catalog.AquaProductRegion
import com.aqua.aqualight.data.devices.catalog.AquaProductColor
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object LightProductCatalog {

    val aquaLight001 = LightDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.LIGHT_WRGB_PRO2,
            productId = AquaProductKey.LIGHT_WRGB_PRO2.productId,
            category = AquaDeviceCategory.LIGHT,

            productFamily = "AquaLight",
            productLine = "WRGB",
            productModel = "WRGB Pro2",
            displayName = "WRGB Pro2",
            setupCode = AquaProductKey.LIGHT_WRGB_PRO2.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.light.wrgb_pro2.060.eu.black",
                    skuCode = "AQL-WP2-060-EU-BLK",
                    displayName = "WRGB Pro2 60cm EU Black",
                    sizeMm = 600,
                    channelCount = 4,
                    maxPowerWatt = 60,
                    region = AquaProductRegion.EU,
                    color = AquaProductColor.BLACK,
                    hardwareRevision = "1.0"
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
                AquaDeviceScreen.LIGHT_TEMPERATURE_PROTECTION,
                AquaDeviceScreen.ADVANCED
            ),

            features = setOf(
                AquaDeviceFeature.WIFI_SETUP,
                AquaDeviceFeature.LAN_DISCOVERY,
                AquaDeviceFeature.LIGHT_CONTROL,
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
            LightFeature.TEMPERATURE_PROTECTION
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

    val aquaLight002 = LightDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.LIGHT_RGB_PRO_ELITE,
            productId = AquaProductKey.LIGHT_RGB_PRO_ELITE.productId,
            category = AquaDeviceCategory.LIGHT,

            productFamily = "AquaLight",
            productLine = "RGB",
            productModel = "RGB Pro Elite",
            displayName = "RGB Pro Elite",
            setupCode = AquaProductKey.LIGHT_RGB_PRO_ELITE.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.light.rgb_pro_elite.global.black",
                    skuCode = "AQL-RPE-GLOBAL-BLK",
                    displayName = "RGB Pro Elite Global Black",
                    channelCount = 3,
                    region = AquaProductRegion.GLOBAL,
                    color = AquaProductColor.BLACK,
                    hardwareRevision = "1.0"
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
                AquaDeviceScreen.ADVANCED
            ),

            features = setOf(
                AquaDeviceFeature.WIFI_SETUP,
                AquaDeviceFeature.LAN_DISCOVERY,
                AquaDeviceFeature.LIGHT_CONTROL,
                AquaDeviceFeature.OTA_UPDATE
            )
        ),

        lightFeatures = setOf(
            LightFeature.MANUAL_POWER,
            LightFeature.GLOBAL_BRIGHTNESS,
            LightFeature.CHANNEL_CONTROL,
            LightFeature.LIGHT_SCHEDULE,
            LightFeature.PRESETS
        ),

        channels = listOf(
            LightChannelDefinition(
                id = "red",
                displayName = "Red",
                color = LightChannelColor.RED,
                order = 0,
                firmwareChannelIndex = 0
            ),
            LightChannelDefinition(
                id = "green",
                displayName = "Green",
                color = LightChannelColor.GREEN,
                order = 1,
                firmwareChannelIndex = 1
            ),
            LightChannelDefinition(
                id = "blue",
                displayName = "Blue",
                color = LightChannelColor.BLUE,
                order = 2,
                firmwareChannelIndex = 2
            )
        ),

        defaultSchedulePointCount = 5,
        maxSchedulePointCount = 24,
        supportsIndependentChannelSchedule = true
    )

    val all: List<LightDeviceDefinition> = listOf(
        aquaLight001,
        aquaLight002
    )

    fun findByProductKey(
        productKey: AquaProductKey
    ): LightDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productKey == productKey
        }
    }

    fun findByType(
        type: AquaDeviceType
    ): LightDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.type == type
        }
    }

    fun findByLegacyIdentity(
        aquaName: String,
        name: String
    ): LightDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.legacyAquaName.equals(aquaName.trim(), ignoreCase = true) &&
                definition.base.legacyName.equals(name.trim(), ignoreCase = true)
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