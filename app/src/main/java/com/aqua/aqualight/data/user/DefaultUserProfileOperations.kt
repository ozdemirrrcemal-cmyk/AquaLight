package com.aqua.aqualight.data.user

import com.aqua.aqualight.application.user.UserAddressInput
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
                profilePhotoUrl = prefs.profilePhotoUrl,
                fullName = prefs.fullName,
                firstName = prefs.firstName,
                lastName = prefs.lastName,
                city = prefs.city,
                addressLine = prefs.addressLine,
                postCode = prefs.postCode,
                phoneNumber = prefs.phoneNumber,
                country = prefs.country
            )
        }

    override suspend fun updateProfilePhoto(photoUri: String) {
        preferences.updateProfilePhoto(photoUri)
    }

    override suspend fun updateUsername(username: String) {
        preferences.updateUsername(username)
    }

    override suspend fun saveAddress(address: UserAddressInput) {
        preferences.saveAddress(
            firstName = address.firstName,
            lastName = address.lastName,
            city = address.city,
            addressLine = address.addressLine,
            postCode = address.postCode,
            phoneNumber = address.phoneNumber,
            country = address.country
        )
    }
}
