package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.DeviceDosingCalibrationError
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.toOperationalFailureOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingCommercialErrorResolverTest {

    @Test
    fun `every channel rejection preserves detail and reservoir copy`() {
        val expectedMessages = channelMessages()

        assertEquals(DeviceDosingChannelRejection.entries.toSet(), expectedMessages.keys)
        listOf(
            DeviceDosingErrorContext.CHANNEL_DETAIL,
            DeviceDosingErrorContext.MANUAL_DOSE,
            DeviceDosingErrorContext.RESERVOIR_SAVE
        ).forEach { context ->
            expectedMessages.forEach { (failure, messageRes) ->
                val message = failure.toCommercialDosingError(context)
                assertEquals(messageRes, message.messageRes)
                assertEquals(DeviceDosingCommercialErrorSeverity.ERROR, message.severity)
            }
        }
    }

    @Test
    fun `every channel rejection preserves plan save copy and warning severity`() {
        val expectedMessages = mapOf(
            DeviceDosingChannelRejection.INVALID_DRAFT to
                R.string.device_dosing_plan_invalid_schedule,
            DeviceDosingChannelRejection.NOT_EDITABLE to
                R.string.device_dosing_plan_rejected_not_editable,
            DeviceDosingChannelRejection.NOT_CALIBRATED to
                R.string.device_dosing_plan_rejected_not_calibrated,
            DeviceDosingChannelRejection.BUSY to R.string.device_dosing_plan_rejected_busy,
            DeviceDosingChannelRejection.CONFLICT to R.string.device_dosing_plan_rejected_conflict,
            DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED to
                R.string.device_dosing_error_output_stop_unconfirmed,
            DeviceDosingChannelRejection.UNSAFE to R.string.device_dosing_plan_rejected_unsafe,
            DeviceDosingChannelRejection.UNKNOWN to
                R.string.device_dosing_detail_operation_failed
        )

        assertEquals(DeviceDosingChannelRejection.entries.toSet(), expectedMessages.keys)
        expectedMessages.forEach { (failure, messageRes) ->
            val message = failure.toCommercialDosingError(DeviceDosingErrorContext.PLAN_SAVE)
            assertEquals(messageRes, message.messageRes)
            assertEquals(DeviceDosingCommercialErrorSeverity.WARNING, message.severity)
        }
    }

    @Test
    fun `every calibration failure preserves copy and severity`() {
        val expected = mapOf(
            DeviceDosingCalibrationFailure.CONNECTION to Expected(
                R.string.device_dosing_calibration_connection_error,
                DeviceDosingCommercialErrorSeverity.ERROR
            ),
            DeviceDosingCalibrationFailure.STORAGE to Expected(
                R.string.device_dosing_calibration_storage_error,
                DeviceDosingCommercialErrorSeverity.ERROR
            ),
            DeviceDosingCalibrationFailure.HARDWARE to Expected(
                R.string.device_dosing_calibration_hardware_error,
                DeviceDosingCommercialErrorSeverity.ERROR
            ),
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED to Expected(
                R.string.device_dosing_error_output_stop_unconfirmed,
                DeviceDosingCommercialErrorSeverity.ERROR
            ),
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS to Expected(
                R.string.device_dosing_calibration_operation_in_progress,
                DeviceDosingCommercialErrorSeverity.WARNING
            ),
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY to Expected(
                R.string.device_dosing_calibration_device_time_not_ready,
                DeviceDosingCommercialErrorSeverity.WARNING
            ),
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH to Expected(
                R.string.device_dosing_calibration_state_mismatch,
                DeviceDosingCommercialErrorSeverity.WARNING
            ),
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT to Expected(
                R.string.device_dosing_calibration_invalid_measurement,
                DeviceDosingCommercialErrorSeverity.WARNING
            ),
            DeviceDosingCalibrationFailure.INTERNAL to Expected(
                R.string.device_dosing_calibration_operation_failed,
                DeviceDosingCommercialErrorSeverity.ERROR
            )
        )

        assertEquals(DeviceDosingCalibrationFailure.entries.toSet(), expected.keys)
        expected.forEach { (failure, expectedPresentation) ->
            val actual = failure.toCommercialDosingError()
            assertEquals(expectedPresentation.messageRes, actual.messageRes)
            assertEquals(expectedPresentation.severity, actual.severity)
            assertEquals(R.string.device_dosing_detail_calibration_title, actual.titleRes)
        }
    }

    @Test
    fun `calibration presentation keeps validation local and delegates operations`() {
        val operationalErrors = mapOf(
            DeviceDosingCalibrationError.CONNECTION to DeviceDosingCalibrationFailure.CONNECTION,
            DeviceDosingCalibrationError.STORAGE to DeviceDosingCalibrationFailure.STORAGE,
            DeviceDosingCalibrationError.HARDWARE to DeviceDosingCalibrationFailure.HARDWARE,
            DeviceDosingCalibrationError.OUTPUT_STOP_UNCONFIRMED to
                DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED,
            DeviceDosingCalibrationError.OPERATION_IN_PROGRESS to
                DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS,
            DeviceDosingCalibrationError.DEVICE_TIME_NOT_READY to
                DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY,
            DeviceDosingCalibrationError.CALIBRATION_STATE_MISMATCH to
                DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH,
            DeviceDosingCalibrationError.OPERATION_FAILED to DeviceDosingCalibrationFailure.INTERNAL
        )
        val validationErrors = setOf(
            DeviceDosingCalibrationError.DISPLAY_NAME_REQUIRED,
            DeviceDosingCalibrationError.DISPLAY_NAME_CONTROL_CHARACTER,
            DeviceDosingCalibrationError.DISPLAY_NAME_TOO_LONG,
            DeviceDosingCalibrationError.INVALID_MEASUREMENT
        )

        operationalErrors.forEach { (error, failure) ->
            assertEquals(failure, error.toOperationalFailureOrNull())
        }
        validationErrors.forEach { error ->
            assertEquals(null, error.toOperationalFailureOrNull())
        }
        assertEquals(
            DeviceDosingCalibrationError.entries.toSet(),
            operationalErrors.keys + validationErrors
        )
    }

    @Test
    fun `generic failures preserve context specific copy`() {
        val expected = mapOf(
            DeviceDosingErrorContext.CHANNEL_OPEN to mapOf(
                DeviceDosingOperationFailure.CONNECTION to
                    R.string.device_dosing_channel_open_failed,
                DeviceDosingOperationFailure.UNAVAILABLE to
                    R.string.device_dosing_channel_open_failed,
                DeviceDosingOperationFailure.INTERNAL to
                    R.string.device_dosing_channel_open_failed
            ),
            DeviceDosingErrorContext.CHANNEL_DETAIL to mapOf(
                DeviceDosingOperationFailure.CONNECTION to
                    R.string.device_dosing_detail_operation_failed,
                DeviceDosingOperationFailure.UNAVAILABLE to
                    R.string.device_dosing_detail_error_unavailable,
                DeviceDosingOperationFailure.INTERNAL to
                    R.string.device_dosing_detail_operation_failed
            ),
            DeviceDosingErrorContext.MANUAL_DOSE to mapOf(
                DeviceDosingOperationFailure.CONNECTION to
                    R.string.device_dosing_detail_operation_failed,
                DeviceDosingOperationFailure.UNAVAILABLE to
                    R.string.device_dosing_detail_error_unavailable,
                DeviceDosingOperationFailure.INTERNAL to
                    R.string.device_dosing_detail_operation_failed
            ),
            DeviceDosingErrorContext.PLAN_SAVE to mapOf(
                DeviceDosingOperationFailure.CONNECTION to
                    R.string.device_dosing_detail_operation_failed,
                DeviceDosingOperationFailure.UNAVAILABLE to
                    R.string.device_dosing_plan_unavailable,
                DeviceDosingOperationFailure.INTERNAL to
                    R.string.device_dosing_detail_operation_failed
            ),
            DeviceDosingErrorContext.RESERVOIR_SAVE to genericOperationMessages(),
            DeviceDosingErrorContext.REFILL to genericOperationMessages(),
            DeviceDosingErrorContext.CALIBRATION to mapOf(
                DeviceDosingOperationFailure.CONNECTION to
                    R.string.device_dosing_calibration_connection_error,
                DeviceDosingOperationFailure.UNAVAILABLE to
                    R.string.device_dosing_calibration_operation_failed,
                DeviceDosingOperationFailure.INTERNAL to
                    R.string.device_dosing_calibration_operation_failed
            )
        )

        assertEquals(DeviceDosingErrorContext.entries.toSet(), expected.keys)
        expected.forEach { (context, failures) ->
            assertEquals(DeviceDosingOperationFailure.entries.toSet(), failures.keys)
            failures.forEach { (failure, messageRes) ->
                val message = failure.toCommercialDosingError(context)
                assertEquals(messageRes, message.messageRes)
                assertEquals(DeviceDosingCommercialErrorSeverity.ERROR, message.severity)
            }
        }
    }

    @Test
    fun `every context exposes an existing title resource`() {
        val expectedTitles = mapOf(
            DeviceDosingErrorContext.CHANNEL_OPEN to R.string.device_family_dosing,
            DeviceDosingErrorContext.CHANNEL_DETAIL to R.string.device_family_dosing,
            DeviceDosingErrorContext.MANUAL_DOSE to R.string.device_dosing_detail_manual_title,
            DeviceDosingErrorContext.PLAN_SAVE to R.string.device_dosing_detail_plan_title,
            DeviceDosingErrorContext.RESERVOIR_SAVE to
                R.string.device_dosing_detail_reservoir_title,
            DeviceDosingErrorContext.REFILL to R.string.device_dosing_detail_reservoir_title,
            DeviceDosingErrorContext.CALIBRATION to
                R.string.device_dosing_detail_calibration_title
        )

        assertEquals(DeviceDosingErrorContext.entries.toSet(), expectedTitles.keys)
        expectedTitles.forEach { (context, titleRes) ->
            val message = DeviceDosingOperationFailure.INTERNAL.toCommercialDosingError(context)
            assertEquals(titleRes, message.titleRes)
        }
    }

    private fun channelMessages() = mapOf(
        DeviceDosingChannelRejection.INVALID_DRAFT to
            R.string.device_dosing_detail_error_invalid_input,
        DeviceDosingChannelRejection.NOT_EDITABLE to
            R.string.device_dosing_detail_error_not_editable,
        DeviceDosingChannelRejection.NOT_CALIBRATED to
            R.string.device_dosing_detail_error_calibration_required,
        DeviceDosingChannelRejection.BUSY to R.string.device_dosing_detail_error_busy,
        DeviceDosingChannelRejection.CONFLICT to
            R.string.device_dosing_detail_error_state_changed,
        DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED to
            R.string.device_dosing_error_output_stop_unconfirmed,
        DeviceDosingChannelRejection.UNSAFE to
            R.string.device_dosing_detail_error_safety_blocked,
        DeviceDosingChannelRejection.UNKNOWN to
            R.string.device_dosing_detail_operation_failed
    )

    private fun genericOperationMessages() = DeviceDosingOperationFailure.entries.associateWith {
        R.string.device_dosing_detail_operation_failed
    }

    private data class Expected(
        val messageRes: Int,
        val severity: DeviceDosingCommercialErrorSeverity
    )
}
