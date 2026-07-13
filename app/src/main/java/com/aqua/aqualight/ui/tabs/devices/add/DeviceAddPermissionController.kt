package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class DeviceAddPermissionController {

    enum class NextAction {
        GRANTED,
        REQUEST_PERMISSION,
        OPEN_APP_SETTINGS
    }

    fun blePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasBlePermissions(context: Context): Boolean {
        return blePermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBlePermissionsFromResult(
        context: Context,
        result: Map<String, Boolean>
    ): Boolean {
        return blePermissions().all { permission ->
            result[permission] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun cameraNextAction(fragment: Fragment): NextAction {
        return nextAction(
            fragment = fragment,
            permissions = arrayOf(Manifest.permission.CAMERA),
            requestKey = CAMERA_REQUESTED_KEY
        )
    }

    fun bleNextAction(fragment: Fragment): NextAction {
        return nextAction(
            fragment = fragment,
            permissions = blePermissions(),
            requestKey = BLE_REQUESTED_KEY
        )
    }

    fun markCameraPermissionRequested(context: Context) {
        markRequested(context, CAMERA_REQUESTED_KEY)
    }

    fun markBlePermissionRequested(context: Context) {
        markRequested(context, BLE_REQUESTED_KEY)
    }

    internal fun decideNextAction(
        allGranted: Boolean,
        requestedBefore: Boolean,
        shouldShowRationale: Boolean
    ): NextAction {
        return when {
            allGranted -> NextAction.GRANTED
            requestedBefore && !shouldShowRationale -> NextAction.OPEN_APP_SETTINGS
            else -> NextAction.REQUEST_PERMISSION
        }
    }

    private fun nextAction(
        fragment: Fragment,
        permissions: Array<String>,
        requestKey: String
    ): NextAction {
        val context = fragment.requireContext()
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
        val shouldShowRationale = permissions.any { permission ->
            fragment.shouldShowRequestPermissionRationale(permission)
        }
        val requestedBefore = preferences(context).getBoolean(requestKey, false)

        return decideNextAction(
            allGranted = allGranted,
            requestedBefore = requestedBefore,
            shouldShowRationale = shouldShowRationale
        )
    }

    private fun markRequested(context: Context, requestKey: String) {
        preferences(context)
            .edit()
            .putBoolean(requestKey, true)
            .apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PERMISSION_PREFERENCES,
            Context.MODE_PRIVATE
        )

    private companion object {
        const val PERMISSION_PREFERENCES = "device_add_permission_state"
        const val CAMERA_REQUESTED_KEY = "camera_requested"
        const val BLE_REQUESTED_KEY = "ble_requested"
    }
}
