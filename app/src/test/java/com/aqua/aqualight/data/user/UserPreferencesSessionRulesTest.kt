package com.aqua.aqualight.data.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesSessionRulesTest {

    @Test
    fun accountSwitchCachesPreviousProfileAndLoadsNextOwnerCache() {
        val current = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setUid(OWNER_A)
            .setIsLoggedIn(true)
            .setEmail("active-a@example.com")
            .setFullName("Active A")
            .addProfileCaches(
                profile(
                    ownerUid = OWNER_B,
                    email = "cached-b@example.com",
                    fullName = "Cached B"
                )
            )
            .build()

        val switched = UserPreferencesSessionRules.activateOwner(
            current = current,
            ownerUid = OWNER_B
        )

        assertEquals(OWNER_B, switched.uid)
        assertTrue(switched.isLoggedIn)
        assertEquals("cached-b@example.com", switched.email)
        assertEquals("Cached B", switched.fullName)
        assertEquals(
            "active-a@example.com",
            switched.profileCachesList.first { cache ->
                cache.ownerUid == OWNER_A
            }.email
        )
        assertEquals(2, switched.profileCachesCount)
    }

    @Test
    fun accountSwitchWithoutNextCacheClearsPreviousActiveProjection() {
        val current = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setUid(OWNER_A)
            .setIsLoggedIn(true)
            .setEmail("private-a@example.com")
            .setFullName("Private A")
            .build()

        val switched = UserPreferencesSessionRules.activateOwner(
            current = current,
            ownerUid = OWNER_B
        )

        assertEquals(OWNER_B, switched.uid)
        assertTrue(switched.isLoggedIn)
        assertTrue(switched.email.isBlank())
        assertTrue(switched.fullName.isBlank())
        assertEquals(
            "private-a@example.com",
            switched.profileCachesList.single().email
        )
    }

    @Test
    fun logoutCachesActiveProfileAndClearsSessionProjection() {
        val current = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setUid(OWNER_A)
            .setIsLoggedIn(true)
            .setEmail("owner-a@example.com")
            .setFullName("Owner A")
            .build()

        val loggedOut = UserPreferencesSessionRules.deactivateOwner(current)

        assertFalse(loggedOut.isLoggedIn)
        assertTrue(loggedOut.uid.isBlank())
        assertTrue(loggedOut.email.isBlank())
        assertTrue(loggedOut.fullName.isBlank())
        assertEquals(
            "owner-a@example.com",
            loggedOut.profileCachesList.single().email
        )
    }

    @Test
    fun reactivatingSameOwnerKeepsActiveProjection() {
        val current = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setUid(OWNER_A)
            .setIsLoggedIn(true)
            .setEmail("owner-a@example.com")
            .setFullName("Owner A")
            .build()

        val reactivated = UserPreferencesSessionRules.activateOwner(
            current = current,
            ownerUid = OWNER_A
        )

        assertEquals("owner-a@example.com", reactivated.email)
        assertEquals("Owner A", reactivated.fullName)
        assertTrue(reactivated.profileCachesList.isEmpty())
    }

    private fun profile(
        ownerUid: String,
        email: String,
        fullName: String
    ): UserProfileCache = UserProfileCache.newBuilder()
        .setOwnerUid(ownerUid)
        .setEmail(email)
        .setFullName(fullName)
        .build()

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
