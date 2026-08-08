package com.aqua.aqualight.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs

enum class AppRouteOpenResult {
    OPENED,
    ALREADY_OPEN,
    REJECTED
}

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
    ): AppRouteOpenResult {
        val normalizedDeviceUid = deviceUid.trim()
        return when {
            normalizedDeviceUid.isBlank() -> AppRouteOpenResult.REJECTED
            DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen(
                currentDestinationId = navController.currentDestination?.id,
                currentDeviceUid = navController.currentBackStackEntry
                    ?.arguments
                    ?.getString(ARG_DEVICE_UID),
                requestedDeviceUid = normalizedDeviceUid
            ) -> AppRouteOpenResult.ALREADY_OPEN
            else -> {
                navController.navigate(
                    deepLinkRequest(
                        uri = Uri.Builder()
                            .scheme(SCHEME)
                            .authority(AUTHORITY)
                            .appendPath(PATH_DEVICE)
                            .appendPath(normalizedDeviceUid)
                            .appendPath(PATH_FIRMWARE_UPDATE)
                            .build()
                    ),
                    firmwareRouteOptions()
                )
                AppRouteOpenResult.OPENED
            }
        }
    }

    private fun standardRouteOptions() = navOptions {
        anim {
            enter = R.anim.nav_slide_in_right
            exit = R.anim.nav_slide_out_left
            popEnter = R.anim.nav_slide_in_left
            popExit = R.anim.nav_slide_out_right
        }
    }

    private fun firmwareRouteOptions() = navOptions {
        launchSingleTop = true
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
    private const val PATH_DEVICE = "device"
    private const val PATH_FIRMWARE_UPDATE = "firmware-update"
    private const val QUERY_START_TAB = "startTab"
    private const val ARG_DEVICE_UID = "deviceUid"

    private val firmwareDestinationId = R.id.deviceFirmwareUpdateFragment

    internal object DeviceFirmwareRouteIdempotencyPolicy {
        fun isAlreadyOpen(
            currentDestinationId: Int?,
            currentDeviceUid: String?,
            requestedDeviceUid: String
        ): Boolean {
            val requested = requestedDeviceUid.trim()
            val current = currentDeviceUid.orEmpty().trim()
            return requested.isNotBlank() &&
                currentDestinationId == firmwareDestinationId &&
                current == requested
        }
    }
}
