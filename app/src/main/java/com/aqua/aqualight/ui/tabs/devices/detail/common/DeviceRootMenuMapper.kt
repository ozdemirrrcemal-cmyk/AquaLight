package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.data.devices.model.DeviceSnapshot

data class DeviceRootMenuItemUi(
    val title: String,
    val subtitle: String,
    val enabled: Boolean
)

data class DeviceRootMenuSections(
    val primary: List<DeviceRootMenuItemUi> = emptyList(),
    val secondary: List<DeviceRootMenuItemUi> = emptyList()
) {
    fun primaryText(emptyText: String): String = primary.toDisplayText(emptyText)

    fun secondaryText(emptyText: String): String = secondary.toDisplayText(emptyText)
}

object DeviceRootMenuMapper {

    fun light(snapshot: DeviceSnapshot): DeviceRootMenuSections {
        val primary = listOf(
            DeviceRootMenuItemUi(
                title = "Manual control",
                subtitle = "Direct channel control for supported light outputs.",
                enabled = snapshot.capabilities.manualLight || snapshot.hasAnyScreen(
                    "light.manual",
                    "manual",
                    "manualLight"
                )
            ),
            DeviceRootMenuItemUi(
                title = "Quick setup",
                subtitle = "Fast setup from firmware-supported light presets and profiles.",
                enabled = snapshot.hasAnyScreen(
                    "light.quickSetup",
                    "quickSetup",
                    "quick_setup"
                )
            )
        )

        val secondary = listOf(
            DeviceRootMenuItemUi(
                title = "Programs",
                subtitle = "Create and manage daily light programs.",
                enabled = snapshot.capabilities.lightProgram || snapshot.hasAnyScreen(
                    "light.programs",
                    "programs",
                    "programList",
                    "program_list"
                )
            ),
            DeviceRootMenuItemUi(
                title = "Presets",
                subtitle = "Use firmware-provided light presets.",
                enabled = snapshot.capabilities.lightPresets || snapshot.hasAnyScreen(
                    "light.presets",
                    "presets"
                )
            ),
            DeviceRootMenuItemUi(
                title = "Simulation",
                subtitle = "Sunrise, sunset and light simulation features.",
                enabled = snapshot.capabilities.lightSimulation || snapshot.hasAnyScreen(
                    "light.simulation",
                    "simulation"
                )
            ),
            DeviceRootMenuItemUi(
                title = "Device settings",
                subtitle = "Device identity, firmware and runtime settings.",
                enabled = snapshot.hasAnyScreen(
                    "device.settings",
                    "settings"
                ) || snapshot.capabilities.ota
            )
        )

        return DeviceRootMenuSections(
            primary = primary,
            secondary = secondary
        )
    }

    fun overview(
        kind: DeviceRootKind,
        snapshot: DeviceSnapshot
    ): DeviceRootMenuSections {
        return when (kind) {
            DeviceRootKind.DOSING -> dosing(snapshot)
            DeviceRootKind.TIMER -> timer(snapshot)
            DeviceRootKind.COOLING -> cooling(snapshot)
        }
    }

    private fun dosing(snapshot: DeviceSnapshot): DeviceRootMenuSections {
        return DeviceRootMenuSections(
            primary = listOf(
                DeviceRootMenuItemUi(
                    title = "Channels",
                    subtitle = "Configure dosing channels and pump parameters.",
                    enabled = snapshot.capabilities.dosing || snapshot.hasAnyScreen(
                        "dosing.channels",
                        "channels",
                        "dosing"
                    )
                ),
                DeviceRootMenuItemUi(
                    title = "Calibration",
                    subtitle = "Calibrate dosing flow for accurate dosing.",
                    enabled = snapshot.hasAnyScreen(
                        "dosing.calibration",
                        "calibration"
                    )
                )
            ),
            secondary = listOf(
                DeviceRootMenuItemUi(
                    title = "Schedules",
                    subtitle = "Single dose, hourly, timer and custom dosing periods.",
                    enabled = snapshot.hasAnyScreen(
                        "dosing.schedules",
                        "schedules",
                        "singleDose",
                        "hourly24",
                        "customPeriods",
                        "timerMode"
                    )
                ),
                DeviceRootMenuItemUi(
                    title = "Device settings",
                    subtitle = "Firmware, runtime and device settings.",
                    enabled = snapshot.capabilities.ota || snapshot.hasAnyScreen(
                        "device.settings",
                        "settings"
                    )
                )
            )
        )
    }

    private fun timer(snapshot: DeviceSnapshot): DeviceRootMenuSections {
        return DeviceRootMenuSections(
            primary = listOf(
                DeviceRootMenuItemUi(
                    title = "Timer channels",
                    subtitle = "Configure timer relay channels.",
                    enabled = snapshot.capabilities.standaloneTimer || snapshot.hasAnyScreen(
                        "timer.channels",
                        "channels",
                        "timer"
                    )
                )
            ),
            secondary = listOf(
                DeviceRootMenuItemUi(
                    title = "Schedules",
                    subtitle = "Create and manage timer schedules.",
                    enabled = snapshot.hasAnyScreen(
                        "timer.schedules",
                        "schedules"
                    )
                ),
                DeviceRootMenuItemUi(
                    title = "Device settings",
                    subtitle = "Firmware, runtime and device settings.",
                    enabled = snapshot.capabilities.ota || snapshot.hasAnyScreen(
                        "device.settings",
                        "settings"
                    )
                )
            )
        )
    }

    private fun cooling(snapshot: DeviceSnapshot): DeviceRootMenuSections {
        return DeviceRootMenuSections(
            primary = listOf(
                DeviceRootMenuItemUi(
                    title = "Fan control",
                    subtitle = "Manual fan control for supported cooling channels.",
                    enabled = snapshot.capabilities.cooling ||
                        snapshot.capabilities.fan ||
                        snapshot.hasAnyScreen(
                            "cooling.fans",
                            "fan",
                            "fans",
                            "cooling"
                        )
                )
            ),
            secondary = listOf(
                DeviceRootMenuItemUi(
                    title = "Temperature automation",
                    subtitle = "Temperature-based cooling automation.",
                    enabled = snapshot.capabilities.temperature || snapshot.hasAnyScreen(
                        "cooling.temperature",
                        "temperature"
                    )
                ),
                DeviceRootMenuItemUi(
                    title = "Device settings",
                    subtitle = "Firmware, runtime and device settings.",
                    enabled = snapshot.capabilities.ota || snapshot.hasAnyScreen(
                        "device.settings",
                        "settings"
                    )
                )
            )
        )
    }

    private fun DeviceSnapshot.hasAnyScreen(
        vararg names: String
    ): Boolean {
        val normalizedSupported = (supportedScreens + supportedFeatures)
            .map { value -> value.trim().lowercase() }
            .filter { value -> value.isNotBlank() }
            .toSet()

        return names.any { name ->
            normalizedSupported.contains(name.trim().lowercase())
        }
    }

    private fun List<DeviceRootMenuItemUi>.toDisplayText(
        emptyText: String
    ): String {
        val available = filter { item -> item.enabled }

        if (available.isEmpty()) {
            return emptyText
        }

        return available.joinToString(separator = "\n\n") { item ->
            "• ${item.title}\n  ${item.subtitle}"
        }
    }
}
