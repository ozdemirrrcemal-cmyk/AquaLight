package com.aqua.aqualight.ui.auth.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class ReAuthManager {

    companion object {

        const val PROVIDER_GOOGLE = "google.com"
        const val PROVIDER_PASSWORD = "password"
        const val PROVIDER_UNKNOWN = "unknown"
    }

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun getProvider(): String {

        val user = auth.currentUser ?: return PROVIDER_UNKNOWN

        val providers = user.providerData

        providers.forEach { info ->

            when (info.providerId) {

                PROVIDER_GOOGLE -> {
                    return PROVIDER_GOOGLE
                }

                PROVIDER_PASSWORD -> {
                    return PROVIDER_PASSWORD
                }
            }
        }

        return PROVIDER_UNKNOWN
    }

    fun isGoogleUser(): Boolean {
        return getProvider() == PROVIDER_GOOGLE
    }

    fun isPasswordUser(): Boolean {
        return getProvider() == PROVIDER_PASSWORD
    }
}