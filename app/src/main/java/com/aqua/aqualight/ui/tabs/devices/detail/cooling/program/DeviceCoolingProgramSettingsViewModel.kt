package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramEditResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSchedule
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidation
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidationResult
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias DeviceCoolingProgramSlot = CoolingProgramSlot

/** Presentation-only slider resolution; authoritative snapping comes from loaded device policy. */
object DeviceCoolingProgramPolicy {
    const val fanLimitStepPercent = 1
}

class DeviceCoolingProgramSettingsViewModel(
    private val operations: DeviceCoolingProgramOperations
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

    fun selectSlot(slotIndex: Int) {
        val state = editableState() ?: return
        if (slotIndex !in state.slots.indices) return
        _uiState.value = state.copy(
            selectedSlotIndex = if (state.selectedSlotIndex == slotIndex) null else slotIndex
        )
    }

    fun updateStartTime(slotIndex: Int, minutesOfDay: Int): Boolean {
        val state = editableState() ?: return false
        val policy = state.policy ?: return false
        return applyEditResult(
            state = state,
            result = CoolingProgramSchedule.updateStartTime(
                slots = state.slots,
                policy = policy,
                slotIndex = slotIndex,
                startMinutes = minutesOfDay
            )
        )
    }

    fun updateEndTime(slotIndex: Int, minutesOfDay: Int): Boolean {
        val state = editableState() ?: return false
        val policy = state.policy ?: return false
        return applyEditResult(
            state = state,
            result = CoolingProgramSchedule.updateEndTime(
                slots = state.slots,
                policy = policy,
                slotIndex = slotIndex,
                endMinutes = minutesOfDay
            )
        )
    }

    fun updateFanLimit(slotIndex: Int, percent: Int) {
        val state = editableState() ?: return
        val policy = state.policy ?: return
        applyEditResult(
            state = state,
            result = CoolingProgramSchedule.updateFanLimit(
                slots = state.slots,
                policy = policy,
                slotIndex = slotIndex,
                percent = percent
            )
        )
    }

    fun addTimeSlot() {
        val state = editableState() ?: return
        val policy = state.policy ?: return
        if (!state.canAddSlot) return
        applyEditResult(
            state = state,
            result = CoolingProgramSchedule.addSlot(state.slots, policy)
        )
    }

    fun deleteTimeSlot(slotIndex: Int): Boolean {
        val state = editableState() ?: return false
        val policy = state.policy ?: return false
        return when (
            val result = CoolingProgramSchedule.deleteSlot(
                slots = state.slots,
                policy = policy,
                slotIndex = slotIndex
            )
        ) {
            is CoolingProgramEditResult.Updated -> {
                _uiState.value = state.copy(
                    slots = result.slots,
                    selectedSlotIndex = null,
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
        val policy = state.policy ?: return
        if (!state.canSave || CoolingProgramValidation.validate(state.slots, policy) !=
            CoolingProgramValidationResult.Valid
        ) {
            return
        }

        val draft = state.slots
        _uiState.value = state.copy(saveState = DeviceCoolingProgramSaveState.SAVING)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            when (val result = operations.saveProgram(deviceUid, draft)) {
                is CoolingProgramSaveResult.Saved -> applySavedSnapshot(result)
                CoolingProgramSaveResult.Unavailable -> updateSaveState(
                    DeviceCoolingProgramSaveState.UNAVAILABLE
                )
                CoolingProgramSaveResult.NotConnected -> updateSaveState(
                    DeviceCoolingProgramSaveState.NOT_CONNECTED
                )
                CoolingProgramSaveResult.Rejected -> updateSaveState(
                    DeviceCoolingProgramSaveState.REJECTED
                )
                CoolingProgramSaveResult.InvalidConfiguration -> updateSaveState(
                    DeviceCoolingProgramSaveState.VALIDATION_ERROR
                )
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
                CoolingProgramReadResult.Unavailable -> {
                    _uiState.value = DeviceCoolingProgramSettingsUiState(
                        loadState = DeviceCoolingProgramLoadState.UNAVAILABLE
                    )
                }
                CoolingProgramReadResult.NotConnected -> {
                    _uiState.value = DeviceCoolingProgramSettingsUiState(
                        loadState = DeviceCoolingProgramLoadState.NOT_CONNECTED
                    )
                }
            }
        }
    }

    private fun applyLoadedSnapshot(result: CoolingProgramReadResult.Loaded) {
        val snapshot = result.snapshot
        _uiState.value = if (
            CoolingProgramValidation.validate(snapshot.slots, snapshot.policy) ==
            CoolingProgramValidationResult.Valid
        ) {
            val ordered = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
            DeviceCoolingProgramSettingsUiState(
                loadState = DeviceCoolingProgramLoadState.CONTENT,
                policy = snapshot.policy,
                slots = ordered,
                baselineSlots = ordered
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
        if (
            CoolingProgramValidation.validate(snapshot.slots, snapshot.policy) !=
            CoolingProgramValidationResult.Valid
        ) {
            updateSaveState(DeviceCoolingProgramSaveState.ERROR)
            return
        }

        val savedSlots = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
        _uiState.value = current.copy(
            policy = snapshot.policy,
            slots = savedSlots,
            baselineSlots = savedSlots,
            selectedSlotIndex = current.selectedSlotIndex?.takeIf(savedSlots.indices::contains),
            saveState = DeviceCoolingProgramSaveState.SAVED
        )
    }

    private fun updateSaveState(saveState: DeviceCoolingProgramSaveState) {
        _uiState.value = _uiState.value.copy(saveState = saveState)
    }

    private fun editableState(): DeviceCoolingProgramSettingsUiState? =
        _uiState.value.takeIf { state ->
            state.loadState == DeviceCoolingProgramLoadState.CONTENT &&
                state.saveState != DeviceCoolingProgramSaveState.SAVING
        }

    private fun applyEditResult(
        state: DeviceCoolingProgramSettingsUiState,
        result: CoolingProgramEditResult
    ): Boolean = when (result) {
        is CoolingProgramEditResult.Updated -> {
            _uiState.value = state.copy(
                slots = result.slots,
                selectedSlotIndex = result.selectedSlotIndex,
                saveState = DeviceCoolingProgramSaveState.IDLE
            )
            true
        }
        is CoolingProgramEditResult.Rejected -> false
    }
}

data class DeviceCoolingProgramSettingsUiState(
    val loadState: DeviceCoolingProgramLoadState = DeviceCoolingProgramLoadState.IDLE,
    val policy: CoolingProgramPolicy? = null,
    val slots: List<DeviceCoolingProgramSlot> = emptyList(),
    val baselineSlots: List<DeviceCoolingProgramSlot> = emptyList(),
    val selectedSlotIndex: Int? = null,
    val saveState: DeviceCoolingProgramSaveState = DeviceCoolingProgramSaveState.IDLE
) {
    val selectedSlot: DeviceCoolingProgramSlot?
        get() = selectedSlotIndex?.let(slots::getOrNull)

    val hasChanges: Boolean
        get() = loadState == DeviceCoolingProgramLoadState.CONTENT && slots != baselineSlots

    val canAddSlot: Boolean
        get() = loadState == DeviceCoolingProgramLoadState.CONTENT &&
            saveState != DeviceCoolingProgramSaveState.SAVING &&
            policy?.let { devicePolicy -> slots.size < devicePolicy.maximumSlotCount } == true

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
    UNAVAILABLE,
    NOT_CONNECTED,
    ERROR
}

enum class DeviceCoolingProgramSaveState {
    IDLE,
    SAVING,
    SAVED,
    UNAVAILABLE,
    NOT_CONNECTED,
    REJECTED,
    VALIDATION_ERROR,
    ERROR
}
