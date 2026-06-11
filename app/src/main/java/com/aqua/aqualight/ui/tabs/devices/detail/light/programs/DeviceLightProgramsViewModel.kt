package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceDataCenter
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramScheduleConflictValidator
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

    init {
        LightDeviceDataCenter.configure(appContext)
    }

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(appContext)

    private val lightProgramCommandManager =
        Esp32LightProgramCommandManager(
            context = appContext
        )

    private val _uiState =
        MutableStateFlow(LightProgramListUiState())

    val uiState: StateFlow<LightProgramListUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<LightProgramsEvent>(Channel.BUFFERED)

    val events =
        eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var observeProgramsJob: Job? = null
    private var isProgramOperationRunning: Boolean = false

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
                    emptyList()
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

        if (deviceId <= 0L) {
            viewModelScope.launch {
                eventsChannel.send(
                    LightProgramsEvent.ShowError(
                        "Device information is missing"
                    )
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
        launchProgramOperation {
            if (deviceId <= 0L) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    "Device information is missing"
                )
            }

            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    "Program could not be found"
                )
            }

            if (savedProgram.isActive == isActive) {
                return@launchProgramOperation null
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

                val conflict =
                    LightProgramScheduleConflictValidator.findConflict(
                        candidate = updatedProgram,
                        existingPrograms = activeProgramsForSameDevice
                    )

                if (conflict != null) {
                    return@launchProgramOperation LightProgramsEvent.ShowError(
                        "This program overlaps with ${conflict.name}"
                    )
                }
            }

            val allProgramsAfterChange = existingPrograms.map { existingProgram ->
                if (existingProgram.id == savedProgram.id) {
                    updatedProgram
                } else {
                    existingProgram
                }
            }

            val activeProgramsForDeviceAfterChange =
                allProgramsAfterChange.activeProgramsForDevice(
                    deviceId = savedProgram.deviceId
                )

            val syncResult =
                lightProgramCommandManager.loadPrograms(
                    deviceId = savedProgram.deviceId,
                    programs = activeProgramsForDeviceAfterChange
                )

            if (!syncResult.isSuccess) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    syncResult.message ?: "Program could not be synced to device"
                )
            }

            lightProgramsDataStoreManager.saveProgram(updatedProgram)

            refreshLiveStateIfNoActiveProgram(
                deviceId = savedProgram.deviceId,
                activeProgramsForDevice = activeProgramsForDeviceAfterChange
            )

            LightProgramsEvent.ShowMessage(
                if (isActive) {
                    "Program activated"
                } else {
                    "Program disabled"
                }
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
                    LightProgramsEvent.ShowError(
                        "Program could not be found"
                    )
                )
                return@launch
            }

            val now =
                System.currentTimeMillis()

            val duplicatedProgram = savedProgram.copy(
                id = UUID.randomUUID().toString(),
                name = "${savedProgram.name} Copy",
                isActive = false,
                createdAt = now,
                updatedAt = now
            )

            runCatching {
                lightProgramsDataStoreManager.saveProgram(duplicatedProgram)
            }.onSuccess {
                eventsChannel.send(
                    LightProgramsEvent.ShowMessage(
                        "Program duplicated"
                    )
                )
            }.onFailure {
                eventsChannel.send(
                    LightProgramsEvent.ShowError(
                        "Program could not be duplicated"
                    )
                )
            }
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
                    LightProgramsEvent.ShowError(
                        "Program could not be found"
                    )
                )
                return@launch
            }

            val cleanName = newName
                .trim()
                .ifBlank {
                    savedProgram.name
                }

            runCatching {
                lightProgramsDataStoreManager.saveProgram(
                    savedProgram.copy(
                        name = cleanName,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                eventsChannel.send(
                    LightProgramsEvent.ShowMessage(
                        "Program renamed"
                    )
                )
            }.onFailure {
                eventsChannel.send(
                    LightProgramsEvent.ShowError(
                        "Program could not be renamed"
                    )
                )
            }
        }
    }

    fun deleteProgram(
        programId: String
    ) {
        launchProgramOperation {
            if (deviceId <= 0L) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    "Device information is missing"
                )
            }

            val savedProgram =
                lightProgramsDataStoreManager.getProgram(programId)

            if (savedProgram == null) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    "Program could not be found"
                )
            }

            val existingPrograms =
                lightProgramsDataStoreManager.programsFlow.first()

            val allProgramsAfterDelete = existingPrograms.filter { existingProgram ->
                existingProgram.id != savedProgram.id
            }

            val activeProgramsForDeviceAfterDelete =
                allProgramsAfterDelete.activeProgramsForDevice(
                    deviceId = savedProgram.deviceId
                )

            val syncResult =
                lightProgramCommandManager.loadPrograms(
                    deviceId = savedProgram.deviceId,
                    programs = activeProgramsForDeviceAfterDelete
                )

            if (!syncResult.isSuccess) {
                return@launchProgramOperation LightProgramsEvent.ShowError(
                    syncResult.message ?: "Program could not be removed from device"
                )
            }

            lightProgramsDataStoreManager.deleteProgram(programId)

            refreshLiveStateIfNoActiveProgram(
                deviceId = savedProgram.deviceId,
                activeProgramsForDevice = activeProgramsForDeviceAfterDelete
            )

            LightProgramsEvent.ShowMessage(
                "Program deleted"
            )
        }
    }

    private fun launchProgramOperation(
        block: suspend () -> LightProgramsEvent?
    ) {
        if (isProgramOperationRunning) {
            return
        }

        isProgramOperationRunning = true

        viewModelScope.launch {
            eventsChannel.send(
                LightProgramsEvent.SetLoading(true)
            )

            val resultEvent = runCatching {
                block()
            }.getOrElse { error ->
                LightProgramsEvent.ShowError(
                    error.message ?: "Program operation failed"
                )
            }

            eventsChannel.send(
                LightProgramsEvent.SetLoading(false)
            )

            isProgramOperationRunning = false

            if (resultEvent != null) {
                eventsChannel.send(resultEvent)
            }
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

    private fun List<SavedLightProgram>.activeProgramsForDevice(
        deviceId: Long
    ): List<SavedLightProgram> {
        return filter { program ->
            program.deviceId == deviceId &&
                program.isActive
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
        activeProgramsForDevice: List<SavedLightProgram>
    ) {
        if (activeProgramsForDevice.isNotEmpty()) {
            return
        }

        LightDeviceDataCenter.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    override fun onCleared() {
        observeProgramsJob?.cancel()
        super.onCleared()
    }
}