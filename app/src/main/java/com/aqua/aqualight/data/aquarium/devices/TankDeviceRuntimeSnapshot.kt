package com.aqua.aqualight.data.aquarium.devices

/**
 * Device-specific runtime payload used by the Aquarium > Tank devices surface.
 *
 * This model is intentionally independent from concrete device modules such as
 * light, timer, dosing or cooling. Those modules can provide their own
 * TankDeviceRuntimeDataSource implementation and translate their internal
 * runtime state into this contract when they are ready to be connected.
 */
sealed class TankDeviceRuntimeSnapshot {

    abstract val deviceId: Long

    data class Light(
        override val deviceId: Long,
        val mode: TankLightRuntimeMode,
        val modeLabel: String,
        val programName: String,
        val startTimeText: String,
        val endTimeText: String,
        val outputPercent: Int,
        val timelineProgressPercent: Int,
        val accentColorInt: Int,
        val channels: List<TankDeviceRuntimeChannelSnapshot>
    ) : TankDeviceRuntimeSnapshot()
}

data class TankDeviceRuntimeChannelSnapshot(
    val key: TankDeviceRuntimeChannelKind,
    val label: String,
    val currentPercent: Int,
    val targetPercent: Int,
    val colorInt: Int
)

enum class TankLightRuntimeMode {
    AUTO,
    MANUAL,
    SCENE,
    MOONLIGHT,
    NO_PROGRAM,
    OFFLINE,
    SYNCING,
    WAITING
}

enum class TankDeviceRuntimeChannelKind {
    WHITE,
    RED,
    GREEN,
    BLUE,
    INTENSITY,
    UV
}
