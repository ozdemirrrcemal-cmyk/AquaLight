package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason

@StringRes
internal fun DeviceLightQuickSetupBlockReason.messageResource(): Int =
    BLOCK_REASON_MESSAGES.getValue(this)

private val BLOCK_REASON_MESSAGES: Map<DeviceLightQuickSetupBlockReason, Int> = mapOf(
    DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID to
        R.string.device_light_quick_setup_error_invalid_device,
    DeviceLightQuickSetupBlockReason.DEVICE_NOT_REGISTERED to
        R.string.device_light_quick_setup_error_device_not_registered,
    DeviceLightQuickSetupBlockReason.DEVICE_METADATA_NOT_READY to
        R.string.device_light_quick_setup_error_metadata_not_ready,
    DeviceLightQuickSetupBlockReason.DEVICE_NOT_ASSIGNED to
        R.string.device_light_quick_setup_error_not_assigned,
    DeviceLightQuickSetupBlockReason.AQUARIUM_NOT_FOUND to
        R.string.device_light_quick_setup_error_tank_not_found,
    DeviceLightQuickSetupBlockReason.MULTIPLE_LIGHT_FIXTURES_UNSUPPORTED to
        R.string.device_light_quick_setup_error_multiple_lights,
    DeviceLightQuickSetupBlockReason.UNSUPPORTED_PRODUCT to
        R.string.device_light_quick_setup_error_unsupported_product,
    DeviceLightQuickSetupBlockReason.NO_PLANTS to
        R.string.device_light_quick_setup_error_no_plants,
    DeviceLightQuickSetupBlockReason.MISSING_PLANT_CATALOG_ID to
        R.string.device_light_quick_setup_error_plant_catalog_id,
    DeviceLightQuickSetupBlockReason.UNKNOWN_PLANT_CATALOG_ID to
        R.string.device_light_quick_setup_error_unknown_plant,
    DeviceLightQuickSetupBlockReason.MISSING_SETUP_DATE to
        R.string.device_light_quick_setup_error_setup_date,
    DeviceLightQuickSetupBlockReason.MISSING_CALIBRATION to
        R.string.device_light_quick_setup_error_calibration,
    DeviceLightQuickSetupBlockReason.CALIBRATION_GEOMETRY_UNSUPPORTED to
        R.string.device_light_quick_setup_error_geometry,
    DeviceLightQuickSetupBlockReason.INSUFFICIENT_FIXTURE_COVERAGE to
        R.string.device_light_quick_setup_error_coverage,
    DeviceLightQuickSetupBlockReason.INVALID_INPUT to
        R.string.device_light_quick_setup_error_input,
    DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE to
        R.string.device_light_quick_setup_error_connection,
    DeviceLightQuickSetupBlockReason.RTC_NOT_READY to
        R.string.device_light_quick_setup_error_rtc,
    DeviceLightQuickSetupBlockReason.STALE_CONTEXT to
        R.string.device_light_quick_setup_error_stale,
    DeviceLightQuickSetupBlockReason.MALFORMED_FIRMWARE_STATE to
        R.string.device_light_quick_setup_error_firmware_state,
    DeviceLightQuickSetupBlockReason.DEVICE_WRITE_FAILED to
        R.string.device_light_quick_setup_error_write
)
