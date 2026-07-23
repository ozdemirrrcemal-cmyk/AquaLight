package com.aqua.aqualight.app

import android.content.Context
import com.aqua.aqualight.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Installs the Firebase boundary before any repository can obtain an Auth or Firestore client.
 *
 * The Gradle flavor and the processed google-services.json must describe the same environment.
 * Development is additionally pinned to the local Emulator Suite, so a developer build cannot
 * fall through to a live Firebase backend.
 */
internal object FirebaseEnvironmentInstaller {

    private const val EMULATOR_HOST = "10.0.2.2"
    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080

    fun install(context: Context) {
        val firebaseApp = FirebaseApp.getInstance()
        val actualProjectId = requireNotNull(firebaseApp.options.projectId) {
            "Firebase project ID is missing from the selected build environment."
        }
        check(actualProjectId == BuildConfig.AQL_FIREBASE_PROJECT_ID) {
            "Firebase project mismatch for ${BuildConfig.AQL_FIREBASE_ENVIRONMENT}: " +
                "expected ${BuildConfig.AQL_FIREBASE_PROJECT_ID}, found $actualProjectId."
        }

        val expectedApplicationId = when (BuildConfig.AQL_FIREBASE_ENVIRONMENT) {
            "development" -> "com.aqua.aqualight.dev"
            "staging" -> "com.aqua.aqualight.staging"
            "production" -> "com.aqua.aqualight"
            else -> error(
                "Unsupported Firebase environment: ${BuildConfig.AQL_FIREBASE_ENVIRONMENT}"
            )
        }
        check(BuildConfig.APPLICATION_ID == expectedApplicationId) {
            "Firebase environment ${BuildConfig.AQL_FIREBASE_ENVIRONMENT} cannot use " +
                "${BuildConfig.APPLICATION_ID}."
        }
        check(context.packageName == expectedApplicationId) {
            "Runtime package ${context.packageName} does not match $expectedApplicationId."
        }

        if (BuildConfig.AQL_FIREBASE_USE_EMULATORS) {
            FirebaseAuth.getInstance(firebaseApp).useEmulator(
                EMULATOR_HOST,
                AUTH_EMULATOR_PORT
            )
            FirebaseFirestore.getInstance(firebaseApp).useEmulator(
                EMULATOR_HOST,
                FIRESTORE_EMULATOR_PORT
            )
        }
    }
}
