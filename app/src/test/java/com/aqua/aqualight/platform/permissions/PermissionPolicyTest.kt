package com.aqua.aqualight.platform.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun `camera capabilities use camera permission on every supported API`() {
        listOf(27, 30, 31, 33, 36).forEach { sdk ->
            assertEquals(
                listOf(Manifest.permission.CAMERA),
                PermissionPolicy.requiredPermissions(AppCapability.CAMERA_PHOTO, sdk)
            )
            assertEquals(
                listOf(Manifest.permission.CAMERA),
                PermissionPolicy.requiredPermissions(AppCapability.CAMERA_QR, sdk)
            )
        }
    }

    @Test
    fun `BLE scan uses location through API 30 and nearby devices from API 31`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_SCAN, 27)
        )
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_SCAN, 30)
        )
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_SCAN, 31)
        )
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_SCAN, 36)
        )
    }

    @Test
    fun `BLE connect is runtime permission only from API 31`() {
        assertEquals(
            emptyList<String>(),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_CONNECT, 30)
        )
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_CONNECT, 31)
        )
    }

    @Test
    fun `BLE provisioning requests complete permission set`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_PROVISIONING, 27)
        )
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            PermissionPolicy.requiredPermissions(AppCapability.BLE_PROVISIONING, 31)
        )
    }

    @Test
    fun `WiFi SSID uses fine location across current implementation matrix`() {
        listOf(27, 30, 31, 33, 36).forEach { sdk ->
            assertEquals(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PermissionPolicy.requiredPermissions(AppCapability.WIFI_SSID, sdk)
            )
        }
    }

    @Test
    fun `notification permission starts at API 33`() {
        assertEquals(
            emptyList<String>(),
            PermissionPolicy.requiredPermissions(AppCapability.NOTIFICATIONS, 32)
        )
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            PermissionPolicy.requiredPermissions(AppCapability.NOTIFICATIONS, 33)
        )
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            PermissionPolicy.requiredPermissions(AppCapability.NOTIFICATIONS, 36)
        )
    }

    @Test
    fun `decision contract distinguishes request rationale and permanent denial`() {
        assertEquals(
            PermissionDecision.GRANTED,
            PermissionPolicy.decide(
                allGranted = true,
                requestedBefore = true,
                shouldShowRationale = false
            )
        )
        assertEquals(
            PermissionDecision.REQUEST,
            PermissionPolicy.decide(
                allGranted = false,
                requestedBefore = false,
                shouldShowRationale = false
            )
        )
        assertEquals(
            PermissionDecision.RATIONALE,
            PermissionPolicy.decide(
                allGranted = false,
                requestedBefore = true,
                shouldShowRationale = true
            )
        )
        assertEquals(
            PermissionDecision.OPEN_SETTINGS,
            PermissionPolicy.decide(
                allGranted = false,
                requestedBefore = true,
                shouldShowRationale = false
            )
        )
    }
}
