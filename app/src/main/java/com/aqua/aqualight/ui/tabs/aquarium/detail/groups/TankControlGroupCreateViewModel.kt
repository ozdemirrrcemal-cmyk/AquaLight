package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.groups.LightGroupSelection
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.application.devices.groups.TankControlGroupDeviceOperations
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TankControlGroupCreateUiState(
    val selected: List<TankControlGroupDevice> = emptyList(),
    val available: List<TankControlGroupDevice> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val hiddenCount: Int = 0,
    val selectionAdjusted: Boolean = false,
    val isSelectionReady: Boolean = false
)

class TankControlGroupCreateViewModel(
    private val operations: TankControlGroupDeviceOperations,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(TankControlGroupCreateUiState())
    val uiState: StateFlow<TankControlGroupCreateUiState> = _uiState.asStateFlow()
    private var tankId: Long? = null
    private var observation: Job? = null
    private var devices = TankControlGroupDevices()
    private val draft = ControlGroupDraftState(savedStateHandle)
    private var selection = draft.selection
    private var adjusted = false

    fun bind(id: Long) {
        if (id <= 0L || tankId == id) return
        observation?.cancel()
        if (draft.tankId != id) {
            selection = LightGroupSelection()
            adjusted = false
        }
        tankId = id
        draft.tankId = id
        devices = TankControlGroupDevices()
        draft.selection = selection
        observe()
    }

    fun retry() {
        if (tankId == null) return
        observation?.cancel()
        devices = devices.copy(isLoading = true)
        observe()
    }

    private fun observe() {
        val id = tankId ?: return
        render()
        observation = viewModelScope.launch {
            try {
                operations.start(viewModelScope)
                operations.refresh()
                operations.observe(id).collect { snapshot ->
                    devices = snapshot
                    if (!snapshot.isLoading) {
                        val next = selection.reconcile(snapshot.devices)
                        adjusted = adjusted || next.deviceUids != selection.deviceUids
                        selection = next
                        draft.selection = selection
                    }
                    render()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true,
                    isSelectionReady = false)
            }
        }
    }

    /** Every gesture is checked against the latest snapshot, never against an adapter row. */
    fun canAdd(uid: String): Boolean = !devices.isLoading && !_uiState.value.loadFailed &&
        selection.add(uid, devices.devices) != selection

    fun canRemove(uid: String): Boolean = !devices.isLoading &&
        !_uiState.value.loadFailed && uid in selection.deviceUids

    fun add(uid: String): Boolean {
        if (!canAdd(uid)) return false
        selection = selection.add(uid, devices.devices)
        changed()
        return true
    }

    fun remove(uid: String): Boolean {
        if (!canRemove(uid)) return false
        selection = selection.remove(uid)
        changed()
        return true
    }

    private fun changed() {
        adjusted = false
        draft.selection = selection
        render()
    }

    private fun render() {
        val selected = selection.selected(devices.devices)
        val available = selection.available(devices.devices)
        _uiState.value = TankControlGroupCreateUiState(
            selected = selected,
            available = available,
            isLoading = devices.isLoading,
            hiddenCount = devices.devices.size - selected.size - available.size,
            selectionAdjusted = adjusted,
            isSelectionReady = !devices.isLoading && selection.isReady(devices.devices)
        )
    }

}
