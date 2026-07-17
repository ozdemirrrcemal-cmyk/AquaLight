package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.user.UserPreferencesManager
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveNotificationPreferenceProjectionInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun projectionRefreshAndClearCannotLeakOutgoingOwnerPreference() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val ownerA = "projection-owner-a-$suffix"
        val ownerB = "projection-owner-b-$suffix"
        val ownerStore = OwnerNotificationPreferences.create(context)
        val projection = ActiveNotificationPreferenceProjection.create(context)
        val legacy = UserPreferencesManager.create(context)

        try {
            ownerStore.setEnabled(ownerA, enabled = true, updatedAtMillis = 10L)
            ownerStore.setEnabled(ownerB, enabled = false, updatedAtMillis = 20L)

            assertTrue(projection.refreshForOwner(ownerA))
            assertTrue(legacy.notificationsEnabled.first())

            projection.clear()
            assertFalse(legacy.notificationsEnabled.first())

            assertFalse(projection.refreshForOwner(ownerB))
            assertFalse(legacy.notificationsEnabled.first())
        } finally {
            projection.clear()
        }
    }
}
