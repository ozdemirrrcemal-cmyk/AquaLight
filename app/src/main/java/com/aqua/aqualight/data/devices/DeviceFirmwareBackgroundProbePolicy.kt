package com.aqua.aqualight.data.devices

/** Fail-closed eligibility gate applied before background OTA availability commands are allowed. */
internal object DeviceFirmwareBackgroundProbePolicy {
    enum class Decision {
        NOT_LIVE,
        METADATA_UNVALIDATED,
        OTA_UNSUPPORTED,
        ELIGIBLE
    }

    fun decide(
        freshlyDiscovered: Boolean,
        hasValidatedRuntimeMetadata: Boolean,
        supportsOta: Boolean
    ): Decision = when {
        !freshlyDiscovered -> Decision.NOT_LIVE
        !hasValidatedRuntimeMetadata -> Decision.METADATA_UNVALIDATED
        !supportsOta -> Decision.OTA_UNSUPPORTED
        else -> Decision.ELIGIBLE
    }
}
