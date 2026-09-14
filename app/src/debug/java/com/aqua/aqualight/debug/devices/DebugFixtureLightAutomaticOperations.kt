package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot

internal class DebugFixtureLightAutomaticOperations(
    private val delegate: DeviceLightAutomaticOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceLightAutomaticOperations {

    private val lock = Any()
    private val fixtureStates = mutableMapOf<String, DeviceLightAutomaticSnapshot>()

    override suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult {
        val normalizedUid = deviceUid.trim()
        val snapshot = synchronized(lock) { fixtureSnapshot(normalizedUid) }
        return snapshot?.let(DeviceLightAutomaticReadResult::Available)
            ?: delegate.read(deviceUid)
    }

    override suspend fun setEnabled(
        deviceUid: String,
        expectedRevision: Long,
        programId: String,
        enabled: Boolean
    ): DeviceLightAutomaticMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.setEnabled(deviceUid, expectedRevision, programId, enabled)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.INVALID_DATA
                )
            val source = current.programs.singleOrNull { program -> program.programId == programId }
                ?: return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.INVALID_DATA
                )
            if (expectedRevision != current.revision) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.REJECTED
                )
            }
            if (source.enabled == enabled) {
                return@synchronized DeviceLightAutomaticMutationResult.Success
            }
            if (enabled && current.programs.any { other ->
                    other.programId != source.programId && other.enabled && source.conflictsWith(other)
                }
            ) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.REJECTED
                )
            }
            fixtureStates[normalizedUid] = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                programs = current.programs.map { program ->
                    if (program.programId == programId) program.copy(enabled = enabled) else program
                }
            )
            DeviceLightAutomaticMutationResult.Success
        }
    }

    override suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        programId: String
    ): DeviceLightAutomaticMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.delete(deviceUid, expectedRevision, programId)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.INVALID_DATA
                )
            if (expectedRevision != current.revision ||
                current.programs.none { program -> program.programId == programId }
            ) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.REJECTED
                )
            }
            fixtureStates[normalizedUid] = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                programs = current.programs.filterNot { program -> program.programId == programId }
            )
            DeviceLightAutomaticMutationResult.Success
        }
    }

    private fun fixtureSnapshot(deviceUid: String): DeviceLightAutomaticSnapshot? {
        if (!isLightFixture(deviceUid)) return null
        return fixtureStates.getOrPut(deviceUid) {
            val root = requireNotNull(fixtures.rootSnapshot(deviceUid))
            val channels = root.channelSlots.lightChannels.map { slot ->
                slot.wireKey.value.toAutomaticChannel()
            }
            DeviceLightAutomaticSnapshot(
                deviceUid = deviceUid,
                revision = INITIAL_REVISION,
                capacity = AUTO_CAPACITY,
                channels = channels,
                programs = fixturePrograms(channels)
            )
        }
    }

    private fun isLightFixture(deviceUid: String): Boolean =
        fixtures.rootSnapshot(deviceUid)?.family == OwnerDeviceFamily.LIGHT
}

private fun fixturePrograms(
    channels: List<DeviceLightAutomaticChannel>
): List<DeviceLightAutomaticProgram> = listOf(
    DeviceLightAutomaticProgram(
        programId = "ap-00000001",
        enabled = true,
        weekdaysMask = EVERY_DAY_MASK,
        startTimeMs = hours(6),
        endTimeMs = hours(22),
        rampDurationMs = minutes(30),
        scene = fixtureScene(channels, red = 70, green = 60, blue = 50, white = 80)
    ),
    DeviceLightAutomaticProgram(
        programId = "ap-00000002",
        enabled = false,
        weekdaysMask = MONDAY_WEDNESDAY_FRIDAY_MASK,
        startTimeMs = hours(8),
        endTimeMs = hours(20),
        rampDurationMs = minutes(60),
        scene = fixtureScene(channels, red = 100, green = 40, blue = 30, white = 40)
    ),
    DeviceLightAutomaticProgram(
        programId = "ap-00000003",
        enabled = true,
        weekdaysMask = TUESDAY_THURSDAY_SATURDAY_MASK,
        startTimeMs = hours(7),
        endTimeMs = hours(21),
        rampDurationMs = minutes(90),
        scene = fixtureScene(channels, red = 50, green = 60, blue = 70, white = 70)
    )
)

private fun fixtureScene(
    channels: List<DeviceLightAutomaticChannel>,
    red: Int,
    green: Int,
    blue: Int,
    white: Int
): DeviceLightAutomaticScene = DeviceLightAutomaticScene(
    channels.associateWith { channel ->
        when (channel) {
            DeviceLightAutomaticChannel.RED -> red
            DeviceLightAutomaticChannel.GREEN -> green
            DeviceLightAutomaticChannel.BLUE -> blue
            DeviceLightAutomaticChannel.WHITE -> white
        }
    }
)

private fun String.toAutomaticChannel(): DeviceLightAutomaticChannel = checkNotNull(
    DeviceLightAutomaticChannel.entries.singleOrNull { channel -> channel.wireKey == this }
) { "Unsupported Debug Light fixture channel: $this" }

private fun DeviceLightAutomaticProgram.conflictsWith(other: DeviceLightAutomaticProgram): Boolean {
    if (weekdaysMask and other.weekdaysMask == 0) return false
    return daySegments().any { left ->
        other.daySegments().any { right -> left.first < right.second && right.first < left.second }
    }
}

private fun DeviceLightAutomaticProgram.daySegments(): List<Pair<Long, Long>> =
    if (endTimeMs > startTimeMs) {
        listOf(startTimeMs to endTimeMs)
    } else {
        listOf(startTimeMs to MILLIS_PER_DAY, 0L to endTimeMs)
    }

private fun hours(value: Int): Long = value * MINUTES_PER_HOUR * MILLIS_PER_MINUTE
private fun minutes(value: Int): Long = value * MILLIS_PER_MINUTE

private const val INITIAL_REVISION = 12L
private const val REVISION_INCREMENT = 1L
private const val AUTO_CAPACITY = 16
private const val EVERY_DAY_MASK = 0x7f
private const val MONDAY_WEDNESDAY_FRIDAY_MASK = 0x54
private const val TUESDAY_THURSDAY_SATURDAY_MASK = 0x2a
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
