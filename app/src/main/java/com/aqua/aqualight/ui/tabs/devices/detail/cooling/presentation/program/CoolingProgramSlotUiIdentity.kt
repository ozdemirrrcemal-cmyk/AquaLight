package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

/** Presentation-only identity wrapper. [uiKey] is never persisted or sent to firmware. */
data class DeviceCoolingProgramSlotUiItem(
    val uiKey: Long,
    val slot: DeviceCoolingProgramSlot
)

/**
 * Owns stable keys only for the lifetime of one Program presentation session.
 *
 * Domain slots remain value objects. Keys preserve Compose identity while a draft is edited,
 * reordered or compacted after deletion; they intentionally have no persistence semantics.
 */
internal class CoolingProgramSlotUiIdentity {
    private var nextKeyValue = 1L

    fun allocateKey(): Long = nextKeyValue++

    fun createItems(slots: List<DeviceCoolingProgramSlot>): List<DeviceCoolingProgramSlotUiItem> =
        slots.map { slot -> DeviceCoolingProgramSlotUiItem(allocateKey(), slot) }

    fun reconcile(
        previousItems: List<DeviceCoolingProgramSlotUiItem>,
        updatedSlots: List<DeviceCoolingProgramSlot>,
        selectedSlotIndex: Int?,
        selectedUiKey: Long?
    ): List<DeviceCoolingProgramSlotUiItem> {
        val unmatchedItems = previousItems.toMutableList()
        selectedUiKey?.let { key ->
            val selectedItemIndex = unmatchedItems.indexOfFirst { item -> item.uiKey == key }
            if (selectedItemIndex >= 0) {
                unmatchedItems.removeAt(selectedItemIndex)
            }
        }

        return updatedSlots.mapIndexed { index, slot ->
            if (index == selectedSlotIndex && selectedUiKey != null) {
                DeviceCoolingProgramSlotUiItem(selectedUiKey, slot)
            } else {
                val matchingItemIndex = unmatchedItems.indexOfFirst { item -> item.slot == slot }
                if (matchingItemIndex >= 0) {
                    unmatchedItems.removeAt(matchingItemIndex)
                } else {
                    DeviceCoolingProgramSlotUiItem(allocateKey(), slot)
                }
            }
        }
    }
}
