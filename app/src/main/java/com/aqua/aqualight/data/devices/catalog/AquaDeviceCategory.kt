package com.aqua.aqualight.data.devices.catalog

/**
 * Ana cihaz kategorisi.
 *
 * Router bu değeri kullanır; ürün modeli, Wi-Fi adı veya kullanıcı adı ile
 * ekran seçimi yapılmaz.
 */
enum class AquaDeviceCategory(
    val storageKey: String,
    val routeKey: String,
    val defaultFamily: AquaDeviceFamily
) {
    LIGHT(
        storageKey = "LIGHT",
        routeKey = "light",
        defaultFamily = AquaDeviceFamily.AQUA_LIGHT
    ),
    TIMER(
        storageKey = "TIMER",
        routeKey = "timer",
        defaultFamily = AquaDeviceFamily.AQUA_TIMER
    ),
    COOLING(
        storageKey = "COOLING",
        routeKey = "cooling",
        defaultFamily = AquaDeviceFamily.AQUA_COOL
    ),
    DOSING(
        storageKey = "DOSING",
        routeKey = "dosing",
        defaultFamily = AquaDeviceFamily.AQUA_DOSE
    ),
    CONTROLLER(
        storageKey = "CONTROLLER",
        routeKey = "controller",
        defaultFamily = AquaDeviceFamily.AQUA_CONTROL
    ),
    UNKNOWN(
        storageKey = "UNKNOWN",
        routeKey = "unknown",
        defaultFamily = AquaDeviceFamily.UNKNOWN
    );

    companion object {
        fun fromStorageKey(
            value: String?
        ): AquaDeviceCategory {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { category ->
                category.storageKey.equals(
                    other = value.trim(),
                    ignoreCase = true
                ) || category.routeKey.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }
    }
}
