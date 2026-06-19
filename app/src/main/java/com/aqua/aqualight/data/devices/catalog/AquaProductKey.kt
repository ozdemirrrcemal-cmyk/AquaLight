package com.aqua.aqualight.data.devices.catalog

/**
 * AquaLight commercial firmware product keys.
 *
 * This enum intentionally mirrors src/product/AqlProductCatalog.hpp from the
 * new firmware. Only com.aqualight.* product ids from the final commercial
 * contract are accepted by the app.
 */
enum class AquaProductKey(
    val storageKey: String,
    val productId: String,
    val setupCode: String,
    val category: AquaDeviceCategory
) {
    LIGHT_WRGB_PRO_ELITE(
        storageKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        setupCode = "WPE",
        category = AquaDeviceCategory.LIGHT
    ),
    LIGHT_RGB_PRO_SLIM(
        storageKey = "LIGHT_RGB_PRO_SLIM",
        productId = "com.aqualight.light.rgb_pro_slim",
        setupCode = "RPS",
        category = AquaDeviceCategory.LIGHT
    ),
    TIMER_RELAY_PRO_2(
        storageKey = "TIMER_RELAY_PRO_2",
        productId = "com.aqualight.timer.relay_pro_2",
        setupCode = "RP2",
        category = AquaDeviceCategory.TIMER
    ),
    TIMER_RELAY_PRO_4(
        storageKey = "TIMER_RELAY_PRO_4",
        productId = "com.aqualight.timer.relay_pro_4",
        setupCode = "RP4",
        category = AquaDeviceCategory.TIMER
    ),
    DOSING_DOSE_PRO_2(
        storageKey = "DOSING_DOSE_PRO_2",
        productId = "com.aqualight.dosing.dose_pro_2",
        setupCode = "DP2",
        category = AquaDeviceCategory.DOSING
    ),
    DOSING_DOSE_PRO_4(
        storageKey = "DOSING_DOSE_PRO_4",
        productId = "com.aqualight.dosing.dose_pro_4",
        setupCode = "DP4",
        category = AquaDeviceCategory.DOSING
    ),
    COOLING_COOL_PRO_1F(
        storageKey = "COOLING_COOL_PRO_1F",
        productId = "com.aqualight.cooling.cool_pro_1f",
        setupCode = "CP1",
        category = AquaDeviceCategory.COOLING
    ),
    COOLING_COOL_PRO_2F(
        storageKey = "COOLING_COOL_PRO_2F",
        productId = "com.aqualight.cooling.cool_pro_2f",
        setupCode = "CP2",
        category = AquaDeviceCategory.COOLING
    ),
    COOLING_COOL_PRO_3F(
        storageKey = "COOLING_COOL_PRO_3F",
        productId = "com.aqualight.cooling.cool_pro_3f",
        setupCode = "CP3",
        category = AquaDeviceCategory.COOLING
    ),
    UNKNOWN(
        storageKey = "UNKNOWN",
        productId = "com.aqualight.unknown",
        setupCode = "UNK",
        category = AquaDeviceCategory.UNKNOWN
    );

    companion object {
        fun fromStorageKey(value: String?): AquaProductKey {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { key ->
                key.storageKey.equals(value.trim(), ignoreCase = true)
            } ?: UNKNOWN
        }

        fun fromProductId(value: String?): AquaProductKey {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { key ->
                key.productId.equals(value.trim(), ignoreCase = true)
            } ?: UNKNOWN
        }

        fun fromSetupCode(value: String?): AquaProductKey {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { key ->
                key.setupCode.equals(value.trim(), ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}
