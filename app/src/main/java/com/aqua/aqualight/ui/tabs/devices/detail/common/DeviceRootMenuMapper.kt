package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText

data class DeviceRootMenuItemUi(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
)

data class DeviceRootMenuSections(
    val primary: List<DeviceRootMenuItemUi> = emptyList(),
    val secondary: List<DeviceRootMenuItemUi> = emptyList()
) {
    fun primaryText(@StringRes emptyTextRes: Int): AquaUiText =
        formatDeviceRootMenuItems(primary, emptyTextRes)

    fun secondaryText(@StringRes emptyTextRes: Int): AquaUiText =
        formatDeviceRootMenuItems(secondary, emptyTextRes)
}

private fun formatDeviceRootMenuItems(
    items: List<DeviceRootMenuItemUi>,
    @StringRes emptyTextRes: Int
): AquaUiText {
    if (items.isEmpty()) return AquaUiText.Resource(emptyTextRes)
    return AquaUiText.Joined(
        parts = items.map { item ->
            AquaUiText.Resource(
                resId = R.string.device_menu_bullet_item,
                args = listOf(
                    AquaUiText.Resource(item.titleRes),
                    AquaUiText.Resource(item.subtitleRes)
                )
            )
        },
        separatorRes = R.string.device_menu_item_separator
    )
}

object DeviceRootMenuMapper {

    fun light(snapshot: DeviceRootSnapshot): DeviceRootMenuSections {
        return DeviceRootMenuSections(
            primary = listOfNotNull(
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.LIGHT_MANUAL,
                    titleRes = R.string.device_menu_manual_control_title,
                    subtitleRes = R.string.device_menu_manual_control_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                    titleRes = R.string.device_menu_quick_setup_title,
                    subtitleRes = R.string.device_menu_quick_setup_description
                )
            ),
            secondary = listOfNotNull(
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.LIGHT_PROGRAMS,
                    titleRes = R.string.device_menu_programs_title,
                    subtitleRes = R.string.device_menu_programs_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.LIGHT_PRESETS,
                    titleRes = R.string.device_menu_presets_title,
                    subtitleRes = R.string.device_menu_presets_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.LIGHT_SIMULATION,
                    titleRes = R.string.device_menu_simulation_title,
                    subtitleRes = R.string.device_menu_simulation_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.COOLING_FANS,
                    titleRes = R.string.device_menu_fan_control_title,
                    subtitleRes = R.string.device_menu_fan_control_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.COOLING_TEMPERATURE,
                    titleRes = R.string.device_menu_temperature_automation_title,
                    subtitleRes = R.string.device_menu_temperature_automation_description
                ),
                snapshot.itemIfSupported(
                    feature = DeviceRootMenuFeature.DEVICE_SETTINGS,
                    titleRes = R.string.device_menu_settings_title,
                    subtitleRes = R.string.device_menu_light_settings_description
                )
            )
        )
    }

    fun overview(
        kind: DeviceRootKind,
        snapshot: DeviceRootSnapshot
    ): DeviceRootMenuSections = when (kind) {
        DeviceRootKind.DOSING -> dosing(snapshot)
        DeviceRootKind.TIMER -> timer(snapshot)
        DeviceRootKind.COOLING -> cooling(snapshot)
    }

    private fun dosing(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DOSING_CHANNELS,
                titleRes = R.string.device_menu_channels_title,
                subtitleRes = R.string.device_menu_dosing_channels_description
            ),
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DOSING_CALIBRATION,
                titleRes = R.string.device_menu_calibration_title,
                subtitleRes = R.string.device_menu_calibration_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DOSING_SCHEDULES,
                titleRes = R.string.device_menu_schedules_title,
                subtitleRes = R.string.device_menu_dosing_schedules_description
            ),
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DEVICE_SETTINGS,
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description
            )
        )
    )

    private fun timer(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.TIMER_CHANNELS,
                titleRes = R.string.device_menu_timer_channels_title,
                subtitleRes = R.string.device_menu_timer_channels_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.TIMER_SCHEDULES,
                titleRes = R.string.device_menu_schedules_title,
                subtitleRes = R.string.device_menu_timer_schedules_description
            ),
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DEVICE_SETTINGS,
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description
            )
        )
    )

    private fun cooling(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.COOLING_FANS,
                titleRes = R.string.device_menu_fan_control_title,
                subtitleRes = R.string.device_menu_fan_control_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.COOLING_TEMPERATURE,
                titleRes = R.string.device_menu_temperature_automation_title,
                subtitleRes = R.string.device_menu_temperature_automation_description
            ),
            snapshot.itemIfSupported(
                feature = DeviceRootMenuFeature.DEVICE_SETTINGS,
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description
            )
        )
    )

    private fun DeviceRootSnapshot.itemIfSupported(
        feature: DeviceRootMenuFeature,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int
    ): DeviceRootMenuItemUi? {
        return if (feature in menuFeatures) {
            DeviceRootMenuItemUi(
                titleRes = titleRes,
                subtitleRes = subtitleRes
            )
        } else {
            null
        }
    }
}
