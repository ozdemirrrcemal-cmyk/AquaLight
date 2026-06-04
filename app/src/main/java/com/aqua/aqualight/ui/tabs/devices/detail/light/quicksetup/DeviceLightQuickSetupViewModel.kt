package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
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

    private val devicesDataStoreManager =
        DevicesDataStoreManager.create(application.applicationContext)

    private val aquariumTankDataStoreManager =
        AquariumTankDataStoreManager(application.applicationContext)

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        QuickSetupUiState()
    )

    val uiState: StateFlow<QuickSetupUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightQuickSetupEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var observeJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        observeJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                isSaving = false,
                hasLinkedTank = false,
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
                Triple(devices, tanks, programs)
            }.collect { (devices, tanks, programs) ->

                val device = devices.firstOrNull { device ->
                    device.id == this@DeviceLightQuickSetupViewModel.deviceId
                }

                if (device == null) {
                    renderUnavailableState("Light device could not be found.")
                    return@collect
                }

                val assignedTankId = device.tankId

                if (assignedTankId == null) {
                    renderUnavailableState("No tank linked to this light device.")
                    return@collect
                }

                val tank = tanks.firstOrNull { tank ->
                    tank.id == assignedTankId
                }

                if (tank == null) {
                    renderUnavailableState("Linked tank could not be found.")
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
        _uiState.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                hasLinkedTank = false,
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
            val profile = QuickSetupTankProfileMapper.map(tank)
            val recommendation = SmartLightRecommendationEngine.recommend(profile)

            val smartProgramId = buildSmartProgramId(profile.tankId)

            val existingSmartProgram = programs.firstOrNull { program ->
                program.id == smartProgramId
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSaving = false,
                    hasLinkedTank = true,
                    tankProfile = profile,
                    recommendation = recommendation,
                    savedProgramId = smartProgramId,
                    isProgramSaved = existingSmartProgram != null,
                    isProgramLoaded = existingSmartProgram?.isActive == true,
                    errorMessage = null
                )
            }
        }.onFailure {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSaving = false,
                    hasLinkedTank = false,
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
        val recommendation = _uiState.value.recommendation

        if (recommendation == null) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightQuickSetupEvent.ShowError("No recommendation available")
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
        val recommendation = _uiState.value.recommendation

        if (recommendation == null) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightQuickSetupEvent.ShowError("No recommendation available")
                )
            }
            return
        }

        viewModelScope.launch {
            saveRecommendation(
                recommendation = recommendation,
                activateProgram = true
            )

            // TODO: Send recommendation.draft to ESP32 as active light program.
        }
    }

    private suspend fun saveRecommendation(
        recommendation: QuickSetupRecommendation,
        activateProgram: Boolean
    ) {
        val currentState = _uiState.value
        val tankProfile = currentState.tankProfile

        val smartProgramId = buildSmartProgramId(tankProfile?.tankId)
        val now = System.currentTimeMillis()

        _uiState.update {
            it.copy(
                isSaving = true
            )
        }

        val existingPrograms = lightProgramsDataStoreManager.programsFlow.first()

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
                tankName = tankProfile?.tankName,
                recommendationTitle = recommendation.title
            ),
            draft = recommendation.draft,
            isActive = shouldBeActive,
            createdAt = existingSmartProgram?.createdAt ?: now,
            updatedAt = now
        )

        if (activateProgram) {
            val conflict = LightProgramScheduleConflictValidator.findConflict(
                candidate = savedProgram,
                existingPrograms = existingPrograms
            )

            if (conflict != null) {
                _uiState.update {
                    it.copy(
                        isSaving = false
                    )
                }

                eventsChannel.send(
                    DeviceLightQuickSetupEvent.ShowError(
                        "This smart program overlaps with ${conflict.name}"
                    )
                )
                return
            }
        }

        runCatching {
            lightProgramsDataStoreManager.saveProgram(savedProgram)
        }.onSuccess {
            _uiState.update {
                it.copy(
                    isSaving = false,
                    savedProgramId = smartProgramId,
                    isProgramSaved = true,
                    isProgramLoaded = shouldBeActive
                )
            }

            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowMessage(
                    when {
                        activateProgram -> "Smart program loaded to device"
                        existingSmartProgram != null -> "Smart program updated"
                        else -> "Smart program saved"
                    }
                )
            )
        }.onFailure {
            _uiState.update {
                it.copy(
                    isSaving = false
                )
            }

            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowError(
                    "Smart program could not be saved"
                )
            )
        }
    }

    private fun buildSmartProgramId(
        tankId: Long?
    ): String {
        val resolvedTankId = tankId ?: 0L

        return "smart_quick_setup_${deviceId}_$resolvedTankId"
    }

    private fun buildSmartProgramName(
        tankName: String?,
        recommendationTitle: String
    ): String {
        val resolvedTankName = tankName
            .orEmpty()
            .ifBlank { "Tank" }

        return "$recommendationTitle · $resolvedTankName"
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}