package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.application.user.UserAddressInput
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserProfileSnapshot
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultUserProfileOperations(
    context: Context,
    private val preferences: UserPreferencesManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserProfileOperations {

    private val appContext = context.applicationContext

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

    override suspend fun updateProfilePhoto(photoUri: String): Unit = withContext(NonCancellable) {
        withContext(dispatcher) {
            val normalized = photoUri.trim()
            val previous = preferences.profilePhotoUrl.first()
                .takeIf { it.isNotBlank() && it != normalized }

            try {
                preferences.updateProfilePhoto(normalized)
            } catch (error: Throwable) {
                runCatching { AppMediaStorage.rollbackPendingMedia(appContext, normalized) }
                throw error
            }

            // The domain write is authoritative. Journal finalization and old-file deletion are
            // idempotent cleanup; recovery reconciles either operation if the process stops here.
            runCatching { AppMediaStorage.commitPendingMedia(appContext, normalized) }
            runCatching { AppMediaStorage.deleteInternalMedia(appContext, previous) }
            Unit
        }
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
