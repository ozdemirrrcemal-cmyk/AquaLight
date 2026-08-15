package com.aqua.aqualight.application.devices.dosing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingDisplayNamePolicyTest {

    @Test
    fun `required name is trimmed before acceptance`() {
        val result = DeviceDosingDisplayNamePolicy.validateRequired("\t Trace Elements \r")

        assertEquals(
            "Trace Elements",
            (result as DeviceDosingDisplayNameValidation.Accepted).normalizedValue
        )
    }

    @Test
    fun `Turkish characters use UTF-8 byte length rather than character count`() {
        assertAccepted("ş".repeat(16))
        assertRejected("ş".repeat(17), DeviceDosingDisplayNameRejection.TOO_LONG)
    }

    @Test
    fun `emoji use their complete UTF-8 byte length`() {
        assertAccepted("🧪".repeat(8))
        assertRejected("🧪".repeat(9), DeviceDosingDisplayNameRejection.TOO_LONG)
    }

    @Test
    fun `combining characters are measured without normalization`() {
        val combiningSequence = "e\u0301"

        assertAccepted(combiningSequence.repeat(10) + "ab")
        assertRejected(
            combiningSequence.repeat(10) + "abc",
            DeviceDosingDisplayNameRejection.TOO_LONG
        )
    }

    @Test
    fun `blank and embedded control characters have distinct rejections`() {
        assertRejected(" \n\t ", DeviceDosingDisplayNameRejection.REQUIRED)
        assertRejected("Trace\nElements", DeviceDosingDisplayNameRejection.CONTROL_CHARACTER)
        assertRejected("Trace\u007fElements", DeviceDosingDisplayNameRejection.CONTROL_CHARACTER)
    }

    @Test
    fun `firmware permits C1 bytes and trims ASCII whitespace only`() {
        assertAcceptedValue("Trace\u0085Elements", "Trace\u0085Elements")
        assertAcceptedValue("\u00a0Trace\u00a0", "\u00a0Trace\u00a0")
    }

    private fun assertAccepted(value: String) {
        assertTrue(
            DeviceDosingDisplayNamePolicy.validateRequired(value) is
                DeviceDosingDisplayNameValidation.Accepted
        )
    }

    private fun assertAcceptedValue(value: String, expected: String) {
        val result = DeviceDosingDisplayNamePolicy.validateRequired(value)

        assertEquals(
            expected,
            (result as DeviceDosingDisplayNameValidation.Accepted).normalizedValue
        )
    }

    private fun assertRejected(
        value: String,
        expected: DeviceDosingDisplayNameRejection
    ) {
        val result = DeviceDosingDisplayNamePolicy.validateRequired(value)

        assertEquals(expected, (result as DeviceDosingDisplayNameValidation.Rejected).reason)
    }
}
