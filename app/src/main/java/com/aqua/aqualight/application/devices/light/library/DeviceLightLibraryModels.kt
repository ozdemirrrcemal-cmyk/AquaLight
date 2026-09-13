package com.aqua.aqualight.application.devices.light.library

enum class DeviceLightLibraryKind {
    MANUAL,
    CUSTOM
}

enum class DeviceLightLibraryChannel(val sceneKey: String) {
    RED("redPercent"),
    GREEN("greenPercent"),
    BLUE("bluePercent"),
    WHITE("whitePercent");

    companion object {
        fun fromSceneKey(sceneKey: String): DeviceLightLibraryChannel? =
            entries.singleOrNull { channel -> channel.sceneKey == sceneKey }
    }
}

data class DeviceLightLibraryTarget(
    val deviceUid: String,
    val productKey: String,
    val channels: List<DeviceLightLibraryChannel>,
    val estimatedPowerWatts: Int?
) {
    init {
        require(deviceUid.isNotBlank())
        require(productKey.isNotBlank())
        require(channels.size in 3..4 && channels.distinct().size == channels.size)
        require(estimatedPowerWatts == null || estimatedPowerWatts >= 0)
    }
}

data class DeviceLightLibraryScene(
    val channels: Map<DeviceLightLibraryChannel, Int>
) {
    init {
        require(channels.isNotEmpty())
        require(channels.values.all { percent -> percent in PERCENT_RANGE })
    }
}

data class DeviceLightLibraryCustomPoint(
    val timeMs: Long,
    val scene: DeviceLightLibraryScene
) {
    init {
        require(timeMs in 0..LAST_DAY_MILLISECOND)
    }
}

sealed interface DeviceLightLibraryPayload {
    data class Manual(
        val scene: DeviceLightLibraryScene
    ) : DeviceLightLibraryPayload

    data class Custom(
        val weekdaysMask: Int,
        /** Actual authored time points; this count is never a sum of channel values. */
        val points: List<DeviceLightLibraryCustomPoint>
    ) : DeviceLightLibraryPayload {
        init {
            require(weekdaysMask in 1..127)
            require(points.size in 1..MAX_CUSTOM_POINTS)
            require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
            require(points.map { point -> point.scene.channels.keys }.distinct().size == 1)
        }
    }
}

data class DeviceLightLibraryEntry(
    val id: String,
    val name: String,
    val productKey: String,
    val channels: List<DeviceLightLibraryChannel>,
    val payload: DeviceLightLibraryPayload,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Derived from authoritative runtime payloads and deliberately never persisted. */
    val isLoaded: Boolean
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(productKey.isNotBlank())
        require(channels.size in 3..4 && channels.distinct().size == channels.size)
        require(updatedAtMillis >= createdAtMillis)
        when (payload) {
            is DeviceLightLibraryPayload.Manual ->
                require(payload.scene.channels.keys == channels.toSet())
            is DeviceLightLibraryPayload.Custom -> payload.points.forEach { point ->
                require(point.scene.channels.keys == channels.toSet())
            }
        }
    }

    val kind: DeviceLightLibraryKind
        get() = when (payload) {
            is DeviceLightLibraryPayload.Manual -> DeviceLightLibraryKind.MANUAL
            is DeviceLightLibraryPayload.Custom -> DeviceLightLibraryKind.CUSTOM
        }
}

data class DeviceLightLibrarySnapshot(
    val target: DeviceLightLibraryTarget,
    val entries: List<DeviceLightLibraryEntry>
)

sealed interface DeviceLightLibraryResult {
    data class Available(val snapshot: DeviceLightLibrarySnapshot) : DeviceLightLibraryResult
    data class Failed(val failure: DeviceLightLibraryFailure) : DeviceLightLibraryResult
}

sealed interface DeviceLightLibraryMutationResult {
    data class Success(val entryId: String? = null) : DeviceLightLibraryMutationResult
    data class Failed(val failure: DeviceLightLibraryFailure) : DeviceLightLibraryMutationResult
}

enum class DeviceLightLibraryFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA,
    INVALID_NAME,
    DUPLICATE_NAME,
    NOT_FOUND,
    INCOMPATIBLE
}

private val PERCENT_RANGE = 0..100
private const val MAX_CUSTOM_POINTS = 96
private const val LAST_DAY_MILLISECOND = 86_399_999L
