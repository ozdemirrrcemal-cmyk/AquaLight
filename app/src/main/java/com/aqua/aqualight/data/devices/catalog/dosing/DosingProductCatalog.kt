package com.aqua.aqualight.data.devices.catalog.dosing

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

object DosingProductCatalog {

    val dosePro2 = doseDefinition(
        productKey = AquaProductKey.DOSING_DOSE_PRO_2,
        productModel = "dose_pro_2",
        displayName = "AquaLight Dose Pro 2",
        skuId = "com.aqualight.dosing.dose_pro_2.global.black",
        skuCode = "AQL-D-DP2-GLB-BLK",
        pumpCount = 2,
        maxScheduleCount = 12
    )

    val dosePro4 = doseDefinition(
        productKey = AquaProductKey.DOSING_DOSE_PRO_4,
        productModel = "dose_pro_4",
        displayName = "AquaLight Dose Pro 4",
        skuId = "com.aqualight.dosing.dose_pro_4.global.black",
        skuCode = "AQL-D-DP4-GLB-BLK",
        pumpCount = 4,
        maxScheduleCount = 16
    )

    val all: List<DosingDeviceDefinition> = listOf(
        dosePro2,
        dosePro4
    )

    fun findByProductKey(productKey: AquaProductKey): DosingDeviceDefinition? =
        all.firstOrNull { definition -> definition.base.productKey == productKey }

    fun findByProductId(productId: String): DosingDeviceDefinition? =
        all.firstOrNull { definition ->
            definition.base.productId.equals(other = productId.trim(), ignoreCase = true)
        }

    private fun doseDefinition(
        productKey: AquaProductKey,
        productModel: String,
        displayName: String,
        skuId: String,
        skuCode: String,
        pumpCount: Int,
        maxScheduleCount: Int
    ): DosingDeviceDefinition {
        return DosingDeviceDefinition(
            base = AquaDeviceDefinition(
                productKey = productKey,
                productId = productKey.productId,
                category = AquaDeviceCategory.DOSING,
                productFamily = "dosing",
                productLine = "dose_pro",
                productModel = productModel,
                displayName = displayName,
                setupCode = productKey.setupCode,
                variants = listOf(
                    AquaProductVariant(
                        skuId = skuId,
                        skuCode = skuCode,
                        displayName = "$displayName Global Black",
                        pumpCount = pumpCount,
                        region = AquaProductRegion.GLOBAL,
                        color = AquaProductColor.BLACK,
                        hardwareRevision = "1.0"
                    )
                ),
                mainModule = AquaDeviceModule.DOSING,
                controllerType = if (pumpCount >= 4) {
                    AquaDeviceControllerType.CUSTOM_DOSING_4CH
                } else {
                    AquaDeviceControllerType.GENERIC_DOSING
                },
                firmwareProtocol = FirmwareProtocol.AQUA_V1,
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
            dosingFeatures = buildSet {
                add(DosingFeature.ML_DOSING)
                add(DosingFeature.PUMP_CALIBRATION)
                add(DosingFeature.MANUAL_RUN)
                add(DosingFeature.WEEKDAY_SCHEDULE)
                add(DosingFeature.REPEAT_COUNT)
                add(DosingFeature.RESERVOIR_TRACKING)
                add(DosingFeature.LOW_LEVEL_WARNING)
                if (pumpCount >= 4) {
                    add(DosingFeature.FOUR_CHANNEL)
                }
            },
            pumpCount = pumpCount,
            maxScheduleCount = maxScheduleCount
        )
    }
}
