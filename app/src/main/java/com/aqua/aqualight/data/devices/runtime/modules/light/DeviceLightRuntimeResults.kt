package com.aqua.aqualight.data.devices.runtime.modules.light

data class DeviceLightControlSetResult(
    val mode: DeviceLightMode,
    val event: String?
)

data class DeviceLightManualSetResult(
    val scene: DeviceLightScene,
    val event: String?
)

data class DeviceLightAutoProgram(
    val programId: String,
    val enabled: Boolean,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightScene
)

data class DeviceLightAutoPrograms(
    val revision: Long,
    val capacity: Int,
    val programCount: Int,
    val enabledCount: Int,
    val programs: List<DeviceLightAutoProgram>
)

data class DeviceLightAutoProgramMutationResult(
    val revision: Long,
    val program: DeviceLightAutoProgram,
    val event: String?
)

data class DeviceLightAutoProgramDeleteResult(
    val revision: Long,
    val programId: String,
    val deleted: Boolean,
    val event: String?
)

data class DeviceLightCustomDocument(
    val revision: Long,
    val installed: Boolean,
    val weekdaysMask: Int,
    val pointCount: Int,
    val points: List<DeviceLightCustomPoint>,
    val event: String?
)

data class DeviceLightGraphPoint(val timeMs: Long, val channelPermille: List<Int>)

data class DeviceLightGraphSpan(
    val startTimeMsWithinToday: Long,
    val endTimeMsWithinToday: Long,
    val programId: String
)

data class DeviceLightGraph(
    val mode: DeviceLightMode,
    val available: Boolean,
    val reason: DeviceLightGraphReason,
    val sourceRevision: Long,
    val schedulerGeneration: Long?,
    val localDate: String?,
    val currentWeekdayMask: Int,
    val nowTimeMs: Long?,
    val basis: DeviceLightGraphBasis,
    val channelScale: Int,
    val hasScheduleToday: Boolean,
    val points: List<DeviceLightGraphPoint>,
    val autoSpans: List<DeviceLightGraphSpan>
)

data class DeviceLightPreviewResult(
    val active: Boolean,
    val remainingMs: Long?,
    val event: String
)
