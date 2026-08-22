package com.aqua.aqualight.application.devices.dosing

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot

/**
 * Validates one complete firmware-authoritative Dosing channel set against commercial topology.
 *
 * Keeping this rule in the application layer gives entry preparation and every presentation
 * surface the same all-or-nothing decision. Partial or mixed-device sets are never renderable.
 */
fun validatedDosingChannelSetOrNull(
    deviceUid: String,
    catalogChannels: List<DeviceDosingChannelSlot>,
    snapshots: Collection<DeviceDosingChannelSnapshot>
): List<DeviceDosingChannelSnapshot>? {
    val expectedCount = catalogChannels.size
    if (deviceUid.isBlank() || expectedCount == 0 || snapshots.size != expectedCount) return null

    val catalogBySlot = catalogChannels.associateBy { slot -> slot.id.value }
    val ordered = snapshots.sortedBy(DeviceDosingChannelSnapshot::channelNumber)
    return ordered.takeIf { channels ->
        channels.map(DeviceDosingChannelSnapshot::slotId).toSet().size == expectedCount &&
            channels.map(DeviceDosingChannelSnapshot::channelNumber) ==
            (1..expectedCount).toList() &&
            channels.all { channel ->
                channel.deviceUid == deviceUid &&
                    channel.pumpCount == expectedCount &&
                    catalogBySlot[channel.slotId]?.index?.position == channel.channelNumber
            }
    }
}
