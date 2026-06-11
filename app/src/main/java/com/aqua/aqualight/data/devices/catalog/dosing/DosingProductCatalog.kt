package com.aqua.aqualight.data.devices.catalog.dosing

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AquaDeviceFeature
import com.aqua.aqualight.data.devices.catalog.AquaDeviceModule
import com.aqua.aqualight.data.devices.catalog.AquaDeviceScreen
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerType
import com.aqua.aqualight.data.devices.catalog.FirmwareProtocol
import com.aqua.aqualight.data.devices.catalog.ModuleVisibility

object DosingProductCatalog {

    val aquaDose001 = DosingDeviceDefinition(
        base = AquaDeviceDefinition(
            type = AquaDeviceType.AQUA_DOSE_001,
            family = AquaDeviceFamily.AQUA_DOSE,

            legacyAquaName = "AquaDose",
            legacyName = "DosePro 4",

            productId = "aquadose.001",
            productFamily = "AquaDose",
            productModel = "DosePro 4",

            displayName = "DosePro 4",

            mainModule = AquaDeviceModule.DOSING,
            controllerType = AquaDeviceControllerType.CUSTOM_DOSING_4CH,
            firmwareProtocol = FirmwareProtocol.LEGACY_GET_SET,

            moduleVisibility = mapOf(
                AquaDeviceModule.LIGHT to ModuleVisibility.HIDDEN,
                AquaDeviceModule.TIMER to ModuleVisibility.HIDDEN,
                AquaDeviceModule.COOLING to ModuleVisibility.HIDDEN,
                AquaDeviceModule.DOSING to ModuleVisibility.TOP_LEVEL,
                AquaDeviceModule.TEMPERATURE to ModuleVisibility.HIDDEN
            ),

            screens = setOf(
                AquaDeviceScreen.OVERVIEW,
                AquaDeviceScreen.DOSING_CONTROL,
                AquaDeviceScreen.DOSING_CHANNELS,
                AquaDeviceScreen.DOSING_SCHEDULES,
                AquaDeviceScreen.DOSING_CALIBRATION,
                AquaDeviceScreen.DOSING_RESERVOIR,
                AquaDeviceScreen.DOSING_MANUAL_RUN,
                AquaDeviceScreen.ADVANCED
            ),

            features = setOf(
                AquaDeviceFeature.WIFI_SETUP,
                AquaDeviceFeature.LAN_DISCOVERY,
                AquaDeviceFeature.DOSING_CONTROL,
                AquaDeviceFeature.DOSING_CALIBRATION,
                AquaDeviceFeature.DOSING_RESERVOIR_TRACKING,
                AquaDeviceFeature.OTA_UPDATE
            )
        ),

        dosingFeatures = setOf(
            DosingFeature.FOUR_CHANNEL,
            DosingFeature.ML_DOSING,
            DosingFeature.PUMP_CALIBRATION,
            DosingFeature.MANUAL_RUN,
            DosingFeature.WEEKDAY_SCHEDULE,
            DosingFeature.REPEAT_COUNT,
            DosingFeature.RESERVOIR_TRACKING,
            DosingFeature.LOW_LEVEL_WARNING
        ),

        pumpCount = 4,
        maxScheduleCount = 16
    )

    val all: List<DosingDeviceDefinition> = listOf(
        aquaDose001
    )

    fun findByType(
        type: AquaDeviceType
    ): DosingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.type == type
        }
    }

    fun findByLegacyIdentity(
        aquaName: String,
        name: String
    ): DosingDeviceDefinition? {
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
    ): DosingDeviceDefinition? {
        return all.firstOrNull { definition ->
            definition.base.productId.equals(
                other = productId.trim(),
                ignoreCase = true
            )
        }
    }
}