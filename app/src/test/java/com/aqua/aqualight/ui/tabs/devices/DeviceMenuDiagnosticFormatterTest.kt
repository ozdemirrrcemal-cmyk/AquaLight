package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOperationCommandDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuDiagnosticFormatterTest {

    @Test
    fun `diagnostic text exposes the exact stage and outcome`() {
        val text = formatDeviceMenuDiagnostic(
            reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
            diagnostic = DeviceOperationDiagnostic(
                stage = "COOLING_COMMAND",
                outcome = "PROTOCOL_ERROR",
                command = DeviceOperationCommandDiagnostic(
                    deviceUid = "cooling-1",
                    module = "cooling",
                    action = "status.get",
                    messageId = "message-1",
                    connectionGeneration = 3L
                ),
                detail = "Unexpected payload"
            )
        )

        assertTrue(text.contains("stage=COOLING_COMMAND"))
        assertTrue(text.contains("outcome=PROTOCOL_ERROR"))
        assertTrue(text.contains("command=cooling.status.get"))
        assertTrue(text.contains("requestGeneration=3"))
    }
}
