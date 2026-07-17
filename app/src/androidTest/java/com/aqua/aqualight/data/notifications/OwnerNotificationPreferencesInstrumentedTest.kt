package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerNotificationPreferencesInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = OwnerNotificationPreferences.create(context)

    @Test
    fun ownerPreferencesRemainIsolatedAndSurviveStoreRecreation() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val ownerA = "notification-owner-a-$suffix"
        val ownerB = "notification-owner-b-$suffix"

        store.setEnabled(ownerA, enabled = true, updatedAtMillis = 10L)
        store.setEnabled(ownerB, enabled = false, updatedAtMillis = 20L)

        assertTrue(store.enabledFlow(ownerA).first())
        assertFalse(store.enabledFlow(ownerB).first())
        assertFalse(store.enabledFlow("notification-owner-missing-$suffix").first())

        val recreated = OwnerNotificationPreferences.create(context)
        assertTrue(recreated.isEnabled(ownerA))
        assertFalse(recreated.isEnabled(ownerB))
        assertTrue(recreated.snapshotForOwner(ownerA)?.enabled == true)
        assertTrue(recreated.snapshotForOwner(ownerB)?.enabled == false)
    }
}
