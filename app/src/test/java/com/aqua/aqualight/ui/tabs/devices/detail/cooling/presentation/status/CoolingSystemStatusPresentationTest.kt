package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPwmOutputHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorReadingHealth
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingSystemStatusPresentationTest {
    @Test
    fun `every firmware diagnostic enum maps to presentation copy`() {
        DeviceCoolingAlarmCode.entries.forEach { code ->
            assertTrue(code.toSystemStatusCopy().titleRes != 0)
            assertTrue(code.toSystemStatusCopy().messageRes != 0)
        }
        DeviceCoolingAlarmSeverity.entries.forEach { severity ->
            assertTrue(severity.toStatusTextRes() != 0)
            severity.toStatusTone()
        }
        DeviceCoolingPwmOutputHealth.entries.forEach { health ->
            assertTrue(health.toStatusTextRes() != 0)
            health.toStatusTone()
        }
        DeviceCoolingFanHealth.entries.forEach { health ->
            assertTrue(health.toStatusTextRes() != 0)
        }
        DeviceCoolingSensorReadingHealth.entries.forEach { health ->
            assertTrue(health.toStatusTextRes() != 0)
        }
        DeviceCoolingControlMode.entries.forEach { mode ->
            assertTrue(mode.toStatusTextRes() != 0)
        }
        DeviceCoolingOperatingState.entries.forEach { state ->
            assertTrue(state.toStatusTextRes() != 0)
        }
        DeviceCoolingControlReason.entries.forEach { reason ->
            assertTrue(reason.toStatusTextRes() != 0)
        }
    }
}
