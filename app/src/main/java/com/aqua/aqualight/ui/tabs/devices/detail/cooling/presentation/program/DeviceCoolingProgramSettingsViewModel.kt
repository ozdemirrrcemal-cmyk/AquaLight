package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramEditResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSchedule
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSnapshot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidation
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidationResult
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias DeviceCoolingProgramSlot = CoolingProgramSlot

class DeviceCoolingProgramSettingsViewModel(
    private val operations: DeviceCoolingProgramOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingProgramSettingsUiState())
    val uiState: StateFlow<DeviceCoolingProgramSettingsUiState> = _uiState.asStateFlow()

    private val slotUiIdentity = CoolingProgramSlotUiIdentity()
    private var boundDeviceUid: String? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun bind(deviceUid: String) {
        val normalized = deviceUid.trim()
        if (normalized.isEmpty()) return
        if (
            boundDeviceUid == normalized &&
            _uiState.value.loadState.keepsExistingBinding
        ) {
            return
        }
        boundDeviceUid = normalized
        saveJob?.cancel()
        loadProgram(normalized)
    }

    fun selectSlot(slotIndex: Int) {
        val state = _uiState.value
        if (!state.isEditable || slotIndex !in state.slotItems.indices) return
        _uiState.value = state.copy(
            selectedSlotIndex = if (state.selectedSlotIndex == slotIndex) null else slotIndex
        )
    }

    fun updateStartTime(slotIndex: Int, minutesOfDay: Int): Boolean {
        val state = _uiState.value
        val policy = state.policy
        return if (state.isEditable && policy != null) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.updateStartTime(
                    slots = state.slots,
                    policy = policy,
                    slotIndex = slotIndex,
                    startMinutes = minutesOfDay
                ),
                slotUiIdentity = slotUiIdentity,
                selectedUiKey = state.slotItems.getOrNull(slotIndex)?.uiKey
            )
        } else {
            false
        }
    }

    fun updateEndTime(slotIndex: Int, minutesOfDay: Int): Boolean {
        val state = _uiState.value
        val policy = state.policy
        return if (state.isEditable && policy != null) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.updateEndTime(
                    slots = state.slots,
                    policy = policy,
                    slotIndex = slotIndex,
                    endMinutes = minutesOfDay
                ),
                slotUiIdentity = slotUiIdentity,
                selectedUiKey = state.slotItems.getOrNull(slotIndex)?.uiKey
            )
        } else {
            false
        }
    }

    fun updateFanOnTemperature(slotIndex: Int, temperatureC: Double): Boolean {
        val state = _uiState.value
        val policy = state.policy
        return if (state.isEditable && policy != null) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.updateFanOnTemperature(
                    slots = state.slots,
                    policy = policy,
                    slotIndex = slotIndex,
                    temperatureC = temperatureC
                ),
                slotUiIdentity = slotUiIdentity,
                selectedUiKey = state.slotItems.getOrNull(slotIndex)?.uiKey
            )
        } else {
            false
        }
    }

    fun updateTargetFanPercent(slotIndex: Int, percent: Int) {
        val state = _uiState.value
        val policy = state.policy
        if (state.isEditable && policy != null) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.updateTargetFanPercent(
                    slots = state.slots,
                    policy = policy,
                    slotIndex = slotIndex,
                    percent = percent
                ),
                slotUiIdentity = slotUiIdentity,
                selectedUiKey = state.slotItems.getOrNull(slotIndex)?.uiKey
            )
        }
    }

    fun addTimeSlot() {
        val state = _uiState.value
        val policy = state.policy
        if (state.isEditable && policy != null && state.canAddSlot) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.addSlot(state.slots, policy),
                slotUiIdentity = slotUiIdentity,
                selectedUiKey = slotUiIdentity.allocateKey()
            )
        }
    }

    fun deleteTimeSlot(slotIndex: Int): Boolean {
        val state = _uiState.value
        val policy = state.policy
        return if (state.isEditable && policy != null) {
            _uiState.applyEdit(
                state = state,
                result = CoolingProgramSchedule.deleteSlot(
                    slots = state.slots,
                    policy = policy,
                    slotIndex = slotIndex
                ),
                slotUiIdentity = slotUiIdentity
            )
        } else {
            false
        }
    }

    fun saveDraft() {
        val deviceUid = boundDeviceUid
        val state = _uiState.value
        val policy = state.policy
        val validDraft = policy != null &&
            CoolingProgramValidation.validate(state.slots, policy) == CoolingProgramValidationResult.Valid
        if (deviceUid == null || !state.canSave) return
        if (!validDraft) {
            _uiState.value = state.copy(
                mutationState = CoolingMutationState.ValidationError,
                commandFailure = DeviceCoolingCommandFailure.INVALID_CONFIGURATION
            )
            return
        }

        val draft = state.slots
        _uiState.value = state.copy(
            mutationState = CoolingMutationState.Saving,
            commandFailure = null
        )
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _uiState.applySaveResult(
                result = operations.saveProgram(deviceUid, draft),
                slotUiIdentity = slotUiIdentity
            )
        }
    }

    private fun loadProgram(deviceUid: String) {
        loadJob?.cancel()
        saveJob?.cancel()
        _uiState.value = DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.Loading
        )
        loadJob = viewModelScope.launch {
            _uiState.applyReadResult(
                result = operations.readProgram(deviceUid),
                slotUiIdentity = slotUiIdentity
            )
        }
    }
}

enum class DeviceCoolingProgramLoadFailure {
    NOT_CONNECTED,
    INVALID_DATA
}

enum class DeviceCoolingProgramSaveFailure {
    UNSUPPORTED,
    UNAVAILABLE,
    NOT_CONNECTED,
    REJECTED,
    INVALID_DATA
}

data class DeviceCoolingProgramSettingsUiState(
    val dataState: CoolingDataState<CoolingProgramSnapshot, DeviceCoolingProgramLoadFailure> =
        CoolingDataState.Initial,
    val mutationState: CoolingMutationState<DeviceCoolingProgramSaveFailure> =
        CoolingMutationState.Idle,
    val policy: CoolingProgramPolicy? = null,
    val slotItems: List<DeviceCoolingProgramSlotUiItem> = emptyList(),
    val baselineSlots: List<DeviceCoolingProgramSlot> = emptyList(),
    val selectedSlotIndex: Int? = null,
    val commandFailure: DeviceCoolingCommandFailure? = null,
    val clockReady: Boolean = false,
    val currentMinuteOfDay: Int? = null,
    val authoritativeActiveSlot: DeviceCoolingProgramSlot? = null
) {
    val loadState: DeviceCoolingProgramLoadState
        get() = when (val state = dataState) {
            CoolingDataState.Initial -> DeviceCoolingProgramLoadState.IDLE
            CoolingDataState.Loading -> DeviceCoolingProgramLoadState.LOADING
            is CoolingDataState.Content,
            is CoolingDataState.Empty -> DeviceCoolingProgramLoadState.CONTENT
            CoolingDataState.Unsupported -> DeviceCoolingProgramLoadState.UNSUPPORTED
            CoolingDataState.Unavailable -> DeviceCoolingProgramLoadState.UNAVAILABLE
            is CoolingDataState.OperationError -> when (state.failure) {
                DeviceCoolingProgramLoadFailure.NOT_CONNECTED ->
                    DeviceCoolingProgramLoadState.NOT_CONNECTED
                DeviceCoolingProgramLoadFailure.INVALID_DATA -> DeviceCoolingProgramLoadState.ERROR
            }
        }

    val saveState: DeviceCoolingProgramSaveState
        get() = when (val state = mutationState) {
            CoolingMutationState.Idle -> DeviceCoolingProgramSaveState.IDLE
            CoolingMutationState.Saving -> DeviceCoolingProgramSaveState.SAVING
            CoolingMutationState.Saved -> DeviceCoolingProgramSaveState.SAVED
            CoolingMutationState.ValidationError -> DeviceCoolingProgramSaveState.VALIDATION_ERROR
            is CoolingMutationState.OperationError -> when (state.failure) {
                DeviceCoolingProgramSaveFailure.UNSUPPORTED -> DeviceCoolingProgramSaveState.UNSUPPORTED
                DeviceCoolingProgramSaveFailure.UNAVAILABLE -> DeviceCoolingProgramSaveState.UNAVAILABLE
                DeviceCoolingProgramSaveFailure.NOT_CONNECTED -> DeviceCoolingProgramSaveState.NOT_CONNECTED
                DeviceCoolingProgramSaveFailure.REJECTED -> DeviceCoolingProgramSaveState.REJECTED
                DeviceCoolingProgramSaveFailure.INVALID_DATA -> DeviceCoolingProgramSaveState.ERROR
            }
        }

    val operationInProgress: Boolean
        get() = mutationState == CoolingMutationState.Saving

    val slots: List<DeviceCoolingProgramSlot>
        get() = slotItems.map(DeviceCoolingProgramSlotUiItem::slot)

    val selectedSlot: DeviceCoolingProgramSlot?
        get() = selectedSlotIndex?.let(slotItems::getOrNull)?.slot

    val hasChanges: Boolean
        get() = hasLoadedProgram && slots != baselineSlots

    val canAddSlot: Boolean
        get() = hasLoadedProgram &&
            mutationState != CoolingMutationState.Saving &&
            policy?.let { devicePolicy -> slotItems.size < devicePolicy.maximumSlotCount } == true

    val canSave: Boolean
        get() = hasChanges && mutationState != CoolingMutationState.Saving

    private val hasLoadedProgram: Boolean
        get() = dataState is CoolingDataState.Content || dataState is CoolingDataState.Empty
}

enum class DeviceCoolingProgramLoadState {
    IDLE,
    LOADING,
    CONTENT,
    UNSUPPORTED,
    UNAVAILABLE,
    NOT_CONNECTED,
    ERROR
}

enum class DeviceCoolingProgramSaveState {
    IDLE,
    SAVING,
    SAVED,
    UNSUPPORTED,
    UNAVAILABLE,
    NOT_CONNECTED,
    REJECTED,
    VALIDATION_ERROR,
    ERROR
}

private val DeviceCoolingProgramLoadState.keepsExistingBinding: Boolean
    get() = this == DeviceCoolingProgramLoadState.LOADING ||
        this == DeviceCoolingProgramLoadState.CONTENT

private val DeviceCoolingProgramSettingsUiState.isEditable: Boolean
    get() = (dataState is CoolingDataState.Content || dataState is CoolingDataState.Empty) &&
        mutationState != CoolingMutationState.Saving

private fun MutableStateFlow<DeviceCoolingProgramSettingsUiState>.applyEdit(
    state: DeviceCoolingProgramSettingsUiState,
    result: CoolingProgramEditResult,
    slotUiIdentity: CoolingProgramSlotUiIdentity,
    selectedUiKey: Long? = null
): Boolean = when (result) {
    is CoolingProgramEditResult.Updated -> {
        value = state.copy(
            slotItems = slotUiIdentity.reconcile(
                previousItems = state.slotItems,
                updatedSlots = result.slots,
                selectedSlotIndex = result.selectedSlotIndex,
                selectedUiKey = selectedUiKey
            ),
            selectedSlotIndex = result.selectedSlotIndex,
            mutationState = CoolingMutationState.Idle,
            commandFailure = null
        )
        true
    }
    is CoolingProgramEditResult.Rejected -> false
}

private fun MutableStateFlow<DeviceCoolingProgramSettingsUiState>.applyReadResult(
    result: CoolingProgramReadResult,
    slotUiIdentity: CoolingProgramSlotUiIdentity
) {
    value = when (result) {
        is CoolingProgramReadResult.Loaded -> result.toProgramUiState(slotUiIdentity)
        CoolingProgramReadResult.Unsupported -> DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.Unsupported
        )
        CoolingProgramReadResult.Unavailable -> DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.Unavailable
        )
        CoolingProgramReadResult.NotConnected -> DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.OperationError(
                DeviceCoolingProgramLoadFailure.NOT_CONNECTED
            )
        )
        is CoolingProgramReadResult.Rejected -> DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.OperationError(
                DeviceCoolingProgramLoadFailure.INVALID_DATA
            ),
            commandFailure = result.reason
        )
    }
}

private fun CoolingProgramReadResult.Loaded.toProgramUiState(
    slotUiIdentity: CoolingProgramSlotUiIdentity
): DeviceCoolingProgramSettingsUiState {
    val valid = CoolingProgramValidation.validate(snapshot.slots, snapshot.policy) ==
        CoolingProgramValidationResult.Valid
    if (!valid) {
        return DeviceCoolingProgramSettingsUiState(
            dataState = CoolingDataState.OperationError(
                DeviceCoolingProgramLoadFailure.INVALID_DATA
            ),
            commandFailure = DeviceCoolingCommandFailure.PROTOCOL_ERROR
        )
    }

    val ordered = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
    val orderedSnapshot = snapshot.copy(slots = ordered)
    val dataState = if (ordered.isEmpty()) {
        CoolingDataState.Empty<CoolingProgramSnapshot, DeviceCoolingProgramLoadFailure>(
            orderedSnapshot
        )
    } else {
        CoolingDataState.Content<CoolingProgramSnapshot, DeviceCoolingProgramLoadFailure>(
            orderedSnapshot
        )
    }
    return DeviceCoolingProgramSettingsUiState(
        dataState = dataState,
        policy = snapshot.policy,
        slotItems = slotUiIdentity.createItems(ordered),
        baselineSlots = ordered,
        clockReady = snapshot.clockReady,
        currentMinuteOfDay = snapshot.currentMinuteOfDay,
        authoritativeActiveSlot = snapshot.activeSlot
    )
}

private fun MutableStateFlow<DeviceCoolingProgramSettingsUiState>.applySaveResult(
    result: CoolingProgramSaveResult,
    slotUiIdentity: CoolingProgramSlotUiIdentity
) {
    when (result) {
        is CoolingProgramSaveResult.Saved -> applySavedSnapshot(result, slotUiIdentity)
        CoolingProgramSaveResult.Unsupported -> updateMutationFailure(
            DeviceCoolingProgramSaveFailure.UNSUPPORTED
        )
        CoolingProgramSaveResult.Unavailable -> updateMutationFailure(
            DeviceCoolingProgramSaveFailure.UNAVAILABLE
        )
        CoolingProgramSaveResult.NotConnected -> updateMutationFailure(
            DeviceCoolingProgramSaveFailure.NOT_CONNECTED
        )
        is CoolingProgramSaveResult.Rejected -> updateMutationFailure(
            failure = DeviceCoolingProgramSaveFailure.REJECTED,
            commandFailure = result.reason
        )
        CoolingProgramSaveResult.InvalidConfiguration -> {
            value = value.copy(
                mutationState = CoolingMutationState.ValidationError,
                commandFailure = DeviceCoolingCommandFailure.INVALID_CONFIGURATION
            )
        }
    }
}

private fun MutableStateFlow<DeviceCoolingProgramSettingsUiState>.applySavedSnapshot(
    result: CoolingProgramSaveResult.Saved,
    slotUiIdentity: CoolingProgramSlotUiIdentity
) {
    val snapshot = result.snapshot
    val current = value
    if (
        CoolingProgramValidation.validate(snapshot.slots, snapshot.policy) !=
        CoolingProgramValidationResult.Valid
    ) {
        updateMutationFailure(
            DeviceCoolingProgramSaveFailure.INVALID_DATA,
            DeviceCoolingCommandFailure.PROTOCOL_ERROR
        )
        return
    }

    val selectedUiKey = current.selectedSlotIndex
        ?.let(current.slotItems::getOrNull)
        ?.uiKey
    val savedSlots = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
    val savedSnapshot = snapshot.copy(slots = savedSlots)
    val savedItems = slotUiIdentity.reconcile(
        previousItems = current.slotItems,
        updatedSlots = savedSlots,
        selectedSlotIndex = null,
        selectedUiKey = null
    )
    val selectedSlotIndex = selectedUiKey?.let { key ->
        savedItems.indexOfFirst { item -> item.uiKey == key }.takeIf { index -> index >= 0 }
    }
    val nextDataState = if (savedSlots.isEmpty()) {
        CoolingDataState.Empty<CoolingProgramSnapshot, DeviceCoolingProgramLoadFailure>(savedSnapshot)
    } else {
        CoolingDataState.Content<CoolingProgramSnapshot, DeviceCoolingProgramLoadFailure>(savedSnapshot)
    }
    value = current.copy(
        dataState = nextDataState,
        policy = snapshot.policy,
        slotItems = savedItems,
        baselineSlots = savedSlots,
        selectedSlotIndex = selectedSlotIndex,
        mutationState = CoolingMutationState.Saved,
        commandFailure = null,
        clockReady = snapshot.clockReady,
        currentMinuteOfDay = snapshot.currentMinuteOfDay,
        authoritativeActiveSlot = snapshot.activeSlot
    )
}

private fun MutableStateFlow<DeviceCoolingProgramSettingsUiState>.updateMutationFailure(
    failure: DeviceCoolingProgramSaveFailure,
    commandFailure: DeviceCoolingCommandFailure? = null
) {
    value = value.copy(
        mutationState = CoolingMutationState.OperationError(failure),
        commandFailure = commandFailure
    )
}
