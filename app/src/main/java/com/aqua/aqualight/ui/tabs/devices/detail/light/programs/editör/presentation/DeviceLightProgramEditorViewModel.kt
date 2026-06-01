package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramAcclimationDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramChannelBalanceDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRepeatRuleDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.repository.LightProgramEditorRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceLightProgramEditorViewModel(
    private val deviceId: Long,
    private val programId: String?,
    private val initialProgramName: String,
    private val repository: LightProgramEditorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeviceLightProgramEditorUiState(
            programId = programId,
            programName = initialProgramName
        )
    )

    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val _effects = Channel<DeviceLightProgramEditorEffect>(
        capacity = Channel.BUFFERED
    )

    val effects = _effects.receiveAsFlow()

    fun onEvent(
        event: DeviceLightProgramEditorEvent
    ) {
        when (event) {
            DeviceLightProgramEditorEvent.LoadRequested -> load()

            is DeviceLightProgramEditorEvent.ProgramNameChanged -> {
                updateProgramName(event.name)
            }

            DeviceLightProgramEditorEvent.SimpleModeSelected -> {
                updateMode(LightProgramEditorMode.SIMPLE)
            }

            DeviceLightProgramEditorEvent.ProModeSelected -> {
                updateMode(LightProgramEditorMode.PRO)
            }

            is DeviceLightProgramEditorEvent.ProChannelSelected -> {
                updateSelectedChannel(event.channel)
            }

            is DeviceLightProgramEditorEvent.RepeatDaysChanged -> {
                updateRepeatDays(event.selectedDays)
            }

            is DeviceLightProgramEditorEvent.RampSmoothingChanged -> {
                updateRampSmoothing(event.smoothing)
            }

            is DeviceLightProgramEditorEvent.ChannelBalanceChanged -> {
                updateChannelBalance(
                    redPercent = event.redPercent,
                    greenPercent = event.greenPercent,
                    bluePercent = event.bluePercent,
                    whitePercent = event.whitePercent
                )
            }

            is DeviceLightProgramEditorEvent.AcclimationChanged -> {
                updateAcclimation(
                    enabled = event.enabled,
                    durationDays = event.durationDays,
                    startIntensityPercent = event.startIntensityPercent
                )
            }

            is DeviceLightProgramEditorEvent.CurvePointAdded -> {
                // TODO: Add point into draft curve when curve editing state is enabled.
            }

            is DeviceLightProgramEditorEvent.CurvePointUpdated -> {
                // TODO: Update point inside draft curve when curve editing state is enabled.
            }

            is DeviceLightProgramEditorEvent.CurvePointDeleted -> {
                // TODO: Delete point from draft curve when curve editing state is enabled.
            }

            DeviceLightProgramEditorEvent.PreviewDayRequested -> {
                sendEffect(
                    DeviceLightProgramEditorEffect.OpenPreviewDay
                )
            }

            DeviceLightProgramEditorEvent.SaveRequested -> save()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true
            )

            val draft =
                if (programId.isNullOrBlank()) {
                    LightProgramEditorDraft.emptyNewProgram(
                        programName = initialProgramName
                    )
                } else {
                    repository.getProgramEditorDraft(
                        deviceId = deviceId,
                        programId = programId
                    )
                }

            _uiState.value = draft.toUiState(
                isLoading = false
            )
        }
    }

    private fun updateProgramName(
        name: String
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            programName = name
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun updateMode(
        mode: LightProgramEditorMode
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            mode = mode
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun updateSelectedChannel(
        channel: LightCurveChannel
    ) {
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel
        )
    }

    private fun updateRepeatDays(
        selectedDays: Set<Int>
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            repeatRule = LightProgramRepeatRuleDraft(
                selectedDays = selectedDays
            )
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun updateRampSmoothing(
        smoothing: LightProgramRampSmoothing
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            rampSmoothing = smoothing
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun updateChannelBalance(
        redPercent: Int?,
        greenPercent: Int?,
        bluePercent: Int?,
        whitePercent: Int?
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            channelBalance = LightProgramChannelBalanceDraft(
                redPercent = redPercent?.safePercent(),
                greenPercent = greenPercent?.safePercent(),
                bluePercent = bluePercent?.safePercent(),
                whitePercent = whitePercent?.safePercent()
            )
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun updateAcclimation(
        enabled: Boolean,
        durationDays: Int?,
        startIntensityPercent: Int?
    ) {
        val currentDraft = _uiState.value.draft ?: return

        val updatedDraft = currentDraft.copy(
            acclimation = LightProgramAcclimationDraft(
                enabled = enabled,
                durationDays = durationDays,
                startIntensityPercent = startIntensityPercent?.safePercent()
            )
        )

        _uiState.value = updatedDraft.toUiState(
            isLoading = false
        )
    }

    private fun save() {
        val currentDraft = _uiState.value.draft ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true
            )

            repository.saveProgramEditorDraft(
                deviceId = deviceId,
                draft = currentDraft
            )

            _uiState.value = _uiState.value.copy(
                isSaving = false
            )

            sendEffect(
                DeviceLightProgramEditorEffect.CloseScreen
            )
        }
    }

    private fun sendEffect(
        effect: DeviceLightProgramEditorEffect
    ) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    private fun LightProgramEditorDraft.toUiState(
        isLoading: Boolean
    ): DeviceLightProgramEditorUiState {
        return DeviceLightProgramEditorUiState(
            isLoading = isLoading,
            isSaving = false,
            programId = programId,
            programName = programName,
            editorMode = mode,
            selectedChannel = _uiState.value.selectedChannel,
            rampSmoothing = rampSmoothing,
            repeatDays = repeatRule.selectedDays,
            chartData = null,
            pointRows = emptyList(),
            channelBalance = ProgramChannelBalanceUi(
                redPercent = channelBalance.redPercent,
                greenPercent = channelBalance.greenPercent,
                bluePercent = channelBalance.bluePercent,
                whitePercent = channelBalance.whitePercent
            ),
            acclimation = ProgramAcclimationUi(
                enabled = acclimation.enabled,
                durationDays = acclimation.durationDays,
                startIntensityPercent = acclimation.startIntensityPercent
            ),
            draft = this
        )
    }

    private fun Int.safePercent(): Int {
        return coerceIn(
            0,
            100
        )
    }

    class Factory(
        private val deviceId: Long,
        private val programId: String?,
        private val initialProgramName: String,
        private val repository: LightProgramEditorRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            return DeviceLightProgramEditorViewModel(
                deviceId = deviceId,
                programId = programId,
                initialProgramName = initialProgramName,
                repository = repository
            ) as T
        }
    }
}