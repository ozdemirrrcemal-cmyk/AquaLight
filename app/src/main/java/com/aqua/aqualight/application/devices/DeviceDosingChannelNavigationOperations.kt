package com.aqua.aqualight.application.devices

/** Central application boundary for resolving one Dosing channel click to an authorized screen. */
fun interface DeviceDosingChannelNavigationOperations {
    suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget?
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
