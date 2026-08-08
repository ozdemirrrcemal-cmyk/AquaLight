package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaAvailabilityFailureMapperTest {

    @Test
    fun `manifest service failure preserves release server reason`() {
        val failure = DeviceOtaFailureMapper.availability(
            DeviceFirmwareManifestHttpException(503)
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_SERVER_UNAVAILABLE, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(503, failure.httpStatus)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, failure.stage)
    }

    @Test
    fun `wrapped manifest rate limit preserves retryable release reason`() {
        val failure = DeviceOtaFailureMapper.availability(
            IllegalStateException(
                "Manifest lookup failed.",
                DeviceFirmwareManifestHttpException(429)
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_RATE_LIMITED, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(429, failure.httpStatus)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, failure.stage)
    }

    @Test
    fun `manifest access denial remains terminal release failure`() {
        val failure = DeviceOtaFailureMapper.availability(
            DeviceFirmwareManifestHttpException(403)
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_ACCESS_DENIED, failure.reason)
        assertFalse(failure.recoverable)
        assertEquals(403, failure.httpStatus)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, failure.stage)
    }

    @Test
    fun `transport failure remains recoverable connection failure`() {
        val failure = DeviceOtaFailureMapper.availability(IOException("offline"))

        assertEquals(DeviceOtaFailureReason.CONNECTION, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, failure.stage)
    }

    @Test
    fun `non transport validation failure remains generic check failure`() {
        val failure = DeviceOtaFailureMapper.availability(
            IllegalArgumentException("invalid signed manifest")
        )

        assertEquals(DeviceOtaFailureReason.CHECK_FAILED, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, failure.stage)
    }
}
