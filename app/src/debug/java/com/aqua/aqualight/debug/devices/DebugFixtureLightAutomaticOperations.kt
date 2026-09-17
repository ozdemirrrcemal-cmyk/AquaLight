package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgramDraft
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class DebugFixtureLightAutomaticOperations(
    private val delegate: DeviceLightAutomaticOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceLightAutomaticOperations {

    private val lock = Any()
    private val fixtureStates = mutableMapOf<String, DeviceLightAutomaticSnapshot>()

    override fun observe(deviceUid: String): Flow<DeviceLightAutomaticReadResult> {
        val snapshot = synchronized(lock) { fixtureSnapshot(deviceUid.trim()) }
        return snapshot?.let { value ->
            flowOf(DeviceLightAutomaticReadResult.Available(value))
        } ?: delegate.observe(deviceUid)
    }

    override fun current(deviceUid: String): DeviceLightAutomaticReadResult {
        val snapshot = synchronized(lock) { fixtureSnapshot(deviceUid.trim()) }
        return snapshot?.let(DeviceLightAutomaticReadResult::Available)
            ?: delegate.current(deviceUid)
    }

    override suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult {
        val normalizedUid = deviceUid.trim()
        val snapshot = synchronized(lock) { fixtureSnapshot(normalizedUid) }
        return snapshot?.let(DeviceLightAutomaticReadResult::Available)
            ?: delegate.read(deviceUid)
    }

    override suspend fun create(
        deviceUid: String,
        expectedRevision: Long,
        enabled: Boolean,
        draft: DeviceLightAutomaticProgramDraft
    ): DeviceLightAutomaticMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.create(deviceUid, expectedRevision, enabled, draft)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized invalidDataFailure()
            if (expectedRevision != current.revision) {
                return@synchronized staleRevisionFailure()
            }
            if (current.programs.size >= current.policy.capacity) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.CAPACITY_REACHED
                )
            }
            val candidate = draft.toProgram(
                programId = nextProgramId(current.programs),
                enabled = enabled
            )
            if (hasEnabledConflict(current, candidate, enabled)) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.OVERLAP
                )
            }
            fixtureStates[normalizedUid] = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                programs = current.programs + candidate
            )
            DeviceLightAutomaticMutationResult.Success
        }
    }

    override suspend fun update(
        deviceUid: String,
        expectedRevision: Long,
        programId: String,
        draft: DeviceLightAutomaticProgramDraft
    ): DeviceLightAutomaticMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.update(deviceUid, expectedRevision, programId, draft)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized invalidDataFailure()
            val source = current.programs.singleOrNull { program ->
                program.programId == programId
            } ?: return@synchronized DeviceLightAutomaticMutationResult.Failed(
                DeviceLightAutomaticFailure.NOT_FOUND
            )
            if (expectedRevision != current.revision) {
                return@synchronized staleRevisionFailure()
            }
            val candidate = draft.toProgram(programId = programId, enabled = source.enabled)
            if (hasEnabledConflict(current, candidate, candidate.enabled)) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.OVERLAP
                )
            }
            fixtureStates[normalizedUid] = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                programs = current.programs.map { program ->
                    if (program.programId == programId) candidate else program
                }
            )
            DeviceLightAutomaticMutationResult.Success
        }
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
                    DeviceLightAutomaticFailure.NOT_FOUND
                )
            if (expectedRevision != current.revision) {
                return@synchronized staleRevisionFailure()
            }
            if (source.enabled == enabled) {
                return@synchronized DeviceLightAutomaticMutationResult.Success
            }
            if (hasEnabledConflict(current, source, enabled)) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.OVERLAP
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
            if (expectedRevision != current.revision) {
                return@synchronized staleRevisionFailure()
            }
            if (current.programs.none { program -> program.programId == programId }) {
                return@synchronized DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.NOT_FOUND
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
                slot.wireKey.value.toDebugAutomaticChannel()
            }
            DeviceLightAutomaticSnapshot(
                deviceUid = deviceUid,
                productDisplayName = root.productDisplayName,
                revision = INITIAL_REVISION,
                policy = DeviceLightAutomaticPolicy(
                    capacity = AUTO_CAPACITY,
                    timeStepMs = MILLIS_PER_MINUTE,
                    rampDurationsMs = debugLightAutomaticRampDurationsMs
                ),
                channels = channels,
                programs = debugLightAutomaticFixturePrograms(channels),
                firmwareWriteAuthoritative = true
            )
        }
    }

    private fun hasEnabledConflict(
        snapshot: DeviceLightAutomaticSnapshot,
        source: DeviceLightAutomaticProgram,
        requestedEnabled: Boolean
    ): Boolean {
        val eligiblePrograms = snapshot.programs.filter { candidate ->
            candidate.programId != source.programId && candidate.enabled
        }
        return requestedEnabled && eligiblePrograms.any(source::conflictsWith)
    }

    private fun isLightFixture(deviceUid: String): Boolean =
        fixtures.rootSnapshot(deviceUid)?.family == OwnerDeviceFamily.LIGHT
}

private fun DeviceLightAutomaticProgramDraft.toProgram(
    programId: String,
    enabled: Boolean
): DeviceLightAutomaticProgram = DeviceLightAutomaticProgram(
    programId = programId,
    enabled = enabled,
    weekdaysMask = weekdaysMask,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    rampDurationMs = rampDurationMs,
    scene = scene
)

private fun nextProgramId(programs: List<DeviceLightAutomaticProgram>): String {
    val nextSequence = programs.maxOfOrNull { program ->
        program.programId.removePrefix(PROGRAM_ID_PREFIX).toLong(PROGRAM_ID_RADIX)
    }?.plus(REVISION_INCREMENT) ?: INITIAL_PROGRAM_SEQUENCE
    return PROGRAM_ID_PREFIX + nextSequence.toString(PROGRAM_ID_RADIX)
        .padStart(PROGRAM_ID_HEX_LENGTH, PROGRAM_ID_PAD_CHARACTER)
}

private fun invalidDataFailure(): DeviceLightAutomaticMutationResult =
    DeviceLightAutomaticMutationResult.Failed(DeviceLightAutomaticFailure.INVALID_DATA)

private fun staleRevisionFailure(): DeviceLightAutomaticMutationResult =
    DeviceLightAutomaticMutationResult.Failed(DeviceLightAutomaticFailure.STALE_REVISION)

private fun DeviceLightAutomaticProgram.conflictsWith(other: DeviceLightAutomaticProgram): Boolean {
    val otherIntervals = other.weeklyIntervals()
    return weeklyIntervals().any { candidate ->
        otherIntervals.any { existing ->
            WEEK_OFFSETS.any { offset ->
                candidate.overlaps(existing.shifted(offset))
            }
        }
    }
}

private fun DeviceLightAutomaticProgram.weeklyIntervals(): List<WeeklyInterval> =
    (0 until DAYS_PER_WEEK).mapNotNull { dayIndex ->
        val mask = 1 shl (WEEKDAY_FIRST_BIT_INDEX - dayIndex)
        if (weekdaysMask and mask == 0) {
            null
        } else {
            val start = dayIndex * MILLIS_PER_DAY + startTimeMs
            val end = dayIndex * MILLIS_PER_DAY + endTimeMs +
                if (endTimeMs <= startTimeMs) MILLIS_PER_DAY else 0L
            WeeklyInterval(start, end)
        }
    }

private data class WeeklyInterval(val startMs: Long, val endMs: Long) {
    fun shifted(offsetMs: Long) = WeeklyInterval(startMs + offsetMs, endMs + offsetMs)

    fun overlaps(other: WeeklyInterval): Boolean =
        startMs < other.endMs && other.startMs < endMs
}

private const val INITIAL_REVISION = 12L
private const val REVISION_INCREMENT = 1L
private const val AUTO_CAPACITY = 16
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val DAYS_PER_WEEK = 7
private const val WEEKDAY_FIRST_BIT_INDEX = 6
private const val MILLIS_PER_WEEK = DAYS_PER_WEEK * MILLIS_PER_DAY
private const val PROGRAM_ID_PREFIX = "ap-"
private const val PROGRAM_ID_RADIX = 16
private const val PROGRAM_ID_HEX_LENGTH = 8
private const val PROGRAM_ID_PAD_CHARACTER = '0'
private const val INITIAL_PROGRAM_SEQUENCE = 1L
private val WEEK_OFFSETS = listOf(-MILLIS_PER_WEEK, 0L, MILLIS_PER_WEEK)
