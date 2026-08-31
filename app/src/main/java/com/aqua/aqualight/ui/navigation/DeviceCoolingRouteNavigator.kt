package com.aqua.aqualight.ui.navigation

import androidx.navigation.NavController
import com.aqua.aqualight.R

/** Owns the Cooling feature's route catalogue while delegating shared route policy. */
object DeviceCoolingRouteNavigator {

    fun openTemperatureHistory(
        navController: NavController,
        deviceUid: String
    ): AppRouteOpenResult = open(
        navController = navController,
        deviceUid = deviceUid,
        path = PATH_HISTORY,
        destinationId = R.id.deviceCoolingTemperatureHistoryFragment
    )

    fun openAutomaticSettings(
        navController: NavController,
        deviceUid: String
    ): AppRouteOpenResult = open(
        navController = navController,
        deviceUid = deviceUid,
        path = PATH_AUTOMATIC,
        destinationId = R.id.deviceCoolingAutomaticSettingsFragment
    )

    fun openProgramSettings(
        navController: NavController,
        deviceUid: String
    ): AppRouteOpenResult = open(
        navController = navController,
        deviceUid = deviceUid,
        path = PATH_PROGRAM,
        destinationId = R.id.deviceCoolingProgramSettingsFragment
    )

    private fun open(
        navController: NavController,
        deviceUid: String,
        path: String,
        destinationId: Int
    ): AppRouteOpenResult = AppRouteNavigator.openCoolingDetailDestination(
        navController = navController,
        deviceUid = deviceUid,
        path = path,
        destinationId = destinationId
    )

    private const val PATH_HISTORY = "history"
    private const val PATH_AUTOMATIC = "automatic"
    private const val PATH_PROGRAM = "program"
}
