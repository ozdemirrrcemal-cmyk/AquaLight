package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceTimerRuntimeRepository.setChannelRegime(
    deviceUid: DeviceUid,
    channelKey: String,
    regime: DeviceTimerRegime,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = withGlobalStatus(
    deviceUid
) { status ->
    setChannel(
        deviceUid,
        DeviceTimerChannelSetPayload(
            channelKey = channelKey,
            expectedRevision = status.revision,
            regime = regime,
            save = save
        )
    )
}

suspend fun DeviceTimerRuntimeRepository.setTemporaryOverride(
    deviceUid: DeviceUid,
    channelKey: String,
    regime: DeviceTimerRegime,
    durationMs: Long
): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = withGlobalStatus(
    deviceUid
) { status ->
    setChannel(
        deviceUid,
        DeviceTimerChannelSetPayload(
            channelKey = channelKey,
            expectedRevision = status.revision,
            regime = regime,
            durationMs = durationMs,
            save = false
        )
    )
}

suspend fun DeviceTimerRuntimeRepository.setChannelDisplayName(
    deviceUid: DeviceUid,
    channelKey: String,
    displayName: String,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
    val access = accessProvider(deviceUid)
    if (!access.supportsApi || !access.supportsChannelDisplayName) {
        return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
    }
    return withGlobalStatus(deviceUid) { status ->
        applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                displayName = DeviceTimerDisplayNameUpdate.Value(displayName),
                save = save
            )
        )
    }
}

suspend fun DeviceTimerRuntimeRepository.clearChannelDisplayName(
    deviceUid: DeviceUid,
    channelKey: String,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
    val access = accessProvider(deviceUid)
    if (!access.supportsApi || !access.supportsChannelDisplayName) {
        return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
    }
    return withGlobalStatus(deviceUid) { status ->
        applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                displayName = DeviceTimerDisplayNameUpdate.Clear,
                save = save
            )
        )
    }
}

suspend fun DeviceTimerRuntimeRepository.replaceSchedules(
    deviceUid: DeviceUid,
    channelKey: String,
    expectedRevision: Long,
    schedules: List<DeviceTimerScheduleConfig>,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = applyConfig(
    deviceUid,
    DeviceTimerConfigApplyPayload(
        channelKey = channelKey,
        expectedRevision = expectedRevision,
        schedules = schedules,
        save = save
    )
)

suspend fun DeviceTimerRuntimeRepository.createSchedule(
    deviceUid: DeviceUid,
    channelKey: String,
    schedule: DeviceTimerScheduleConfig,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
    deviceUid,
    channelKey,
    save
) { current ->
    require(current.none { it.slotId == schedule.slotId }) {
        "Timer slotId already exists for this channel: ${schedule.slotId}"
    }
    current + schedule
}

suspend fun DeviceTimerRuntimeRepository.updateSchedule(
    deviceUid: DeviceUid,
    channelKey: String,
    slotId: Int,
    schedule: DeviceTimerScheduleConfig,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
    deviceUid,
    channelKey,
    save
) { current ->
    require(schedule.slotId == slotId) { "Timer schedule slotId is immutable." }
    require(current.any { it.slotId == slotId }) { "Unknown Timer slotId: $slotId" }
    current.map { existing -> if (existing.slotId == slotId) schedule else existing }
}

suspend fun DeviceTimerRuntimeRepository.deleteSchedule(
    deviceUid: DeviceUid,
    channelKey: String,
    slotId: Int,
    save: Boolean = true
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
    deviceUid,
    channelKey,
    save
) { current ->
    require(current.any { it.slotId == slotId }) { "Unknown Timer slotId: $slotId" }
    current.filterNot { it.slotId == slotId }
}

private suspend fun DeviceTimerRuntimeRepository.mutateSchedules(
    deviceUid: DeviceUid,
    channelKey: String,
    save: Boolean,
    transform: (List<DeviceTimerScheduleConfig>) -> List<DeviceTimerScheduleConfig>
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
    val detail = when (val result = ensureChannelDetail(deviceUid, channelKey)) {
        is TimerStatusBaseline.Ready -> result.status
        is TimerStatusBaseline.Failed -> return result.outcome.asTimerFailure()
    }
    val current = detail.schedules.map(DeviceTimerScheduleStatus::toPayload)
    return applyConfig(
        deviceUid,
        DeviceTimerConfigApplyPayload(
            channelKey = channelKey,
            expectedRevision = detail.revision,
            schedules = transform(current),
            save = save
        )
    )
}

private fun DeviceTimerScheduleStatus.toPayload(): DeviceTimerScheduleConfig =
    DeviceTimerScheduleConfig(
        slotId = slotId,
        enabled = enabled,
        name = name,
        weekdays = weekdays,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        spansMidnight = spansMidnight
    )
