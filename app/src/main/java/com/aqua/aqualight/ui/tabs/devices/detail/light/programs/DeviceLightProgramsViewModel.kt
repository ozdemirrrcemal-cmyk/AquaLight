package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val programStore = LightProgramDataStoreManager.create(application)

    private val _uiState = MutableStateFlow(
        LightProgramListUiState()
    )
    val uiState: StateFlow<LightProgramListUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LightProgramsEvent>()
    val events: SharedFlow<LightProgramsEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L
    private var programsJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId
        programsJob?.cancel()
        programsJob = viewModelScope.launch {
            programStore.programsForDeviceFlow(deviceId)
                .collectLatest { programs ->
                    _uiState.update { state ->
                        val items = programs.map { program -> program.toListItem() }
                        state.copy(
                            allPrograms = items,
                            visiblePrograms = filterPrograms(
                                programs = items,
                                filter = state.selectedFilter
                            )
                        )
                    }
                }
        }
    }

    fun applyFilter(
        filter: ProgramFilter
    ) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                visiblePrograms = filterPrograms(
                    programs = state.allPrograms,
                    filter = filter
                )
            )
        }
    }

    fun setProgramActive(
        programId: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val updated = programStore.setProgramActive(
                deviceId = deviceId,
                programId = programId,
                active = isActive
            )
            if (updated) {
                _events.emit(
                    LightProgramsEvent.ShowMessage(
                        if (isActive) {
                            "Program marked active locally. Device schedule sync is ready for firmware connection."
                        } else {
                            "Program disabled locally."
                        }
                    )
                )
            } else {
                _events.emit(
                    LightProgramsEvent.ShowError("Program could not be updated.")
                )
            }
        }
    }

    fun duplicateProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val duplicated = programStore.duplicateProgram(
                deviceId = deviceId,
                programId = programId
            )
            _events.emit(
                if (duplicated != null) {
                    LightProgramsEvent.ShowMessage("Program duplicated.")
                } else {
                    LightProgramsEvent.ShowError("Program could not be duplicated.")
                }
            )
        }
    }

    fun renameProgram(
        programId: String,
        newName: String
    ) {
        viewModelScope.launch {
            val renamed = programStore.renameProgram(
                deviceId = deviceId,
                programId = programId,
                newName = newName
            )
            _events.emit(
                if (renamed) {
                    LightProgramsEvent.ShowMessage("Program renamed.")
                } else {
                    LightProgramsEvent.ShowError("Program could not be renamed.")
                }
            )
        }
    }

    fun deleteProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val deleted = programStore.deleteProgram(
                deviceId = deviceId,
                programId = programId
            )
            _events.emit(
                if (deleted) {
                    LightProgramsEvent.ShowMessage("Program deleted.")
                } else {
                    LightProgramsEvent.ShowError("Program could not be deleted.")
                }
            )
        }
    }

    private fun filterPrograms(
        programs: List<LightProgramListItem>,
        filter: ProgramFilter
    ): List<LightProgramListItem> {
        return when (filter) {
            ProgramFilter.ALL -> programs
            ProgramFilter.ACTIVE -> programs.filter { program -> program.isActive }
            ProgramFilter.DISABLED -> programs.filter { program -> !program.isActive }
        }
    }

    private fun SavedLightProgram.toListItem(): LightProgramListItem {
        val draft = toDraft()
        val start = draft.start.label
        val end = LightProgramTimeMath.endLabel(draft.end)
        val rampUp = ((peakStartMinute - startMinute).coerceAtLeast(0) / 60.0)
        val rampDown = ((endMinute - peakEndMinute).coerceAtLeast(0) / 60.0)
        val pointCount = 2 + 24 + 1 + 24 + 1

        return LightProgramListItem(
            id = id,
            name = name,
            subtitle = if (active) "Active local schedule" else "Saved local copy",
            isActive = active,
            startTime = start,
            endTime = end,
            rampText = "Ramp %.1fh / %.1fh".format(rampUp, rampDown),
            pointText = "$pointCount LP points ready",
            peakText = "Peak ${draft.channelValues.red}/${draft.channelValues.green}/${draft.channelValues.blue}/${draft.channelValues.white}%",
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }
}
