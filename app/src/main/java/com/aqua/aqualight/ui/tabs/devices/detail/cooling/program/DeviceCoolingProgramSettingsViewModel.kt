package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.CoolingProgramCapabilities
import com.aqua.aqualight.application.devices.cooling.CoolingProgramDraftSlotIdFactory
import com.aqua.aqualight.application.devices.cooling.CoolingProgramEditResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSchedule
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingProgramOperations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias DeviceCoolingProgramSlot = CoolingProgramSlot

/**
 * Presentation-only slider resolution. Device limits and snapping come from loaded application
 * capabilities; this value never represents a firmware or hardware constraint.
 */
object DeviceCoolingProgramPolicy {
    const val fanLimitStepPercent = 1
}

class DeviceCoolingProgramSettingsViewModel(
    private val operations: DeviceCoolingProgramOperations,
    private val draftSlotIdFactory: CoolingProgramDraftSlotIdFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingProgramSettingsUiState())
    val uiState: StateFlow<DeviceCoolingProgramSettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun bind(deviceUid: String) {
        val normalized = deviceUid.trim()
        if (normalized.isEmpty()) return
        if (boundDeviceUid == normalized && _uiState.value.loadState != DeviceCoolingProgramLoadState.IDLE) {
            return
        }
        boundDeviceUid = normalized
        saveJob?.cancel()
        loadProgram(normalized)
    }

    fun retry() {
        boundDeviceUid?.let(::loadProgram)
    }

    fun selectSlot(slotId: String) {
        val state = editableState() ?: return
        if (state.slots.none { slot -> slot.id == slotId }) return
        _uiState.value = state.copy(
            selectedSlotId = if (state.selectedSlotId == slotId) null else slotId
        )
    }

    fun updateStartTime(slotId: String, minutesOfDay: Int): Boolean {
        val state = editableState() ?: return false
        val capabilities = state.capabilities ?: return false
        return applyEditResult(
            state = state,
            slotId = slotId,
            result = CoolingProgramSchedule.updateStartTime(
                slots = state.slots,
                capabilities = capabilities,
                slotId = slotId,
                startMinutes = minutesOfDay
            )
        )
    }

    fun updateEndTime(slotId: String, minutesOfDay: Int): Boolean {
        val state = editableState() ?: return false
        val capabilities = state.capabilities ?: return false
        return applyEditResult(
            state = state,
            slotId = slotId,
            result = CoolingProgramSchedule.updateEndTime(
                slots = state.slots,
                capabilities = capabilities,
                slotId = slotId,
                endMinutes = minutesOfDay
            )
        )
    }

    fun updateFanLimit(slotId: String, percent: Int) {
        val state = editableState() ?: return
        val capabilities = state.capabilities ?: return
        applyEditResult(
            state = state,
            slotId = slotId,
            result = CoolingProgramSchedule.updateFanLimit(
                slots = state.slots,
                capabilities = capabilities,
                slotId = slotId,
                percent = percent
            )
        )
    }

    fun addTimeSlot() {
        val state = editableState() ?: return
        val capabilities = state.capabilities ?: return
        if (!state.canAddSlot) return
        val newSlotId = draftSlotIdFactory.create()
        val result = CoolingProgramSchedule.addSlot(
            slots = state.slots,
            capabilities = capabilities,
            newSlotId = newSlotId
        )
        applyEditResult(state = state, slotId = newSlotId, result = result)
    }

    fun deleteTimeSlot(slotId: String): Boolean {
        val state = editableState() ?: return false
        val capabilities = state.capabilities ?: return false
        return when (
            val result = CoolingProgramSchedule.deleteSlot(
                slots = state.slots,
                capabilities = capabilities,
                slotId = slotId
            )
        ) {
            is CoolingProgramEditResult.Updated -> {
                _uiState.value = state.copy(
                    slots = result.slots,
                    selectedSlotId = state.selectedSlotId.takeUnless { selected -> selected == slotId },
                    saveState = DeviceCoolingProgramSaveState.IDLE
                )
                true
            }
            is CoolingProgramEditResult.Rejected -> false
        }
    }

    fun saveDraft() {
        val deviceUid = boundDeviceUid ?: return
        val state = _uiState.value
        val capabilities = state.capabilities ?: return
        if (!state.canSave || !CoolingProgramSchedule.isValidProgram(state.slots, capabilities)) {
            return
        }
        val draft = state.slots
        _uiState.value = state.copy(saveState = DeviceCoolingProgramSaveState.SAVING)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            when (val result = operations.saveProgram(deviceUid, draft)) {
                is CoolingProgramSaveResult.Saved -> applySavedSnapshot(result)
                CoolingProgramSaveResult.Unsupported -> {
                    _uiState.value = DeviceCoolingProgramSettingsUiState(
                        loadState = DeviceCoolingProgramLoadState.UNSUPPORTED,
                        saveState = DeviceCoolingProgramSaveState.ERROR
                    )
                }
                CoolingProgramSaveResult.Unavailable,
                CoolingProgramSaveResult.InvalidConfiguration -> {
                    _uiState.value = _uiState.value.copy(
                        saveState = DeviceCoolingProgramSaveState.ERROR
                    )
                }
            }
        }
    }

    private fun loadProgram(deviceUid: String) {
        loadJob?.cancel()
        saveJob?.cancel()
        _uiState.value = DeviceCoolingProgramSettingsUiState(
            loadState = DeviceCoolingProgramLoadState.LOADING
        )
        loadJob = viewModelScope.launch {
            when (val result = operations.readProgram(deviceUid)) {
                is CoolingProgramReadResult.Loaded -> applyLoadedSnapshot(result)
                CoolingProgramReadResult.Unsupported -> {
                    _uiState.value = DeviceCoolingProgramSettingsUiState(
                        loadState = DeviceCoolingProgramLoadState.UNSUPPORTED
                    )
                }
                CoolingProgramReadResult.Unavailable -> {
                    _uiState.value = DeviceCoolingProgramSettingsUiState(
                        loadState = DeviceCoolingProgramLoadState.UNAVAILABLE
                    )
                }
            }
        }
    }

    private fun applyLoadedSnapshot(result: CoolingProgramReadResult.Loaded) {
        val snapshot = result.snapshot
        _uiState.value = if (
            CoolingProgramSchedule.isValidProgram(snapshot.slots, snapshot.capabilities)
        ) {
            DeviceCoolingProgramSettingsUiState(
                loadState = DeviceCoolingProgramLoadState.CONTENT,
                capabilities = snapshot.capabilities,
                slots = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes),
                persistedSlots = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
            )
        } else {
            DeviceCoolingProgramSettingsUiState(
                loadState = DeviceCoolingProgramLoadState.ERROR
            )
        }
    }

    private fun applySavedSnapshot(result: CoolingProgramSaveResult.Saved) {
        val snapshot = result.snapshot
        val current = _uiState.value
        if (!CoolingProgramSchedule.isValidProgram(snapshot.slots, snapshot.capabilities)) {
            _uiState.value = current.copy(saveState = DeviceCoolingProgramSaveState.ERROR)
            return
        }
        val savedSlots = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
        _uiState.value = current.copy(
            capabilities = snapshot.capabilities,
            slots = savedSlots,
            persistedSlots = savedSlots,
            selectedSlotId = current.selectedSlotId?.takeIf { selectedId ->
                savedSlots.any { slot -> slot.id == selectedId }
            },
            saveState = DeviceCoolingProgramSaveState.SAVED
        )
    }

    private fun editableState(): DeviceCoolingProgramSettingsUiState? =
        _uiState.value.takeIf { state ->
            state.loadState == DeviceCoolingProgramLoadState.CONTENT &&
                state.saveState != DeviceCoolingProgramSaveState.SAVING
        }

    private fun applyEditResult(
        state: DeviceCoolingProgramSettingsUiState,
        slotId: String,
        result: CoolingProgramEditResult
    ): Boolean = when (result) {
        is CoolingProgramEditResult.Updated -> {
            _uiState.value = state.copy(
                slots = result.slots,
                selectedSlotId = slotId,
                saveState = DeviceCoolingProgramSaveState.IDLE
            )
            true
        }
        is CoolingProgramEditResult.Rejected -> false
    }
}

data class DeviceCoolingProgramSettingsUiState(
    val loadState: DeviceCoolingProgramLoadState = DeviceCoolingProgramLoadState.IDLE,
    val capabilities: CoolingProgramCapabilities? = null,
    val slots: List<DeviceCoolingProgramSlot> = emptyList(),
    val persistedSlots: List<DeviceCoolingProgramSlot> = emptyList(),
    val selectedSlotId: String? = null,
    val saveState: DeviceCoolingProgramSaveState = DeviceCoolingProgramSaveState.IDLE
) {
    val selectedSlot: DeviceCoolingProgramSlot?
        get() = slots.firstOrNull { slot -> slot.id == selectedSlotId }

    val hasChanges: Boolean
        get() = loadState == DeviceCoolingProgramLoadState.CONTENT && slots != persistedSlots

    val canAddSlot: Boolean
        get() = loadState == DeviceCoolingProgramLoadState.CONTENT &&
            saveState != DeviceCoolingProgramSaveState.SAVING &&
            capabilities?.let { policy -> slots.size < policy.maximumSlotCount } == true

    val canSave: Boolean
        get() = hasChanges && saveState != DeviceCoolingProgramSaveState.SAVING

    fun activeSlotAt(minutesOfDay: Int): DeviceCoolingProgramSlot? =
        if (loadState == DeviceCoolingProgramLoadState.CONTENT) {
            CoolingProgramSchedule.activeSlotAt(slots, minutesOfDay)
        } else {
            null
        }
}

enum class DeviceCoolingProgramLoadState {
    IDLE,
    LOADING,
    CONTENT,
    UNSUPPORTED,
    UNAVAILABLE,
    ERROR
}

enum class DeviceCoolingProgramSaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}
