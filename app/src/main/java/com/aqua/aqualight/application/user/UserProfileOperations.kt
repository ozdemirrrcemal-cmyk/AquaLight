package com.aqua.aqualight.application.user

import kotlinx.coroutines.flow.Flow

/** User-profile data exposed to UI without leaking the Proto/DataStore implementation. */
interface UserProfileOperations {
    val profile: Flow<UserProfileSnapshot>

    suspend fun updateProfilePhoto(photoUri: String)
}

data class UserProfileSnapshot(
    val username: String,
    val email: String,
    val profilePhotoUrl: String
)
