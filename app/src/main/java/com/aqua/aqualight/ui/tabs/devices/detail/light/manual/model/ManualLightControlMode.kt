package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

/**
 * UI-level control mode for the manual light screen.
 *
 * AUTO keeps the screen read-only in meaning: sliders show the live output that
 * the automatic schedule/device runtime reports. Touching a slider or applying
 * a scene intentionally moves the device into manual override.
 */
enum class ManualLightControlMode {
    AUTO,
    MANUAL_OVERRIDE,
    SCENE_OVERRIDE
}
