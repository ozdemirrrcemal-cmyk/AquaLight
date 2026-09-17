package com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.mapper

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFirmwareUpdateProgressPresentationMapperTest {

    @Test
    fun `availability failure does not claim an installation was stopped`() {
        val state = failedState(DeviceOtaFailureStage.AVAILABILITY_CHECK)

        assertEquals(
            R.string.device_settings_update_phase_check_failed,
            DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state)
        )
        assertEquals(
            R.string.device_settings_retry_update_action,
            DeviceFirmwareUpdateProgressPresentationMapper.action(state).textRes
        )
    }

    @Test
    fun `execution failure keeps stopped-installation recovery copy`() {
        val state = failedState(DeviceOtaFailureStage.UPDATE_EXECUTION)

        assertEquals(
            R.string.device_settings_update_phase_failed_recoverable,
            DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state)
        )
    }

    @Test
    fun `unpublished release is a completed neutral check`() {
        val state = DeviceFirmwareUpdateUiState(
            mode = DeviceFirmwareUpdateMode.RELEASE_NOT_PUBLISHED
        )

        assertEquals(
            R.string.device_settings_update_phase_not_published,
            DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state)
        )
        assertEquals(
            R.string.device_settings_update_done_action,
            DeviceFirmwareUpdateProgressPresentationMapper.action(state).textRes
        )
    }

    private fun failedState(stage: DeviceOtaFailureStage) = DeviceFirmwareUpdateUiState(
        mode = DeviceFirmwareUpdateMode.FAILED,
        failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            stage = stage
        )
    )
}
