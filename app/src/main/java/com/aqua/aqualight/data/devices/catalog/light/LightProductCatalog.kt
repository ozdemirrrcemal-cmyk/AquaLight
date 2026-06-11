package com.aqua.aqualight.data.devices.catalog.light

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object LightProductCatalog {

    val aquaLight001 = LightDeviceDefinition(
        base = AquaDeviceDefinition(
            type = AquaDeviceType.AQUA_LIGHT_001,
            family = AquaDeviceFamily.AQUA_LIGHT,

            legacyAquaName = "AquaLight",
            legacyName = "WRGB Pro2",

            productId = "aqualight.001",
            productFamily = "AquaLight",
            productModel = "WRGB Pro2",

            displayName = "WRGB Pro2",

            mainModule = AquaDeviceModule.LIGHT,
            controllerType = AquaDeviceControllerType.GENERIC_LIGHT,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

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
            type = AquaDeviceType.AQUA_LIGHT_002,
            family = AquaDeviceFamily.AQUA_LIGHT,

            legacyAquaName = "AquaLight",
            legacyName = "RGB Pro Elite",

            productId = "aqualight.002",
            productFamily = "AquaLight",
            productModel = "RGB Pro Elite",

            displayName = "RGB Pro Elite",

            mainModule = AquaDeviceModule.LIGHT,
            controllerType = AquaDeviceControllerType.GENERIC_LIGHT,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

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