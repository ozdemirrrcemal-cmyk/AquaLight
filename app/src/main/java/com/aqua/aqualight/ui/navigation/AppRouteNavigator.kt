package com.aqua.aqualight.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.navOptions
import com.aqua.aqualight.NavDevicesDirections
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs

object AppRouteNavigator {

    fun openTankSettings(
        navController: NavController,
        tankId: Long,
        startTab: String = AquariumTabArgs.BASIC
    ) {
        if (tankId <= 0L) {
            return
        }

        navController.navigate(
            deepLinkRequest(
                uri = Uri.Builder()
                    .scheme(SCHEME)
                    .authority(AUTHORITY)
                    .appendPath(PATH_TANK)
                    .appendPath(tankId.toString())
                    .appendPath(PATH_SETTINGS)
                    .appendQueryParameter(QUERY_START_TAB, startTab)
                    .build()
            ),
            standardRouteOptions()
        )
    }

    fun openTaskDetail(
        navController: NavController,
        taskId: Long
    ) {
        if (taskId <= 0L) {
            return
        }

        navController.navigate(
            deepLinkRequest(
                uri = Uri.Builder()
                    .scheme(SCHEME)
                    .authority(AUTHORITY)
                    .appendPath(PATH_CARE_TASK)
                    .appendPath(taskId.toString())
                    .build()
            ),
            standardRouteOptions()
        )
    }

    fun openDeviceFirmwareUpdate(
        navController: NavController,
        deviceUid: String
    ) {
        val normalizedDeviceUid = deviceUid.trim()
        if (normalizedDeviceUid.isBlank()) return

        navController.navigate(
            NavDevicesDirections.actionGlobalDeviceFirmwareUpdateFragment(
                normalizedDeviceUid
            ),
            standardRouteOptions()
        )
    }

    private fun standardRouteOptions() = navOptions {
        anim {
            enter = R.anim.nav_slide_in_right
            exit = R.anim.nav_slide_out_left
            popEnter = R.anim.nav_slide_in_left
            popExit = R.anim.nav_slide_out_right
        }
    }

    private fun deepLinkRequest(
        uri: Uri
    ): NavDeepLinkRequest {
        return NavDeepLinkRequest.Builder
            .fromUri(uri)
            .build()
    }

    private const val SCHEME = "aqualight"
    private const val AUTHORITY = "app"

    private const val PATH_TANK = "tank"
    private const val PATH_SETTINGS = "settings"
    private const val PATH_CARE_TASK = "care-task"

    private const val QUERY_START_TAB = "startTab"
}
