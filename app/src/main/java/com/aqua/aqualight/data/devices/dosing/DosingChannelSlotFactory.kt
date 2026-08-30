package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex

/** Materializes the fixed addressable channel shape for a validated Dosing catalog product. */
internal object DosingChannelSlotFactory {

    fun createSequential(
        count: Int,
        displayNameEditable: Boolean
    ): List<DeviceDosingChannelSlot> = List(count) { index ->
        val slotIndex = DeviceSlotIndex(index)
        DeviceDosingChannelSlot(
            index = slotIndex,
            wireKey = DeviceChannelWireKey("channel${slotIndex.position}"),
            defaultDisplayName = "Channel ${slotIndex.position}",
            displayNameEditable = displayNameEditable
        )
    }
}
