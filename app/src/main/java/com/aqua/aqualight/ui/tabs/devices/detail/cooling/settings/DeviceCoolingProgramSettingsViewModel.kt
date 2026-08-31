@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI draft state for user-defined Cooling working periods.
 *
 * A program period owns only its start/end time and fan speed limit. Preset types and temperature
 * thresholds are intentionally not part of this presentation contract or firmware/user settings.
 */
class DeviceCoolingProgramSettingsViewModel : ViewModel() {

    private val initialSlots = emptyList<DeviceCoolingProgramSlot>()
    private val _uiState = MutableStateFlow(
        DeviceCoolingProgramSettingsUiState(
            slots = initialSlots,
            persistedSlots = initialSlots
        )
    )
    val uiState: StateFlow<DeviceCoolingProgramSettingsUiState> = _uiState.asStateFlow()

    private var nextSlotNumber = 1

    fun selectSlot(slotId: String) {
        _uiState.update { state ->
            if (state.slots.none { slot -> slot.id == slotId }) {
                state
            } else {
                state.copy(
                    selectedSlotId = if (state.selectedSlotId == slotId) null else slotId
                )
            }
        }
    }

    fun updateStartTime(slotId: String, minutesOfDay: Int): Boolean =
        updateSlot(slotId, rejectScheduleOverlap = true) { slot ->
            val normalized = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            if (normalized == slot.endMinutes) slot else slot.copy(startMinutes = normalized)
        }

    fun updateEndTime(slotId: String, minutesOfDay: Int): Boolean =
        updateSlot(slotId, rejectScheduleOverlap = true) { slot ->
            val normalized = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            if (normalized == slot.startMinutes) slot else slot.copy(endMinutes = normalized)
        }

    fun updateFanLimit(slotId: String, percent: Int) {
        updateSlot(slotId) { slot ->
            slot.copy(fanLimitPercent = snapFanLimit(percent))
        }
    }

    fun addTimeSlot() {
        _uiState.update { state ->
            if (!state.canAddSlot) return@update state
            val window = findNextFreeWindow(state.slots) ?: return@update state
            val newSlot = DeviceCoolingProgramSlot(
                id = "period-${nextSlotNumber++}",
                startMinutes = window.first,
                endMinutes = window.second,
                fanLimitPercent = DeviceCoolingProgramPolicy.maximumFanLimitPercent
            )
            state.copy(
                slots = (state.slots + newSlot).sortedBy(DeviceCoolingProgramSlot::startMinutes),
                selectedSlotId = newSlot.id,
                saveState = DeviceCoolingProgramSaveState.IDLE
            )
        }
    }

    fun deleteTimeSlot(slotId: String): Boolean {
        var deleted = false
        _uiState.update { state ->
            if (state.slots.none { slot -> slot.id == slotId }) {
                state
            } else {
                deleted = true
                state.copy(
                    slots = state.slots.filterNot { slot -> slot.id == slotId },
                    selectedSlotId = state.selectedSlotId.takeUnless { selected -> selected == slotId },
                    saveState = DeviceCoolingProgramSaveState.IDLE
                )
            }
        }
        return deleted
    }

    fun saveDraft() {
        _uiState.update { state ->
            if (!state.hasChanges) state
            else state.copy(
                persistedSlots = state.slots,
                saveState = DeviceCoolingProgramSaveState.SAVED
            )
        }
    }

    private fun updateSlot(
        slotId: String,
        rejectScheduleOverlap: Boolean = false,
        transform: (DeviceCoolingProgramSlot) -> DeviceCoolingProgramSlot
    ): Boolean {
        var accepted = true
        _uiState.update { state ->
            val updated = state.slots.map { slot ->
                if (slot.id == slotId) transform(slot) else slot
            }
            when {
                updated == state.slots -> {
                    accepted = true
                    state
                }
                rejectScheduleOverlap && updated.hasScheduleOverlapFor(slotId) -> {
                    accepted = false
                    state
                }
                else -> {
                    accepted = true
                    state.copy(
                        slots = updated.sortedBy(DeviceCoolingProgramSlot::startMinutes),
                        selectedSlotId = slotId,
                        saveState = DeviceCoolingProgramSaveState.IDLE
                    )
                }
            }
        }
        return accepted
    }
}

data class DeviceCoolingProgramSettingsUiState(
    val slots: List<DeviceCoolingProgramSlot> = emptyList(),
    val persistedSlots: List<DeviceCoolingProgramSlot> = emptyList(),
    val selectedSlotId: String? = null,
    val saveState: DeviceCoolingProgramSaveState = DeviceCoolingProgramSaveState.IDLE
) {
    val selectedSlot: DeviceCoolingProgramSlot?
        get() = slots.firstOrNull { slot -> slot.id == selectedSlotId }

    val hasChanges: Boolean
        get() = slots != persistedSlots

    val canAddSlot: Boolean
        get() = slots.size < DeviceCoolingProgramPolicy.maximumSlotCount

    fun activeSlotAt(minutesOfDay: Int): DeviceCoolingProgramSlot? {
        val minute = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return slots.firstOrNull { slot -> slot.contains(minute) }
    }
}

data class DeviceCoolingProgramSlot(
    val id: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val fanLimitPercent: Int
) {
    init {
        require(id.isNotBlank())
        require(startMinutes in 0 until MINUTES_PER_DAY)
        require(endMinutes in 0 until MINUTES_PER_DAY)
        require(startMinutes != endMinutes)
        require(fanLimitPercent in 0..100)
    }

    fun contains(minutesOfDay: Int): Boolean = if (startMinutes < endMinutes) {
        minutesOfDay in startMinutes until endMinutes
    } else {
        minutesOfDay >= startMinutes || minutesOfDay < endMinutes
    }
}

enum class DeviceCoolingProgramSaveState {
    IDLE,
    SAVED
}

object DeviceCoolingProgramPolicy {
    const val minimumFanLimitPercent = 0
    const val maximumFanLimitPercent = 100
    const val fanLimitStepPercent = 5
    const val maximumSlotCount = 6
}

private fun snapFanLimit(percent: Int): Int {
    val bounded = percent.coerceIn(
        DeviceCoolingProgramPolicy.minimumFanLimitPercent,
        DeviceCoolingProgramPolicy.maximumFanLimitPercent
    )
    val step = DeviceCoolingProgramPolicy.fanLimitStepPercent
    return (((bounded + step / 2) / step) * step).coerceIn(
        DeviceCoolingProgramPolicy.minimumFanLimitPercent,
        DeviceCoolingProgramPolicy.maximumFanLimitPercent
    )
}

private fun List<DeviceCoolingProgramSlot>.hasScheduleOverlapFor(slotId: String): Boolean {
    val candidate = firstOrNull { slot -> slot.id == slotId } ?: return false
    return any { other -> other.id != slotId && candidate.overlaps(other) }
}

private fun DeviceCoolingProgramSlot.overlaps(other: DeviceCoolingProgramSlot): Boolean =
    contains(other.startMinutes) || other.contains(startMinutes)

private fun findNextFreeWindow(
    slots: List<DeviceCoolingProgramSlot>
): Pair<Int, Int>? {
    val occupied = BooleanArray(MINUTES_PER_DAY)
    slots.forEach { slot ->
        var minute = slot.startMinutes
        while (minute != slot.endMinutes) {
            occupied[minute] = true
            minute = (minute + 1) % MINUTES_PER_DAY
        }
    }

    for (start in 0..(MINUTES_PER_DAY - NEW_SLOT_DURATION_MINUTES) step NEW_SLOT_SCAN_STEP_MINUTES) {
        val end = start + NEW_SLOT_DURATION_MINUTES
        if ((start until end).all { minute -> !occupied[minute] }) {
            return start to end
        }
    }
    return null
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val NEW_SLOT_DURATION_MINUTES = 60
private const val NEW_SLOT_SCAN_STEP_MINUTES = 30
