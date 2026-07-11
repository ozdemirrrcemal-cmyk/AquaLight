package com.aqua.aqualight.ui.tabs.devices.add

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePermissionActionResolverTest {

    @Test
    fun grantedPermission_continuesImmediately() {
        assertEquals(
            DevicePermissionAction.GRANTED,
            resolveDevicePermissionAction(
                isGranted = true,
                allDeniedPermissionsRequestedBefore = false,
                anyDeniedPermissionShowsRationale = false
            )
        )
    }

    @Test
    fun neverRequestedPermission_opensSystemPermissionDialog() {
        assertEquals(
            DevicePermissionAction.REQUEST_PERMISSION,
            resolveDevicePermissionAction(
                isGranted = false,
                allDeniedPermissionsRequestedBefore = false,
                anyDeniedPermissionShowsRationale = false
            )
        )
    }

    @Test
    fun retryableDenial_opensSystemPermissionDialogAgain() {
        assertEquals(
            DevicePermissionAction.REQUEST_PERMISSION,
            resolveDevicePermissionAction(
                isGranted = false,
                allDeniedPermissionsRequestedBefore = true,
                anyDeniedPermissionShowsRationale = true
            )
        )
    }

    @Test
    fun permanentDenial_opensApplicationSettings() {
        assertEquals(
            DevicePermissionAction.OPEN_APP_SETTINGS,
            resolveDevicePermissionAction(
                isGranted = false,
                allDeniedPermissionsRequestedBefore = true,
                anyDeniedPermissionShowsRationale = false
            )
        )
    }
}
