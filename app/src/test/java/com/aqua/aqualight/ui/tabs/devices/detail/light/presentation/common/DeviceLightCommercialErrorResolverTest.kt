package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightTemperatureSensorState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightCommercialErrorResolverTest {

    @Test
    fun `every adaptation failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightAdaptationFailure.UNAVAILABLE to
                R.string.device_light_adaptation_operation_error,
            DeviceLightAdaptationFailure.NOT_CONNECTED to
                R.string.device_light_adaptation_not_connected_error,
            DeviceLightAdaptationFailure.UNSUPPORTED to
                R.string.device_light_adaptation_unsupported_error,
            DeviceLightAdaptationFailure.STALE_REVISION to
                R.string.device_light_adaptation_stale_error,
            DeviceLightAdaptationFailure.CLOCK_NOT_READY to
                R.string.device_light_adaptation_clock_error,
            DeviceLightAdaptationFailure.INVALID_REQUEST to
                R.string.device_light_adaptation_invalid_error,
            DeviceLightAdaptationFailure.REJECTED to
                R.string.device_light_adaptation_operation_error,
            DeviceLightAdaptationFailure.INVALID_DATA to
                R.string.device_light_adaptation_operation_error
        )

        assertMessages(
            DeviceLightAdaptationFailure.entries.toSet(),
            expectedMessages
        ) { failure -> failure.toCommercialLightError() }
    }

    @Test
    fun `every automatic failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightAutomaticFailure.UNAVAILABLE to R.string.device_light_auto_operation_error,
            DeviceLightAutomaticFailure.NOT_CONNECTED to
                R.string.device_light_auto_editor_not_connected,
            DeviceLightAutomaticFailure.UNSUPPORTED to R.string.device_light_auto_operation_error,
            DeviceLightAutomaticFailure.STALE_REVISION to
                R.string.device_light_auto_editor_stale,
            DeviceLightAutomaticFailure.CAPACITY_REACHED to
                R.string.device_light_auto_editor_capacity,
            DeviceLightAutomaticFailure.OVERLAP to R.string.device_light_auto_editor_overlap,
            DeviceLightAutomaticFailure.NOT_FOUND to R.string.device_light_auto_editor_not_found,
            DeviceLightAutomaticFailure.REJECTED to R.string.device_light_auto_operation_error,
            DeviceLightAutomaticFailure.INVALID_DATA to R.string.device_light_auto_operation_error
        )

        assertMessages(
            DeviceLightAutomaticFailure.entries.toSet(),
            expectedMessages
        ) { failure -> failure.toCommercialLightError() }
    }

    @Test
    fun `every custom failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightCustomFailure.UNAVAILABLE to R.string.device_light_custom_operation_error,
            DeviceLightCustomFailure.NOT_CONNECTED to
                R.string.device_light_error_not_connected_message,
            DeviceLightCustomFailure.UNSUPPORTED to R.string.device_light_custom_operation_error,
            DeviceLightCustomFailure.STALE_REVISION to R.string.device_light_custom_stale_error,
            DeviceLightCustomFailure.REJECTED to R.string.device_light_custom_operation_error,
            DeviceLightCustomFailure.INVALID_DATA to R.string.device_light_custom_operation_error
        )

        assertMessages(DeviceLightCustomFailure.entries.toSet(), expectedMessages) { failure ->
            failure.toCommercialLightError()
        }
    }

    @Test
    fun `every library failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightLibraryFailure.UNAVAILABLE to R.string.device_light_library_operation_error,
            DeviceLightLibraryFailure.NOT_CONNECTED to
                R.string.device_light_error_not_connected_message,
            DeviceLightLibraryFailure.UNSUPPORTED to R.string.device_light_library_operation_error,
            DeviceLightLibraryFailure.REJECTED to R.string.device_light_library_operation_error,
            DeviceLightLibraryFailure.INVALID_DATA to R.string.device_light_library_operation_error,
            DeviceLightLibraryFailure.INVALID_NAME to
                R.string.device_light_library_name_invalid_error,
            DeviceLightLibraryFailure.DUPLICATE_NAME to
                R.string.device_light_library_name_duplicate_error,
            DeviceLightLibraryFailure.NOT_FOUND to R.string.device_light_library_operation_error,
            DeviceLightLibraryFailure.INCOMPATIBLE to R.string.device_light_library_operation_error
        )

        assertMessages(DeviceLightLibraryFailure.entries.toSet(), expectedMessages) { failure ->
            failure.toCommercialLightError()
        }
    }

    @Test
    fun `library read failure uses read-specific commercial copy`() {
        val offline = DeviceLightLibraryFailure.NOT_CONNECTED.toCommercialLightReadError()
        val invalidData = DeviceLightLibraryFailure.INVALID_DATA.toCommercialLightReadError()

        assertEquals(R.string.device_light_library_error_title, offline.titleRes)
        assertEquals(R.string.device_light_library_error_message, offline.messageRes)
        assertEquals(R.string.device_light_library_error_title, invalidData.titleRes)
        assertEquals(R.string.device_light_library_error_message, invalidData.messageRes)
    }

    @Test
    fun `every manual failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightManualFailure.UNAVAILABLE to R.string.device_light_manual_operation_error,
            DeviceLightManualFailure.NOT_CONNECTED to
                R.string.device_light_manual_not_connected_error,
            DeviceLightManualFailure.UNSUPPORTED to R.string.device_light_manual_operation_error,
            DeviceLightManualFailure.REJECTED to R.string.device_light_manual_operation_error,
            DeviceLightManualFailure.INVALID_DATA to R.string.device_light_manual_operation_error
        )

        assertMessages(DeviceLightManualFailure.entries.toSet(), expectedMessages) { failure ->
            failure.toCommercialLightError()
        }
    }

    @Test
    fun `every system failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceLightSystemFailure.UNAVAILABLE to R.string.device_light_system_operation_error,
            DeviceLightSystemFailure.NOT_CONNECTED to
                R.string.device_light_system_not_connected_error,
            DeviceLightSystemFailure.UNSUPPORTED to
                R.string.device_light_system_unsupported_error,
            DeviceLightSystemFailure.REJECTED to R.string.device_light_system_operation_error,
            DeviceLightSystemFailure.INVALID_DATA to
                R.string.device_light_system_invalid_data_error
        )

        assertMessages(DeviceLightSystemFailure.entries.toSet(), expectedMessages) { failure ->
            failure.toCommercialLightError()
        }
    }

    @Test
    fun `every system sensor state resolves to centralized customer copy`() {
        val expected = mapOf(
            DeviceLightTemperatureSensorState.HEALTHY to
                R.string.device_light_system_sensor_healthy,
            DeviceLightTemperatureSensorState.NOT_DETECTED to
                R.string.device_light_system_sensor_not_detected,
            DeviceLightTemperatureSensorState.UNRESPONSIVE to
                R.string.device_light_system_sensor_unresponsive,
            DeviceLightTemperatureSensorState.INVALID_READING to
                R.string.device_light_system_sensor_invalid,
            DeviceLightTemperatureSensorState.STALE_READING to
                R.string.device_light_system_sensor_stale
        )

        assertEquals(DeviceLightTemperatureSensorState.entries.toSet(), expected.keys)
        expected.forEach { (state, expectedRes) ->
            assertEquals(expectedRes, state.toCommercialLightSensorStatusRes())
        }
    }

    @Test
    fun `partial system save overrides its base failure copy`() {
        val message = DeviceLightSystemFailure.REJECTED.toCommercialLightError(
            partialApplyPossible = true
        )

        assertEquals(R.string.device_light_error_partial_apply_title, message.titleRes)
        assertEquals(R.string.device_light_system_partial_save_error, message.messageRes)
    }

    private fun <Failure> assertMessages(
        failures: Set<Failure>,
        expectedMessages: Map<Failure, Int>,
        resolve: (Failure) -> DeviceLightCommercialErrorMessage
    ) {
        assertEquals(failures, expectedMessages.keys)
        expectedMessages.forEach { (failure, messageRes) ->
            assertEquals(messageRes, resolve(failure).messageRes)
        }
    }
}
