package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot

data class DeviceRootMenuItemUi(
    val title: String,
    val subtitle: String,
    val enabled: Boolean
)

data class DeviceRootMenuSections(
    val primary: List<DeviceRootMenuItemUi> = emptyList(),
    val secondary: List<DeviceRootMenuItemUi> = emptyList()
) {
    fun primaryText(emptyText: String): String = formatDeviceRootMenuItems(primary, emptyText)

    fun secondaryText(emptyText: String): String = formatDeviceRootMenuItems(secondary, emptyText)
}

private fun formatDeviceRootMenuItems(
    items: List<DeviceRootMenuItemUi>,
    emptyText: String
): String {
    val available = items.filter(DeviceRootMenuItemUi::enabled)
    if (available.isEmpty()) return emptyText
    return available.joinToString(separator = "\n\n") { item ->
        "• ${item.title}\n  ${item.subtitle}"
    }
}

object DeviceRootMenuMapper {

    fun light(snapshot: DeviceRootSnapshot): DeviceRootMenuSections {
        return DeviceRootMenuSections(
            primary = listOf(
                item(
                    title = "Manual control",
                    subtitle = "Direct channel control for supported light outputs.",
                    enabled = DeviceRootMenuFeature.LIGHT_MANUAL in snapshot.menuFeatures
                ),
                item(
                    title = "Quick setup",
                    subtitle = "Fast setup from firmware-supported light presets and profiles.",
                    enabled = DeviceRootMenuFeature.LIGHT_QUICK_SETUP in snapshot.menuFeatures
                )
            ),
            secondary = listOf(
                item(
                    title = "Programs",
                    subtitle = "Create and manage daily light programs.",
                    enabled = DeviceRootMenuFeature.LIGHT_PROGRAMS in snapshot.menuFeatures
                ),
                item(
                    title = "Presets",
                    subtitle = "Use firmware-provided light presets.",
                    enabled = DeviceRootMenuFeature.LIGHT_PRESETS in snapshot.menuFeatures
                ),
                item(
                    title = "Simulation",
                    subtitle = "Sunrise, sunset and light simulation features.",
                    enabled = DeviceRootMenuFeature.LIGHT_SIMULATION in snapshot.menuFeatures
                ),
                item(
                    title = "Device settings",
                    subtitle = "Device identity, firmware and runtime settings.",
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
                title = "Channels",
                subtitle = "Configure dosing channels and pump parameters.",
                enabled = DeviceRootMenuFeature.DOSING_CHANNELS in snapshot.menuFeatures
            ),
            item(
                title = "Calibration",
                subtitle = "Calibrate dosing flow for accurate dosing.",
                enabled = DeviceRootMenuFeature.DOSING_CALIBRATION in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                title = "Schedules",
                subtitle = "Single dose, hourly, timer and custom dosing periods.",
                enabled = DeviceRootMenuFeature.DOSING_SCHEDULES in snapshot.menuFeatures
            ),
            item(
                title = "Device settings",
                subtitle = "Firmware, runtime and device settings.",
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun timer(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOf(
            item(
                title = "Timer channels",
                subtitle = "Configure timer relay channels.",
                enabled = DeviceRootMenuFeature.TIMER_CHANNELS in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                title = "Schedules",
                subtitle = "Create and manage timer schedules.",
                enabled = DeviceRootMenuFeature.TIMER_SCHEDULES in snapshot.menuFeatures
            ),
            item(
                title = "Device settings",
                subtitle = "Firmware, runtime and device settings.",
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun cooling(snapshot: DeviceRootSnapshot) = DeviceRootMenuSections(
        primary = listOf(
            item(
                title = "Fan control",
                subtitle = "Manual fan control for supported cooling channels.",
                enabled = DeviceRootMenuFeature.COOLING_FANS in snapshot.menuFeatures
            )
        ),
        secondary = listOf(
            item(
                title = "Temperature automation",
                subtitle = "Temperature-based cooling automation.",
                enabled = DeviceRootMenuFeature.COOLING_TEMPERATURE in snapshot.menuFeatures
            ),
            item(
                title = "Device settings",
                subtitle = "Firmware, runtime and device settings.",
                enabled = DeviceRootMenuFeature.DEVICE_SETTINGS in snapshot.menuFeatures
            )
        )
    )

    private fun item(
        title: String,
        subtitle: String,
        enabled: Boolean
    ) = DeviceRootMenuItemUi(title = title, subtitle = subtitle, enabled = enabled)
}
