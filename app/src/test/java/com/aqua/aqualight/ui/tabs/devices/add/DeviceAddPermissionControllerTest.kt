package com.aqua.aqualight.ui.tabs.devices.add

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAddPermissionControllerTest {

    private val controller = DeviceAddPermissionController()

    @Test
    fun `first denial can request permission`() {
        assertEquals(
            DeviceAddPermissionController.NextAction.REQUEST_PERMISSION,
            controller.decideNextAction(
                allGranted = false,
                requestedBefore = false,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `permanent denial opens app settings`() {
        assertEquals(
            DeviceAddPermissionController.NextAction.OPEN_APP_SETTINGS,
            controller.decideNextAction(
                allGranted = false,
                requestedBefore = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `rationale state can request permission again`() {
        assertEquals(
            DeviceAddPermissionController.NextAction.REQUEST_PERMISSION,
            controller.decideNextAction(
                allGranted = false,
                requestedBefore = true,
                shouldShowRationale = true
            )
        )
    }
}
