package com.aqua.aqualight.data

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

// --------------------------------------------------------
//  Dışarıya verilen UI modelleri
// --------------------------------------------------------

data class UsageAnalyticsUi(
    val weeklyAutomationCount: Int,
    val weeklyAlertCount: Int,
    val todayAutomationCount: Int,
    val todayManualActionCount: Int,
    val lastEventTimeMillis: Long,
    val lastEventDescription: String
)

data class DeviceInfoUi(
    val id: Long,
    val aquaName: String,
    val name: String,
    val ip: String,
    val serial: String,
    val lastSeenMillis: Long
)

// --------------------------------------------------------
//  UserPreferencesManager
// --------------------------------------------------------

class UserPreferencesManager private constructor(
    private val dataStore: DataStore<UserPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        const val DEFAULT_THEME_MODE = "dark"
        const val DEFAULT_LANGUAGE_CODE = "en"

        fun create(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDataStore(appContext: Context): UserPreferencesManager {
            val delegate = UserPreferencesSerializer
            val encryptedSerializer = EncryptedUserPreferencesSerializer(appContext, delegate)

            val ds = DataStoreFactory.create(
                serializer = encryptedSerializer,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { appContext.dataStoreFile("user_prefs.pb") }
            )

            return UserPreferencesManager(ds)
        }
    }

    // --------------------------------------------------------
    //  BASE FLOW
    // --------------------------------------------------------

    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw e
            }
        }

    // --------------------------------------------------------
    //  PROFILE & SESSION FLOW
    // --------------------------------------------------------

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { it.isLoggedIn }
    val idToken: Flow<String> = userPrefsFlow.map { it.idToken }
    val email: Flow<String> = userPrefsFlow.map { it.email }
    val username: Flow<String> = userPrefsFlow.map { it.username }
    val fullName: Flow<String> = userPrefsFlow.map { it.fullName }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { it.profilePhotoUrl }

    val themeMode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.themeMode.ifBlank { DEFAULT_THEME_MODE }
    }

    val languageCode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.languageCode.ifBlank { DEFAULT_LANGUAGE_CODE }
    }

    val notificationsEnabled: Flow<Boolean> = userPrefsFlow.map { it.notificationsEnabled }
    val autoUpdateEnabled: Flow<Boolean> = userPrefsFlow.map { it.autoUpdateEnabled }
    val loginAlertsEnabled: Flow<Boolean> = userPrefsFlow.map { it.loginAlertsEnabled }
    val twoFactorEnabled: Flow<Boolean> = userPrefsFlow.map { it.twoFactorEnabled }

    val firstName: Flow<String> = userPrefsFlow.map { it.firstName }
    val lastName: Flow<String> = userPrefsFlow.map { it.lastName }
    val city: Flow<String> = userPrefsFlow.map { it.city }
    val addressLine: Flow<String> = userPrefsFlow.map { it.addressLine }
    val postCode: Flow<String> = userPrefsFlow.map { it.postCode }
    val phoneNumber: Flow<String> = userPrefsFlow.map { it.phoneNumber }
    val country: Flow<String> = userPrefsFlow.map { it.country }

    // --------------------------------------------------------
    //  USAGE / ANALYTICS
    // --------------------------------------------------------

    val usageAnalyticsFlow: Flow<UsageAnalyticsUi> = userPrefsFlow.map { prefs ->
        UsageAnalyticsUi(
            weeklyAutomationCount = prefs.weeklyAutomationCount,
            weeklyAlertCount = prefs.weeklyAlertCount,
            todayAutomationCount = prefs.todayAutomationCount,
            todayManualActionCount = prefs.todayManualActionCount,
            lastEventTimeMillis = prefs.lastEventTimeMillis,
            lastEventDescription = prefs.lastEventDescription
        )
    )

    // --------------------------------------------------------
    //  GENERAL UPDATE
    // --------------------------------------------------------

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { current -> transform(current) }
    }

    // --------------------------------------------------------
    //  SESSION & PROFILE OPS
    // --------------------------------------------------------

    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
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

            email?.let { builder.setEmail(it) }
            username?.let { builder.setUsername(it) }
            fullName?.let { builder.setFullName(it) }
            photoUrl?.let { builder.setProfilePhotoUrl(it) }

            builder.build()
        }
    }

    suspend fun updateProfilePhoto(photoUrl: String) {
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

    // --------------------------------------------------------
    //  THEME / LANGUAGE / NOTIFICATIONS
    // --------------------------------------------------------

    suspend fun updateThemeMode(mode: String) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setThemeMode(mode)
                .build()
        }
    }

    suspend fun updateLanguage(code: String) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setLanguageCode(code)
                .build()
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setNotificationsEnabled(enabled)
                .build()
        }
    }

    suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setAutoUpdateEnabled(enabled)
                .build()
        }
    }

    suspend fun updateLoginAlertsEnabled(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setLoginAlertsEnabled(enabled)
                .build()
        }
    }

    suspend fun updateTwoFactorEnabled(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setTwoFactorEnabled(enabled)
                .build()
        }
    }

    // --------------------------------------------------------
    //  USAGE LOG
    // --------------------------------------------------------

    suspend fun logUsageEvent(
        isManual: Boolean,
        isAlert: Boolean,
        description: String
    ) {
        val now = System.currentTimeMillis()

        val today = LocalDate.now()
        val dayKey = today.toString() // "2025-11-20"

        val weekFields = WeekFields.of(Locale.getDefault())
        val weekOfYear = today.get(weekFields.weekOfWeekBasedYear())
        val weekKey = "${today.year}-W$weekOfYear" // "2025-W47"

        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()

            if (prefs.lastUsageDayKey != dayKey) {
                builder.clearTodayAutomationCount()
                builder.clearTodayManualActionCount()
            }

            if (prefs.lastUsageWeekKey != weekKey) {
                builder.clearWeeklyAutomationCount()
                builder.clearWeeklyAlertCount()
            }

            val updatedWeeklyAutomation = builder.weeklyAutomationCount + 1
            builder.weeklyAutomationCount = updatedWeeklyAutomation

            if (isAlert) {
                val updatedWeeklyAlerts = builder.weeklyAlertCount + 1
                builder.weeklyAlertCount = updatedWeeklyAlerts
            }

            if (isManual) {
                val updatedTodayManual = builder.todayManualActionCount + 1
                builder.todayManualActionCount = updatedTodayManual
            } else {
                val updatedTodayAuto = builder.todayAutomationCount + 1
                builder.todayAutomationCount = updatedTodayAuto
            }

            builder.lastEventTimeMillis = now
            builder.lastEventDescription = description
            builder.lastUsageDayKey = dayKey
            builder.lastUsageWeekKey = weekKey

            builder.build()
        }
    }

    // --------------------------------------------------------
    //  MULTI-DEVICE FLOWS
    // --------------------------------------------------------

    val devicesFlow: Flow<List<DeviceInfoUi>> = userPrefsFlow.map { prefs ->
        prefs.devicesList.map {
            DeviceInfoUi(
                id = it.id,
                aquaName = it.aquaName,
                name = it.name,
                ip = it.ip,
                serial = it.serial,
                lastSeenMillis = it.lastSeenMillis
            )
        }
    }

    // --------------------------------------------------------
    //  MULTI-DEVICE OPERATIONS
    // --------------------------------------------------------

    suspend fun addDevice(
        id: Long,
        aquaName: String,
        name: String,
        ip: String,
        serial: String
    ) {
        dataStore.updateData { prefs ->
            val builder = prefs.toBuilder()

            // Aynı id varsa tekrar ekleme
            if (builder.devicesList.any { it.id == id }) {
                return@updateData prefs
            }

            val now = System.currentTimeMillis()

            val device = UserPreferences.DeviceInfo.newBuilder()
                .setId(id)
                .setAquaName(aquaName)
                .setName(name)
                .setIp(ip)
                .setSerial(serial)
                .setLastSeenMillis(now) // ilk eklerken şu an görüldü say
                .build()

            builder.addDevices(device)
            builder.build()
        }
    }

    suspend fun updateDevice(
        id: Long,
        aquaName: String? = null,
        name: String? = null,
        ip: String? = null,
        serial: String? = null
    ) {
        dataStore.updateData { prefs ->
            val updated = prefs.devicesList.map { dev ->
                if (dev.id != id) return@map dev

                dev.toBuilder().apply {
                    aquaName?.let { setAquaName(it) }
                    name?.let { setName(it) }
                    ip?.let { setIp(it) }
                    serial?.let { setSerial(it) }
                }.build()
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updated)
                .build()
        }
    }

    suspend fun deleteDevices(ids: Set<Long>) {
        dataStore.updateData { prefs ->
            val filtered = prefs.devicesList.filter { it.id !in ids }
            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(filtered)
                .build()
        }
    }

    suspend fun clearAllDevices() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearDevices()
                .build()
        }
    }

    /**
     * LAN monitör her tarama sonrası çağıracak:
     * discovered listesinde olan device'ların lastSeenMillis alanını now ile günceller.
     */
    suspend fun updateDevicesLastSeen(
        discovered: List<com.aqua.aqualight.ui.tabs.devices.DiscoveredDevice>
    ) {
        val now = System.currentTimeMillis()

        dataStore.updateData { prefs ->
            val updated = prefs.devicesList.map { dev ->
                val match = discovered.firstOrNull { d ->
                    d.id == dev.id || (!d.ip.isNullOrBlank() && d.ip == dev.ip)
                }

                if (match != null) {
                    dev.toBuilder()
                        .setLastSeenMillis(now)
                        .build()
                } else {
                    dev
                }
            }

            prefs.toBuilder()
                .clearDevices()
                .addAllDevices(updated)
                .build()
        }
    }

    // --------------------------------------------------------
    //  ADDRESS SAVE
    // --------------------------------------------------------

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
            .filter { it.isNotBlank() }
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

    // --------------------------------------------------------
    //  RESET ALL DATA
    // --------------------------------------------------------

    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }
}