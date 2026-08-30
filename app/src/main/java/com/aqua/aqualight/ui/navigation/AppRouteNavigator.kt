package com.aqua.aqualight.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
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

    fun openDosingChannel(
        navController: NavController,
        target: DeviceDosingChannelNavigationTarget
    ): AppRouteOpenResult {
        val deviceUid = target.deviceUid.trim()
        val slotId = target.slotId.trim()
        val pumpCount = target.pumpCount
        val channelNumber = target.channelNumber
        val lastCalibratedAtEpochSeconds = target.lastCalibratedAtEpochSeconds
        val destinationId = target.destination.destinationId
        val hasRouteIdentity = deviceUid.isNotBlank() && slotId.isNotBlank()
        val hasValidChannel =
            pumpCount > 0 &&
                channelNumber in 1..pumpCount
        val hasValidCalibrationTimestamp =
            target.destination != DeviceDosingChannelDestination.DETAIL ||
                lastCalibratedAtEpochSeconds > 0L

        return when {
            !hasRouteIdentity || !hasValidChannel || !hasValidCalibrationTimestamp ->
                AppRouteOpenResult.REJECTED
            DosingChannelRouteIdempotencyPolicy.isAlreadyOpen(
                current = DosingChannelRouteIdentity(
                    destinationId = navController.currentDestination?.id,
                    deviceUid = navController.currentBackStackEntry
                        ?.arguments
                        ?.getString(ARG_DEVICE_UID),
                    slotId = navController.currentBackStackEntry
                        ?.arguments
                        ?.getString(ARG_SLOT_ID)
                ),
                requested = DosingChannelRouteIdentity(
                    destinationId = destinationId,
                    deviceUid = deviceUid,
                    slotId = slotId
                )
            ) -> AppRouteOpenResult.ALREADY_OPEN
            navController.currentDestination?.id != dosingRootDestinationId ->
                AppRouteOpenResult.REJECTED
            else -> {
                navController.navigate(
                    deepLinkRequest(
                        uri = dosingChannelRouteUri(
                            target = target,
                            deviceUid = deviceUid,
                            slotId = slotId
                        )
                    ),
                    dosingChannelRouteOptions()
                )
                AppRouteOpenResult.OPENED
            }
        }
    }

    private fun dosingChannelRouteUri(
        target: DeviceDosingChannelNavigationTarget,
        deviceUid: String,
        slotId: String
    ): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY)
        .appendPath(PATH_DEVICE)
        .appendPath(deviceUid)
        .appendPath(PATH_DOSING)
        .appendPath(PATH_CHANNEL)
        .appendPath(slotId)
        .appendPath(target.destination.path)
        .appendQueryParameter(QUERY_PUMP_COUNT, target.pumpCount.toString())
        .appendQueryParameter(QUERY_CHANNEL_NUMBER, target.channelNumber.toString())
        .apply {
            if (target.destination == DeviceDosingChannelDestination.DETAIL) {
                appendQueryParameter(
                    QUERY_LAST_CALIBRATED_AT_EPOCH_SECONDS,
                    target.lastCalibratedAtEpochSeconds.toString()
                )
            }
        }
        .build()

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

    private fun dosingChannelRouteOptions() = navOptions {
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
    private const val PATH_DOSING = "dosing"
    private const val PATH_CHANNEL = "channel"
    private const val PATH_CALIBRATION = "calibration"
    private const val PATH_DETAIL = "detail"
    private const val QUERY_START_TAB = "startTab"
    private const val QUERY_PUMP_COUNT = "pumpCount"
    private const val QUERY_CHANNEL_NUMBER = "channelNumber"
    private const val QUERY_LAST_CALIBRATED_AT_EPOCH_SECONDS =
        "lastCalibratedAtEpochSeconds"
    private const val ARG_DEVICE_UID = "deviceUid"
    private const val ARG_SLOT_ID = "slotId"

    private val firmwareDestinationId = R.id.deviceFirmwareUpdateFragment
    private val dosingRootDestinationId = R.id.deviceDosingRootFragment

    private val DeviceDosingChannelDestination.destinationId: Int
        get() = when (this) {
            DeviceDosingChannelDestination.CALIBRATION ->
                R.id.deviceDosingChannelCalibrationFragment
            DeviceDosingChannelDestination.DETAIL -> R.id.deviceDosingChannelDetailFragment
        }

    private val DeviceDosingChannelDestination.path: String
        get() = when (this) {
            DeviceDosingChannelDestination.CALIBRATION -> PATH_CALIBRATION
            DeviceDosingChannelDestination.DETAIL -> PATH_DETAIL
        }

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

    internal object DosingChannelRouteIdempotencyPolicy {
        fun isAlreadyOpen(
            current: DosingChannelRouteIdentity,
            requested: DosingChannelRouteIdentity
        ): Boolean {
            val deviceUid = requested.deviceUid.orEmpty().trim()
            val slotId = requested.slotId.orEmpty().trim()
            return deviceUid.isNotBlank() &&
                slotId.isNotBlank() &&
                current.destinationId == requested.destinationId &&
                current.deviceUid.orEmpty().trim() == deviceUid &&
                current.slotId.orEmpty().trim() == slotId
        }
    }

    internal data class DosingChannelRouteIdentity(
        val destinationId: Int?,
        val deviceUid: String?,
        val slotId: String?
    )
}
