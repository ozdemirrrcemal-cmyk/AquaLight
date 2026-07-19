package com.aqua.aqualight.data.user

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.localization.SupportedLocaleRegistry

/** Authoritative invariant rules for encrypted user preferences. */
object UserPreferencesStoreRules {

    private const val MAX_UID_CHARS = 128
    private const val MAX_EMAIL_CHARS = 254
    private const val MAX_NAME_CHARS = 160
    private const val MAX_URI_CHARS = 2_048
    private const val MAX_ADDRESS_CHARS = 240
    private const val MAX_PHONE_CHARS = 40
    private const val MAX_EVENT_DESCRIPTION_CHARS = 500
    private const val MIN_DATE_MILLIS = 946_684_800_000L // 2000-01-01 UTC
    private const val MAX_DATE_MILLIS = 4_102_444_800_000L // 2100-01-01 UTC

    private val allowedThemeModes = setOf("dark", "light", "system")
    private val allowedLanguageTags = SupportedLocaleRegistry.publishedLocales
        .mapTo(linkedSetOf()) { locale -> locale.languageTag }

    fun defaultPreferences(): UserPreferences = UserPreferences.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.USER_PREFERENCES_VERSION)
        .setThemeMode(UserPreferencesManager.DEFAULT_THEME_MODE)
        .setLanguageCode(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)
        .build()

    fun validate(preferences: UserPreferences): UserPreferences {
        CommercialStoreSchema.requireCurrent(
            storeName = "UserPreferences",
            actualVersion = preferences.schemaVersion,
            expectedVersion = CommercialStoreSchema.USER_PREFERENCES_VERSION
        )

        val activeUid = preferences.uid
        if (preferences.isLoggedIn) {
            canonicalUid(activeUid)
        } else if (activeUid.isNotBlank()) {
            violation("Logged-out preferences must not retain an active uid.")
        }

        requireTextLength("email", preferences.email, MAX_EMAIL_CHARS)
        requireTextLength("username", preferences.username, MAX_NAME_CHARS)
        requireTextLength("fullName", preferences.fullName, MAX_NAME_CHARS)
        requireTextLength("profilePhotoUrl", preferences.profilePhotoUrl, MAX_URI_CHARS)
        requireTextLength("firstName", preferences.firstName, MAX_NAME_CHARS)
        requireTextLength("lastName", preferences.lastName, MAX_NAME_CHARS)
        requireTextLength("city", preferences.city, MAX_ADDRESS_CHARS)
        requireTextLength("addressLine", preferences.addressLine, MAX_ADDRESS_CHARS)
        requireTextLength("postCode", preferences.postCode, 32)
        requireTextLength("phoneNumber", preferences.phoneNumber, MAX_PHONE_CHARS)
        requireTextLength("country", preferences.country, MAX_ADDRESS_CHARS)

        val theme = requireCanonicalRequiredSetting(
            field = "themeMode",
            value = preferences.themeMode
        )
        if (theme !in allowedThemeModes) {
            violation("themeMode contains an unsupported value.")
        }

        val language = requireCanonicalRequiredSetting(
            field = "languageCode",
            value = preferences.languageCode
        )
        if (language !in allowedLanguageTags) {
            violation("languageCode is not a published AquaLight locale.")
        }

        requireNonNegative("weeklyAutomationCount", preferences.weeklyAutomationCount)
        requireNonNegative("weeklyAlertCount", preferences.weeklyAlertCount)
        requireNonNegative("todayAutomationCount", preferences.todayAutomationCount)
        requireNonNegative("todayManualActionCount", preferences.todayManualActionCount)
        requireOptionalDate("lastEventTimeMillis", preferences.lastEventTimeMillis)
        requireTextLength(
            "lastEventDescription",
            preferences.lastEventDescription,
            MAX_EVENT_DESCRIPTION_CHARS
        )

        val owners = mutableSetOf<String>()
        preferences.profileCachesList.forEach { cache ->
            val ownerUid = canonicalUid(cache.ownerUid)
            if (!owners.add(ownerUid)) {
                violation("Duplicate profile cache for owner $ownerUid.")
            }
            validateProfileCache(cache)
        }

        return preferences
    }

    private fun validateProfileCache(cache: UserProfileCache) {
        requireTextLength("profile.email", cache.email, MAX_EMAIL_CHARS)
        requireTextLength("profile.username", cache.username, MAX_NAME_CHARS)
        requireTextLength("profile.fullName", cache.fullName, MAX_NAME_CHARS)
        requireTextLength("profile.profilePhotoUrl", cache.profilePhotoUrl, MAX_URI_CHARS)
        requireTextLength("profile.firstName", cache.firstName, MAX_NAME_CHARS)
        requireTextLength("profile.lastName", cache.lastName, MAX_NAME_CHARS)
        requireTextLength("profile.city", cache.city, MAX_ADDRESS_CHARS)
        requireTextLength("profile.addressLine", cache.addressLine, MAX_ADDRESS_CHARS)
        requireTextLength("profile.postCode", cache.postCode, 32)
        requireTextLength("profile.phoneNumber", cache.phoneNumber, MAX_PHONE_CHARS)
        requireTextLength("profile.country", cache.country, MAX_ADDRESS_CHARS)
    }

    private fun canonicalUid(value: String): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("uid must be non-blank and canonical.")
        }
        if (canonical.length > MAX_UID_CHARS) {
            violation("uid exceeds $MAX_UID_CHARS characters.")
        }
        return canonical
    }

    private fun requireCanonicalRequiredSetting(
        field: String,
        value: String
    ): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("$field must be non-blank and canonical.")
        }
        return canonical
    }

    private fun requireNonNegative(field: String, value: Int) {
        if (value < 0) {
            violation("$field must not be negative.")
        }
    }

    private fun requireTextLength(field: String, value: String, maxChars: Int) {
        if (value.length > maxChars) {
            violation("$field exceeds $maxChars characters.")
        }
    }

    private fun requireOptionalDate(field: String, value: Long) {
        if (value != 0L && value !in MIN_DATE_MILLIS..MAX_DATE_MILLIS) {
            violation("$field is outside the supported commercial date range.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}
