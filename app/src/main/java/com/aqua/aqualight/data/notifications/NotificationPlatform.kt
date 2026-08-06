package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationLifecycleUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.platform.notifications.AndroidNotificationRenderer

/** Process-scoped composition for the central notification platform. */
class NotificationPlatform private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = OwnerNotificationPreferences.create(appContext)

    val permissionPolicy = AndroidNotificationPermissionPolicy(appContext)
    val renderer = AndroidNotificationRenderer(appContext)
    val scheduler = DefaultNotificationScheduler(
        context = appContext,
        preferences = repository,
        renderer = renderer
    )
    val dispatchUseCase = NotificationDispatchUseCase(
        repository = repository,
        permissionPolicy = permissionPolicy,
        renderer = renderer
    )
    val lifecycleUseCase = NotificationLifecycleUseCase(renderer)
    val preferenceUseCase = NotificationPreferenceUseCase(
        repository = repository,
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
