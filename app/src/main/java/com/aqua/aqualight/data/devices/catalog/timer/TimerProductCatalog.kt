package com.aqua.aqualight.data.devices.catalog.timer

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object TimerProductCatalog {

    val aquaTimer001 = TimerDeviceDefinition(
        base = AquaDeviceDefinition(
            type = AquaDeviceType.AQUA_TIMER_001,
            family = AquaDeviceFamily.AQUA_TIMER,

            legacyAquaName = "AquaTimer",
            legacyName = "TimerPro",

            productId = "aquatimer.001",
            productFamily = "AquaTimer",
            productModel = "TimerPro",

            displayName = "TimerPro",

            mainModule = AquaDeviceModule.TIMER,
            controllerType = AquaDeviceControllerType.GENERIC_TIMER,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

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
     * ESP32 legacy identity:
     * AquaName = Proelite
     * Name     = Multi control
     *
     * 4 çıkışlı timer cihazıdır.
     * Şu an standart Generic Timer ekranına yönlendirilir.
     */
    val aquaTimer002 = TimerDeviceDefinition(
        base = AquaDeviceDefinition(
            type = AquaDeviceType.AQUA_TIMER_002,
            family = AquaDeviceFamily.CUSTOM,

            legacyAquaName = "Proelite",
            legacyName = "Multi control",

            productId = "proelite.multi_control",
            productFamily = "Proelite",
            productModel = "Multi control",

            displayName = "Multi control",

            mainModule = AquaDeviceModule.TIMER,
            controllerType = AquaDeviceControllerType.GENERIC_TIMER,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

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

    fun findByType(
        type: AquaDeviceType
    ): TimerDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.type == type
        }
    }

    fun findByLegacyIdentity(
        aquaName: String,
        name: String
    ): TimerDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.legacyAquaName.equals(
                other = aquaName.trim(),
                ignoreCase = true
            ) &&
                definition.base.legacyName.equals(
                    other = name.trim(),
                    ignoreCase = true
                )
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