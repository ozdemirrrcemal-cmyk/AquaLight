package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for program list.
 *
 * Program persistence is not connected at this stage. The list stays as a
 * UI-only shell until the Light program contract is finalized.
 */
class DeviceLightProgramsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        LightProgramListUiState()
    )
    val uiState: StateFlow<LightProgramListUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LightProgramsEvent>()
    val events: SharedFlow<LightProgramsEvent> =
        _events.asSharedFlow()

    fun initialize(
        deviceId: Long
    ) {
        _uiState.value = LightProgramListUiState()
    }

    fun applyFilter(
        filter: ProgramFilter
    ) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                visiblePrograms = when (filter) {
                    ProgramFilter.ALL -> state.allPrograms
                    ProgramFilter.ACTIVE -> state.allPrograms.filter { it.isActive }
                    ProgramFilter.DISABLED -> state.allPrograms.filter { !it.isActive }
                }
            )
        }
    }

    fun setProgramActive(
        programId: String,
        isActive: Boolean
    ) {
        emitUnavailable()
    }

    fun duplicateProgram(
        programId: String
    ) {
        emitUnavailable()
    }

    fun renameProgram(
        programId: String,
        newName: String
    ) {
        emitUnavailable()
    }

    fun deleteProgram(
        programId: String
    ) {
        emitUnavailable()
    }

    private fun emitUnavailable() {
        viewModelScope.launch {
            _events.emit(
                LightProgramsEvent.ShowError(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}
