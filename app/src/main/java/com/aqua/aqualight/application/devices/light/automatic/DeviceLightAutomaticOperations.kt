package com.aqua.aqualight.application.devices.light.automatic

enum class DeviceLightAutomaticChannel(
    val wireKey: String,
    val sceneKey: String
) {
    RED("red", "redPercent"),
    GREEN("green", "greenPercent"),
    BLUE("blue", "bluePercent"),
    WHITE("white", "whitePercent")
}

data class DeviceLightAutomaticScene(
    val channels: Map<DeviceLightAutomaticChannel, Int>
) {
    init {
        require(channels.isNotEmpty())
        require(channels.values.all { value -> value in PERCENT_RANGE })
    }
}

data class DeviceLightAutomaticProgram(
    val programId: String,
    val enabled: Boolean,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightAutomaticScene
) {
    init {
        require(PROGRAM_ID.matches(programId))
        require(weekdaysMask in WEEKDAYS_MASK_RANGE)
        require(startTimeMs in AUTHORED_TIME_RANGE && startTimeMs % MINUTE_MILLIS == 0L)
        require(endTimeMs in AUTHORED_TIME_RANGE && endTimeMs % MINUTE_MILLIS == 0L)
        require(startTimeMs != endTimeMs)
        require(rampDurationMs in MIN_RAMP_DURATION_MS..MAX_RAMP_DURATION_MS)
        require(rampDurationMs % RAMP_DURATION_STEP_MS == 0L)
        require(rampDurationMs * 2L <= occupiedDurationMs())
    }

    private fun occupiedDurationMs(): Long = if (endTimeMs > startTimeMs) {
        endTimeMs - startTimeMs
    } else {
        MILLIS_PER_DAY - startTimeMs + endTimeMs
    }
}

data class DeviceLightAutomaticSnapshot(
    val deviceUid: String,
    val revision: Long,
    val capacity: Int,
    val channels: List<DeviceLightAutomaticChannel>,
    val programs: List<DeviceLightAutomaticProgram>
) {
    init {
        require(deviceUid.isNotBlank())
        require(revision >= 0L)
        require(capacity > 0)
        require(programs.size <= capacity)
        require(channels.isNotEmpty() && channels.distinct().size == channels.size)
        require(programs.map(DeviceLightAutomaticProgram::programId).distinct().size == programs.size)
        require(programs.all { program -> program.scene.channels.keys == channels.toSet() })
    }
}

sealed interface DeviceLightAutomaticReadResult {
    data class Available(val snapshot: DeviceLightAutomaticSnapshot) : DeviceLightAutomaticReadResult
    data class Failed(val failure: DeviceLightAutomaticFailure) : DeviceLightAutomaticReadResult
}

sealed interface DeviceLightAutomaticMutationResult {
    data object Success : DeviceLightAutomaticMutationResult
    data class Failed(val failure: DeviceLightAutomaticFailure) : DeviceLightAutomaticMutationResult
}

enum class DeviceLightAutomaticFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA
}

/** Firmware-backed boundary for the installed AUTO program collection. */
interface DeviceLightAutomaticOperations {
    suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult

    suspend fun setEnabled(
        deviceUid: String,
        expectedRevision: Long,
        programId: String,
        enabled: Boolean
    ): DeviceLightAutomaticMutationResult

    suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        programId: String
    ): DeviceLightAutomaticMutationResult
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
private const val MIN_WEEKDAYS_MASK = 1
private const val MAX_WEEKDAYS_MASK = 127
private const val MINUTE_MILLIS = 60_000L
private const val LAST_AUTHORED_MINUTE_MS = 86_340_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val MIN_RAMP_DURATION_MS = 0L
private const val RAMP_DURATION_STEP_MINUTES = 30L
private const val MAX_RAMP_DURATION_MINUTES = 150L
private const val RAMP_DURATION_STEP_MS = RAMP_DURATION_STEP_MINUTES * MINUTE_MILLIS
private const val MAX_RAMP_DURATION_MS = MAX_RAMP_DURATION_MINUTES * MINUTE_MILLIS
private val PERCENT_RANGE = MIN_PERCENT..MAX_PERCENT
private val WEEKDAYS_MASK_RANGE = MIN_WEEKDAYS_MASK..MAX_WEEKDAYS_MASK
private val AUTHORED_TIME_RANGE = 0L..LAST_AUTHORED_MINUTE_MS
private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
