package com.aqua.aqualight.data.feedback

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * Provides the only Firestore instance used by AquaLight.
 *
 * Feedback may contain personal data, so Firestore's Android default disk persistence is replaced
 * with a process-only memory cache before the first collection reference is created.
 */
object FeedbackFirestoreProvider {

    @Volatile
    private var configuredInstance: FirebaseFirestore? = null

    fun get(): FirebaseFirestore {
        return configuredInstance ?: synchronized(this) {
            configuredInstance ?: FirebaseFirestore.getInstance().also { firestore ->
                val settings = FirebaseFirestoreSettings.Builder(
                    firestore.firestoreSettings
                )
                    .setLocalCacheSettings(
                        MemoryCacheSettings.newBuilder().build()
                    )
                    .build()
                firestore.firestoreSettings = settings
                configuredInstance = firestore
            }
        }
    }
}
