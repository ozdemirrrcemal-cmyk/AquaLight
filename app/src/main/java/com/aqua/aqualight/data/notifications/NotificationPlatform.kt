package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase

/** Process-scoped composition for the central notification platform. */
class NotificationPlatform private constructor(context: Context) {
    private val appContext = context.applicationContext

    val permissionPolicy = AndroidNotificationPermissionPolicy(appContext)
    val renderer = AndroidNotificationRenderer(appContext, permissionPolicy)
    val scheduler = DefaultNotificationScheduler(
        context = appContext,
        preferences = OwnerNotificationPreferences.create(appContext),
        renderer = renderer
    )
    val preferenceUseCase = NotificationPreferenceUseCase(
        repository = OwnerNotificationPreferences.create(appContext),
        permissionPolicy = permissionPolicy,
        scheduler = scheduler,
        renderer = renderer
    )

    companion object {
        @Volatile
        private var instance: NotificationPlatform? = null

        fun get(context: Context): NotificationPlatform {
            return instance ?: synchronized(this) {
                instance ?: NotificationPlatform(context.applicationContext).also { created ->
                    instance = created
                }
            }
        }
    }
}
