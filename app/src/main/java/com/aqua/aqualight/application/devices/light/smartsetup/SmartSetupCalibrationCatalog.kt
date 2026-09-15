package com.aqua.aqualight.application.devices.light.smartsetup

/** Reviewed Smart Setup output profiles. Products absent from this list are unsupported. */
object SmartSetupCalibrationCatalog {
    const val WRGB_PRO_ELITE_PRODUCT = "LIGHT_WRGB_PRO_ELITE"
    const val RGB_PRO_SLIM_PRODUCT = "LIGHT_RGB_PRO_SLIM"
    const val WRGB_PRO_ELITE_PROFILE_ID = "aql.wrgb-pro-elite.design-r2.smart-light-r1"

    val wrgbProElite = SmartSetupCalibrationProfile(
        id = WRGB_PRO_ELITE_PROFILE_ID,
        productKey = WRGB_PRO_ELITE_PRODUCT,
        revision = 2,
        channels = listOf(
            SmartSetupChannel.RED,
            SmartSetupChannel.GREEN,
            SmartSetupChannel.BLUE,
            SmartSetupChannel.WHITE
        ),
        minimumWaterDepthCm = 15,
        maximumWaterDepthCm = 60,
        minimumFixtureMountHeightCm = 0,
        maximumFixtureMountHeightCm = 30
    )

    val knownProducts = setOf(WRGB_PRO_ELITE_PRODUCT, RGB_PRO_SLIM_PRODUCT)

    fun reviewedProfileFor(productKey: String): SmartSetupCalibrationProfile? = when (productKey) {
        WRGB_PRO_ELITE_PRODUCT -> wrgbProElite
        RGB_PRO_SLIM_PRODUCT -> null
        else -> null
    }
}
