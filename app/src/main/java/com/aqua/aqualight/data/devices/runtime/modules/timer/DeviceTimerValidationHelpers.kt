package com.aqua.aqualight.data.devices.runtime.modules.timer

internal fun DeviceTimerChannelStatus.sameTimerChannelIdentity(
    other: DeviceTimerChannelStatus
): Boolean = index == other.index &&
    listIndex == other.listIndex &&
    key == other.key &&
    name == other.name &&
    profileManaged == other.profileManaged &&
    channelKind == other.channelKind &&
    gpio == other.gpio &&
    ledcChannel == other.ledcChannel &&
    group == other.group &&
    invert == other.invert &&
    pwmResolutionBits == other.pwmResolutionBits &&
    pwmFrequencyHz == other.pwmFrequencyHz &&
    physicalFeedbackAvailable == other.physicalFeedbackAvailable &&
    editable == other.editable
