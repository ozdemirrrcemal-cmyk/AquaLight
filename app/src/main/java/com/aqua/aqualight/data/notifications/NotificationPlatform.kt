package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.application.notifications.DeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.devices.update.DefaultDeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.data.devices.update.DeviceFirmwareAvailabilityTrustStore
import com.aqua.aqualight.platform.notifications.AndroidDeviceFirmwareUpdateNotificationPublisher
import com.aqua.aqualight.platform.notifications.AndroidNotificationRenderer
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

/** Process-scoped composition for the central notification platform. */
class NotificationPlatform private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = OwnerNotificationPreferences.create(appContext)
    private val deviceUpdateLedger = DeviceUpdateNotificationLedger.create(appContext)
    private val deviceUpdateTrust =
        DeviceFirmwareAvailabilityTrustStore.create(appContext)

    val permissionPolicy = AndroidNotificationPermissionPolicy(appContext)
    val renderer = AndroidNotificationRenderer(appContext)
    val scheduler = DefaultNotificationScheduler(
        context = appContext,
        preferences = repository,
        renderer = renderer
    )
    internal val deviceUpdateWorkCoordinator: DeviceUpdateNotificationWorkCoordinator =
        DefaultDeviceUpdateNotificationWorkCoordinator.create(
            context = appContext,
            preferences = repository
        )
    val dispatchUseCase = NotificationDispatchUseCase(
        repository = repository,
        permissionPolicy = permissionPolicy,
        renderer = renderer
    )
    val preferenceUseCase = NotificationPreferenceUseCase(
        repository = repository,
        permissionPolicy = permissionPolicy,
        scheduler = scheduler,
        deviceUpdateWorkCoordinator = deviceUpdateWorkCoordinator,
        renderer = renderer
    )
    internal val deviceFirmwareUpdates: DeviceFirmwareUpdateNotificationOperations =
        AndroidDeviceFirmwareUpdateNotificationPublisher(
            context = appContext,
            dispatchUseCase = dispatchUseCase,
            renderer = renderer,
            ledger = deviceUpdateLedger,
            trust = deviceUpdateTrust
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
