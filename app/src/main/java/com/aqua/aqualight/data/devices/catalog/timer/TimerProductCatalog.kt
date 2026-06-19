package com.aqua.aqualight.data.devices.catalog.timer

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

object TimerProductCatalog {

    val relayPro2 = relayDefinition(
        productKey = AquaProductKey.TIMER_RELAY_PRO_2,
        productModel = "relay_pro_2",
        displayName = "AquaLight Relay Pro 2",
        skuId = "com.aqualight.timer.relay_pro_2.global.black",
        skuCode = "AQL-T-RP2-GLB-BLK",
        outputCount = 2,
        maxTimerCount = 12
    )

    val relayPro4 = relayDefinition(
        productKey = AquaProductKey.TIMER_RELAY_PRO_4,
        productModel = "relay_pro_4",
        displayName = "AquaLight Relay Pro 4",
        skuId = "com.aqualight.timer.relay_pro_4.global.black",
        skuCode = "AQL-T-RP4-GLB-BLK",
        outputCount = 4,
        maxTimerCount = 16
    )

    val all: List<TimerDeviceDefinition> = listOf(
        relayPro2,
        relayPro4
    )

    fun findByProductKey(productKey: AquaProductKey): TimerDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productKey == productKey }

    fun findByProductId(productId: String): TimerDeviceDefinition? =
        all.firstOrNull { definition ->
            definition.base.productId.equals(productId.trim(), ignoreCase = true)
        }

    private fun relayDefinition(
        productKey: AquaProductKey,
        productModel: String,
        displayName: String,
        skuId: String,
        skuCode: String,
        outputCount: Int,
        maxTimerCount: Int
    ): TimerDeviceDefinition {
        return TimerDeviceDefinition(
            base = AquaDeviceDefinition(
                productKey = productKey,
                productId = productKey.productId,
                category = AquaDeviceCategory.TIMER,
                productFamily = "timer",
                productLine = "relay_pro",
                productModel = productModel,
                displayName = displayName,
                setupCode = productKey.setupCode,
                variants = listOf(
                    AquaProductVariant(
                        skuId = skuId,
                        skuCode = skuCode,
                        displayName = "$displayName Global Black",
                        outputCount = outputCount,
                        region = AquaProductRegion.GLOBAL,
                        color = AquaProductColor.BLACK,
                        hardwareRevision = "1.0"
                    )
                ),
                mainModule = AquaDeviceModule.TIMER,
                controllerType = AquaDeviceControllerType.GENERIC_TIMER,
                firmwareProtocol = FirmwareProtocol.AQUA_V1,
                moduleVisibility = mapOf(
                    AquaDeviceModule.LIGHT to ModuleVisibility.HIDDEN,
                    AquaDeviceModule.TEMPERATURE to ModuleVisibility.HIDDEN,
                    AquaDeviceModule.TIMER to ModuleVisibility.TOP_LEVEL,
                    AquaDeviceModule.COOLING to ModuleVisibility.HIDDEN,
                    AquaDeviceModule.DOSING to ModuleVisibility.HIDDEN
                ),
                screens = setOf(
                    AquaDeviceScreen.OVERVIEW,
                    AquaDeviceScreen.TIMER_CONTROL,
                    AquaDeviceScreen.TIMER_CHANNELS,
                    AquaDeviceScreen.TIMER_SCHEDULES,
                    AquaDeviceScreen.TIMER_MANUAL_RUN,
                    AquaDeviceScreen.ADVANCED
                ),
                features = setOf(
                    AquaDeviceFeature.WIFI_SETUP,
                    AquaDeviceFeature.LAN_DISCOVERY,
                    AquaDeviceFeature.TIMER_CONTROL,
                    AquaDeviceFeature.OTA_UPDATE
                )
            ),
            timerFeatures = setOf(
                TimerFeature.TIMER_LIST,
                TimerFeature.WEEKDAY_SCHEDULE,
                TimerFeature.INTERVAL_ON_OFF,
                TimerFeature.REPEAT_COUNT,
                TimerFeature.MANUAL_RUN,
                TimerFeature.MULTI_OUTPUT
            ),
            maxTimerCount = maxTimerCount,
            maxOutputCount = outputCount
        )
    }
}
