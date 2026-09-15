package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupChannel
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupConfidence
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupFactorEffect
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupFactorId
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupLifecycleStage
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupMissingField
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupUnsupportedReason
import com.aqua.aqualight.i18n.LocaleFormatter

@Composable
internal fun quickSetupDateText(epochDay: Long): String =
    LocaleFormatter.formatDateEpochDay(LocalContext.current, epochDay)

@Composable
internal fun quickSetupTimeText(minutesOfDay: Int): String =
    LocaleFormatter.formatTimeOfDay24Hour(LocalContext.current, minutesOfDay)

@Composable
internal fun quickSetupDurationText(durationMs: Long): String =
    stringResource(R.string.device_light_smart_setup_minutes, durationMs / MILLIS_PER_MINUTE)

@StringRes
internal fun PlantDensity.labelRes(): Int = when (this) {
    PlantDensity.LOW -> R.string.device_light_smart_setup_level_low
    PlantDensity.MEDIUM -> R.string.device_light_smart_setup_level_medium
    PlantDensity.HIGH -> R.string.device_light_smart_setup_level_high
}

@StringRes
internal fun PlantLightDemand.labelRes(): Int = when (this) {
    PlantLightDemand.LOW -> R.string.device_light_smart_setup_level_low
    PlantLightDemand.MEDIUM -> R.string.device_light_smart_setup_level_medium
    PlantLightDemand.HIGH -> R.string.device_light_smart_setup_level_high
}

@StringRes
internal fun Co2Status.labelRes(): Int = when (this) {
    Co2Status.NONE -> R.string.device_light_smart_setup_co2_none
    Co2Status.INSTALLED -> R.string.device_light_smart_setup_co2_installed
    Co2Status.ACTIVE -> R.string.device_light_smart_setup_co2_active
}

@StringRes
internal fun AquariumObservationSeverity.labelRes(): Int = when (this) {
    AquariumObservationSeverity.NONE -> R.string.device_light_smart_setup_observation_none
    AquariumObservationSeverity.MILD -> R.string.device_light_smart_setup_observation_mild
    AquariumObservationSeverity.SIGNIFICANT ->
        R.string.device_light_smart_setup_observation_significant
}

@StringRes
internal fun SmartSetupLifecycleStage.labelRes(): Int = when (this) {
    SmartSetupLifecycleStage.STARTUP -> R.string.device_light_smart_setup_stage_startup
    SmartSetupLifecycleStage.ESTABLISHING ->
        R.string.device_light_smart_setup_stage_establishing
    SmartSetupLifecycleStage.MATURE -> R.string.device_light_smart_setup_stage_mature
}

@StringRes
internal fun SmartSetupConfidence.labelRes(): Int = when (this) {
    SmartSetupConfidence.MODERATE -> R.string.device_light_smart_setup_confidence_moderate
    SmartSetupConfidence.HIGH -> R.string.device_light_smart_setup_confidence_high
}

@StringRes
internal fun SmartSetupChannel.labelRes(): Int = when (this) {
    SmartSetupChannel.RED -> R.string.device_light_smart_setup_channel_red
    SmartSetupChannel.GREEN -> R.string.device_light_smart_setup_channel_green
    SmartSetupChannel.BLUE -> R.string.device_light_smart_setup_channel_blue
    SmartSetupChannel.WHITE -> R.string.device_light_smart_setup_channel_white
}

@StringRes
internal fun SmartSetupFactorEffect.labelRes(): Int = when (this) {
    SmartSetupFactorEffect.INCREASE -> R.string.device_light_smart_setup_effect_increase
    SmartSetupFactorEffect.DECREASE -> R.string.device_light_smart_setup_effect_decrease
    SmartSetupFactorEffect.LIMIT -> R.string.device_light_smart_setup_effect_limit
    SmartSetupFactorEffect.SCHEDULE -> R.string.device_light_smart_setup_effect_schedule
    SmartSetupFactorEffect.INFORMATION -> R.string.device_light_smart_setup_effect_information
}

@StringRes
internal fun SmartSetupFactorId.labelRes(): Int = when (this) {
    SmartSetupFactorId.AQUARIUM_STAGE -> R.string.device_light_smart_setup_factor_stage
    SmartSetupFactorId.UNPLANTED_AQUARIUM ->
        R.string.device_light_smart_setup_factor_unplanted
    SmartSetupFactorId.PLANT_DENSITY -> R.string.device_light_smart_setup_factor_density
    SmartSetupFactorId.PLANT_LIGHT_DEMAND -> R.string.device_light_smart_setup_factor_demand
    SmartSetupFactorId.CO2_STATE -> R.string.device_light_smart_setup_factor_co2
    SmartSetupFactorId.ACTIVE_SOIL -> R.string.device_light_smart_setup_factor_soil
    SmartSetupFactorId.OPTICAL_DISTANCE -> R.string.device_light_smart_setup_factor_distance
    SmartSetupFactorId.ALGAE_OBSERVATION -> R.string.device_light_smart_setup_factor_algae
    SmartSetupFactorId.PLANT_STRESS_OBSERVATION ->
        R.string.device_light_smart_setup_factor_plant_stress
    SmartSetupFactorId.RECENT_ALGAE_MAINTENANCE ->
        R.string.device_light_smart_setup_factor_algae_maintenance
    SmartSetupFactorId.OVERDUE_MAINTENANCE ->
        R.string.device_light_smart_setup_factor_overdue_maintenance
    SmartSetupFactorId.VIEWING_WINDOW -> R.string.device_light_smart_setup_factor_viewing
    SmartSetupFactorId.DEVICE_CALIBRATION ->
        R.string.device_light_smart_setup_factor_calibration
}

@StringRes
internal fun SmartSetupMissingField.labelRes(): Int = when (this) {
    SmartSetupMissingField.DEVICE_LOCAL_DATE ->
        R.string.device_light_smart_setup_missing_device_date
    SmartSetupMissingField.SETUP_DATE -> R.string.device_light_smart_setup_missing_setup_date
    SmartSetupMissingField.PLANT_DENSITY ->
        R.string.device_light_smart_setup_missing_plant_density
    SmartSetupMissingField.HIGHEST_PLANT_LIGHT_DEMAND ->
        R.string.device_light_smart_setup_missing_plant_demand
    SmartSetupMissingField.CO2_STATUS -> R.string.device_light_smart_setup_missing_co2
    SmartSetupMissingField.ACTIVE_SOIL -> R.string.device_light_smart_setup_missing_active_soil
    SmartSetupMissingField.WATER_DEPTH -> R.string.device_light_smart_setup_missing_water_depth
    SmartSetupMissingField.FIXTURE_MOUNT_HEIGHT ->
        R.string.device_light_smart_setup_missing_mount_height
    SmartSetupMissingField.VIEWING_WINDOW ->
        R.string.device_light_smart_setup_missing_viewing_window
    SmartSetupMissingField.ALGAE_OBSERVATION ->
        R.string.device_light_smart_setup_missing_algae
    SmartSetupMissingField.PLANT_STRESS_OBSERVATION ->
        R.string.device_light_smart_setup_missing_plant_stress
    SmartSetupMissingField.OBSERVATION_DATE ->
        R.string.device_light_smart_setup_missing_observation_date
    SmartSetupMissingField.OBSERVATIONS_STALE ->
        R.string.device_light_smart_setup_missing_observations_stale
    SmartSetupMissingField.CALIBRATION_PROFILE ->
        R.string.device_light_smart_setup_missing_calibration
}

@StringRes
internal fun SmartSetupUnsupportedReason.labelRes(): Int = when (this) {
    SmartSetupUnsupportedReason.UNKNOWN_AQUARIUM_ENVIRONMENT ->
        R.string.device_light_smart_setup_unsupported_environment
    SmartSetupUnsupportedReason.MARINE_EVIDENCE_NOT_AVAILABLE ->
        R.string.device_light_smart_setup_unsupported_marine
    SmartSetupUnsupportedReason.SETUP_DATE_IN_FUTURE ->
        R.string.device_light_smart_setup_unsupported_future_setup
    SmartSetupUnsupportedReason.LIFECYCLE_MISMATCH ->
        R.string.device_light_smart_setup_unsupported_lifecycle
    SmartSetupUnsupportedReason.INCONSISTENT_PLANT_FACTS ->
        R.string.device_light_smart_setup_unsupported_plant_facts
    SmartSetupUnsupportedReason.OBSERVATION_DATE_IN_FUTURE ->
        R.string.device_light_smart_setup_unsupported_future_observation
    SmartSetupUnsupportedReason.OBSERVATION_BEFORE_SETUP ->
        R.string.device_light_smart_setup_unsupported_observation_before_setup
    SmartSetupUnsupportedReason.UNKNOWN_DEVICE_PRODUCT ->
        R.string.device_light_smart_setup_unsupported_product
    SmartSetupUnsupportedReason.CALIBRATION_NOT_AVAILABLE_FOR_PRODUCT ->
        R.string.device_light_smart_setup_unsupported_calibration_unavailable
    SmartSetupUnsupportedReason.DEVICE_CALIBRATION_METADATA_MISSING ->
        R.string.device_light_smart_setup_unsupported_calibration_missing
    SmartSetupUnsupportedReason.CALIBRATION_PRODUCT_MISMATCH ->
        R.string.device_light_smart_setup_unsupported_calibration_product
    SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH ->
        R.string.device_light_smart_setup_unsupported_calibration_mismatch
    SmartSetupUnsupportedReason.UNSUPPORTED_CHANNEL_SET ->
        R.string.device_light_smart_setup_unsupported_channels
    SmartSetupUnsupportedReason.CALENDAR_OUTSIDE_FIRMWARE_RANGE ->
        R.string.device_light_smart_setup_unsupported_calendar
    SmartSetupUnsupportedReason.INSTALLATION_OUTSIDE_CALIBRATION ->
        R.string.device_light_smart_setup_unsupported_installation
    SmartSetupUnsupportedReason.VIEWING_WINDOW_UNSUPPORTED ->
        R.string.device_light_smart_setup_unsupported_viewing
}

@StringRes
internal fun DeviceLightQuickSetupLoadFailure.labelRes(): Int = when (this) {
    DeviceLightQuickSetupLoadFailure.DEVICE_NOT_ASSIGNED ->
        R.string.device_light_smart_setup_assignment_error
    DeviceLightQuickSetupLoadFailure.AQUARIUM_NOT_FOUND ->
        R.string.device_light_smart_setup_aquarium_error
    DeviceLightQuickSetupLoadFailure.NOT_CONNECTED ->
        R.string.device_light_smart_setup_not_connected_error
    DeviceLightQuickSetupLoadFailure.INVALID_DEVICE ->
        R.string.device_light_smart_setup_invalid_device_error
    DeviceLightQuickSetupLoadFailure.INVALID_FIRMWARE_DATA ->
        R.string.device_light_smart_setup_firmware_error
    DeviceLightQuickSetupLoadFailure.UNAVAILABLE ->
        R.string.device_light_smart_setup_operation_error
}

private const val MILLIS_PER_MINUTE = 60_000L
