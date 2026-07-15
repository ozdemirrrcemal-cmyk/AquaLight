package com.aqua.aqualight.data.user

import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserProfileSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultUserProfileOperations(
    private val preferences: UserPreferencesManager
) : UserProfileOperations {

    override val profile: Flow<UserProfileSnapshot> =
        preferences.userPrefsFlow.map { prefs ->
            UserProfileSnapshot(
                username = prefs.username,
                email = prefs.email,
                profilePhotoUrl = prefs.profilePhotoUrl
            )
        }

    override suspend fun updateProfilePhoto(photoUri: String) {
        preferences.updateProfilePhoto(photoUri)
    }
}
