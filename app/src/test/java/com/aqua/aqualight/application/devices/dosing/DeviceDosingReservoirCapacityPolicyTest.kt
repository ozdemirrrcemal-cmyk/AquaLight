package com.aqua.aqualight.application.devices.dosing

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingReservoirCapacityPolicyTest {

    @Test
    fun `US and Turkish decimal input produce the same exact intent`() {
        assertAccepted("450.125", Locale.US, 450_125L)
        assertAccepted("450,125", Locale.forLanguageTag("tr-TR"), 450_125L)
    }

    @Test
    fun `localized digits and decimal separator are parsed without Double`() {
        assertAccepted("١٢٣٫٤٥٦", Locale.forLanguageTag("ar"), 123_456L)
    }

    @Test
    fun `locale grouping separators are rejected instead of reinterpreted as decimals`() {
        assertRejected(
            "1,234",
            Locale.US,
            DeviceDosingReservoirCapacityRejection.INVALID_NUMBER
        )
        assertRejected(
            "1.234",
            Locale.forLanguageTag("tr-TR"),
            DeviceDosingReservoirCapacityRejection.INVALID_NUMBER
        )
    }

    @Test
    fun `trailing zeros do not create unsupported precision`() {
        assertAccepted("1.2300", Locale.US, 1_230L)
    }

    @Test
    fun `sub quantum precision is rejected semantically`() {
        assertRejected(
            "1.0001",
            Locale.US,
            DeviceDosingReservoirCapacityRejection.UNSUPPORTED_PRECISION
        )
    }

    @Test
    fun `unsigned firmware range accepts its exact maximum only`() {
        assertAccepted("4294967.295", Locale.US, 4_294_967_295L)
        assertRejected(
            "4294967.296",
            Locale.US,
            DeviceDosingReservoirCapacityRejection.OUT_OF_RANGE
        )
    }

    @Test
    fun `required invalid and non positive input have distinct results`() {
        assertRejected("  ", Locale.US, DeviceDosingReservoirCapacityRejection.REQUIRED)
        assertRejected("1.2.3", Locale.US, DeviceDosingReservoirCapacityRejection.INVALID_NUMBER)
        assertRejected("0", Locale.US, DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED)
        assertRejected("-2", Locale.US, DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED)
    }

    @Test
    fun `oversized raw input is rejected before decimal parsing`() {
        assertRejected(
            "1".repeat(1_000),
            Locale.US,
            DeviceDosingReservoirCapacityRejection.INVALID_NUMBER
        )
    }

    @Test
    fun `exact intent formats with the active locale`() {
        assertEquals(
            "450,125",
            DeviceDosingReservoirCapacityPolicy.format(
                450_125L,
                Locale.forLanguageTag("tr-TR")
            )
        )
    }

    private fun assertAccepted(rawValue: String, locale: Locale, expectedMicroliters: Long) {
        val result = DeviceDosingReservoirCapacityPolicy.validate(rawValue, locale)

        assertEquals(
            expectedMicroliters,
            (result as DeviceDosingReservoirCapacityValidation.Accepted).capacityMicroliters
        )
    }

    private fun assertRejected(
        rawValue: String,
        locale: Locale,
        expected: DeviceDosingReservoirCapacityRejection
    ) {
        val result = DeviceDosingReservoirCapacityPolicy.validate(rawValue, locale)

        assertEquals(expected, (result as DeviceDosingReservoirCapacityValidation.Rejected).reason)
    }
}
