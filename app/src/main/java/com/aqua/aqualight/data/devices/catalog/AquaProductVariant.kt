package com.aqua.aqualight.data.devices.catalog

data class AquaProductVariant(
    val skuId: String,
    val skuCode: String,
    val displayName: String,

    val sizeMm: Int? = null,
    val channelCount: Int? = null,
    val outputCount: Int? = null,
    val sensorCount: Int? = null,
    val fanCount: Int? = null,
    val pumpCount: Int? = null,
    val maxPowerWatt: Int? = null,
    val region: AquaProductRegion = AquaProductRegion.GLOBAL,
    val color: AquaProductColor = AquaProductColor.UNKNOWN,

    val hardwareRevision: String? = null
) {
    init {
        require(skuId.isNotBlank()) {
            "skuId cannot be blank."
        }

        require(skuCode.isNotBlank()) {
            "skuCode cannot be blank."
        }

        require(displayName.isNotBlank()) {
            "displayName cannot be blank."
        }
    }
}

enum class AquaProductRegion {
    GLOBAL,
    EU,
    US,
    UK,
    TR
}

enum class AquaProductColor {
    BLACK,
    WHITE,
    SILVER,
    UNKNOWN
}
