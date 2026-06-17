package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramRepository
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncState
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncStatus
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItemMapper
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = LightProgramRepository.get(
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
    private var observeJob: Job? = null
    private val pendingAutoRecoverChecksums = mutableSetOf<String>()
    private val runtimeConsumerKey = "light_programs_${System.identityHashCode(this)}"

    fun initialize(
        deviceId: Long
    ) {
        if (deviceId <= 0L) {
            releaseRuntimeSyncIfNeeded()
            observeJob?.cancel()
            this.deviceId = 0L
            _uiState.value = LightProgramListUiState()
            emitError(LIGHT_DEVICE_INFORMATION_MISSING)
            return
        }

        if (this.deviceId == deviceId && observeJob?.isActive == true) {
            return
        }

        releaseRuntimeSyncIfNeeded()
        this.deviceId = deviceId
        repository.acquireDeviceRuntimeSync(
            deviceId = deviceId,
            consumerKey = runtimeConsumerKey
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeProgramsWithDeviceSync(deviceId)
                .catch { exception ->
                    _events.emit(
                        LightProgramsEvent.ShowError(
                            exception.message ?: "Programs could not be loaded."
                        )
                    )
                }
                .collect { snapshot ->
                    maybeAutoRecoverDeviceProgram(snapshot.syncState)

                    val items = snapshot.programs.map { program ->
                        LightProgramListItemMapper.map(
                            program = program,
                            deviceSyncState = snapshot.syncState
                        )
                    }
                    val selectedFilter = _uiState.value.selectedFilter
                    _uiState.value = LightProgramListUiState(
                        selectedFilter = selectedFilter,
                        allPrograms = items,
                        visiblePrograms = filterPrograms(
                            programs = items,
                            filter = selectedFilter
                        )
                    )
                }
        }
    }


    fun onProgramsVisible() {
        val currentDeviceId = deviceId
        if (currentDeviceId > 0L) {
            repository.acquireDeviceRuntimeSync(
                deviceId = currentDeviceId,
                consumerKey = runtimeConsumerKey
            )
        }
    }

    fun onProgramsHidden() {
        releaseRuntimeSyncIfNeeded()
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
        runProgramAction(
            successMessage = if (isActive) {
                "Program uploaded to the device and activated."
            } else {
                "Program disabled locally."
            }
        ) {
            if (isActive) {
                repository.activateProgram(
                    deviceId = requireDeviceId(),
                    programId = programId
                )
            } else {
                repository.setProgramActive(
                    deviceId = requireDeviceId(),
                    programId = programId,
                    isActive = false
                )
            }
        }
    }

    fun duplicateProgram(
        programId: String
    ) {
        runProgramAction(
            successMessage = "Program duplicated."
        ) {
            repository.duplicateProgram(
                deviceId = requireDeviceId(),
                programId = programId
            )
        }
    }

    fun renameProgram(
        programId: String,
        newName: String
    ) {
        val cleanedName = newName.trim()
        when (val result = LightProgramDraftValidator.validateName(cleanedName)) {
            LightProgramValidationResult.Valid -> Unit
            is LightProgramValidationResult.Invalid -> {
                emitError(result.message)
                return
            }
        }

        runProgramAction(
            successMessage = "Program renamed."
        ) {
            repository.renameProgram(
                deviceId = requireDeviceId(),
                programId = programId,
                name = cleanedName
            )
        }
    }

    fun deleteProgram(
        programId: String
    ) {
        runProgramAction(
            successMessage = "Program deleted."
        ) {
            repository.deleteProgram(
                deviceId = requireDeviceId(),
                programId = programId
            )
        }
    }

    private fun runProgramAction(
        successMessage: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _events.emit(LightProgramsEvent.SetLoading(true))
            try {
                action()
                _events.emit(
                    LightProgramsEvent.ShowMessage(successMessage)
                )
            } catch (exception: Exception) {
                _events.emit(
                    LightProgramsEvent.ShowError(
                        exception.message ?: "Program action failed."
                    )
                )
            } finally {
                _events.emit(LightProgramsEvent.SetLoading(false))
            }
        }
    }

    private fun maybeAutoRecoverDeviceProgram(
        syncState: LightProgramDeviceSyncState
    ) {
        val checksum = syncState.deviceChecksum
        val canRecover = syncState.status == LightProgramDeviceSyncStatus.DEVICE_PROGRAM_UNKNOWN ||
            syncState.status == LightProgramDeviceSyncStatus.LOCAL_ACTIVE_OUT_OF_SYNC ||
            syncState.status == LightProgramDeviceSyncStatus.SAVED_PROGRAM_MATCHED

        if (!canRecover ||
            checksum.isBlank() ||
            checksum in pendingAutoRecoverChecksums
        ) {
            return
        }

        pendingAutoRecoverChecksums.add(checksum)
        viewModelScope.launch {
            try {
                repository.autoRecoverActiveDeviceProgram(
                    deviceId = requireDeviceId()
                )
            } finally {
                pendingAutoRecoverChecksums.remove(checksum)
            }
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

    private fun requireDeviceId(): Long {
        return deviceId.takeIf { id -> id > 0L }
            ?: error(LIGHT_DEVICE_INFORMATION_MISSING)
    }

    private fun emitError(
        message: String
    ) {
        viewModelScope.launch {
            _events.emit(
                LightProgramsEvent.ShowError(message)
            )
        }
    }

    private fun releaseRuntimeSyncIfNeeded() {
        val currentDeviceId = deviceId
        if (currentDeviceId > 0L) {
            repository.releaseDeviceRuntimeSync(
                deviceId = currentDeviceId,
                consumerKey = runtimeConsumerKey
            )
        }
    }

    override fun onCleared() {
        releaseRuntimeSyncIfNeeded()
        super.onCleared()
    }
}
