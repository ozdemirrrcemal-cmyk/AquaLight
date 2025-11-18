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
    //  FLOW ALANLAR
    // --------------------------------------------------------

    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw e
            }
        }

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { it.isLoggedIn }
    val idToken: Flow<String> = userPrefsFlow.map { it.idToken }
    val email: Flow<String> = userPrefsFlow.map { it.email }
    val username: Flow<String> = userPrefsFlow.map { it.username }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { it.profilePhotoUrl }
    val fullName: Flow<String> = userPrefsFlow.map { it.fullName }

    val themeMode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.themeMode.ifBlank { DEFAULT_THEME_MODE }
    }

    val languageCode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.languageCode.ifBlank { DEFAULT_LANGUAGE_CODE }
    }

    val notificationsEnabled: Flow<Boolean> =
        userPrefsFlow.map { it.notificationsEnabled }

    val autoUpdateEnabled: Flow<Boolean> =
        userPrefsFlow.map { it.autoUpdateEnabled }

    // 🆕 Adres alanları için Flow'lar
    val firstName: Flow<String> = userPrefsFlow.map { it.firstName }
    val lastName: Flow<String> = userPrefsFlow.map { it.lastName }
    val city: Flow<String> = userPrefsFlow.map { it.city }
    val addressLine: Flow<String> = userPrefsFlow.map { it.addressLine }
    val postCode: Flow<String> = userPrefsFlow.map { it.postCode }
    val phoneNumber: Flow<String> = userPrefsFlow.map { it.phoneNumber }
    val country: Flow<String> = userPrefsFlow.map { it.country }

    // --------------------------------------------------------
    //  GENEL UPDATE
    // --------------------------------------------------------

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { current -> transform(current) }
    }

    // --------------------------------------------------------
    //  OTURUM & PROFIL
    // --------------------------------------------------------

    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    /**
     *  email / username / fullName / photoUrl:
     *  - parametre NULL ise: o alanı DEĞİŞTİRME
     *  - parametre değer gelirse: yeni değeri yaz
     */
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
            this@UserPreferencesManager
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
    //  TEMA / DIL / BILDIRIM
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

    // --------------------------------------------------------
    //  🆕 ADRES KAYDI
    // --------------------------------------------------------

    /**
     *  Adres ekranında girdiğin alanlar:
     *  - firstName, lastName → fullName otomatik set edilir
     *  - city, addressLine, postCode, phoneNumber, country
     */
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
                .setFullName(fullName) // 🔥 FULL NAME BURADA OLUŞUYOR
                .build()
        }
    }

    // --------------------------------------------------------
    //  TAM SIFIRLAMA
    // --------------------------------------------------------

    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }
}