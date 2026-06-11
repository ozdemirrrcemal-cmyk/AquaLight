package com.aqua.aqualight.data.user

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

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

    val idToken: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.idToken
    }

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

    data class UsageAnalyticsUi(
        val weeklyAutomationCount: Int,
        val weeklyAlertCount: Int,
        val todayAutomationCount: Int,
        val todayManualActionCount: Int,
        val lastEventTimeMillis: Long,
        val lastEventDescription: String
    )

    val usageAnalyticsFlow: Flow<UsageAnalyticsUi> = userPrefsFlow.map { prefs ->
        UsageAnalyticsUi(
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
        idToken: String,
        isLoggedIn: Boolean
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    suspend fun saveProfile(
        email: String?,
        username: String?,
        fullName: String?,
        photoUrl: String?
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

            builder.build()
        }
    }

    suspend fun updateProfilePhoto(
        photoUrl: String
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setProfilePhotoUrl(photoUrl)
                .build()
        }
    }

    suspend fun logout() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearIdToken()
                .setIsLoggedIn(false)
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
            prefs.toBuilder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .setCity(city)
                .setAddressLine(addressLine)
                .setPostCode(postCode)
                .setPhoneNumber(phoneNumber)
                .setCountry(country)
                .setFullName(fullName)
                .build()
        }
    }

    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }
}