package com.aqua.aqualight.application.user

import kotlinx.coroutines.flow.Flow

/** User-profile data exposed to UI without leaking the Proto/DataStore implementation. */
interface UserProfileOperations {
    val profile: Flow<UserProfileSnapshot>

    suspend fun updateProfilePhoto(photoUri: String)

    suspend fun updateUsername(username: String)

    suspend fun saveAddress(address: UserAddressInput)
}

data class UserProfileSnapshot(
    val username: String = "",
    val email: String = "",
    val profilePhotoUrl: String = "",
    val fullName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val city: String = "",
    val addressLine: String = "",
    val postCode: String = "",
    val phoneNumber: String = "",
    val country: String = ""
)

data class UserAddressInput(
    val firstName: String,
    val lastName: String,
    val city: String,
    val addressLine: String,
    val postCode: String,
    val phoneNumber: String,
    val country: String
)
