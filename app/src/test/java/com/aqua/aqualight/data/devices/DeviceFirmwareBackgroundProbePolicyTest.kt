package com.aqua.aqualight.data.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFirmwareBackgroundProbePolicyTest {

    @Test
    fun staleDiscoveryNeverAllowsBackgroundOtaCommand() {
        assertEquals(
            DeviceFirmwareBackgroundProbePolicy.Decision.NOT_LIVE,
            DeviceFirmwareBackgroundProbePolicy.decide(
                freshlyDiscovered = false,
                hasValidatedRuntimeMetadata = true,
                supportsOta = true
            )
        )
    }

    @Test
    fun freshDiscoveryStillRequiresAuthenticatedRuntimeMetadata() {
        assertEquals(
            DeviceFirmwareBackgroundProbePolicy.Decision.METADATA_UNVALIDATED,
            DeviceFirmwareBackgroundProbePolicy.decide(
                freshlyDiscovered = true,
                hasValidatedRuntimeMetadata = false,
                supportsOta = true
            )
        )
    }

    @Test
    fun validatedNonOtaDeviceIsSkipped() {
        assertEquals(
            DeviceFirmwareBackgroundProbePolicy.Decision.OTA_UNSUPPORTED,
            DeviceFirmwareBackgroundProbePolicy.decide(
                freshlyDiscovered = true,
                hasValidatedRuntimeMetadata = true,
                supportsOta = false
            )
        )
    }

    @Test
    fun onlyFreshValidatedOtaDeviceIsEligible() {
        assertEquals(
            DeviceFirmwareBackgroundProbePolicy.Decision.ELIGIBLE,
            DeviceFirmwareBackgroundProbePolicy.decide(
                freshlyDiscovered = true,
                hasValidatedRuntimeMetadata = true,
                supportsOta = true
            )
        )
    }
}
