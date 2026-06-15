package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsRepository
import com.aqua.aqualight.data.devices.light.programs.LoadLightProgramResult
import com.aqua.aqualight.data.devices.light.programs.SaveLightProgramResult
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = LightProgramsRepository.get(
        context = application.applicationContext
    )

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
        if (this.deviceId == deviceId && programsJob != null) {
            return
        }

        this.deviceId = deviceId
        programsJob?.cancel()

        if (deviceId <= 0L) {
            _uiState.value = LightProgramListUiState()
            return
        }

        programsJob = viewModelScope.launch {
            repository.observePrograms(deviceId).collect { programs ->
                val mapped = LightProgramListItemMapper.map(programs)
                _uiState.update { state ->
                    state.copy(
                        allPrograms = mapped,
                        visiblePrograms = mapped.filteredBy(state.selectedFilter)
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
                visiblePrograms = state.allPrograms.filteredBy(filter)
            )
        }
    }

    fun setProgramActive(
        programId: String,
        isActive: Boolean
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        viewModelScope.launch {
            _events.emit(LightProgramsEvent.SetLoading(true))

            when (val result = repository.setProgramActive(
                deviceId = deviceId,
                programId = programId,
                isActive = isActive
            )) {
                is LoadLightProgramResult.Loaded -> {
                    _events.emit(
                        LightProgramsEvent.ShowMessage(
                            "Program loaded to device"
                        )
                    )
                }

                is LoadLightProgramResult.LocalOnly -> {
                    _events.emit(
                        LightProgramsEvent.ShowMessage(result.message)
                    )
                }

                is LoadLightProgramResult.Error -> {
                    _events.emit(
                        LightProgramsEvent.ShowError(result.message)
                    )
                }
            }

            _events.emit(LightProgramsEvent.SetLoading(false))
        }
    }

    fun duplicateProgram(
        programId: String
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        viewModelScope.launch {
            when (val result = repository.duplicateProgram(
                deviceId = deviceId,
                programId = programId
            )) {
                is SaveLightProgramResult.Success -> {
                    _events.emit(
                        LightProgramsEvent.ShowMessage(
                            "Program duplicated"
                        )
                    )
                }

                is SaveLightProgramResult.Error -> {
                    _events.emit(
                        LightProgramsEvent.ShowError(result.message)
                    )
                }
            }
        }
    }

    fun renameProgram(
        programId: String,
        newName: String
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        viewModelScope.launch {
            when (val result = repository.renameProgram(
                deviceId = deviceId,
                programId = programId,
                newName = newName
            )) {
                is SaveLightProgramResult.Success -> {
                    _events.emit(
                        LightProgramsEvent.ShowMessage(
                            "Program renamed"
                        )
                    )
                }

                is SaveLightProgramResult.Error -> {
                    _events.emit(
                        LightProgramsEvent.ShowError(result.message)
                    )
                }
            }
        }
    }

    fun deleteProgram(
        programId: String
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        viewModelScope.launch {
            val deleted = repository.deleteProgram(
                deviceId = deviceId,
                programId = programId
            )

            _events.emit(
                if (deleted) {
                    LightProgramsEvent.ShowMessage("Program deleted")
                } else {
                    LightProgramsEvent.ShowError("Program could not be found")
                }
            )
        }
    }

    override fun onCleared() {
        programsJob?.cancel()
        super.onCleared()
    }

    private fun List<LightProgramListItem>.filteredBy(
        filter: ProgramFilter
    ): List<LightProgramListItem> {
        return when (filter) {
            ProgramFilter.ALL -> this
            ProgramFilter.ACTIVE -> filter { item -> item.isActive }
            ProgramFilter.DISABLED -> filter { item -> !item.isActive }
        }
    }
}
