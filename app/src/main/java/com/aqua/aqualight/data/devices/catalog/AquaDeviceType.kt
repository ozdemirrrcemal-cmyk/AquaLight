package com.aqua.aqualight.data.devices.catalog

enum class AquaDeviceType(
    val storageKey: String
) {
    AQUA_LIGHT_001("AQUA_LIGHT_001"),
    AQUA_LIGHT_002("AQUA_LIGHT_002"),
    AQUA_LIGHT_003("AQUA_LIGHT_003"),
    AQUA_LIGHT_004("AQUA_LIGHT_004"),

    AQUA_TIMER_001("AQUA_TIMER_001"),
    AQUA_TIMER_002("AQUA_TIMER_002"),
	
	AQUA_DOSE_001("AQUA_DOSE_001"),
	AQUA_DOSE_002("AQUA_DOSE_002"),
	
	
    AQUA_COOL_001("AQUA_COOL_001"),
	AQUA_COOL_002("AQUA_COOL_002"),

    AQUA_CONTROL_001("AQUA_CONTROL_001"),

    UNKNOWN("UNKNOWN");

    companion object {
        fun fromStorageKey(
            value: String?
        ): AquaDeviceType {
            if (value.isNullOrBlank()) {
                return UNKNOWN
            }

            return values().firstOrNull { type ->
                type.storageKey.equals(
                    other = value.trim(),
                    ignoreCase = true
                )
            } ?: UNKNOWN
        }
    }
}