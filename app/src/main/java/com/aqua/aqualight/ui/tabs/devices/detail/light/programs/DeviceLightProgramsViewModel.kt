package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(appContext)

    private val lightProgramCommandManager =
        Esp32LightProgramCommandManager(
            context = appContext
        )

    private val _uiState = MutableStateFlow(
        LightProgramListUiState()
    )

    val uiState: StateFlow<LightProgramListUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<LightProgramsEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var observeProgramsJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        if (
            this.deviceId == deviceId &&
            observeProgramsJob != null
        ) {
            return
        }

        this.deviceId = deviceId

        observeProgramsJob?.cancel()

        observeProgramsJob = viewModelScope.launch {
            lightProgramsDataStoreManager.programsFlow.collect { savedPrograms ->
                val devicePrograms = if (deviceId > 0L) {
                    savedPrograms.filter { program ->
                        program.deviceId == deviceId
                    }
                } else {
                    savedPrograms
                }

                val listItems = devicePrograms
                    .sortedWith(
                        compareByDescending<SavedLightProgram> { program ->
                            program.isActive
                        }.thenBy { program ->
                            program.draft.start.totalMinutes
                        }.thenBy { program ->
                            program.name.lowercase()
                        }
                    )
                    .map { program ->
                        program.toListItem()
                    }

                updatePrograms(
                    programs = listItems
                )
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
            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                eventsChannel.send(
                    LightProgramsEvent.ShowError("Program could not be found")
                )
                return@launch
            }

            if (savedProgram.isActive == isActive) {
                return@launch
            }

            val existingPrograms =
                lightProgramsDataStoreManager.programsFlow.first()

            val updatedProgram = savedProgram.copy(
                isActive = isActive,
                updatedAt = System.currentTimeMillis()
            )

            if (isActive) {
                val activeProgramsForSameDevice = existingPrograms.filter { existingProgram ->
                    existingProgram.deviceId == savedProgram.deviceId &&
                        existingProgram.isActive &&
                        existingProgram.id != savedProgram.id
                }

                val conflict = LightProgramScheduleConflictValidator.findConflict(
                    candidate = updatedProgram,
                    existingPrograms = activeProgramsForSameDevice
                )

                if (conflict != null) {
                    eventsChannel.send(
                        LightProgramsEvent.ShowError(
                            "This program overlaps with ${conflict.name}"
                        )
                    )
                    return@launch
                }
            }

            val programsForDeviceAfterChange = existingPrograms
                .map { existingProgram ->
                    if (existingProgram.id == savedProgram.id) {
                        updatedProgram
                    } else {
                        existingProgram
                    }
                }
                .filter { existingProgram ->
                    existingProgram.deviceId == savedProgram.deviceId
                }

            val syncResult = lightProgramCommandManager.loadPrograms(
                deviceId = savedProgram.deviceId,
                programs = programsForDeviceAfterChange
            )

            if (!syncResult.isSuccess) {
                eventsChannel.send(
                    LightProgramsEvent.ShowError(
                        syncResult.message ?: "Program could not be synced to device"
                    )
                )
                return@launch
            }

            lightProgramsDataStoreManager.saveProgram(updatedProgram)

            refreshLiveStateIfNoActiveProgram(
                deviceId = savedProgram.deviceId,
                programsForDevice = programsForDeviceAfterChange
            )

            eventsChannel.send(
                LightProgramsEvent.ShowMessage(
                    if (isActive) {
                        "Program activated"
                    } else {
                        "Program disabled"
                    }
                )
            )
        }
    }

    fun duplicateProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                eventsChannel.send(
                    LightProgramsEvent.ShowError("Program could not be found")
                )
                return@launch
            }

            val now = System.currentTimeMillis()

            val duplicatedProgram = savedProgram.copy(
                id = UUID.randomUUID().toString(),
                name = "${savedProgram.name} Copy",
                isActive = false,
                createdAt = now,
                updatedAt = now
            )

            lightProgramsDataStoreManager.saveProgram(duplicatedProgram)

            eventsChannel.send(
                LightProgramsEvent.ShowMessage("Program duplicated")
            )
        }
    }

    fun renameProgram(
        programId: String,
        newName: String
    ) {
        viewModelScope.launch {
            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                eventsChannel.send(
                    LightProgramsEvent.ShowError("Program could not be found")
                )
                return@launch
            }

            val cleanName = newName
                .trim()
                .ifBlank {
                    savedProgram.name
                }

            lightProgramsDataStoreManager.saveProgram(
                savedProgram.copy(
                    name = cleanName,
                    updatedAt = System.currentTimeMillis()
                )
            )

            eventsChannel.send(
                LightProgramsEvent.ShowMessage("Program renamed")
            )
        }
    }

    fun deleteProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                eventsChannel.send(
                    LightProgramsEvent.ShowError("Program could not be found")
                )
                return@launch
            }

            val existingPrograms =
                lightProgramsDataStoreManager.programsFlow.first()

            val programsForDeviceAfterDelete = existingPrograms
                .filter { existingProgram ->
                    existingProgram.deviceId == savedProgram.deviceId &&
                        existingProgram.id != savedProgram.id
                }

            if (savedProgram.isActive) {
                val syncResult = lightProgramCommandManager.loadPrograms(
                    deviceId = savedProgram.deviceId,
                    programs = programsForDeviceAfterDelete
                )

                if (!syncResult.isSuccess) {
                    eventsChannel.send(
                        LightProgramsEvent.ShowError(
                            syncResult.message ?: "Program could not be removed from device"
                        )
                    )
                    return@launch
                }
            }

            lightProgramsDataStoreManager.deleteProgram(programId)

            refreshLiveStateIfNoActiveProgram(
                deviceId = savedProgram.deviceId,
                programsForDevice = programsForDeviceAfterDelete
            )

            eventsChannel.send(
                LightProgramsEvent.ShowMessage("Program deleted")
            )
        }
    }

    private fun updatePrograms(
        programs: List<LightProgramListItem>
    ) {
        _uiState.update { state ->
            state.copy(
                allPrograms = programs,
                visiblePrograms = filterPrograms(
                    programs = programs,
                    filter = state.selectedFilter
                )
            )
        }
    }

    private fun filterPrograms(
        programs: List<LightProgramListItem>,
        filter: ProgramFilter
    ): List<LightProgramListItem> {
        return when (filter) {
            ProgramFilter.ALL -> {
                programs
            }

            ProgramFilter.ACTIVE -> {
                programs.filter { program ->
                    program.isActive
                }
            }

            ProgramFilter.DISABLED -> {
                programs.filter { program ->
                    !program.isActive
                }
            }
        }
    }

    private fun SavedLightProgram.toListItem(): LightProgramListItem {
        val draft = draft

        val peakPercent = maxOf(
            draft.channelValues.red,
            draft.channelValues.green,
            draft.channelValues.blue,
            draft.channelValues.white
        ).coerceIn(0, 100)

        return LightProgramListItem(
            id = id,
            name = name,
            subtitle = draft.repeatMode.toSubtitle(),
            isActive = isActive,
            startTime = draft.start.label,
            endTime = draft.end.label,
            rampText = "Rise ${shortTimeLabel(draft.start.label)}–${shortTimeLabel(draft.peakStart.label)}",
            pointText = "4 pts",
            peakText = "Peak $peakPercent%",
            red = draft.channelValues.red,
            green = draft.channelValues.green,
            blue = draft.channelValues.blue,
            white = draft.channelValues.white
        )
    }

    private fun RepeatMode.toSubtitle(): String {
        return when (this) {
            RepeatMode.EVERY -> "Every day schedule"
            RepeatMode.WEEK -> "Weekday schedule"
            RepeatMode.WEEKEND -> "Weekend schedule"
            RepeatMode.CUSTOM -> "Custom schedule"
        }
    }

    private fun shortTimeLabel(
        value: String
    ): String {
        val cleanValue = value.trim()

        return if (cleanValue.endsWith(":00")) {
            cleanValue.removeSuffix(":00")
        } else {
            cleanValue
        }
    }

    private fun refreshLiveStateIfNoActiveProgram(
        deviceId: Long,
        programsForDevice: List<SavedLightProgram>
    ) {
        val hasActiveProgram = programsForDevice.any { program ->
            program.isActive
        }

        if (!hasActiveProgram) {
            LightDeviceLiveRefreshManager.refreshNow(
                context = appContext,
                deviceId = deviceId
            )
        }
    }

    override fun onCleared() {
        observeProgramsJob?.cancel()
        super.onCleared()
    }
}