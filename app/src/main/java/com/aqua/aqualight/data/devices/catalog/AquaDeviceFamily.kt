package com.aqua.aqualight.data.devices.catalog

enum class AquaDeviceFamily(
    val legacyAquaName: String,
    val displayName: String
) {
    AQUA_LIGHT(
        legacyAquaName = "AquaLight",
        displayName = "AquaLight"
    ),

    AQUA_TIMER(
        legacyAquaName = "AquaTimer",
        displayName = "AquaTimer"
    ),
	
	AQUA_DOSE(
        legacyAquaName = "AquaDose",
        displayName = "AquaDose"
    ),

    AQUA_COOL(
        legacyAquaName = "AquaCool",
        displayName = "AquaCool"
    ),

    AQUA_CONTROL(
        legacyAquaName = "AquaControl",
        displayName = "AquaControl"
    ),

    CUSTOM(
        legacyAquaName = "Custom",
        displayName = "Custom"
    ),

    UNKNOWN(
        legacyAquaName = "Unknown",
        displayName = "Unknown"
    );

    companion object {
        fun fromLegacyAquaName(
            value: String?
        ): AquaDeviceFamily {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { family ->
                family.legacyAquaName.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }
    }
}