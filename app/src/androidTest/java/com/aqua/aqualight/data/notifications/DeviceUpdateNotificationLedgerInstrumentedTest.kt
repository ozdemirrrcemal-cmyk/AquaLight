package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUpdateNotificationLedgerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun availabilityDeliveryIsDurablePerOwnerDeviceAndTargetVersion() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val owner = "owner-$suffix"
        val device = "device-$suffix"
        val ledger = DeviceUpdateNotificationLedger.create(context)

        try {
            assertTrue(ledger.shouldDeliverAvailability(owner, device, "1.2.0"))
            ledger.markDelivered(
                ownerUid = owner,
                deviceUid = device,
                targetVersion = "1.2.0",
                deliveryKey = "available:1.2.0"
            )

            assertFalse(ledger.shouldDeliverAvailability(owner, device, "1.2.0"))
            assertTrue(ledger.shouldDeliverAvailability(owner, device, "1.3.0"))
            assertTrue(device in ledger.recordedDeviceUids(owner))

            ledger.markResolved(owner, device, "1.2.0")
            assertFalse(ledger.shouldDeliverAvailability(owner, device, "1.2.0"))
            assertTrue(ledger.shouldDeliverAvailability(owner, device, "1.3.0"))

            ledger.clearDevice(owner, device)
            assertTrue(ledger.shouldDeliverAvailability(owner, device, "1.2.0"))
        } finally {
            ledger.clearOwner(owner)
        }
    }
}
