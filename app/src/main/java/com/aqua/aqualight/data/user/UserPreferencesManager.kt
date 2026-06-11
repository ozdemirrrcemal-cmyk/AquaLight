package com.aqua.aqualight.data.user

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserPreferencesManager private constructor(
    private val dataStore: DataStore<UserPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        const val DEFAULT_THEME_MODE = "dark"
        const val DEFAULT_LANGUAGE_CODE = "en"

        fun create(
            context: Context
        ): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(
                    appContext = context.applicationContext
                ).also { manager ->
                    INSTANCE = manager
                }
            }
        }

        private fun buildDataStore(
            appContext: Context
        ): UserPreferencesManager {
            val delegate = UserPreferencesSerializer

            val encryptedSerializer = EncryptedUserPreferencesSerializer(
                context = appContext,
                delegate = delegate
            )

            val dataStore = DataStoreFactory.create(
                serializer = encryptedSerializer,
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                ),
                produceFile = {
                    appContext.dataStoreFile("user_prefs.pb")
                }
            )

            return UserPreferencesManager(dataStore)
        }
    }

    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.isLoggedIn
    }

    val uid: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.uid
    }

    @Deprecated(
        message = "Use uid instead. This value was never a Firebase ID token.",
        replaceWith = ReplaceWith("uid")
    )
    val idToken: Flow<String> = uid

    val email: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.email
    }

    val username: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.username
    }

    val fullName: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.fullName
    }

    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.profilePhotoUrl
    }

    val themeMode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.themeMode.ifBlank {
            DEFAULT_THEME_MODE
        }
    }

    val languageCode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.languageCode.ifBlank {
            DEFAULT_LANGUAGE_CODE
        }
    }

    val notificationsEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.notificationsEnabled
    }

    val autoUpdateEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.autoUpdateEnabled
    }

    val loginAlertsEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.loginAlertsEnabled
    }

    val twoFactorEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        prefs.twoFactorEnabled
    }

    val firstName: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.firstName
    }

    val lastName: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.lastName
    }

    val city: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.city
    }

    val addressLine: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.addressLine
    }

    val postCode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.postCode
    }

    val phoneNumber: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.phoneNumber
    }

    val country: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.country
    }

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

    suspend fun update(
        transform: (UserPreferences) -> UserPreferences
    ) {
        dataStore.updateData { current ->
            transform(current)
        }
    }

    suspend fun saveUserSession(
        uid: String,
        isLoggedIn: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setUid(uid)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    suspend fun restoreProfileForLogin(
        ownerUid: String,
        email: String,
        fullName: String = "",
        photoUrl: String = ""
    ) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return
        }

        dataStore.updateData { prefs ->
            val existingCache = prefs.profileCacheFor(
                ownerUid = normalizedOwnerUid
            )
            val legacyActiveProfile = if (
                existingCache == null &&
                UserDataScope.normalizeOwnerUid(prefs.uid) == normalizedOwnerUid &&
                prefs.hasActiveProfileData()
            ) {
                prefs.toActiveProfileCache(
                    ownerUid = normalizedOwnerUid
                )
            } else {
                null
            }
            val source = existingCache ?: legacyActiveProfile
            val restoredProfile = UserProfileCache.newBuilder()
                .setOwnerUid(normalizedOwnerUid)
                .setEmail(
                    email.ifBlank {
                        source?.email.orEmpty()
                    }
                )
                .setUsername(
                    source?.username.orEmpty()
                )
                .setFullName(
                    source?.fullName?.ifBlank {
                        fullName
                    } ?: fullName
                )
                .setProfilePhotoUrl(
                    source?.profilePhotoUrl?.ifBlank {
                        photoUrl
                    } ?: photoUrl
                )
                .setFirstName(
                    source?.firstName.orEmpty()
                )
                .setLastName(
                    source?.lastName.orEmpty()
                )
                .setCity(
                    source?.city.orEmpty()
                )
                .setAddressLine(
                    source?.addressLine.orEmpty()
                )
                .setPostCode(
                    source?.postCode.orEmpty()
                )
                .setPhoneNumber(
                    source?.phoneNumber.orEmpty()
                )
                .setCountry(
                    source?.country.orEmpty()
                )
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
        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()
                .setEmail(email)
                .setUsername(username)
                .setFullName(fullName)
                .setProfilePhotoUrl(photoUrl)

            val ownerUid = UserDataScope.normalizeOwnerUid(
                prefs.uid
            )

            if (ownerUid.isNotBlank()) {
                builder.replaceProfileCache(
                    builder.build().toActiveProfileCache(
                        ownerUid = ownerUid
                    )
                )
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
        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()

            email?.let { value ->
                builder.setEmail(value)
            }

            username?.let { value ->
                builder.setUsername(value)
            }

            fullName?.let { value ->
                builder.setFullName(value)
            }

            photoUrl?.let { value ->
                builder.setProfilePhotoUrl(value)
            }

            val ownerUid = UserDataScope.normalizeOwnerUid(
                prefs.uid
            )

            if (ownerUid.isNotBlank()) {
                builder.replaceProfileCache(
                    builder.build().toActiveProfileCache(
                        ownerUid = ownerUid
                    )
                )
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

    suspend fun updateUsername(
        username: String
    ) {
        patchProfile(
            username = username
        )
    }

    suspend fun updateProfilePhoto(
        photoUrl: String
    ) {
        patchProfile(
            photoUrl = photoUrl
        )
    }

    suspend fun logout() {
        dataStore.updateData { prefs ->
            val ownerUid = UserDataScope.normalizeOwnerUid(
                prefs.uid
            )
            val builder = prefs.toBuilder()

            if (ownerUid.isNotBlank() && prefs.hasActiveProfileData()) {
                builder.replaceProfileCache(
                    prefs.toActiveProfileCache(
                        ownerUid = ownerUid
                    )
                )
            }

            builder
                .clearUid()
                .setIsLoggedIn(false)
                .clearActiveProfile()
                .build()
        }
    }

    suspend fun updateThemeMode(
        mode: String
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setThemeMode(mode)
                .build()
        }
    }

    suspend fun updateLanguage(
        code: String
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setLanguageCode(code)
                .build()
        }
    }

    suspend fun updateNotificationsEnabled(
        enabled: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setNotificationsEnabled(enabled)
                .build()
        }
    }

    suspend fun updateAutoUpdateEnabled(
        enabled: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setAutoUpdateEnabled(enabled)
                .build()
        }
    }

    suspend fun updateLoginAlertsEnabled(
        enabled: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setLoginAlertsEnabled(enabled)
                .build()
        }
    }

    suspend fun updateTwoFactorEnabled(
        enabled: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setTwoFactorEnabled(enabled)
                .build()
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

        dataStore.updateData { prefs ->
            val isNewDay = prefs.lastUsageDayKey != dayKey
            val isNewWeek = prefs.lastUsageWeekKey != weekKey

            val currentWeeklyAutomationCount = if (isNewWeek) {
                0
            } else {
                prefs.weeklyAutomationCount
            }

            val currentWeeklyAlertCount = if (isNewWeek) {
                0
            } else {
                prefs.weeklyAlertCount
            }

            val currentTodayAutomationCount = if (isNewDay) {
                0
            } else {
                prefs.todayAutomationCount
            }

            val currentTodayManualActionCount = if (isNewDay) {
                0
            } else {
                prefs.todayManualActionCount
            }

            prefs.toBuilder()
                .setWeeklyAutomationCount(
                    currentWeeklyAutomationCount + 1
                )
                .setWeeklyAlertCount(
                    currentWeeklyAlertCount + if (isAlert) 1 else 0
                )
                .setTodayAutomationCount(
                    currentTodayAutomationCount + if (isManual) 0 else 1
                )
                .setTodayManualActionCount(
                    currentTodayManualActionCount + if (isManual) 1 else 0
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
        val fullName = listOf(
            firstName,
            lastName
        )
            .filter { value ->
                value.isNotBlank()
            }
            .joinToString(" ")

        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .setCity(city)
                .setAddressLine(addressLine)
                .setPostCode(postCode)
                .setPhoneNumber(phoneNumber)
                .setCountry(country)
                .setFullName(fullName)

            val ownerUid = UserDataScope.normalizeOwnerUid(
                prefs.uid
            )

            if (ownerUid.isNotBlank()) {
                builder.replaceProfileCache(
                    builder.build().toActiveProfileCache(
                        ownerUid = ownerUid
                    )
                )
            }

            builder.build()
        }
    }

    suspend fun profilePhotoUrlForOwner(
        ownerUid: String
    ): String {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return ""
        }

        val prefs = dataStore.data.first()

        if (UserDataScope.normalizeOwnerUid(prefs.uid) == normalizedOwnerUid) {
            return prefs.profilePhotoUrl
        }

        return prefs.profileCacheFor(
            ownerUid = normalizedOwnerUid
        )?.profilePhotoUrl.orEmpty()
    }

    suspend fun clearUserDataForOwner(
        ownerUid: String
    ) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return
        }

        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()
                .removeProfileCache(
                    ownerUid = normalizedOwnerUid
                )

            if (UserDataScope.normalizeOwnerUid(prefs.uid) == normalizedOwnerUid) {
                builder
                    .clearUid()
                    .setIsLoggedIn(false)
                    .clearActiveProfile()
            }

            builder.build()
        }
    }

    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }

    private fun UserPreferences.profileCacheFor(
        ownerUid: String
    ): UserProfileCache? {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return null
        }

        return getProfileCachesList().firstOrNull { cache ->
            UserDataScope.normalizeOwnerUid(
                cache.ownerUid
            ) == normalizedOwnerUid
        }
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
        ).any { value ->
            value.isNotBlank()
        }
    }

    private fun UserPreferences.toActiveProfileCache(
        ownerUid: String
    ): UserProfileCache {
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
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            profile.ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return this
        }

        return removeProfileCache(
            ownerUid = normalizedOwnerUid
        ).addProfileCaches(profile)
    }

    private fun UserPreferences.Builder.removeProfileCache(
        ownerUid: String
    ): UserPreferences.Builder {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(
            ownerUid
        )

        if (normalizedOwnerUid.isBlank()) {
            return this
        }

        val retainedProfiles = getProfileCachesList().filterNot { cache ->
            UserDataScope.normalizeOwnerUid(
                cache.ownerUid
            ) == normalizedOwnerUid
        }

        clearProfileCaches()
        addAllProfileCaches(retainedProfiles)

        return this
    }
}
