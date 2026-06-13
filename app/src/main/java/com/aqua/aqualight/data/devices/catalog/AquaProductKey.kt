package com.aqua.aqualight.data.devices.catalog

/**
 * Uygulama içindeki sabit ürün anahtarı.
 *
 * ProductId firmware/app sözleşmesi için kullanılır; ProductKey ise uygulama
 * kodunda enum güvenliği sağlar.
 */
enum class AquaProductKey(
    val storageKey: String,
    val productId: String,
    val setupCode: String,
    val category: AquaDeviceCategory,
    val legacyDeviceType: AquaDeviceType
) {
    LIGHT_WRGB_PRO_ELITE(
        storageKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqua.light.wrgb_pro_elite",
        setupCode = "WPE",
        category = AquaDeviceCategory.LIGHT,
        legacyDeviceType = AquaDeviceType.AQUA_LIGHT_001
    ),
    TIMER_TIMER_PRO(
        storageKey = "TIMER_TIMER_PRO",
        productId = "com.aqua.timer.timer_pro",
        setupCode = "TPR",
        category = AquaDeviceCategory.TIMER,
        legacyDeviceType = AquaDeviceType.AQUA_TIMER_001
    ),
    TIMER_MULTI_CONTROL(
        storageKey = "TIMER_MULTI_CONTROL",
        productId = "com.aqua.timer.multi_control",
        setupCode = "TMC",
        category = AquaDeviceCategory.TIMER,
        legacyDeviceType = AquaDeviceType.AQUA_TIMER_002
    ),
    COOLING_COOL_PRO(
        storageKey = "COOLING_COOL_PRO",
        productId = "com.aqua.cooling.cool_pro",
        setupCode = "CPR",
        category = AquaDeviceCategory.COOLING,
        legacyDeviceType = AquaDeviceType.AQUA_COOL_001
    ),
    DOSING_DOSE_PRO_4(
        storageKey = "DOSING_DOSE_PRO_4",
        productId = "com.aqua.dosing.dose_pro_4",
        setupCode = "DP4",
        category = AquaDeviceCategory.DOSING,
        legacyDeviceType = AquaDeviceType.AQUA_DOSE_001
    ),
    UNKNOWN(
        storageKey = "UNKNOWN",
        productId = "com.aqua.unknown",
        setupCode = "UNK",
        category = AquaDeviceCategory.UNKNOWN,
        legacyDeviceType = AquaDeviceType.UNKNOWN
    );

    companion object {
        fun fromStorageKey(
            value: String?
        ): AquaProductKey {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { key ->
                key.storageKey.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }

        fun fromProductId(
            value: String?
        ): AquaProductKey {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { key ->
                key.productId.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }

        fun fromSetupCode(
            value: String?
        ): AquaProductKey {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { key ->
                key.setupCode.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }
    }
}
