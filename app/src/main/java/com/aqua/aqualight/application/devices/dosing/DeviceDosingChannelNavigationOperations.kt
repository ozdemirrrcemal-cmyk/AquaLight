package com.aqua.aqualight.application.devices.dosing

import com.aqua.aqualight.application.devices.DeviceRootRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Central application boundary for resolving one Dosing channel click to an authorized screen. */
fun interface DeviceDosingChannelNavigationOperations {
    suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget?

    /** Observes targets projected from the central, device-scoped Dosing runtime state. */
    fun observeTargets(deviceUid: String): Flow<List<DeviceDosingChannelNavigationTarget>> =
        flowOf(emptyList())

    /** Refreshes the central Dosing runtime state when the root screen is bound. */
    suspend fun refreshTargets(deviceUid: String): Boolean = false

    /**
     * Resolves from current published state when available; implementations may fall back to
     * authoritative runtime status/recovery when published state is absent.
     */
    suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = resolve(deviceUid, slotId)
}

data class DeviceDosingChannelNavigationTarget(
    val deviceUid: String,
    val slotId: String,
    val pumpCount: Int,
    val channelNumber: Int,
    val channelTitle: String,
    val lastCalibratedAtEpochSeconds: Long,
    val destination: DeviceDosingChannelDestination,
    val revision: Long = 0L,
    val runtimeEnabled: Boolean = false,
    val runtimeReason: String = "none",
    val programConfigured: Boolean = false,
    val programEnabled: Boolean = false,
    val programWeekdays: List<Boolean> = emptyList(),
    val dailyDoseMl: Double = 0.0,
    val scheduledDeliveredTodayMl: Double = 0.0,
    val doseMilestonesMl: List<Double> = emptyList(),
    val deliveryAccountingCertain: Boolean = true,
    val active: Boolean = false
)

enum class DeviceDosingChannelDestination {
    CALIBRATION,
    DETAIL
}

/** Keeps the calibrated/detail gate inside the same commercial route authorization contract. */
object DeviceDosingChannelDestinationPolicy {
    fun resolve(
        calibrated: Boolean,
        allowedRoutes: Set<DeviceRootRoute>
    ): DeviceDosingChannelDestination? = when {
        DeviceRootRoute.DOSING_CHANNELS !in allowedRoutes -> null
        calibrated -> DeviceDosingChannelDestination.DETAIL
        DeviceRootRoute.DOSING_CALIBRATION in allowedRoutes ->
            DeviceDosingChannelDestination.CALIBRATION
        else -> null
    }
}
