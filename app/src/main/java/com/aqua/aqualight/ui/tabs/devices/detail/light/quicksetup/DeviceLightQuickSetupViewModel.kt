package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramScheduleConflictValidator
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.engine.SmartLightRecommendationEngine
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.mapper.QuickSetupTankProfileMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightQuickSetupViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val devicesDataStoreManager =
        DevicesDataStoreManager.create(appContext)

    private val aquariumTankDataStoreManager =
        AquariumTankDataStoreManager(appContext)

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(appContext)

    private val lightProgramCommandManager =
        Esp32LightProgramCommandManager(
            context = appContext
        )

    private val _uiState =
        MutableStateFlow(QuickSetupUiState())

    val uiState: StateFlow<QuickSetupUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightQuickSetupEvent>(Channel.BUFFERED)

    val events =
        eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var observeJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        observeJob?.cancel()

        _uiState.update { state ->
            state.copy(
                isLoading = true,
                isSaving = false,
                tankProfile = null,
                recommendation = null,
                savedProgramId = null,
                isProgramSaved = false,
                isProgramLoaded = false,
                errorMessage = null
            )
        }

        observeJob = viewModelScope.launch {
            combine(
                devicesDataStoreManager.devicesFlow,
                aquariumTankDataStoreManager.tanksFlow,
                lightProgramsDataStoreManager.programsFlow
            ) { devices, tanks, programs ->
                Triple(
                    devices,
                    tanks,
                    programs
                )
            }.collect { (devices, tanks, programs) ->
                val device = devices.firstOrNull { device ->
                    device.id == this@DeviceLightQuickSetupViewModel.deviceId
                }

                if (device == null) {
                    renderUnavailableState(
                        message = "Light device could not be found."
                    )
                    return@collect
                }

                val assignedTankId = device.tankId

                if (assignedTankId == null) {
                    renderUnavailableState(
                        message = "No tank linked to this light device."
                    )
                    return@collect
                }

                val tank = tanks.firstOrNull { tank ->
                    tank.id == assignedTankId
                }

                if (tank == null) {
                    renderUnavailableState(
                        message = "Linked tank could not be found."
                    )
                    return@collect
                }

                renderRecommendationFromTank(
                    tank = tank,
                    programs = programs
                )
            }
        }
    }

    private fun renderUnavailableState(
        message: String
    ) {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isSaving = false,
                tankProfile = null,
                recommendation = null,
                savedProgramId = null,
                isProgramSaved = false,
                isProgramLoaded = false,
                errorMessage = message
            )
        }
    }

    private fun renderRecommendationFromTank(
        tank: SavedAquariumTank,
        programs: List<SavedLightProgram>
    ) {
        runCatching {
            val profile =
                QuickSetupTankProfileMapper.map(tank)

            val recommendation =
                SmartLightRecommendationEngine.recommend(profile)

            val smartProgramId =
                buildSmartProgramId(profile.tankId)

            val existingSmartProgram = programs.firstOrNull { program ->
                program.id == smartProgramId
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSaving = state.isSaving,
                    tankProfile = profile,
                    recommendation = recommendation,
                    savedProgramId = smartProgramId,
                    isProgramSaved = existingSmartProgram != null,
                    isProgramLoaded = existingSmartProgram?.isActive == true,
                    errorMessage = null
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSaving = false,
                    tankProfile = null,
                    recommendation = null,
                    savedProgramId = null,
                    isProgramSaved = false,
                    isProgramLoaded = false,
                    errorMessage = "Smart recommendation could not be generated."
                )
            }
        }
    }

    fun saveProgram() {
        val state = _uiState.value

        if (state.isSaving) {
            return
        }

        val recommendation = state.recommendation

        if (recommendation == null) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightQuickSetupEvent.ShowError(
                        "No recommendation available"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            saveRecommendation(
                recommendation = recommendation,
                activateProgram = false
            )
        }
    }

    fun loadToDevice() {
        val state = _uiState.value

        if (state.isSaving) {
            return
        }

        val recommendation = state.recommendation

        if (recommendation == null) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightQuickSetupEvent.ShowError(
                        "No recommendation available"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            saveRecommendation(
                recommendation = recommendation,
                activateProgram = true
            )
        }
    }

    private suspend fun saveRecommendation(
        recommendation: QuickSetupRecommendation,
        activateProgram: Boolean
    ) {
        if (deviceId <= 0L) {
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowError(
                    "Device information is missing"
                )
            )
            return
        }

        val currentState = _uiState.value
        val tankProfile = currentState.tankProfile

        if (tankProfile == null) {
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowError(
                    "Tank profile is missing"
                )
            )
            return
        }

        val smartProgramId =
            buildSmartProgramId(tankProfile.tankId)

        val now =
            System.currentTimeMillis()

        var successMessage: String? = null
        var errorMessage: String? = null
        var shouldNavigateBack = false

        _uiState.update { state ->
            state.copy(
                isSaving = true
            )
        }

        eventsChannel.send(
            DeviceLightQuickSetupEvent.SetLoading(true)
        )

        try {
            val existingPrograms =
                lightProgramsDataStoreManager.programsFlow.first()

            val existingSmartProgram = existingPrograms.firstOrNull { program ->
                program.id == smartProgramId
            }

            val shouldBeActive = if (activateProgram) {
                true
            } else {
                existingSmartProgram?.isActive == true
            }

            val savedProgram = SavedLightProgram(
                id = smartProgramId,
                deviceId = deviceId,
                name = buildSmartProgramName(
                    tankName = tankProfile.tankName,
                    recommendationTitle = recommendation.title
                ),
                draft = recommendation.draft,
                isActive = shouldBeActive,
                createdAt = existingSmartProgram?.createdAt ?: now,
                updatedAt = now
            )

            val shouldSyncToDevice =
                savedProgram.isActive

            if (shouldSyncToDevice) {
                val conflict =
                    LightProgramScheduleConflictValidator.findConflict(
                        candidate = savedProgram,
                        existingPrograms = existingPrograms.filterNot { program ->
                            program.id == savedProgram.id
                        }
                    )

                if (conflict != null) {
                    throw IllegalStateException(
                        "This smart program overlaps with ${conflict.name}"
                    )
                }
            }

            if (shouldSyncToDevice) {
                val programsToLoad = existingPrograms
                    .filter { program ->
                        program.deviceId == savedProgram.deviceId &&
                            program.isActive &&
                            program.id != savedProgram.id
                    } + savedProgram

                val loadResult =
                    lightProgramCommandManager.loadPrograms(
                        deviceId = savedProgram.deviceId,
                        programs = programsToLoad
                    )

                if (!loadResult.isSuccess) {
                    throw IllegalStateException(
                        loadResult.message
                            ?: "Smart program could not be loaded to device"
                    )
                }
            }

            lightProgramsDataStoreManager.saveProgram(savedProgram)

            _uiState.update { state ->
                state.copy(
                    savedProgramId = smartProgramId,
                    isProgramSaved = true,
                    isProgramLoaded = savedProgram.isActive
                )
            }

            successMessage = when {
                activateProgram -> {
                    "Smart program loaded to device"
                }

                shouldSyncToDevice -> {
                    "Smart program updated on device"
                }

                existingSmartProgram != null -> {
                    "Smart program updated"
                }

                else -> {
                    "Smart program saved"
                }
            }

            shouldNavigateBack = true
        } catch (error: Exception) {
            errorMessage =
                error.message ?: "Smart program could not be saved"
        } finally {
            _uiState.update { state ->
                state.copy(
                    isSaving = false
                )
            }

            eventsChannel.send(
                DeviceLightQuickSetupEvent.SetLoading(false)
            )
        }

        successMessage?.let { message ->
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowMessage(message)
            )
        }

        errorMessage?.let { message ->
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowError(message)
            )
        }

        if (shouldNavigateBack) {
            eventsChannel.send(
                DeviceLightQuickSetupEvent.NavigateBack
            )
        }
    }

    private fun buildSmartProgramId(
        tankId: Long?
    ): String {
        val resolvedTankId =
            tankId ?: 0L

        return "smart_quick_setup_${deviceId}_$resolvedTankId"
    }

    private fun buildSmartProgramName(
        tankName: String?,
        recommendationTitle: String
    ): String {
        val resolvedTankName = tankName
            .orEmpty()
            .ifBlank {
                "Tank"
            }

        return "$recommendationTitle · $resolvedTankName"
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}