package com.aqua.aqualight.data.user

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.localization.SupportedLocaleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserPreferencesStoreRulesTest {

    @Test
    fun commercialDefaultIsVersionedAndValid() {
        val preferences = UserPreferencesStoreRules.defaultPreferences()

        assertEquals(
            CommercialStoreSchema.USER_PREFERENCES_VERSION,
            preferences.schemaVersion
        )
        assertEquals(
            SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
            preferences.languageCode
        )
        assertEquals(
            preferences,
            UserPreferencesStoreRules.validate(preferences)
        )
    }

    @Test
    fun loggedInPreferencesRequireCanonicalUid() {
        val invalid = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setIsLoggedIn(true)
            .setUid(" ")
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            UserPreferencesStoreRules.validate(invalid)
        }
    }

    @Test
    fun duplicateOwnerProfileCacheIsRejected() {
        val first = profile(ownerUid = "owner-a", email = "first@example.com")
        val second = profile(ownerUid = "owner-a", email = "second@example.com")
        val invalid = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .addProfileCaches(first)
            .addProfileCaches(second)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            UserPreferencesStoreRules.validate(invalid)
        }
    }

    @Test
    fun negativeUsageCountersAndUnsupportedSettingsAreRejected() {
        val negativeCounter = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setWeeklyAlertCount(-1)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            UserPreferencesStoreRules.validate(negativeCounter)
        }

        val unsupportedTheme = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setThemeMode("unknown-theme")
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            UserPreferencesStoreRules.validate(unsupportedTheme)
        }
    }

    @Test
    fun blankOrNonCanonicalThemeAndLanguageValuesAreRejected() {
        listOf("", " ", " dark ").forEach { invalidTheme ->
            val preferences = UserPreferencesStoreRules.defaultPreferences()
                .toBuilder()
                .setThemeMode(invalidTheme)
                .build()

            assertThrows(StoreInvariantViolation::class.java) {
                UserPreferencesStoreRules.validate(preferences)
            }
        }

        listOf("", " ", " en ", "EN").forEach { invalidLanguage ->
            val preferences = UserPreferencesStoreRules.defaultPreferences()
                .toBuilder()
                .setLanguageCode(invalidLanguage)
                .build()

            assertThrows(StoreInvariantViolation::class.java) {
                UserPreferencesStoreRules.validate(preferences)
            }
        }
    }

    @Test
    fun plannedAndRemovedLocalesCannotBePersisted() {
        listOf("tr", "de", "fr", "ru", "zh").forEach { unavailableLanguage ->
            val preferences = UserPreferencesStoreRules.defaultPreferences()
                .toBuilder()
                .setLanguageCode(unavailableLanguage)
                .build()

            assertThrows(StoreInvariantViolation::class.java) {
                UserPreferencesStoreRules.validate(preferences)
            }
        }
    }

    private fun profile(
        ownerUid: String,
        email: String
    ): UserProfileCache = UserProfileCache.newBuilder()
        .setOwnerUid(ownerUid)
        .setEmail(email)
        .setUsername("user")
        .setFullName("Commercial User")
        .build()
}
