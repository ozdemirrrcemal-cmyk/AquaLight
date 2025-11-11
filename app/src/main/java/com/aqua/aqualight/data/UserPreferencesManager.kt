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

class UserPreferencesManager private constructor(private val dataStore: DataStore<UserPreferences>) {

    companion object {
        fun create(context: Context): UserPreferencesManager {
            // delegate serializer is proto serializer
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
                encryptedStore.updateData { _ -> legacyData }
            }
            legacyFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
    }

    // Flows
    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { emit(UserPreferences.getDefaultInstance()) }

    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { it.isLoggedIn }
    val idToken: Flow<String> = userPrefsFlow.map { it.idToken }
    val email: Flow<String> = userPrefsFlow.map { it.email }
    val username: Flow<String> = userPrefsFlow.map { it.username }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { it.profilePhotoUrl }

    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    suspend fun saveProfile(email: String, username: String?, photoUrl: String?) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setEmail(email)
                .setUsername(username ?: "")
                .setProfilePhotoUrl(photoUrl ?: "")
                .build()
        }
    }

    suspend fun clearUserData() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearIdToken()
                .clearEmail()
                .clearUsername()
                .clearProfilePhotoUrl()
                .setIsLoggedIn(false)
                .build()
        }
    }
}