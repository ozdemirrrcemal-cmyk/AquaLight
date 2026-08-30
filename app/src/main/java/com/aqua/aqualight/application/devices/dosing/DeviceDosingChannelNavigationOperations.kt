package com.aqua.aqualight.application.devices.dosing

import com.aqua.aqualight.application.devices.DeviceRootRoute

/** Central application boundary for resolving one Dosing channel click to an authorized screen. */
fun interface DeviceDosingChannelNavigationOperations {
    suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget?

    /** Resolves from current central state, refreshing authoritative runtime state if required. */
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
    val lastCalibratedAtEpochSeconds: Long,
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
