package com.aqua.aqualight.application.devices.cooling.control

/** Product-level Cooling control modes. Firmware wire values must not leak above the data boundary. */
enum class DeviceCoolingControlMode {
    AUTOMATIC,
    MANUAL,
    PROGRAM
}
