package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareReminderScheduleLedgerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ownerLedgersRemainIsolatedIdempotentAndPersistent() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val ownerA = "schedule-owner-a-$suffix"
        val ownerB = "schedule-owner-b-$suffix"
        val ledger = CareReminderScheduleLedger.create(context)

        ledger.markScheduled(ownerA, 11L)
        ledger.markScheduled(ownerA, 11L)
        ledger.markScheduled(ownerA, 12L)
        ledger.markScheduled(ownerB, 21L)

        assertEquals(setOf(11L, 12L), ledger.taskIds(ownerA))
        assertEquals(setOf(21L), ledger.taskIds(ownerB))

        ledger.markCancelled(ownerA, 11L)
        assertEquals(setOf(12L), ledger.taskIds(ownerA))
        assertEquals(setOf(21L), ledger.taskIds(ownerB))

        val recreated = CareReminderScheduleLedger.create(context)
        assertEquals(setOf(12L), recreated.taskIds(ownerA))
        assertEquals(setOf(21L), recreated.taskIds(ownerB))

        recreated.clearOwner(ownerA)
        assertTrue(recreated.taskIds(ownerA).isEmpty())
        assertEquals(setOf(21L), recreated.taskIds(ownerB))
        recreated.clearOwner(ownerB)
    }
}
