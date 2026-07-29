package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText

data class DeviceRootMenuItemUi(
    val route: DeviceRootRoute,
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

    fun light(snapshot: DeviceRootSnapshot): DeviceRootMenuSections = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                R.string.device_menu_manual_control_title,
                R.string.device_menu_manual_control_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                R.string.device_menu_quick_setup_title,
                R.string.device_menu_quick_setup_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                R.string.device_menu_programs_title,
                R.string.device_menu_programs_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.LIGHT_PRESETS,
                R.string.device_menu_presets_title,
                R.string.device_menu_presets_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.LIGHT_SIMULATION,
                R.string.device_menu_simulation_title,
                R.string.device_menu_simulation_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.COOLING_FANS,
                R.string.device_menu_fan_control_title,
                R.string.device_menu_fan_control_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                R.string.device_menu_temperature_automation_title,
                R.string.device_menu_temperature_automation_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DEVICE_SETTINGS,
                R.string.device_menu_settings_title,
                R.string.device_menu_light_settings_description
            )
        )
    )

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
                DeviceRootMenuFeature.DOSING_CHANNELS,
                R.string.device_menu_channels_title,
                R.string.device_menu_dosing_channels_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DOSING_CALIBRATION,
                R.string.device_menu_calibration_title,
                R.string.device_menu_calibration_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DOSING_SCHEDULES,
                R.string.device_menu_schedules_title,
                R.string.device_menu_dosing_schedules_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DEVICE_SETTINGS,
                R.string.device_menu_settings_title,
                R.string.device_menu_settings_description
            )
        )
    )

    private fun timer(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.TIMER_CHANNELS,
                R.string.device_menu_timer_channels_title,
                R.string.device_menu_timer_channels_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.TIMER_SCHEDULES,
                R.string.device_menu_schedules_title,
                R.string.device_menu_timer_schedules_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DEVICE_SETTINGS,
                R.string.device_menu_settings_title,
                R.string.device_menu_settings_description
            )
        )
    )

    private fun cooling(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.COOLING_FANS,
                R.string.device_menu_fan_control_title,
                R.string.device_menu_fan_control_description
            )
        ),
        secondary = listOfNotNull(
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                R.string.device_menu_temperature_automation_title,
                R.string.device_menu_temperature_automation_description
            ),
            snapshot.itemIfSupported(
                DeviceRootMenuFeature.DEVICE_SETTINGS,
                R.string.device_menu_settings_title,
                R.string.device_menu_settings_description
            )
        )
    )

    private fun DeviceRootSnapshot.itemIfSupported(
        feature: DeviceRootMenuFeature,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int
    ): DeviceRootMenuItemUi? {
        if (feature !in menuFeatures) return null
        val route = DeviceRootRouteResolver.resolve(family, feature) ?: return null
        if (route !in allowedRoutes) return null
        return DeviceRootMenuItemUi(
            route = route,
            titleRes = titleRes,
            subtitleRes = subtitleRes
        )
    }
}
