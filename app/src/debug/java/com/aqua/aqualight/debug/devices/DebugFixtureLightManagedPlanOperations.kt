package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanAuthority
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import java.time.LocalDate

/** Firmware-shaped managed-plan authority for installable-debug Light fixtures. */
internal class DebugFixtureLightManagedPlanOperations(
    private val delegate: DeviceLightManagedPlanOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() }
) : DeviceLightManagedPlanOperations {

    private val lock = Any()
    private val fixtureStates = mutableMapOf<String, DeviceLightManagedPlanSnapshot>()
    private val nextPlanSequences = mutableMapOf<String, Long>()

    override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult {
        val normalizedUid = deviceUid.trim()
        val snapshot = synchronized(lock) { fixtureSnapshot(normalizedUid) }
        return snapshot?.let(DeviceLightManagedPlanReadResult::Available)
            ?: delegate.read(deviceUid)
    }

    override suspend fun apply(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority,
        draft: DeviceLightManagedPlanDraft
    ): DeviceLightManagedPlanMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.apply(deviceUid, authority, draft)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized failed(DeviceLightManagedPlanFailure.INVALID_DATA)
            if (current.authority != authority) {
                return@synchronized failed(DeviceLightManagedPlanFailure.STALE_AUTHORITY)
            }
            if (!draft.isValidFor(current.channels)) {
                return@synchronized failed(DeviceLightManagedPlanFailure.INVALID_DATA)
            }
            val planId = authority.installedPlanId ?: nextPlanId(normalizedUid)
            val activePhaseIndex = draft.phases.activePhaseIndex(todayEpochDay())
            val applied = current.copy(
                authority = authority.next(planId),
                installed = true,
                initialStartPercent = draft.initialStartPercent,
                phases = draft.phases,
                runtimeState = if (activePhaseIndex == null) {
                    DeviceLightManagedPlanRuntimeState.BEFORE_PLAN
                } else {
                    DeviceLightManagedPlanRuntimeState.ACTIVE
                },
                activePhaseIndex = activePhaseIndex
            )
            fixtureStates[normalizedUid] = applied
            DeviceLightManagedPlanMutationResult.Applied(applied)
        }
    }

    override suspend fun delete(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority
    ): DeviceLightManagedPlanMutationResult {
        val normalizedUid = deviceUid.trim()
        if (!isLightFixture(normalizedUid)) {
            return delegate.delete(deviceUid, authority)
        }
        return synchronized(lock) {
            val current = fixtureSnapshot(normalizedUid)
                ?: return@synchronized failed(DeviceLightManagedPlanFailure.INVALID_DATA)
            if (current.authority != authority) {
                return@synchronized failed(DeviceLightManagedPlanFailure.STALE_AUTHORITY)
            }
            if (!current.installed || authority.installedPlanId == null) {
                return@synchronized failed(DeviceLightManagedPlanFailure.NOT_FOUND)
            }
            fixtureStates[normalizedUid] = current.copy(
                authority = authority.next(installedPlanId = null),
                installed = false,
                initialStartPercent = DEFAULT_INITIAL_START_PERCENT,
                phases = emptyList(),
                runtimeState = DeviceLightManagedPlanRuntimeState.NOT_INSTALLED,
                activePhaseIndex = null
            )
            DeviceLightManagedPlanMutationResult.Deleted
        }
    }

    private fun fixtureSnapshot(deviceUid: String): DeviceLightManagedPlanSnapshot? {
        if (!isLightFixture(deviceUid)) return null
        return fixtureStates.getOrPut(deviceUid) {
            val root = requireNotNull(fixtures.rootSnapshot(deviceUid))
            val channels = root.channelSlots.lightChannels.map { slot ->
                requireNotNull(
                    DeviceLightAutomaticChannel.entries.singleOrNull { channel ->
                        channel.wireKey == slot.wireKey.value
                    }
                ) { "Unsupported managed-plan fixture channel: ${slot.wireKey.value}" }
            }
            DeviceLightManagedPlanSnapshot(
                deviceUid = deviceUid,
                productDisplayName = root.productDisplayName,
                channels = channels,
                authority = DeviceLightManagedPlanAuthority(
                    storageGeneration = INITIAL_STORAGE_GENERATION,
                    revision = INITIAL_REVISION,
                    installedPlanId = null
                ),
                installed = false,
                initialStartPercent = DEFAULT_INITIAL_START_PERCENT,
                phases = emptyList(),
                runtimeState = DeviceLightManagedPlanRuntimeState.NOT_INSTALLED,
                activePhaseIndex = null
            )
        }
    }

    private fun nextPlanId(deviceUid: String): String {
        val sequence = nextPlanSequences.getOrDefault(deviceUid, INITIAL_PLAN_SEQUENCE)
        nextPlanSequences[deviceUid] = sequence + REVISION_INCREMENT
        return PLAN_ID_PREFIX + sequence.toString(PLAN_ID_RADIX)
            .padStart(PLAN_ID_HEX_LENGTH, PLAN_ID_PAD_CHARACTER)
    }

    private fun isLightFixture(deviceUid: String): Boolean =
        fixtures.rootSnapshot(deviceUid)?.family == OwnerDeviceFamily.LIGHT
}

private fun DeviceLightManagedPlanAuthority.next(
    installedPlanId: String?
): DeviceLightManagedPlanAuthority = copy(
    storageGeneration = storageGeneration + REVISION_INCREMENT,
    revision = revision + REVISION_INCREMENT,
    installedPlanId = installedPlanId
)

private fun DeviceLightManagedPlanDraft.isValidFor(
    expectedChannels: List<DeviceLightAutomaticChannel>
): Boolean {
    if (
        initialStartPercent !in MIN_INITIAL_START_PERCENT..MAX_PERCENT ||
        initialStartPercent % INITIAL_START_PERCENT_STEP != 0 ||
        phases.size !in 1..MAX_PHASE_COUNT
    ) {
        return false
    }
    return phases.indices.all { index ->
        phases[index].isValid(
            expectedChannels = expectedChannels,
            expectedNextStart = phases.getOrNull(index + 1)?.validFromEpochDay,
            isFinal = index == phases.lastIndex
        )
    }
}

private fun DeviceLightManagedPlanPhaseDraft.isValid(
    expectedChannels: List<DeviceLightAutomaticChannel>,
    expectedNextStart: Long?,
    isFinal: Boolean
): Boolean {
    val durationMs = endTimeMs - startTimeMs
    val dateRangeValid = validFromEpochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY &&
        if (isFinal) {
            validUntilEpochDayExclusive == null
        } else {
            validUntilEpochDayExclusive == expectedNextStart &&
                expectedNextStart != null &&
                expectedNextStart > validFromEpochDay &&
                expectedNextStart <= MAX_EPOCH_DAY
        }
    val sceneValid = scene.channels.keys == expectedChannels.toSet() &&
        scene.channels.values.all { percent -> percent in MIN_PERCENT..MAX_PERCENT }
    return dateRangeValid &&
        transitionDays in MIN_TRANSITION_DAYS..MAX_TRANSITION_DAYS &&
        weekdaysMask in MIN_WEEKDAYS_MASK..MAX_WEEKDAYS_MASK &&
        startTimeMs in MIN_TIME_MS..MAX_TIME_MS &&
        endTimeMs in MIN_TIME_MS..MAX_TIME_MS &&
        durationMs > 0L &&
        rampDurationMs in MIN_RAMP_MS..(durationMs / TWO_RAMPS) &&
        sceneValid
}

private fun List<DeviceLightManagedPlanPhaseDraft>.activePhaseIndex(todayEpochDay: Long): Int? =
    indexOfLast { phase ->
        todayEpochDay >= phase.validFromEpochDay &&
            (phase.validUntilEpochDayExclusive == null ||
                todayEpochDay < phase.validUntilEpochDayExclusive)
    }.takeIf { index -> index >= 0 }

private fun failed(
    failure: DeviceLightManagedPlanFailure
): DeviceLightManagedPlanMutationResult = DeviceLightManagedPlanMutationResult.Failed(failure)

private const val INITIAL_STORAGE_GENERATION = 1L
private const val INITIAL_REVISION = 0L
private const val REVISION_INCREMENT = 1L
private const val INITIAL_PLAN_SEQUENCE = 1L
private const val PLAN_ID_PREFIX = "lp-"
private const val PLAN_ID_RADIX = 16
private const val PLAN_ID_HEX_LENGTH = 8
private const val PLAN_ID_PAD_CHARACTER = '0'
private const val MIN_INITIAL_START_PERCENT = 20
private const val DEFAULT_INITIAL_START_PERCENT = 100
private const val INITIAL_START_PERCENT_STEP = 5
private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
private const val MAX_PHASE_COUNT = 8
private const val MIN_EPOCH_DAY = 10_957L
private const val MAX_EPOCH_DAY = 47_481L
private const val MIN_TRANSITION_DAYS = 0
private const val MAX_TRANSITION_DAYS = 90
private const val MIN_WEEKDAYS_MASK = 1
private const val MAX_WEEKDAYS_MASK = 127
private const val MIN_TIME_MS = 0L
private const val MAX_TIME_MS = 86_399_999L
private const val MIN_RAMP_MS = 0L
private const val TWO_RAMPS = 2L
