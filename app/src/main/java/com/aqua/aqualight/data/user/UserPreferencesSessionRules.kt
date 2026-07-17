package com.aqua.aqualight.data.user

/**
 * Atomic owner transition rules for the encrypted preference store.
 *
 * A process interruption during account switching may leave either the previous
 * valid session or the next valid session, but never the next UID with the
 * previous owner's active profile projection.
 */
object UserPreferencesSessionRules {

    fun activateOwner(
        current: UserPreferences,
        ownerUid: String
    ): UserPreferences {
        UserPreferencesStoreRules.validate(current)

        val nextOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        require(nextOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        val builder = current.toBuilder()
        val previousOwnerUid = current.uid.takeIf { uid ->
            current.isLoggedIn && uid.isNotBlank()
        }

        if (
            previousOwnerUid != null &&
            previousOwnerUid != nextOwnerUid &&
            current.hasActiveProfileData()
        ) {
            builder.replaceProfileCache(
                current.toProfileCache(previousOwnerUid)
            )
        }

        val nextProfile = current.profileCachesList.firstOrNull { profile ->
            profile.ownerUid == nextOwnerUid
        }

        builder
            .setUid(nextOwnerUid)
            .setIsLoggedIn(true)

        if (nextProfile == null) {
            builder.clearActiveProfile()
        } else {
            builder.applyActiveProfile(nextProfile)
        }

        return UserPreferencesStoreRules.validate(builder.build())
    }

    fun deactivateOwner(
        current: UserPreferences
    ): UserPreferences {
        UserPreferencesStoreRules.validate(current)

        val builder = current.toBuilder()
        val activeOwnerUid = current.uid.takeIf { uid ->
            current.isLoggedIn && uid.isNotBlank()
        }

        if (activeOwnerUid != null && current.hasActiveProfileData()) {
            builder.replaceProfileCache(
                current.toProfileCache(activeOwnerUid)
            )
        }

        return UserPreferencesStoreRules.validate(
            builder
                .clearUid()
                .setIsLoggedIn(false)
                .clearActiveProfile()
                .build()
        )
    }

    private fun UserPreferences.hasActiveProfileData(): Boolean {
        return listOf(
            email,
            username,
            fullName,
            profilePhotoUrl,
            firstName,
            lastName,
            city,
            addressLine,
            postCode,
            phoneNumber,
            country
        ).any(String::isNotBlank)
    }

    private fun UserPreferences.toProfileCache(
        ownerUid: String
    ): UserProfileCache {
        return UserProfileCache.newBuilder()
            .setOwnerUid(ownerUid)
            .setEmail(email)
            .setUsername(username)
            .setProfilePhotoUrl(profilePhotoUrl)
            .setFullName(fullName)
            .setFirstName(firstName)
            .setLastName(lastName)
            .setCity(city)
            .setAddressLine(addressLine)
            .setPostCode(postCode)
            .setPhoneNumber(phoneNumber)
            .setCountry(country)
            .build()
    }

    private fun UserPreferences.Builder.applyActiveProfile(
        profile: UserProfileCache
    ): UserPreferences.Builder {
        return setEmail(profile.email)
            .setUsername(profile.username)
            .setProfilePhotoUrl(profile.profilePhotoUrl)
            .setFullName(profile.fullName)
            .setFirstName(profile.firstName)
            .setLastName(profile.lastName)
            .setCity(profile.city)
            .setAddressLine(profile.addressLine)
            .setPostCode(profile.postCode)
            .setPhoneNumber(profile.phoneNumber)
            .setCountry(profile.country)
    }

    private fun UserPreferences.Builder.clearActiveProfile(): UserPreferences.Builder {
        return clearEmail()
            .clearUsername()
            .clearProfilePhotoUrl()
            .clearFullName()
            .clearFirstName()
            .clearLastName()
            .clearCity()
            .clearAddressLine()
            .clearPostCode()
            .clearPhoneNumber()
            .clearCountry()
    }

    private fun UserPreferences.Builder.replaceProfileCache(
        profile: UserProfileCache
    ): UserPreferences.Builder {
        val ownerUid = profile.ownerUid
        val retained = profileCachesList.filterNot { cached ->
            cached.ownerUid == ownerUid
        }

        clearProfileCaches()
        addAllProfileCaches(retained)
        addProfileCaches(profile)
        return this
    }
}
