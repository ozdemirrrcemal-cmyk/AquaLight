package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.LightProgramDraftMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.engine.SmartLightRecommendationEngine
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.mapper.QuickSetupTankProfileMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasLinkedTank = false,
                    tankProfile = null,
                    recommendation = null,
                    errorMessage = null
                )
            }

            runCatching {
                val devices = devicesDataStoreManager.devicesFlow.first()

                val device = devices.firstOrNull { device ->
                    device.id == deviceId
                } ?: throw IllegalStateException("Light device could not be found.")

                val assignedTankId = device.tankId
                    ?: throw IllegalStateException("No tank linked to this light device.")

                val tanks = aquariumTankDataStoreManager.tanksFlow.first()

                val tank = tanks.firstOrNull { tank ->
                    tank.id == assignedTankId
                } ?: throw IllegalStateException("Linked tank could not be found.")

                loadRecommendationFromTank(tank)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLinkedTank = false,
                        tankProfile = null,
                        recommendation = null,
                        errorMessage = throwable.message ?: "Smart setup could not be prepared."
                    )
                }
            }
        }
    }

    private fun loadRecommendationFromTank(
        tank: SavedAquariumTank
    ) {
        val profile = QuickSetupTankProfileMapper.map(tank)
        val recommendation = SmartLightRecommendationEngine.recommend(profile)

        _uiState.update {
            it.copy(
                isLoading = false,
                hasLinkedTank = true,
                tankProfile = profile,
                recommendation = recommendation,
                errorMessage = null
            )
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
                isActive = false,
                successMessage = "Smart program saved"
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
                isActive = true,
                successMessage = "Smart program loaded to device"
            )

            // TODO: Send recommendation.draft to ESP32 as active light program.
        }
    }

    private suspend fun saveRecommendation(
        recommendation: QuickSetupRecommendation,
        isActive: Boolean,
        successMessage: String
    ) {
        runCatching {
            val savedProgram = LightProgramDraftMapper.toSavedProgram(
                draft = recommendation.draft,
                name = recommendation.title,
                deviceId = deviceId,
                isActive = isActive
            )

            if (isActive) {
                val existingPrograms = lightProgramsDataStoreManager.programsFlow.first()

                val conflict = LightProgramScheduleConflictValidator.findConflict(
                    candidate = savedProgram,
                    existingPrograms = existingPrograms
                )

                if (conflict != null) {
                    eventsChannel.send(
                        DeviceLightQuickSetupEvent.ShowError(
                            "This smart program overlaps with ${conflict.name}"
                        )
                    )
                    return
                }
            }

            lightProgramsDataStoreManager.saveProgram(savedProgram)
        }.onSuccess {
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowMessage(successMessage)
            )
            eventsChannel.send(DeviceLightQuickSetupEvent.NavigateBack)
        }.onFailure {
            eventsChannel.send(
                DeviceLightQuickSetupEvent.ShowError(
                    "Smart program could not be saved"
                )
            )
        }
    }
}