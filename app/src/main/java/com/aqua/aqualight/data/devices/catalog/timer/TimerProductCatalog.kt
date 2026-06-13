package com.aqua.aqualight.data.devices.catalog.timer

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

object TimerProductCatalog {

    val aquaTimer001 = TimerDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.TIMER_TIMER_PRO,
            productId = AquaProductKey.TIMER_TIMER_PRO.productId,
            category = AquaDeviceCategory.TIMER,

            productFamily = "AquaTimer",
            productLine = "Timer",
            productModel = "TimerPro",
            displayName = "TimerPro",
            setupCode = AquaProductKey.TIMER_TIMER_PRO.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.timer.timer_pro.global.black",
                    skuCode = "AQL-TPR-GLOBAL-BLK",
                    displayName = "TimerPro Global Black",
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
            TimerFeature.MANUAL_RUN
        ),

        maxTimerCount = 8,
        maxOutputCount = 1
    )

    /**
     * İkinci timer ürün modeli.
     *
     * 4 çıkışlı timer cihazıdır.
     * Şu an standart Generic Timer ekranına yönlendirilir.
     */
    val aquaTimer002 = TimerDeviceDefinition(
        base = AquaDeviceDefinition(
            productKey = AquaProductKey.TIMER_MULTI_CONTROL,
            productId = AquaProductKey.TIMER_MULTI_CONTROL.productId,
            category = AquaDeviceCategory.TIMER,

            productFamily = "AquaTimer",
            productLine = "Timer",
            productModel = "Multi Control",
            displayName = "Multi Control",
            setupCode = AquaProductKey.TIMER_MULTI_CONTROL.setupCode,

            variants = listOf(
                AquaProductVariant(
                    skuId = "com.aqua.timer.multi_control.global.black",
                    skuCode = "AQL-TMC-GLOBAL-BLK",
                    displayName = "Multi Control Global Black",
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
                AquaDeviceScreen.TIMER_SCHEDULES,
                AquaDeviceScreen.TIMER_MANUAL_RUN,
                AquaDeviceScreen.TIMER_MULTI_OUTPUT,
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

        maxTimerCount = 12,
        maxOutputCount = 4
    )

    val all: List<TimerDeviceDefinition> = listOf(
        aquaTimer001,
        aquaTimer002
    )

    fun findByProductKey(
        productKey: AquaProductKey
    ): TimerDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productKey == productKey
        }
    }


    fun findByProductId(
        productId: String
    ): TimerDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productId.equals(
                other = productId.trim(),
                ignoreCase = true
            )
        }
    }
}