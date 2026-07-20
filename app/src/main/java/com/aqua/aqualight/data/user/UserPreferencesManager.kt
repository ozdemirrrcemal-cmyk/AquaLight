package com.aqua.aqualight.data.user

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserPreferencesManager private constructor(
    private val dataStore: DataStore<UserPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        const val DEFAULT_THEME_MODE = "dark"

        fun create(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(context.applicationContext).also { manager ->
                    INSTANCE = manager
                }
            }
        }

        private fun buildDataStore(appContext: Context): UserPreferencesManager {
            val encryptedSerializer = EncryptedUserPreferencesSerializer(
                context = appContext,
                delegate = UserPreferencesSerializer
            )

            val dataStore = DataStoreFactory.create(
                serializer = encryptedSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler {
                    LocalDataRecoveryTracker.markRecovered(
                        LocalDataRecoveryTracker.Area.USER_PREFERENCES
                    )
                    UserPreferencesStoreRules.defaultPreferences()
                },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { appContext.dataStoreFile("user_prefs.pb") }
            )

            return UserPreferencesManager(dataStore)
        }
    }

    val userPrefsFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferencesStoreRules.validate(preferences)
    }

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { prefs -> prefs.isLoggedIn }
    val uid: Flow<String> = userPrefsFlow.map { prefs -> prefs.uid }

    @Deprecated(
        message = "Use uid instead. This value was never a Firebase ID token.",
        replaceWith = ReplaceWith("uid")
    )
    val idToken: Flow<String> = uid

    val email: Flow<String> = userPrefsFlow.map { prefs -> prefs.email }
    val username: Flow<String> = userPrefsFlow.map { prefs -> prefs.username }
    val fullName: Flow<String> = userPrefsFlow.map { prefs -> prefs.fullName }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { prefs -> prefs.profilePhotoUrl }
    val themeMode: Flow<String> = userPrefsFlow.map { prefs -> prefs.themeMode }
    val languageCode: Flow<String> = userPrefsFlow.map { prefs -> prefs.languageCode }
    val autoUpdateEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.autoUpdateEnabled
    }
    val loginAlertsEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.loginAlertsEnabled
    }
    val twoFactorEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.twoFactorEnabled
    }
    val firstName: Flow<String> = userPrefsFlow.map { prefs -> prefs.firstName }
    val lastName: Flow<String> = userPrefsFlow.map { prefs -> prefs.lastName }
    val city: Flow<String> = userPrefsFlow.map { prefs -> prefs.city }
    val addressLine: Flow<String> = userPrefsFlow.map { prefs -> prefs.addressLine }
    val postCode: Flow<String> = userPrefsFlow.map { prefs -> prefs.postCode }
    val phoneNumber: Flow<String> = userPrefsFlow.map { prefs -> prefs.phoneNumber }
    val country: Flow<String> = userPrefsFlow.map { prefs -> prefs.country }

    data class UsageAnalytics(
        val weeklyAutomationCount: Int,
        val weeklyAlertCount: Int,
        val todayAutomationCount: Int,
        val todayManualActionCount: Int,
        val lastEventTimeMillis: Long,
        val lastEventDescription: String
    )

    val usageAnalyticsFlow: Flow<UsageAnalytics> = userPrefsFlow.map { prefs ->
        UsageAnalytics(
            weeklyAutomationCount = prefs.weeklyAutomationCount,
            weeklyAlertCount = prefs.weeklyAlertCount,
            todayAutomationCount = prefs.todayAutomationCount,
            todayManualActionCount = prefs.todayManualActionCount,
            lastEventTimeMillis = prefs.lastEventTimeMillis,
            lastEventDescription = prefs.lastEventDescription
        )
    }

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        updateValidated(transform)
    }

    suspend fun saveUserSession(uid: String, isLoggedIn: Boolean) {
        val normalizedUid = UserDataScope.normalizeOwnerUid(uid)

        updateValidated { prefs ->
            if (isLoggedIn) {
                require(normalizedUid.isNotBlank()) {
                    "Authenticated user preferences require a non-blank uid."
                }
                prefs.toBuilder()
                    .setUid(normalizedUid)
                    .setIsLoggedIn(true)
                    .build()
            } else {
                prefs.toBuilder()
                    .clearUid()
                    .setIsLoggedIn(false)
                    .clearActiveProfile()
                    .build()
            }
        }
    }

    suspend fun restoreProfileForLogin(
        ownerUid: String,
        email: String,
        fullName: String = "",
        photoUrl: String = ""
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        updateValidated { prefs ->
            val source = prefs.profileCacheFor(normalizedOwnerUid)
            val restoredProfile = UserProfileCache.newBuilder()
                .setOwnerUid(normalizedOwnerUid)
                .setEmail(email.ifBlank { source?.email.orEmpty() })
                .setUsername(source?.username.orEmpty())
                .setFullName(source?.fullName?.ifBlank { fullName } ?: fullName)
                .setProfilePhotoUrl(
                    source?.profilePhotoUrl?.ifBlank { photoUrl } ?: photoUrl
                )
                .setFirstName(source?.firstName.orEmpty())
                .setLastName(source?.lastName.orEmpty())
                .setCity(source?.city.orEmpty())
                .setAddressLine(source?.addressLine.orEmpty())
                .setPostCode(source?.postCode.orEmpty())
                .setPhoneNumber(source?.phoneNumber.orEmpty())
                .setCountry(source?.country.orEmpty())
                .build()

            prefs.toBuilder()
                .applyActiveProfile(restoredProfile)
                .replaceProfileCache(restoredProfile)
                .build()
        }
    }

    suspend fun replaceProfile(
        email: String,
        username: String = "",
        fullName: String = "",
        photoUrl: String = ""
    ) {
        updateValidated { prefs ->
            val builder = prefs.toBuilder()
                .setEmail(email)
                .setUsername(username)
                .setFullName(fullName)
                .setProfilePhotoUrl(photoUrl)

            val ownerUid = activeOwnerUidOrNull(prefs)
            if (ownerUid != null) {
                builder.replaceProfileCache(builder.build().toActiveProfileCache(ownerUid))
            }
            builder.build()
        }
    }

    suspend fun patchProfile(
        email: String? = null,
        username: String? = null,
        fullName: String? = null,
        photoUrl: String? = null
    ) {
        updateValidated { prefs ->
            val builder = prefs.toBuilder()
            email?.let(builder::setEmail)
            username?.let(builder::setUsername)
            fullName?.let(builder::setFullName)
            photoUrl?.let(builder::setProfilePhotoUrl)

            val ownerUid = activeOwnerUidOrNull(prefs)
            if (ownerUid != null) {
                builder.replaceProfileCache(builder.build().toActiveProfileCache(ownerUid))
            }
            builder.build()
        }
    }

    @Deprecated(
        message = "Use replaceProfile for login/session replacement or patchProfile for profile edits."
    )
    suspend fun saveProfile(
        email: String?,
        username: String?,
        fullName: String?,
        photoUrl: String?
    ) {
        patchProfile(
            email = email,
            username = username,
            fullName = fullName,
            photoUrl = photoUrl
        )
    }

    suspend fun updateUsername(username: String) {
        patchProfile(username = username)
    }

    suspend fun updateProfilePhoto(photoUrl: String) {
        patchProfile(photoUrl = photoUrl)
    }

    suspend fun logout() {
        updateValidated { prefs ->
            val builder = prefs.toBuilder()
            val ownerUid = activeOwnerUidOrNull(prefs)

            if (ownerUid != null && prefs.hasActiveProfileData()) {
                builder.replaceProfileCache(prefs.toActiveProfileCache(ownerUid))
            }

            builder
                .clearUid()
                .setIsLoggedIn(false)
                .clearActiveProfile()
                .build()
        }
    }

    suspend fun updateThemeMode(mode: String) {
        updateValidated { prefs ->
            prefs.toBuilder().setThemeMode(mode).build()
        }
    }

    suspend fun updateLanguage(code: String) {
        updateValidated { prefs ->
            prefs.toBuilder().setLanguageCode(code).build()
        }
    }

    suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        updateValidated { prefs ->
            prefs.toBuilder().setAutoUpdateEnabled(enabled).build()
        }
    }

    suspend fun updateLoginAlertsEnabled(enabled: Boolean) {
        updateValidated { prefs ->
            prefs.toBuilder().setLoginAlertsEnabled(enabled).build()
        }
    }

    suspend fun updateTwoFactorEnabled(enabled: Boolean) {
        updateValidated { prefs ->
            prefs.toBuilder().setTwoFactorEnabled(enabled).build()
        }
    }

    suspend fun logUsageEvent(
        isManual: Boolean,
        isAlert: Boolean,
        description: String
    ) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val dayKey = today.toString()
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekOfYear = today.get(weekFields.weekOfWeekBasedYear())
        val weekKey = "${today.year}-W$weekOfYear"

        updateValidated { prefs ->
            val isNewDay = prefs.lastUsageDayKey != dayKey
            val isNewWeek = prefs.lastUsageWeekKey != weekKey
            val weeklyAutomationCount = if (isNewWeek) 0 else prefs.weeklyAutomationCount
            val weeklyAlertCount = if (isNewWeek) 0 else prefs.weeklyAlertCount
            val todayAutomationCount = if (isNewDay) 0 else prefs.todayAutomationCount
            val todayManualActionCount = if (isNewDay) 0 else prefs.todayManualActionCount

            prefs.toBuilder()
                .setWeeklyAutomationCount(weeklyAutomationCount + 1)
                .setWeeklyAlertCount(weeklyAlertCount + if (isAlert) 1 else 0)
                .setTodayAutomationCount(
                    todayAutomationCount + if (isManual) 0 else 1
                )
                .setTodayManualActionCount(
                    todayManualActionCount + if (isManual) 1 else 0
                )
                .setLastEventTimeMillis(now)
                .setLastEventDescription(description)
                .setLastUsageDayKey(dayKey)
                .setLastUsageWeekKey(weekKey)
                .build()
        }
    }

    suspend fun saveAddress(
        firstName: String,
        lastName: String,
        city: String,
        addressLine: String,
        postCode: String,
        phoneNumber: String,
        country: String
    ) {
        val fullName = listOf(firstName, lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")

        updateValidated { prefs ->
            val builder = prefs.toBuilder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .setCity(city)
                .setAddressLine(addressLine)
                .setPostCode(postCode)
                .setPhoneNumber(phoneNumber)
                .setCountry(country)
                .setFullName(fullName)

            val ownerUid = activeOwnerUidOrNull(prefs)
            if (ownerUid != null) {
                builder.replaceProfileCache(builder.build().toActiveProfileCache(ownerUid))
            }
            builder.build()
        }
    }

    suspend fun profilePhotoUrlForOwner(ownerUid: String): String {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val prefs = userPrefsFlow.first()

        if (prefs.uid == normalizedOwnerUid) {
            return prefs.profilePhotoUrl
        }

        return prefs.profileCacheFor(normalizedOwnerUid)
            ?.profilePhotoUrl
            .orEmpty()
    }

    suspend fun clearUserDataForOwner(ownerUid: String) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)

        updateValidated { prefs ->
            val builder = prefs.toBuilder().removeProfileCache(normalizedOwnerUid)
            if (prefs.uid == normalizedOwnerUid) {
                builder
                    .clearUid()
                    .setIsLoggedIn(false)
                    .clearActiveProfile()
            }
            builder.build()
        }
    }

    suspend fun clearAllUserData() {
        updateValidated { UserPreferencesStoreRules.defaultPreferences() }
    }

    private suspend fun updateValidated(
        transform: (UserPreferences) -> UserPreferences
    ) {
        dataStore.updateData { current ->
            UserPreferencesStoreRules.validate(current)
            UserPreferencesStoreRules.validate(transform(current))
        }
    }

    private fun activeOwnerUidOrNull(preferences: UserPreferences): String? {
        return preferences.uid.takeIf { uid ->
            preferences.isLoggedIn && uid.isNotBlank()
        }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        val normalized = UserDataScope.normalizeOwnerUid(ownerUid)
        require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        return normalized
    }

    private fun UserPreferences.profileCacheFor(ownerUid: String): UserProfileCache? {
        return profileCachesList.firstOrNull { cache -> cache.ownerUid == ownerUid }
    }

    private fun UserPreferences.hasActiveProfileData(): Boolean {
        return listOf(
            email,
            username,
            fullName,
            profilePhotoUrl,
            firstName,
            lastName,
            city,
            addressLine,
            postCode,
            phoneNumber,
            country
        ).any(String::isNotBlank)
    }

    private fun UserPreferences.toActiveProfileCache(ownerUid: String): UserProfileCache {
        return UserProfileCache.newBuilder()
            .setOwnerUid(ownerUid)
            .setEmail(email)
            .setUsername(username)
            .setFullName(fullName)
            .setProfilePhotoUrl(profilePhotoUrl)
            .setFirstName(firstName)
            .setLastName(lastName)
            .setCity(city)
            .setAddressLine(addressLine)
            .setPostCode(postCode)
            .setPhoneNumber(phoneNumber)
            .setCountry(country)
            .build()
    }

    private fun UserPreferences.Builder.applyActiveProfile(
        profile: UserProfileCache
    ): UserPreferences.Builder {
        return setEmail(profile.email)
            .setUsername(profile.username)
            .setFullName(profile.fullName)
            .setProfilePhotoUrl(profile.profilePhotoUrl)
            .setFirstName(profile.firstName)
            .setLastName(profile.lastName)
            .setCity(profile.city)
            .setAddressLine(profile.addressLine)
            .setPostCode(profile.postCode)
            .setPhoneNumber(profile.phoneNumber)
            .setCountry(profile.country)
    }

    private fun UserPreferences.Builder.clearActiveProfile(): UserPreferences.Builder {
        return clearEmail()
            .clearUsername()
            .clearFullName()
            .clearProfilePhotoUrl()
            .clearFirstName()
            .clearLastName()
            .clearCity()
            .clearAddressLine()
            .clearPostCode()
            .clearPhoneNumber()
            .clearCountry()
    }

    private fun UserPreferences.Builder.replaceProfileCache(
        profile: UserProfileCache
    ): UserPreferences.Builder {
        val ownerUid = requireOwnerUid(profile.ownerUid)
        return removeProfileCache(ownerUid).addProfileCaches(profile)
    }

    private fun UserPreferences.Builder.removeProfileCache(
        ownerUid: String
    ): UserPreferences.Builder {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        val retainedProfiles = profileCachesList.filterNot { cache ->
            cache.ownerUid == normalizedOwnerUid
        }

        clearProfileCaches()
        addAllProfileCaches(retainedProfiles)
        return this
    }
}
