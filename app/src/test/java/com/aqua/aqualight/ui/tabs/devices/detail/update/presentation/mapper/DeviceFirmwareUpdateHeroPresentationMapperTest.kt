package com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.mapper

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceFirmwareUpdateHeroPresentationMapperTest {

    @Test
    fun `optional release maps install copy and positive treatment`() {
        val state = updateState(mandatory = false, targetVersion = TARGET_VERSION)

        assertEquals(
            DeviceFirmwareUpdateHeroPresentation(
                titleRes = R.string.device_settings_update_hero_title_available,
                summary = DeviceFirmwareUpdateText(
                    R.string.device_settings_update_hero_summary_available_version,
                    TARGET_VERSION
                ),
                statusTextRes = R.string.device_settings_update_status_available,
                statusColorRes = R.color.aqua_accent_positive,
                statusBackgroundColorRes = R.color.aqua_surface_positive,
                icon = firmwareIcon()
            ),
            DeviceFirmwareUpdateHeroPresentationMapper.map(state)
        )
    }

    @Test
    fun `mandatory release maps required copy and warning treatment`() {
        val state = updateState(mandatory = true, targetVersion = TARGET_VERSION)

        assertEquals(
            DeviceFirmwareUpdateHeroPresentation(
                titleRes = R.string.device_settings_update_hero_title_required,
                summary = DeviceFirmwareUpdateText(
                    R.string.device_settings_update_hero_summary_required_version,
                    TARGET_VERSION
                ),
                statusTextRes = R.string.device_settings_update_status_required,
                statusColorRes = R.color.aqua_content_warning,
                statusBackgroundColorRes =
                    R.color.aqua_bg_maintenance_profile_percent_warning_fill,
                icon = firmwareIcon()
            ),
            DeviceFirmwareUpdateHeroPresentationMapper.map(state)
        )
    }

    @Test
    fun `available state without target uses generic summary`() {
        val presentation = DeviceFirmwareUpdateHeroPresentationMapper.map(
            updateState(mandatory = false, targetVersion = "")
        )

        assertEquals(
            DeviceFirmwareUpdateText(R.string.device_settings_update_hero_summary_available),
            presentation.summary
        )
    }

    @Test
    fun `completed states preserve version format arguments`() {
        val succeeded = mapState(
            DeviceFirmwareUpdateMode.SUCCEEDED,
            targetVersion = TARGET_VERSION
        )
        val upToDate = mapState(
            DeviceFirmwareUpdateMode.UP_TO_DATE,
            currentVersion = CURRENT_VERSION
        )

        assertEquals(
            DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_succeeded_version,
                TARGET_VERSION
            ),
            succeeded.summary
        )
        assertEquals(
            DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_up_to_date_version,
                CURRENT_VERSION
            ),
            upToDate.summary
        )
    }

    @Test
    fun `failure reason maps localized copy and danger treatment`() {
        val failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true
        )
        val presentation = DeviceFirmwareUpdateHeroPresentationMapper.map(
            DeviceFirmwareUpdateUiState(
                mode = DeviceFirmwareUpdateMode.FAILED,
                failure = failure
            )
        )

        assertEquals(R.string.device_settings_update_hero_title_failed, presentation.titleRes)
        assertEquals(
            DeviceFirmwareUpdateText(R.string.device_settings_update_error_connection),
            presentation.summary
        )
        assertEquals(R.string.device_settings_update_status_failed, presentation.statusTextRes)
        assertEquals(R.color.aqua_status_danger, presentation.statusColorRes)
        assertEquals(
            R.color.aqua_aquarium_fragment_button_outline,
            presentation.statusBackgroundColorRes
        )
        assertEquals(
            DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_error,
                R.color.aqua_status_danger
            ),
            presentation.icon
        )
    }

    @Test
    fun `every mode produces a complete presentation`() {
        DeviceFirmwareUpdateMode.entries.forEach { mode ->
            val presentation = mapState(mode)

            assertNotEquals(0, presentation.titleRes)
            assertNotEquals(0, presentation.summary.stringRes)
            assertNotEquals(0, presentation.statusTextRes)
            assertNotEquals(0, presentation.statusColorRes)
            assertNotEquals(0, presentation.statusBackgroundColorRes)
            assertNotEquals(0, presentation.icon.drawableRes)
            assertNotEquals(0, presentation.icon.colorRes)
        }
    }
}

private fun updateState(
    mandatory: Boolean,
    targetVersion: String
): DeviceFirmwareUpdateUiState = DeviceFirmwareUpdateUiState(
    mode = DeviceFirmwareUpdateMode.AVAILABLE,
    targetVersion = targetVersion,
    releaseContent = DeviceFirmwareReleaseContent.EMPTY.copy(mandatory = mandatory)
)

private fun mapState(
    mode: DeviceFirmwareUpdateMode,
    currentVersion: String = "",
    targetVersion: String = ""
): DeviceFirmwareUpdateHeroPresentation = DeviceFirmwareUpdateHeroPresentationMapper.map(
    DeviceFirmwareUpdateUiState(
        mode = mode,
        currentVersion = currentVersion,
        targetVersion = targetVersion
    )
)

private fun firmwareIcon(): DeviceFirmwareUpdateIconPresentation =
    DeviceFirmwareUpdateIconPresentation(
        R.drawable.ic_firmware_update,
        R.color.aqua_accent_primary
    )

private const val CURRENT_VERSION = "1.4.0"
private const val TARGET_VERSION = "1.5.0"
