package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLightPlanNavigationTest {

    @Test
    fun manualCallToActionOpensAutomaticPrograms() {
        assertEquals(
            DeviceLightPlanDestination.AutomaticPrograms,
            DeviceLightControlMode.MANUAL.planDestination(activeAutomaticProgramId = null)
        )
    }

    @Test
    fun autoProgramOpensItsEditorWhenFirmwareReportsAnActiveProgram() {
        assertEquals(
            DeviceLightPlanDestination.AutomaticProgramEditor("program-1"),
            DeviceLightControlMode.AUTOMATIC.planDestination("program-1")
        )
    }

    @Test
    fun autoModeFallsBackToProgramListOutsideAnActiveSpan() {
        assertEquals(
            DeviceLightPlanDestination.AutomaticPrograms,
            DeviceLightControlMode.AUTOMATIC.planDestination(activeAutomaticProgramId = null)
        )
    }

    @Test
    fun customProgramOpensCustomCurveEditor() {
        assertEquals(
            DeviceLightPlanDestination.CustomCurveEditor,
            DeviceLightControlMode.CUSTOM.planDestination(activeAutomaticProgramId = null)
        )
    }

    @Test
    fun unavailableModeDoesNotExposePlanNavigation() {
        assertNull(null.planDestination(activeAutomaticProgramId = null))
    }
}
