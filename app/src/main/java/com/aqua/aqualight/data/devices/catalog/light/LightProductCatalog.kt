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
        base = lightBase(
            productKey = AquaProductKey.LIGHT_WRGB_PRO_ELITE,
            productLine = "wrgb_pro",
            productModel = "wrgb_pro_elite_120",
            displayName = "AquaLight WRGB Pro Elite 120",
            skuId = "com.aqualight.light.wrgb_pro_elite_120.global.black",
            skuCode = "AQL-L-WPE120-GLB-BLK",
            variantName = "WRGB Pro Elite 120 Global Black",
            channelCount = 4,
            fanCount = 2,
            sensorCount = 1,
            maxPowerWatt = 120,
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
                AquaDeviceScreen.COOLING_CONTROL,
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
                AquaDeviceFeature.COOLING_CONTROL,
                AquaDeviceFeature.OTA_UPDATE
            ),
            hasCooling = true,
            hasTemperature = true
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
            LightChannelDefinition("white", "White", LightChannelColor.WHITE, 0, 0),
            LightChannelDefinition("red", "Red", LightChannelColor.RED, 1, 1),
            LightChannelDefinition("green", "Green", LightChannelColor.GREEN, 2, 2),
            LightChannelDefinition("blue", "Blue", LightChannelColor.BLUE, 3, 3)
        ),
        defaultSchedulePointCount = 5,
        maxSchedulePointCount = 24,
        supportsIndependentChannelSchedule = true
    )

    val rgbProSlim = LightDeviceDefinition(
        base = lightBase(
            productKey = AquaProductKey.LIGHT_RGB_PRO_SLIM,
            productLine = "rgb_pro",
            productModel = "rgb_pro_slim",
            displayName = "AquaLight RGB Pro Slim",
            skuId = "com.aqualight.light.rgb_pro_slim.global.black",
            skuCode = "AQL-L-RPS-GLB-BLK",
            variantName = "RGB Pro Slim Global Black",
            channelCount = 3,
            fanCount = 0,
            sensorCount = 0,
            maxPowerWatt = null,
            screens = setOf(
                AquaDeviceScreen.OVERVIEW,
                AquaDeviceScreen.LIGHT_CONTROL,
                AquaDeviceScreen.LIGHT_CHANNELS,
                AquaDeviceScreen.LIGHT_SCHEDULE,
                AquaDeviceScreen.LIGHT_PRESETS,
                AquaDeviceScreen.LIGHT_QUICK_SETUP,
                AquaDeviceScreen.LIGHT_MOONLIGHT,
                AquaDeviceScreen.LIGHT_ACCLIMATION,
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
                AquaDeviceFeature.OTA_UPDATE
            ),
            hasCooling = false,
            hasTemperature = false
        ),
        lightFeatures = setOf(
            LightFeature.MANUAL_POWER,
            LightFeature.GLOBAL_BRIGHTNESS,
            LightFeature.CHANNEL_CONTROL,
            LightFeature.LIGHT_SCHEDULE,
            LightFeature.PRESETS,
            LightFeature.MOONLIGHT,
            LightFeature.ACCLIMATION_MODE
        ),
        channels = listOf(
            LightChannelDefinition("red", "Red", LightChannelColor.RED, 0, 0),
            LightChannelDefinition("green", "Green", LightChannelColor.GREEN, 1, 1),
            LightChannelDefinition("blue", "Blue", LightChannelColor.BLUE, 2, 2)
        ),
        defaultSchedulePointCount = 5,
        maxSchedulePointCount = 24,
        supportsIndependentChannelSchedule = true
    )

    val all: List<LightDeviceDefinition> = listOf(
        wrgbProElite120,
        rgbProSlim
    )

    fun findByProductKey(productKey: AquaProductKey): LightDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productKey == productKey }

    fun findByProductId(productId: String): LightDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productId.equals(productId.trim(), ignoreCase = true) }

    private fun lightBase(
        productKey: AquaProductKey,
        productLine: String,
        productModel: String,
        displayName: String,
        skuId: String,
        skuCode: String,
        variantName: String,
        channelCount: Int,
        fanCount: Int,
        sensorCount: Int,
        maxPowerWatt: Int?,
        screens: Set<AquaDeviceScreen>,
        features: Set<AquaDeviceFeature>,
        hasCooling: Boolean,
        hasTemperature: Boolean
    ): AquaDeviceDefinition {
        return AquaDeviceDefinition(
            productKey = productKey,
            productId = productKey.productId,
            category = AquaDeviceCategory.LIGHT,
            productFamily = "light",
            productLine = productLine,
            productModel = productModel,
            displayName = displayName,
            setupCode = productKey.setupCode,
            variants = listOf(
                AquaProductVariant(
                    skuId = skuId,
                    skuCode = skuCode,
                    displayName = variantName,
                    channelCount = channelCount,
                    fanCount = fanCount,
                    sensorCount = sensorCount,
                    maxPowerWatt = maxPowerWatt,
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
                AquaDeviceModule.TEMPERATURE to if (hasTemperature) ModuleVisibility.EMBEDDED else ModuleVisibility.HIDDEN,
                AquaDeviceModule.COOLING to if (hasCooling) ModuleVisibility.EMBEDDED else ModuleVisibility.HIDDEN,
                AquaDeviceModule.TIMER to ModuleVisibility.HIDDEN,
                AquaDeviceModule.DOSING to ModuleVisibility.HIDDEN
            ),
            screens = screens,
            features = features
        )
    }
}
