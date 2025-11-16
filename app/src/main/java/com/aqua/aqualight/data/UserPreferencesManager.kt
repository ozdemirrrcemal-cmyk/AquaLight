class UserPreferencesManager private constructor(
    private val dataStore: DataStore<UserPreferences>
) {

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        // 🔹 Global default’lar
        const val DEFAULT_THEME_MODE = "dark"
        const val DEFAULT_LANGUAGE_CODE = "en"
        const val DEFAULT_NOTIFICATIONS_ENABLED = false
        const val DEFAULT_AUTO_UPDATE_ENABLED = false

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
    val fullName: Flow<String> = userPrefsFlow.map { it.fullName }

    // Tema
    val themeMode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.themeMode.ifBlank { DEFAULT_THEME_MODE }
    }

    // Dil
    val languageCode: Flow<String> = userPrefsFlow.map { prefs ->
        prefs.languageCode.ifBlank { DEFAULT_LANGUAGE_CODE }
    }

    // 🔔 Bildirim tercihi (proto default=false → ilk açılışta false)
    val notificationsEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        if (prefs == UserPreferences.getDefaultInstance()) {
            DEFAULT_NOTIFICATIONS_ENABLED
        } else {
            prefs.notificationsEnabled
        }
    }

    // 🌐 Auto-update tercihi
    val autoUpdateEnabled: Flow<Boolean> = userPrefsFlow.map { prefs ->
        if (prefs == UserPreferences.getDefaultInstance()) {
            DEFAULT_AUTO_UPDATE_ENABLED
        } else {
            prefs.autoUpdateEnabled
        }
    }

    // ------- UPDATE METODLARI -------

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { current -> transform(current) }
    }

    suspend fun saveUserSession(idToken: String, isLoggedIn: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toBuilder()
                .setIdToken(idToken)
                .setIsLoggedIn(isLoggedIn)
                .build()
        }
    }

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
                .setFullName(fullName.orEmpty())
                .setProfilePhotoUrl(photoUrl.orEmpty())
                .build()
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

    suspend fun clearAllUserData() {
        dataStore.updateData {
            UserPreferences.getDefaultInstance()
        }
    }
}