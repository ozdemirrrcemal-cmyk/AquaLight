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

    // 🔹 Tüm user prefs akışı
    val userPrefsFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            // Eğer IO veya serialize hatası olursa default dön
            if (e is IOException) {
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw e
            }
        }

    // 🔹 Kolay erişim için alt akışlar
    val isLoggedIn: Flow<Boolean> = userPrefsFlow.map { it.isLoggedIn }
    val idToken: Flow<String> = userPrefsFlow.map { it.idToken }
    val email: Flow<String> = userPrefsFlow.map { it.email }
    val username: Flow<String> = userPrefsFlow.map { it.username }
    val profilePhotoUrl: Flow<String> = userPrefsFlow.map { it.profilePhotoUrl }
    val fullName: Flow<String> = userPrefsFlow.map { it.fullName }   // 👈 yeni alan

    // 🔹 Genel amaçlı update helper (gerekirse kullanırsın)
    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { current -> transform(current) }
    }

    // 🔹 Oturum kaydet (login sonucu gelen token + login durumu)
    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

    // 🔹 Kullanıcının kimlik/profil bilgilerini kaydet
    // email ve fullName login ekranından,
    // username ve photoUrl uygulama içi ekranlardan gelebilir
    suspend fun saveProfile(
        email: String,
        username: String?,
        fullName: String?,
        photoUrl: String?
    ) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setEmail(email)
                .setUsername(username.orEmpty())
                .setFullName(fullName.orEmpty())           // 👈 fullName kaydı
                .setProfilePhotoUrl(photoUrl.orEmpty())
                .build()
        }
    }

    // 🔹 Sadece profil fotoğrafını güncelle (EditProfileFragment burayı kullanıyor)
    suspend fun updateProfilePhoto(photoUrl: String) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setProfilePhotoUrl(photoUrl)
                .build()
        }
    }

    // 🔹 Logout: sadece oturumu kapat, kullanıcı bilgilerini silme
    suspend fun logout() {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .clearIdToken()
                .setIsLoggedIn(false)
                .build()
        }
    }

    // 🔹 Tüm user verisini sil (cihazdan hesabı tamamen kaldırmak istersen)
    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }
}