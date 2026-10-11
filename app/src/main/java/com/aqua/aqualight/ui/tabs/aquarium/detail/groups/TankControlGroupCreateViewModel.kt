package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

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
    val source: TankControlGroupDevices = TankControlGroupDevices(),
    val selected: List<TankControlGroupDevice> = emptyList(),
    val available: List<TankControlGroupDevice> = emptyList(),
    val hiddenCount: Int = 0,
    val isReady: Boolean = false,
    val loadFailed: Boolean = false
)

/** Transient UI draft; no group or assignment writes in this stage. */
class TankControlGroupCreateViewModel(private val operations: TankControlGroupDeviceOperations) : ViewModel() {
    private val _uiState = MutableStateFlow(TankControlGroupCreateUiState())
    val uiState: StateFlow<TankControlGroupCreateUiState> = _uiState.asStateFlow()
    private var boundTank: Long? = null
    private var observation: Job? = null
    private var source = TankControlGroupDevices()
    private var selection = LightGroupSelection()

    fun bind(tankId: Long) {
        if (tankId <= 0L || boundTank == tankId) return
        observation?.cancel()
        boundTank = tankId
        selection = LightGroupSelection()
        source = TankControlGroupDevices()
        render()
        observation = viewModelScope.launch {
            try {
                operations.start(viewModelScope)
                operations.observe(tankId).collect { snapshot ->
                    source = snapshot
                    if (!snapshot.isLoading) selection = selection.reconcile(snapshot.devices)
                    render()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(loadFailed = true, isReady = false)
            }
        }
    }

    fun canMove(uid: String, toGroup: Boolean): Boolean {
        if (source.isLoading || !source.tankExists || _uiState.value.loadFailed) return false
        return if (toGroup) selection.add(uid, source.devices) != selection else uid in selection.deviceUids
    }

    fun move(uid: String, toGroup: Boolean): Boolean {
        if (!canMove(uid, toGroup)) return false
        selection = if (toGroup) selection.add(uid, source.devices) else selection.remove(uid)
        render()
        return true
    }

    private fun render() {
        val selected = selection.selected(source.devices)
        val available = selection.available(source.devices)
        _uiState.value = TankControlGroupCreateUiState(source, selected, available,
            source.devices.size - selected.size - available.size,
            !source.isLoading && source.tankExists && selection.isReady(source.devices))
    }
}
