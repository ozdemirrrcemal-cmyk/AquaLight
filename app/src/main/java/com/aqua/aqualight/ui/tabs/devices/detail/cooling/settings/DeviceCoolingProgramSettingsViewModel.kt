@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI draft state for the Cooling multi-period program editor.
 *
 * The schedule contract is intentionally isolated from firmware transport for this design pass.
 * Every control is live and testable now; a firmware-backed operations boundary can replace the
 * in-memory baseline without changing the screen or the shared bottom sheets.
 */
class DeviceCoolingProgramSettingsViewModel : ViewModel() {

    private val initialSlots = defaultProgramSlots()
    private val _uiState = MutableStateFlow(
        DeviceCoolingProgramSettingsUiState(
            slots = initialSlots,
            persistedSlots = initialSlots
        )
    )
    val uiState: StateFlow<DeviceCoolingProgramSettingsUiState> = _uiState.asStateFlow()

    private var nextCustomSlotNumber = 1

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

    fun updateStartTime(slotId: String, minutesOfDay: Int) {
        updateSlot(slotId) { slot ->
            val normalized = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            if (normalized == slot.endMinutes) slot else slot.copy(startMinutes = normalized)
        }
    }

    fun updateEndTime(slotId: String, minutesOfDay: Int) {
        updateSlot(slotId) { slot ->
            val normalized = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            if (normalized == slot.startMinutes) slot else slot.copy(endMinutes = normalized)
        }
    }

    fun updateStartTemperature(slotId: String, temperatureC: Double) {
        updateSlot(slotId) { slot ->
            val value = temperatureC.coerceIn(
                DeviceCoolingProgramPolicy.minimumTemperatureC,
                slot.maximumSpeedTemperatureC - DeviceCoolingProgramPolicy.minimumTemperatureGapC
            )
            slot.copy(startTemperatureC = value)
        }
    }

    fun updateMaximumSpeedTemperature(slotId: String, temperatureC: Double) {
        updateSlot(slotId) { slot ->
            val value = temperatureC.coerceIn(
                slot.startTemperatureC + DeviceCoolingProgramPolicy.minimumTemperatureGapC,
                DeviceCoolingProgramPolicy.maximumTemperatureC
            )
            slot.copy(maximumSpeedTemperatureC = value)
        }
    }

    fun updateFanLimit(slotId: String, percent: Int) {
        updateSlot(slotId) { slot ->
            slot.copy(
                fanLimitPercent = percent.coerceIn(
                    DeviceCoolingProgramPolicy.minimumFanLimitPercent,
                    DeviceCoolingProgramPolicy.maximumFanLimitPercent
                )
            )
        }
    }

    fun addTimeSlot() {
        _uiState.update { state ->
            if (!state.canAddSlot) return@update state
            val window = findNextFreeWindow(state.slots) ?: return@update state
            val newSlot = DeviceCoolingProgramSlot(
                id = "custom-${nextCustomSlotNumber++}",
                label = DeviceCoolingProgramSlotLabel.CUSTOM,
                startMinutes = window.first,
                endMinutes = window.second,
                startTemperatureC = 25.0,
                maximumSpeedTemperatureC = 27.0,
                fanLimitPercent = 60
            )
            state.copy(
                slots = (state.slots + newSlot).sortedBy(DeviceCoolingProgramSlot::startMinutes),
                selectedSlotId = newSlot.id,
                saveState = DeviceCoolingProgramSaveState.IDLE
            )
        }
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
        transform: (DeviceCoolingProgramSlot) -> DeviceCoolingProgramSlot
    ) {
        _uiState.update { state ->
            val updated = state.slots.map { slot ->
                if (slot.id == slotId) transform(slot) else slot
            }
            if (updated == state.slots) state
            else state.copy(
                slots = updated.sortedBy(DeviceCoolingProgramSlot::startMinutes),
                selectedSlotId = slotId,
                saveState = DeviceCoolingProgramSaveState.IDLE
            )
        }
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
    val label: DeviceCoolingProgramSlotLabel,
    val startMinutes: Int,
    val endMinutes: Int,
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double,
    val fanLimitPercent: Int
) {
    init {
        require(id.isNotBlank())
        require(startMinutes in 0 until MINUTES_PER_DAY)
        require(endMinutes in 0 until MINUTES_PER_DAY)
        require(startMinutes != endMinutes)
        require(startTemperatureC.isFinite())
        require(maximumSpeedTemperatureC.isFinite())
        require(maximumSpeedTemperatureC > startTemperatureC)
        require(fanLimitPercent in 0..100)
    }

    fun contains(minutesOfDay: Int): Boolean = if (startMinutes < endMinutes) {
        minutesOfDay in startMinutes until endMinutes
    } else {
        minutesOfDay >= startMinutes || minutesOfDay < endMinutes
    }
}

enum class DeviceCoolingProgramSlotLabel {
    QUIET,
    INTENSIVE,
    NIGHT,
    CUSTOM
}

enum class DeviceCoolingProgramSaveState {
    IDLE,
    SAVED
}

object DeviceCoolingProgramPolicy {
    const val minimumTemperatureC = 0.0
    const val maximumTemperatureC = 90.0
    const val temperatureStepC = 0.5
    const val minimumTemperatureGapC = 0.5
    const val minimumFanLimitPercent = 0
    const val maximumFanLimitPercent = 100
    const val fanLimitStepPercent = 5
    const val maximumSlotCount = 6
}

private fun defaultProgramSlots(): List<DeviceCoolingProgramSlot> = listOf(
    DeviceCoolingProgramSlot(
        id = "quiet",
        label = DeviceCoolingProgramSlotLabel.QUIET,
        startMinutes = 8 * MINUTES_PER_HOUR,
        endMinutes = 14 * MINUTES_PER_HOUR,
        startTemperatureC = 25.0,
        maximumSpeedTemperatureC = 27.0,
        fanLimitPercent = 60
    ),
    DeviceCoolingProgramSlot(
        id = "intensive",
        label = DeviceCoolingProgramSlotLabel.INTENSIVE,
        startMinutes = 14 * MINUTES_PER_HOUR,
        endMinutes = 20 * MINUTES_PER_HOUR,
        startTemperatureC = 24.5,
        maximumSpeedTemperatureC = 26.5,
        fanLimitPercent = 100
    ),
    DeviceCoolingProgramSlot(
        id = "night",
        label = DeviceCoolingProgramSlotLabel.NIGHT,
        startMinutes = 20 * MINUTES_PER_HOUR,
        endMinutes = 23 * MINUTES_PER_HOUR + 30,
        startTemperatureC = 25.5,
        maximumSpeedTemperatureC = 27.5,
        fanLimitPercent = 40
    )
)

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
