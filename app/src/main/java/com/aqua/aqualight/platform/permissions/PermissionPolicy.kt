package com.aqua.aqualight.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Single source of truth for AquaLight runtime-permission decisions.
 *
 * Screens submit a product [AppCapability] and receive one of the four standard
 * [PermissionDecision] values. API-level branching, grant checks, rationale checks,
 * and permanent-denial detection stay in this class.
 */
class PermissionPolicy(
    private val context: Context,
    private val historyStore: PermissionRequestHistoryStore =
        PermissionRequestHistoryStore(context)
) {

    fun evaluate(fragment: Fragment, capability: AppCapability): PermissionDecision {
        val missing = missingPermissions(capability)
        if (missing.isEmpty()) return PermissionDecision.GRANTED

        val shouldShowRationale = missing.any(
            fragment::shouldShowRequestPermissionRationale
        )
        val requestedBefore = historyStore.wereAllRequested(missing)

        return decide(
            allGranted = false,
            requestedBefore = requestedBefore,
            shouldShowRationale = shouldShowRationale
        )
    }

    fun isGranted(capability: AppCapability): Boolean {
        return missingPermissions(capability).isEmpty()
    }

    fun requiredPermissions(capability: AppCapability): Array<String> {
        return requiredPermissions(capability, Build.VERSION.SDK_INT).toTypedArray()
    }

    fun markRequested(capability: AppCapability) {
        historyStore.markRequested(requiredPermissions(capability).asList())
    }

    private fun missingPermissions(capability: AppCapability): List<String> {
        return requiredPermissions(capability).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        fun requiredPermissions(
            capability: AppCapability,
            sdkInt: Int
        ): List<String> {
            return when (capability) {
                AppCapability.CAMERA_PHOTO,
                AppCapability.CAMERA_QR -> listOf(Manifest.permission.CAMERA)

                AppCapability.BLE_SCAN -> if (sdkInt >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }

                AppCapability.BLE_CONNECT -> if (sdkInt >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    emptyList()
                }

                AppCapability.BLE_PROVISIONING -> if (sdkInt >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }

                // AquaLight currently reads the connected SSID. That operation remains
                // location-sensitive across the supported API range (27..36).
                AppCapability.WIFI_SSID -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)

                AppCapability.NOTIFICATIONS -> if (
                    sdkInt >= Build.VERSION_CODES.TIRAMISU
                ) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
            }
        }

        internal fun decide(
            allGranted: Boolean,
            requestedBefore: Boolean,
            shouldShowRationale: Boolean
        ): PermissionDecision {
            return when {
                allGranted -> PermissionDecision.GRANTED
                shouldShowRationale -> PermissionDecision.RATIONALE
                requestedBefore -> PermissionDecision.OPEN_SETTINGS
                else -> PermissionDecision.REQUEST
            }
        }
    }
}
