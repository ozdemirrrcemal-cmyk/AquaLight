package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUpdateNotificationLedgerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun targetVersionIsAnnouncedOnlyAfterExplicitMark() = runBlocking {
        val ledger = DeviceUpdateNotificationLedger.create(context)
        val ownerUid = "ledger-owner-target-${UUID.randomUUID()}"
        val deviceUid = "ledger-device-target"
        ledger.clearOwner(ownerUid)

        assertFalse(ledger.isAnnounced(ownerUid, deviceUid, "1.5.0"))
        ledger.markAnnounced(ownerUid, deviceUid, "1.5.0")

        val recreated = DeviceUpdateNotificationLedger.create(context)
        assertTrue(recreated.isAnnounced(ownerUid, deviceUid, "1.5.0"))
        assertFalse(recreated.isAnnounced(ownerUid, deviceUid, "1.6.0"))
        recreated.clearOwner(ownerUid)
    }

    @Test
    fun everyAnnouncedTargetRemainsDeduplicatedAcrossRecreation() = runBlocking {
        val ledger = DeviceUpdateNotificationLedger.create(context)
        val ownerUid = "ledger-owner-history-${UUID.randomUUID()}"
        val deviceUid = "ledger-device-history"
        ledger.clearOwner(ownerUid)

        ledger.markAnnounced(ownerUid, deviceUid, "1.5.0")
        ledger.markAnnounced(ownerUid, deviceUid, "1.6.0")

        val recreated = DeviceUpdateNotificationLedger.create(context)
        assertTrue(recreated.isAnnounced(ownerUid, deviceUid, "1.5.0"))
        assertTrue(recreated.isAnnounced(ownerUid, deviceUid, "1.6.0"))
        recreated.clearDevice(ownerUid, deviceUid)
        assertFalse(recreated.isAnnounced(ownerUid, deviceUid, "1.5.0"))
        assertFalse(recreated.isAnnounced(ownerUid, deviceUid, "1.6.0"))
        recreated.clearOwner(ownerUid)
    }

    @Test
    fun trackedDevicesRemainOwnerIsolatedAndClearable() = runBlocking {
        val ledger = DeviceUpdateNotificationLedger.create(context)
        val suffix = UUID.randomUUID().toString()
        val ownerUid = "ledger-owner-devices-$suffix"
        val otherOwnerUid = "ledger-owner-other-$suffix"
        ledger.clearOwner(ownerUid)
        ledger.clearOwner(otherOwnerUid)
        ledger.markAnnounced(ownerUid, "device-a", "2.0.0")
        ledger.markAnnounced(ownerUid, "device-a", "2.1.0")
        ledger.markAnnounced(ownerUid, "device-b", "2.0.0")
        ledger.markAnnounced(otherOwnerUid, "device-c", "3.0.0")

        assertEquals(setOf("device-a", "device-b"), ledger.trackedDeviceUids(ownerUid))
        assertEquals(setOf("device-c"), ledger.trackedDeviceUids(otherOwnerUid))
        ledger.clearDevice(ownerUid, "device-a")
        assertEquals(setOf("device-b"), ledger.trackedDeviceUids(ownerUid))

        ledger.clearOwner(ownerUid)
        assertTrue(ledger.trackedDeviceUids(ownerUid).isEmpty())
        assertEquals(setOf("device-c"), ledger.trackedDeviceUids(otherOwnerUid))
        ledger.clearOwner(otherOwnerUid)
    }
}
