package com.aqua.aqualight.data.devices.catalog

enum class AquaDeviceFamily(
    val productFamilyName: String,
    val displayName: String
) {
    AQUA_LIGHT(
        productFamilyName = "AquaLight",
        displayName = "AquaLight"
    ),

    AQUA_TIMER(
        productFamilyName = "AquaTimer",
        displayName = "AquaTimer"
    ),

    AQUA_DOSE(
        productFamilyName = "AquaDose",
        displayName = "AquaDose"
    ),

    AQUA_COOL(
        productFamilyName = "AquaCool",
        displayName = "AquaCool"
    ),

    AQUA_CONTROL(
        productFamilyName = "AquaControl",
        displayName = "AquaControl"
    ),

    CUSTOM(
        productFamilyName = "Custom",
        displayName = "Custom"
    ),

    UNKNOWN(
        productFamilyName = "Unknown",
        displayName = "Unknown"
    );

    companion object {
        fun fromProductFamilyName(
            value: String?
        ): AquaDeviceFamily {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { family ->
                family.productFamilyName.equals(
                    other = value.trim(),
                    ignoreCase = true
                ) || family.displayName.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }
    }
}
