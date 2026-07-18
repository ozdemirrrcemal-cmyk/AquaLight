package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText

data class DeviceRootMenuItemUi(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val enabled: Boolean
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
    val available = items.filter(DeviceRootMenuItemUi::enabled)
    if (available.isEmpty()) return AquaUiText.Resource(emptyTextRes)
    return AquaUiText.Joined(
        parts = available.map { item ->
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
            primary = listOf(
                item(
                    titleRes = R.string.device_menu_manual_control_title,
                    subtitleRes = R.string.device_menu_manual_control_description,
                    enabled = DeviceRootMenuFeature.LIGHT_MANUAL in snapshot.menuFeatures
                ),
                item(
                    titleRes = R.string.device_menu_quick_setup_title,
                    subtitleRes = R.string.device_menu_quick_setup_description,
                    enabled = DeviceRootMenuFeature.LIGHT_QUICK_SETUP in snapshot.menuFeatures
                )
            ),
            secondary = listOf(
                item(
                    titleRes = R.string.device_menu_programs_title,
                    subtitleRes = R.string.device_menu_programs_description,
                    enabled = DeviceRootMenuFeature.LIGHT_PROGRAMS in snapshot.menuFeatures
                ),
                item(
                    titleRes = R.string.device_menu_presets_title,
                    subtitleRes = R.string.device_menu_presets_description,
                    enabled = DeviceRootMenuFeature.LIGHT_PRESETS in snapshot.menuFeatures
                ),
                item(
                    titleRes = R.string.device_menu_simulation_title,
                    subtitleRes = R.string.device_menu_simulation_description,
                    enabled = DeviceRootMenuFeature.LIGHT_SIMULATION in snapshot.menuFeatures
                ),
                item(
                    titleRes = R.string.device_menu_settings_title,
                    subtitleRes = R.string.device_menu_light_settings_description,
                    enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
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
        primary = listOf(
            item(
                titleRes = R.string.device_menu_channels_title,
                subtitleRes = R.string.device_menu_dosing_channels_description,
                enabled = DeviceRootMenuFeature.DOSING_CHANNELS in snapshot.menuFeatures
            ),
            item(
                titleRes = R.string.device_menu_calibration_title,
                subtitleRes = R.string.device_menu_calibration_description,
                enabled = DeviceRootMenuFeature.DOSING_CALIBRATION in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                titleRes = R.string.device_menu_schedules_title,
                subtitleRes = R.string.device_menu_dosing_schedules_description,
                enabled = DeviceRootMenuFeature.DOSING_SCHEDULES in snapshot.menuFeatures
            ),
            item(
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description,
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun timer(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOf(
            item(
                titleRes = R.string.device_menu_timer_channels_title,
                subtitleRes = R.string.device_menu_timer_channels_description,
                enabled = DeviceRootMenuFeature.TIMER_CHANNELS in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                titleRes = R.string.device_menu_schedules_title,
                subtitleRes = R.string.device_menu_timer_schedules_description,
                enabled = DeviceRootMenuFeature.TIMER_SCHEDULES in snapshot.menuFeatures
            ),
            item(
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description,
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun cooling(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOf(
            item(
                titleRes = R.string.device_menu_fan_control_title,
                subtitleRes = R.string.device_menu_fan_control_description,
                enabled = DeviceRootMenuFeature.COOLING_FANS in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                titleRes = R.string.device_menu_temperature_automation_title,
                subtitleRes = R.string.device_menu_temperature_automation_description,
                enabled = DeviceRootMenuFeature.COOLING_TEMPERATURE in snapshot.menuFeatures
            ),
            item(
                titleRes = R.string.device_menu_settings_title,
                subtitleRes = R.string.device_menu_settings_description,
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun item(
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        enabled: Boolean
    ) = DeviceRootMenuItemUi(
        titleRes = titleRes,
        subtitleRes = subtitleRes,
        enabled = enabled
    )
}
