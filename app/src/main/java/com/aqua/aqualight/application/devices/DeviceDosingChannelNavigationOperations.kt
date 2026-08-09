package com.aqua.aqualight.application.devices

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

    /** Resolves a click from already-published state without performing a device request. */
    suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = resolve(deviceUid, slotId)
}

data class DeviceDosingChannelNavigationTarget(
    val deviceUid: String,
    val slotId: String,
    val channelTitle: String,
    val destination: DeviceDosingChannelDestination
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
