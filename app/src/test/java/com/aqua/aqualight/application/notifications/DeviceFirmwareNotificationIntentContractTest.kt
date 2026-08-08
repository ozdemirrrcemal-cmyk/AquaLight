package com.aqua.aqualight.application.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceFirmwareNotificationIntentContractTest {

    @Test
    fun knownKindsRoundTripThroughStableWireName() {
        DeviceFirmwareNotificationKind.entries.forEach { kind ->
            assertEquals(
                kind,
                DeviceFirmwareNotificationIntentContract.parseKind(kind.name)
            )
        }
    }

    @Test
    fun missingOrUnknownKindFailsClosed() {
        assertNull(DeviceFirmwareNotificationIntentContract.parseKind(null))
        assertNull(DeviceFirmwareNotificationIntentContract.parseKind(""))
        assertNull(DeviceFirmwareNotificationIntentContract.parseKind("legacy"))
    }
}
