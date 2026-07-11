package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

internal enum class DevicePermissionAction {
    GRANTED,
    REQUEST_PERMISSION,
    OPEN_APP_SETTINGS
}

internal fun resolveDevicePermissionAction(
    isGranted: Boolean,
    allDeniedPermissionsRequestedBefore: Boolean,
    anyDeniedPermissionShowsRationale: Boolean
): DevicePermissionAction {
    return when {
        isGranted -> DevicePermissionAction.GRANTED
        !allDeniedPermissionsRequestedBefore -> DevicePermissionAction.REQUEST_PERMISSION
        anyDeniedPermissionShowsRationale -> DevicePermissionAction.REQUEST_PERMISSION
        else -> DevicePermissionAction.OPEN_APP_SETTINGS
    }
}

internal class DeviceAddPermissionController {

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
            hasPermission(context, permission)
        }
    }

    fun hasBlePermissionsFromResult(
        context: Context,
        result: Map<String, Boolean>
    ): Boolean {
        return blePermissions().all { permission ->
            result[permission] == true || hasPermission(context, permission)
        }
    }

    fun cameraPermissionAction(fragment: Fragment): DevicePermissionAction {
        return permissionAction(
            fragment = fragment,
            permissions = arrayOf(Manifest.permission.CAMERA)
        )
    }

    fun blePermissionAction(fragment: Fragment): DevicePermissionAction {
        return permissionAction(
            fragment = fragment,
            permissions = blePermissions()
        )
    }

    fun markCameraPermissionRequested(context: Context) {
        markPermissionsRequested(
            context = context,
            permissions = arrayOf(Manifest.permission.CAMERA)
        )
    }

    fun markBlePermissionsRequested(context: Context) {
        markPermissionsRequested(
            context = context,
            permissions = blePermissions()
        )
    }

    private fun permissionAction(
        fragment: Fragment,
        permissions: Array<String>
    ): DevicePermissionAction {
        val context = fragment.requireContext()
        val deniedPermissions = permissions.filterNot { permission ->
            hasPermission(context, permission)
        }

        if (deniedPermissions.isEmpty()) {
            return DevicePermissionAction.GRANTED
        }

        val allDeniedPermissionsRequestedBefore = deniedPermissions.all { permission ->
            wasPermissionRequested(context, permission)
        }
        val anyDeniedPermissionShowsRationale = deniedPermissions.any { permission ->
            fragment.shouldShowRequestPermissionRationale(permission)
        }

        return resolveDevicePermissionAction(
            isGranted = false,
            allDeniedPermissionsRequestedBefore = allDeniedPermissionsRequestedBefore,
            anyDeniedPermissionShowsRationale = anyDeniedPermissionShowsRationale
        )
    }

    private fun markPermissionsRequested(
        context: Context,
        permissions: Array<String>
    ) {
        val editor = permissionHistory(context).edit()
        permissions.forEach { permission ->
            editor.putBoolean(permissionHistoryKey(permission), true)
        }
        editor.apply()
    }

    private fun wasPermissionRequested(
        context: Context,
        permission: String
    ): Boolean {
        return permissionHistory(context).getBoolean(
            permissionHistoryKey(permission),
            false
        )
    }

    private fun hasPermission(
        context: Context,
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionHistory(context: Context) =
        context.applicationContext.getSharedPreferences(
            PERMISSION_HISTORY_PREFERENCES,
            Context.MODE_PRIVATE
        )

    private fun permissionHistoryKey(permission: String): String {
        return "$PERMISSION_HISTORY_KEY_PREFIX$permission"
    }

    private companion object {
        const val PERMISSION_HISTORY_PREFERENCES =
            "aql_device_permission_history_v1"
        const val PERMISSION_HISTORY_KEY_PREFIX = "requested_"
    }
}
