package com.aqua.aqualight.data.devices.timer

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerScheduleStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus

/** Maps central Timer authority to firmware-independent application snapshots. */
internal object DeviceTimerControlSnapshotMapper {

    fun map(
        root: DeviceRootSnapshot,
        state: DeviceTimerRuntimeState
    ): DeviceTimerControlSnapshot? {
        val status = state.status
        val slots = root.channelSlots.timerChannels
        return if (
            status == null ||
            !root.matchesTimerCatalog(slots) ||
            !state.isReadableGlobalStatus(status, slots)
        ) {
            null
        } else {
            mapChannels(status, slots, state)?.let { channels ->
                DeviceTimerControlSnapshot(
                    deviceUid = root.deviceUid,
                    revision = status.revision,
                    lockLoop = status.lockLoop,
                    uptimeMillis = status.uptimeMs,
                    maxSchedulesPerChannel = status.maxSchedulesPerChannel,
                    capabilities = status.toApplicationCapabilities(slots),
                    channels = channels
                )
            }
        }
    }

    private fun mapChannels(
        status: DeviceTimerStatus,
        slots: List<DeviceTimerChannelSlot>,
        state: DeviceTimerRuntimeState
    ): List<DeviceTimerChannelSnapshot>? {
        val orderedChannels = status.channels.sortedBy(DeviceTimerChannelStatus::listIndex)
        val orderedSlots = slots.sortedBy { slot -> slot.index.zeroBased }
        val channels = orderedChannels.zip(orderedSlots).map { (channel, slot) ->
            val detail = state.channelDetails[channel.key]
            val detailMatches = detail == null || detail.matchesChannelDetail(status, channel)
            val schedules = detail?.schedules
                ?.map(DeviceTimerScheduleStatus::toApplicationSnapshot)
            if (channel.matches(slot) && detailMatches) {
                channel.toApplicationSnapshot(slot, schedules)
            } else {
                null
            }
        }
        return channels
            .takeIf { mapped -> mapped.all { channel -> channel != null } }
            ?.filterNotNull()
    }
}

internal fun DeviceRootSnapshot.matchesTimerCatalog(
    expectedSlots: List<DeviceTimerChannelSlot> = channelSlots.timerChannels
): Boolean = catalogState == DeviceRootCatalogState.VALID &&
    family == OwnerDeviceFamily.TIMER &&
    expectedSlots.isNotEmpty() &&
    timerChannelCount == expectedSlots.size

@Suppress("ComplexCondition")
private fun DeviceTimerRuntimeState.isReadableGlobalStatus(
    status: DeviceTimerStatus,
    slots: List<DeviceTimerChannelSlot>
): Boolean = authoritative &&
    !requiresStatusRefresh &&
    connectionGeneration != null &&
    !status.channelScoped &&
    !status.schedulesIncluded &&
    status.selectedChannelKey == null &&
    status.returnedScheduleCount == 0 &&
    status.channels.size == slots.size &&
    status.channelCount == slots.size &&
    status.runtime.supportsChannels

@Suppress("ComplexCondition")
private fun DeviceTimerChannelStatus.matches(slot: DeviceTimerChannelSlot): Boolean =
    index == slot.index.zeroBased &&
        listIndex == slot.index.zeroBased &&
        key == slot.wireKey.value &&
        name == slot.defaultDisplayName &&
        profileManaged &&
        editable.displayName == slot.displayNameEditable

private fun DeviceTimerStatus.matchesChannelDetail(
    global: DeviceTimerStatus,
    channel: DeviceTimerChannelStatus
): Boolean = channelScoped &&
    schedulesIncluded &&
    selectedChannelKey == channel.key &&
    revision == global.revision &&
    channelCount == global.channelCount &&
    maxSchedulesPerChannel == global.maxSchedulesPerChannel &&
    channels.singleOrNull()?.let { detailChannel ->
        detailChannel.key == channel.key &&
            detailChannel.index == channel.index &&
            detailChannel.listIndex == channel.listIndex &&
            detailChannel.scheduleCount == schedules.size
    } == true &&
    schedules.all { schedule -> schedule.channelKey == channel.key }

private fun DeviceTimerStatus.toApplicationCapabilities(
    slots: List<DeviceTimerChannelSlot>
) = DeviceTimerControlCapabilities(
    readOnly = runtime.readOnly,
    supportsConfigApply = runtime.supportsConfigApply,
    supportsChannelState = runtime.supportsChannelSet,
    supportsSchedules = runtime.supportsSchedules,
    supportsSpansMidnight = runtime.supportsSpansMidnight,
    supportsTemporaryOverride = runtime.supportsTemporaryOverride,
    supportsChannelDisplayName = runtime.supportsConfigApply &&
        slots.any(DeviceTimerChannelSlot::displayNameEditable)
)

private fun DeviceTimerChannelStatus.toApplicationSnapshot(
    slot: DeviceTimerChannelSlot,
    schedules: List<DeviceTimerScheduleSnapshot>?
) = DeviceTimerChannelSnapshot(
    slotId = slot.id.value,
    channelNumber = slot.index.position,
    defaultName = slot.defaultDisplayName,
    displayName = displayName,
    regime = DeviceTimerChannelRegime.valueOf(regime.name),
    operatingState = DeviceTimerOperatingState.valueOf(operatingState.name),
    scheduleCount = scheduleCount,
    activeScheduleSlotId = activeSlotId,
    activeScheduleName = activeSlotName,
    nextTransitionType = DeviceTimerNextTransitionType.valueOf(nextTransitionType.name),
    nextTransitionAtEpochMillis = nextTransitionAt,
    runtimeReason = DeviceTimerRuntimeReason.valueOf(runtimeReason.name),
    clockReady = clockReady,
    temporaryOverrideActive = temporaryOverrideActive,
    temporaryOverrideRemainingMillis = temporaryOverrideRemainingMs,
    outputHealth = DeviceTimerOutputHealth.valueOf(outputHealth.name),
    physicalFeedbackAvailable = physicalFeedbackAvailable,
    displayNameEditable = editable.displayName,
    schedules = schedules
)

private fun DeviceTimerScheduleStatus.toApplicationSnapshot() = DeviceTimerScheduleSnapshot(
    index = index,
    slotId = slotId,
    enabled = enabled,
    name = name,
    weekdays = weekdays,
    startTimeMillis = startTimeMs,
    endTimeMillis = endTimeMs,
    spansMidnight = spansMidnight
)
