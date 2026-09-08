package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Single authoritative in-process Timer state owner for Installable Debug fixtures. */
internal class DebugTimerFixtureRuntime(
    fixtures: DebugDeviceFixtureCatalog,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val snapshots = MutableStateFlow(
        fixtures.snapshots
            .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
            .filter { root -> root.family == OwnerDeviceFamily.TIMER }
            .associate { root -> root.deviceUid to root.toFixtureTimerControl(clock()) }
    )

    fun contains(deviceUid: String): Boolean = deviceUid.trim() in snapshots.value

    fun observe(deviceUid: String): Flow<DeviceTimerControlSnapshot?> {
        val normalizedUid = deviceUid.trim()
        return snapshots
            .map { current -> current[normalizedUid] }
            .distinctUntilChanged()
    }

    fun current(deviceUid: String): DeviceTimerControlSnapshot? = snapshots.value[deviceUid.trim()]

    fun updateChannel(
        deviceUid: String,
        slotId: String,
        transform: (DeviceTimerChannelSnapshot, Long) -> DeviceTimerChannelSnapshot
    ): DeviceTimerControlSnapshot? = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val current = snapshots.value[normalizedUid] ?: return@synchronized null
        val channelIndex = current.channels.indexOfFirst { channel ->
            channel.slotId == slotId.trim()
        }
        if (channelIndex < 0) return@synchronized null

        val updatedChannel = transform(current.channels[channelIndex], clock())
        val updated = if (updatedChannel == current.channels[channelIndex]) {
            current
        } else {
            current.copy(
                revision = current.revision.nextFixtureRevision(),
                uptimeMillis = current.uptimeMillis + FIXTURE_COMMAND_UPTIME_MILLIS,
                channels = current.channels.toMutableList().apply {
                    this[channelIndex] = updatedChannel
                }
            )
        }
        snapshots.value = snapshots.value + (normalizedUid to updated)
        updated
    }
}

internal fun DeviceTimerChannelSnapshot.withFixtureRegime(
    regime: DeviceTimerChannelRegime,
    nowMillis: Long
): DeviceTimerChannelSnapshot = when (regime) {
    DeviceTimerChannelRegime.AUTO -> toFixtureAutomaticState(nowMillis)
    DeviceTimerChannelRegime.ON -> copy(
        regime = regime,
        operatingState = DeviceTimerOperatingState.ON,
        activeScheduleSlotId = null,
        activeScheduleName = null,
        nextTransitionType = DeviceTimerNextTransitionType.NONE,
        nextTransitionAtEpochMillis = null,
        runtimeReason = DeviceTimerRuntimeReason.MANUAL_ON,
        temporaryOverrideActive = false,
        temporaryOverrideRemainingMillis = 0L
    )
    DeviceTimerChannelRegime.OFF -> copy(
        regime = regime,
        operatingState = DeviceTimerOperatingState.OFF,
        activeScheduleSlotId = null,
        activeScheduleName = null,
        nextTransitionType = DeviceTimerNextTransitionType.NONE,
        nextTransitionAtEpochMillis = null,
        runtimeReason = DeviceTimerRuntimeReason.MANUAL_OFF,
        temporaryOverrideActive = false,
        temporaryOverrideRemainingMillis = 0L
    )
}

internal fun DeviceTimerChannelSnapshot.withFixtureTemporaryOverride(
    regime: DeviceTimerChannelRegime,
    durationMillis: Long
): DeviceTimerChannelSnapshot = copy(
    regime = regime,
    operatingState = if (regime == DeviceTimerChannelRegime.ON) {
        DeviceTimerOperatingState.ON
    } else {
        DeviceTimerOperatingState.OFF
    },
    activeScheduleSlotId = null,
    activeScheduleName = null,
    nextTransitionType = DeviceTimerNextTransitionType.NONE,
    nextTransitionAtEpochMillis = null,
    runtimeReason = if (regime == DeviceTimerChannelRegime.ON) {
        DeviceTimerRuntimeReason.TEMPORARY_OVERRIDE_ON
    } else {
        DeviceTimerRuntimeReason.TEMPORARY_OVERRIDE_OFF
    },
    temporaryOverrideActive = true,
    temporaryOverrideRemainingMillis = durationMillis
)

internal fun DeviceTimerChannelSnapshot.withFixtureDisplayName(
    displayName: String
): DeviceTimerChannelSnapshot = copy(displayName = displayName)

internal fun DeviceTimerChannelSnapshot.withFixtureSchedules(
    drafts: List<DeviceTimerScheduleDraft>,
    nowMillis: Long
): DeviceTimerChannelSnapshot {
    val replacement = drafts.mapIndexed { index, draft -> draft.toFixtureSchedule(index) }
    val updated = copy(
        scheduleCount = replacement.size,
        schedules = replacement,
        activeScheduleSlotId = null,
        activeScheduleName = null
    )
    return if (regime == DeviceTimerChannelRegime.AUTO) {
        updated.toFixtureAutomaticState(nowMillis)
    } else {
        updated
    }
}

private fun DeviceRootSnapshot.toFixtureTimerControl(nowMillis: Long) =
    DeviceTimerControlSnapshot(
        deviceUid = deviceUid,
        revision = FIXTURE_INITIAL_REVISION,
        lockLoop = false,
        uptimeMillis = FIXTURE_INITIAL_UPTIME_MILLIS,
        maxSchedulesPerChannel = FIXTURE_MAX_SCHEDULES_PER_CHANNEL,
        capabilities = DeviceTimerControlCapabilities(
            readOnly = false,
            supportsConfigApply = true,
            supportsChannelState = true,
            supportsSchedules = true,
            supportsSpansMidnight = true,
            supportsTemporaryOverride = true,
            supportsChannelDisplayName = channelSlots.timerChannels.any { slot ->
                slot.displayNameEditable
            }
        ),
        channels = channelSlots.timerChannels.map { slot ->
            val schedule = slot.index.zeroBased.toFixtureSchedule()
            DeviceTimerChannelSnapshot(
                slotId = slot.id.value,
                channelNumber = slot.index.position,
                defaultName = slot.defaultDisplayName,
                displayName = slot.defaultDisplayName,
                regime = DeviceTimerChannelRegime.AUTO,
                operatingState = DeviceTimerOperatingState.OFF,
                scheduleCount = 1,
                activeScheduleSlotId = null,
                activeScheduleName = null,
                nextTransitionType = DeviceTimerNextTransitionType.NONE,
                nextTransitionAtEpochMillis = null,
                runtimeReason = DeviceTimerRuntimeReason.NOT_EVALUATED,
                clockReady = true,
                temporaryOverrideActive = false,
                temporaryOverrideRemainingMillis = 0L,
                outputHealth = DeviceTimerOutputHealth.UNVERIFIED,
                physicalFeedbackAvailable = false,
                displayNameEditable = slot.displayNameEditable,
                schedules = listOf(schedule)
            ).toFixtureAutomaticState(nowMillis)
        }
    )

private fun DeviceTimerChannelSnapshot.toFixtureAutomaticState(
    nowMillis: Long
): DeviceTimerChannelSnapshot {
    val enabledSchedule = schedules.orEmpty().firstOrNull { schedule -> schedule.enabled }
    val outputOn = enabledSchedule != null && channelNumber % FIXTURE_OUTPUT_PATTERN_DIVISOR != 0
    return copy(
        regime = DeviceTimerChannelRegime.AUTO,
        operatingState = if (outputOn) {
            DeviceTimerOperatingState.ON
        } else {
            DeviceTimerOperatingState.OFF
        },
        activeScheduleSlotId = enabledSchedule?.slotId?.takeIf { outputOn },
        activeScheduleName = enabledSchedule?.name?.takeIf { outputOn },
        nextTransitionType = if (enabledSchedule == null) {
            DeviceTimerNextTransitionType.NONE
        } else if (outputOn) {
            DeviceTimerNextTransitionType.OFF
        } else {
            DeviceTimerNextTransitionType.ON
        },
        nextTransitionAtEpochMillis = enabledSchedule?.let {
            nowMillis + channelNumber * MILLIS_PER_HOUR
        },
        runtimeReason = when {
            enabledSchedule == null -> DeviceTimerRuntimeReason.NO_ENABLED_SCHEDULES
            outputOn -> DeviceTimerRuntimeReason.SCHEDULE_ACTIVE
            else -> DeviceTimerRuntimeReason.OUTSIDE_SCHEDULE
        },
        temporaryOverrideActive = false,
        temporaryOverrideRemainingMillis = 0L
    )
}

private fun Int.toFixtureSchedule(): DeviceTimerScheduleSnapshot {
    val position = this + 1
    val start = (FIXTURE_SCHEDULE_START_HOUR + position) * MILLIS_PER_HOUR
    return DeviceTimerScheduleSnapshot(
        index = this,
        slotId = FIXTURE_SCHEDULE_SLOT_BASE + position,
        enabled = true,
        name = "Program $position",
        weekdays = List(FIXTURE_WEEKDAY_COUNT) { true },
        startTimeMillis = start,
        endTimeMillis = start + FIXTURE_SCHEDULE_DURATION_HOURS * MILLIS_PER_HOUR,
        spansMidnight = false
    )
}

private fun DeviceTimerScheduleDraft.toFixtureSchedule(index: Int) =
    DeviceTimerScheduleSnapshot(
        index = index,
        slotId = slotId,
        enabled = enabled,
        name = name,
        weekdays = weekdays,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        spansMidnight = spansMidnight
    )

private fun Long.nextFixtureRevision(): Long = if (this == UInt.MAX_VALUE.toLong()) 0L else this + 1L

private const val FIXTURE_INITIAL_REVISION = 1L
private const val FIXTURE_INITIAL_UPTIME_MILLIS = 120_000L
private const val FIXTURE_COMMAND_UPTIME_MILLIS = 1_000L
private const val FIXTURE_MAX_SCHEDULES_PER_CHANNEL = 8
private const val FIXTURE_WEEKDAY_COUNT = 7
private const val FIXTURE_OUTPUT_PATTERN_DIVISOR = 2
private const val FIXTURE_SCHEDULE_SLOT_BASE = 10
private const val FIXTURE_SCHEDULE_START_HOUR = 6L
private const val FIXTURE_SCHEDULE_DURATION_HOURS = 8L
private const val MILLIS_PER_HOUR = 3_600_000L
