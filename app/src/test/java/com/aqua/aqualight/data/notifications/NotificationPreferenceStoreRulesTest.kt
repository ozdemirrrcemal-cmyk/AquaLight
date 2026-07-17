package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPreferenceStoreRulesTest {

    @Test
    fun defaultStoreUsesFirstCommercialSchema() {
        val store = NotificationPreferenceStoreRules.defaultStore()

        assertEquals(
            NotificationPreferenceStoreRules.CURRENT_SCHEMA_VERSION,
            store.schemaVersion
        )
        assertEquals(0, store.ownerPreferencesCount)
    }

    @Test
    fun distinctOwnersRemainIsolated() {
        val store = NotificationPreferencesStore.newBuilder()
            .setSchemaVersion(NotificationPreferenceStoreRules.CURRENT_SCHEMA_VERSION)
            .addOwnerPreferences(preference("owner-a", enabled = true, updatedAt = 10L))
            .addOwnerPreferences(preference("owner-b", enabled = false, updatedAt = 20L))
            .build()

        val validated = NotificationPreferenceStoreRules.validateStore(store)

        assertEquals(2, validated.ownerPreferencesCount)
        assertEquals(true, validated.ownerPreferencesList[0].enabled)
        assertEquals(false, validated.ownerPreferencesList[1].enabled)
    }

    @Test(expected = StoreInvariantViolation::class)
    fun duplicateOwnerFailsClosed() {
        NotificationPreferenceStoreRules.validateStore(
            NotificationPreferencesStore.newBuilder()
                .setSchemaVersion(NotificationPreferenceStoreRules.CURRENT_SCHEMA_VERSION)
                .addOwnerPreferences(preference("owner-a", true, 10L))
                .addOwnerPreferences(preference("owner-a", false, 20L))
                .build()
        )
    }

    @Test(expected = StoreInvariantViolation::class)
    fun blankOwnerFailsClosed() {
        NotificationPreferenceStoreRules.validatePreference(
            preference("", true, 10L)
        )
    }

    @Test(expected = StoreInvariantViolation::class)
    fun unsupportedSchemaFailsClosed() {
        NotificationPreferenceStoreRules.validateStore(
            NotificationPreferencesStore.newBuilder()
                .setSchemaVersion(2)
                .build()
        )
    }

    private fun preference(
        ownerUid: String,
        enabled: Boolean,
        updatedAt: Long
    ): OwnerNotificationPreference {
        return OwnerNotificationPreference.newBuilder()
            .setOwnerUid(ownerUid)
            .setEnabled(enabled)
            .setUpdatedAtMillis(updatedAt)
            .build()
    }
}
