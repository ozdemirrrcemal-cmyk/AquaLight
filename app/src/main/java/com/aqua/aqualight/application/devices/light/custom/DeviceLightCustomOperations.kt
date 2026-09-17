package com.aqua.aqualight.application.devices.light.custom

import kotlinx.coroutines.flow.Flow

enum class DeviceLightCustomChannel(
    val wireKey: String,
    val sceneKey: String
) {
    RED("red", "redPercent"),
    GREEN("green", "greenPercent"),
    BLUE("blue", "bluePercent"),
    WHITE("white", "whitePercent")
}

data class DeviceLightCustomScene(
    val channels: Map<DeviceLightCustomChannel, Int>
) {
    init {
        require(channels.isNotEmpty())
        require(channels.values.all { value -> value in PERCENT_RANGE })
    }
}

data class DeviceLightCustomPoint(
    val timeMs: Long,
    val scene: DeviceLightCustomScene
) {
    init {
        require(timeMs in 0..LAST_DAY_MILLISECOND)
        require(timeMs % MINUTE_MILLIS == 0L)
    }
}

data class DeviceLightCustomSnapshot(
    val deviceUid: String,
    val productKey: String,
    val revision: Long,
    val installed: Boolean,
    val weekdaysMask: Int,
    val maxPoints: Int,
    val timeStepMs: Long,
    val currentTimeMs: Long?,
    val channels: List<DeviceLightCustomChannel>,
    val points: List<DeviceLightCustomPoint>,
    val firmwareWriteAuthoritative: Boolean
) {
    init {
        require(deviceUid.isNotBlank())
        require(productKey.isNotBlank())
        require(revision >= 0)
        require(weekdaysMask in WEEKDAYS_MASK_RANGE)
        require(maxPoints > 0)
        require(timeStepMs == MINUTE_MILLIS)
        require(channels.size in CHANNEL_COUNT_RANGE && channels.distinct().size == channels.size)
        require(points.size <= maxPoints)
        require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        require(points.all { point -> point.scene.channels.keys == channels.toSet() })
        require(installed || points.isEmpty())
    }
}

sealed interface DeviceLightCustomReadResult {
    data class Available(val snapshot: DeviceLightCustomSnapshot) : DeviceLightCustomReadResult
    data class Failed(val failure: DeviceLightCustomFailure) : DeviceLightCustomReadResult
}

sealed interface DeviceLightCustomMutationResult {
    data object Success : DeviceLightCustomMutationResult
    data class Failed(val failure: DeviceLightCustomFailure) : DeviceLightCustomMutationResult
}

enum class DeviceLightCustomFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA
}

/** Firmware-backed boundary for the single installed Custom document and volatile preview. */
interface DeviceLightCustomOperations {
    fun observe(deviceUid: String): Flow<DeviceLightCustomReadResult>
    fun current(deviceUid: String): DeviceLightCustomReadResult
    suspend fun read(deviceUid: String): DeviceLightCustomReadResult
    suspend fun preview(deviceUid: String, virtualTimeMs: Long): DeviceLightCustomMutationResult
    suspend fun clearPreview(deviceUid: String): DeviceLightCustomMutationResult
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
private const val MIN_WEEKDAYS_MASK = 1
private const val MAX_WEEKDAYS_MASK = 127
private const val MIN_CHANNEL_COUNT = 3
private const val MAX_CHANNEL_COUNT = 4
private const val LAST_DAY_MILLISECOND = 86_399_999L
private const val MINUTE_MILLIS = 60_000L
private val PERCENT_RANGE = MIN_PERCENT..MAX_PERCENT
private val WEEKDAYS_MASK_RANGE = MIN_WEEKDAYS_MASK..MAX_WEEKDAYS_MASK
private val CHANNEL_COUNT_RANGE = MIN_CHANNEL_COUNT..MAX_CHANNEL_COUNT
