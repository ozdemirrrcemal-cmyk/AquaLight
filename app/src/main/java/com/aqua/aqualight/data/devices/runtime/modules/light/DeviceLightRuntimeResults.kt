package com.aqua.aqualight.data.devices.runtime.modules.light

enum class DeviceLightManualOperation(
    val wireValue: String
) {
    MANUAL_STATE("manualState"),
    CLEAR_MANUAL("clearManual");

    companion object {
        fun fromWireExact(value: String): DeviceLightManualOperation =
            values().singleOrNull { operation -> operation.wireValue == value }
                ?: error("Unknown firmware manual-light operation: $value")
    }
}

data class DeviceLightChannelMutationSnapshot(
    val listIndex: Int,
    val channel: DeviceLightChannelStatus
)

data class DeviceLightManualMutationResult(
    val operation: DeviceLightManualOperation,
    val manualActive: Boolean,
    val durationMs: Long,
    val affectedChannelCount: Int,
    val saved: Boolean,
    val channels: List<DeviceLightChannelMutationSnapshot>
)

data class DeviceLightChannelRegimeMutationResult(
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val channelKey: String,
    val regime: DeviceLightRegime,
    val channel: DeviceLightChannelMutationSnapshot
)

data class DeviceLightProgramApplyResult(
    val created: Boolean,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val programIndex: Int,
    val channelKey: String,
    val channelListIndex: Int,
    val program: DeviceLightProgramStatus
)

data class DeviceLightProgramDeleteResult(
    val deleted: Boolean,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val programIndex: Int,
    val deletedListIndex: Int,
    val channelKey: String,
    val deletedPointCount: Int,
    val programCount: Int
)
