package com.aqua.aqualight.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.io.File

class UserPreferencesManager private constructor(
    private val dataStore: DataStore<UserPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        fun create(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(context).also { INSTANCE = it }
            }
        }

        private fun buildDataStore(context: Context): UserPreferencesManager {
            val delegate = UserPreferencesSerializer
            val encryptedSerializer = EncryptedUserPreferencesSerializer(context, delegate)

            val ds = DataStoreFactory.create(
                serializer = encryptedSerializer,
                scope = CoroutineScope(Dispatchers.IO),
                produceFile = { context.dataStoreFile("user_prefs.pb") }
            )

            migrateLegacyIfNeeded(context, delegate, ds)
            return UserPreferencesManager(ds)
        }

        private fun migrateLegacyIfNeeded(
            context: Context,
            legacySerializer: androidx.datastore.core.Serializer<UserPreferences>,
            encryptedStore: DataStore<UserPreferences>
        ) {
            val legacyFile = File(context.filesDir, "user_prefs.pb")
            if (legacyFile.exists()) {
                try {
                    runBlocking {
                        val legacyData = legacySerializer.readFrom(legacyFile.inputStream())
                        encryptedStore.updateData { legacyData }
                    }
                    legacyFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 🔹 Flow'lar
    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { emit(UserPreferences.getDefaultInstance()) }

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { it.isLoggedIn }
    val idToken: Flow<String> = userPrefsFlow.map { it.idToken }
    val email: Flow<String> = userPrefsFlow.map { it.email }
    val username: Flow<String> = userPrefsFlow.map { it.username }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { it.profilePhotoUrl }
    val fullName: Flow<String> = userPrefsFlow.map { it.fullName }   // 🆕

    // 🔹 Oturum kaydet
    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    // 🔹 Profil bilgilerini kaydet (toplu)
    suspend fun saveProfile(
        email: String,
        username: String?,
        photoUrl: String?,
        fullName: String? = null           // 🆕 opsiyonel
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setEmail(email)
                .setUsername(username ?: "")
                .setProfilePhotoUrl(photoUrl ?: "")
                .setFullName(fullName ?: prefs.fullName)  // varsa güncelle, yoksa eskiyi koru
                .build()
        }
    }

    // 🔹 Sadece profil foto'yu güncelle (EditProfile ekranında kullanmak için ideal)
    suspend fun updateProfilePhoto(photoUrl: String) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setProfilePhotoUrl(photoUrl)
                .build()
        }
    }

    // 🔹 Sadece full name güncelle (ileride kullanmak için)
    suspend fun updateFullName(fullName: String) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setFullName(fullName)
                .build()
        }
    }

    // 🔹 Oturumu temizle
    suspend fun clearUserData() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearIdToken()
                .clearEmail()
                .clearUsername()
                .clearProfilePhotoUrl()
                .clearFullName()
                .setIsLoggedIn(false)
                .build()
        }
    }
}