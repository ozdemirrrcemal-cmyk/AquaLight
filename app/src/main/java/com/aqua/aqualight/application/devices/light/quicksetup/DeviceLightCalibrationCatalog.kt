package com.aqua.aqualight.application.devices.light.quicksetup

/**
 * Versioned registry for AquaLight laboratory optical profiles.
 *
 * No profile is registered until an integrating-sphere/PAR measurement set is approved for the
 * exact product key, hardware revision and fixture length. An empty result is a supported
 * conservative state and prevents the UI from making PPFD or DLI claims from channel percentages.
 */
object DeviceLightCalibrationCatalog {
    const val CATALOG_REVISION = 1

    private val approvedProfiles: List<DeviceLightCalibrationProfile> = emptyList()

    fun find(
        productKey: String,
        hardwareRevision: String,
        fixtureLengthMm: Int
    ): DeviceLightCalibrationProfile? = approvedProfiles.singleOrNull { profile ->
        profile.productKey == productKey &&
            profile.hardwareRevision == hardwareRevision &&
            profile.fixtureLengthMm == fixtureLengthMm
    }
}
