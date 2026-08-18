package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ChannelSnapshotMapperControlsTest {

    @Test
    fun `persisted controls are editable only while firmware mutation guard is open`() {
        val editable = controls()

        assertTrue(editable.programEditable)
        assertTrue(editable.reservoirEditable)
        assertTrue(editable.displayNameEditable)

        val blocked = listOf(
            controls(bootReady = false),
            controls(storageHealthy = false),
            controls(activeRun = true),
            controls(calibrationState = "running"),
            controls(calibrationState = "pendingVerification")
        )

        blocked.forEach { controls ->
            assertFalse(controls.programEditable)
            assertFalse(controls.reservoirEditable)
            assertFalse(controls.displayNameEditable)
        }
    }

    @Test
    fun `channel reset requires firmware readiness and healthy storage`() {
        assertTrue(controls().resetSupported)
        assertFalse(controls(bootReady = false).resetSupported)
        assertFalse(controls(storageHealthy = false).resetSupported)
    }

    @Test
    fun `channel reset remains available during active run`() {
        val controls = controls(activeRun = true)

        assertTrue(controls.resetSupported)
        assertFalse(controls.programEditable)
    }

    private fun controls(
        bootReady: Boolean = true,
        storageHealthy: Boolean = true,
        activeRun: Boolean = false,
        calibrationState: String = "idle"
    ): DeviceDosingChannelControls {
        val global = DeviceDosingV1StatusParser.parseGlobal(
            DeviceDosingV1TestFixtures.globalStatus()
                .put("bootReady", bootReady)
                .put("storageHealthy", storageHealthy)
        )
        val detail = DeviceDosingV1StatusParser.parseChannelDetail(
            DeviceDosingV1TestFixtures.channelDetail().also { channel ->
                channel.getJSONObject("calibration").put("state", calibrationState)
                channel.getJSONObject("activeRun")
                    .put("active", activeRun)
                    .put("source", if (activeRun) "manual" else "none")
                    .put("targetAmountMl", if (activeRun) 1.0 else 0.0)
                    .put("remainingMs", if (activeRun) 1_000 else 0)
            }
        )
        return DeviceDosingV1ChannelSnapshotMapper.controls(detail, global)
    }
}
