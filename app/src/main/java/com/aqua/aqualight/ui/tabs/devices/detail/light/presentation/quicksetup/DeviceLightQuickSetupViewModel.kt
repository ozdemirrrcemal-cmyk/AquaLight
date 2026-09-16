package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAlgaeLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLight
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import java.time.LocalDate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceLightQuickSetupViewModel(
    private val tankOperations: DeviceLightQuickSetupTankOperations,
    private val managedPlanOperations: DeviceLightManagedPlanOperations,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightQuickSetupUiState())
    val uiState: StateFlow<DeviceLightQuickSetupUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<DeviceLightQuickSetupEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightQuickSetupEffect> = _effects.asSharedFlow()
    private var boundDeviceUid = ""

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Quick setup deviceUid must not be blank." }
        if (deviceUid == boundDeviceUid) return
        boundDeviceUid = deviceUid
        load()
    }

    fun toggleDetails() {
        _uiState.update { state -> state.copy(detailsExpanded = !state.detailsExpanded) }
    }

    fun selectAmbientLight(value: DeviceLightAmbientLight) {
        _uiState.update { state -> state.copy(ambientLight = value) }
        recalculateAssessment()
    }

    fun selectAlgaeLevel(value: DeviceLightAlgaeLevel) {
        _uiState.update { state -> state.copy(algaeLevel = value) }
        recalculateAssessment()
    }

    fun setInstalledPlanEditing(editing: Boolean) {
        _uiState.update { state ->
            if (!state.hasInstalledPlan) {
                state
            } else {
                state.copy(
                    ambientLight = null,
                    algaeLevel = null,
                    plan = null,
                    editingInstalledPlan = editing,
                    detailsExpanded = false
                )
            }
        }
    }

    fun apply() {
        val state = _uiState.value
        val plan = state.plan
        val snapshot = state.managedPlanSnapshot
        if (plan != null && snapshot != null && state.canApply) {
            viewModelScope.launch {
                _uiState.update { it.copy(applying = true) }
                when (
                    val result = managedPlanOperations.apply(
                        deviceUid = boundDeviceUid,
                        authority = snapshot.authority,
                        draft = plan.toManagedPlanDraft()
                    )
                ) {
                    is DeviceLightManagedPlanMutationResult.Applied -> {
                        _uiState.update {
                            it.copy(
                                managedPlanSnapshot = result.snapshot,
                                editingInstalledPlan = false,
                                applying = false
                            )
                        }
                        _effects.emit(DeviceLightQuickSetupEffect.Applied)
                    }
                    is DeviceLightManagedPlanMutationResult.Failed ->
                        handleMutationFailure(result.failure)
                    DeviceLightManagedPlanMutationResult.Deleted ->
                        error("Apply cannot return Deleted.")
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val today = todayEpochDay()
            _uiState.value = DeviceLightQuickSetupUiState(
                deviceUid = boundDeviceUid,
                todayEpochDay = today,
                initialLoading = true
            )
            when (val tankResult = tankOperations.readForDevice(boundDeviceUid)) {
                is DeviceLightQuickSetupTankReadResult.Failed -> closeUnavailable(tankResult.failure)
                is DeviceLightQuickSetupTankReadResult.Available -> {
                    _uiState.update { state -> state.copy(tank = tankResult.tank) }
                    loadAuthorityAndPlan()
                }
            }
        }
    }

    private suspend fun loadAuthorityAndPlan() {
        when (val result = managedPlanOperations.read(boundDeviceUid)) {
            is DeviceLightManagedPlanReadResult.Available -> {
                _uiState.update {
                    it.copy(
                        managedPlanSnapshot = result.snapshot,
                        plan = null,
                        ambientLight = null,
                        algaeLevel = null,
                        editingInstalledPlan = false,
                        contentEnabled = true,
                        initialLoading = false,
                        applying = false
                    )
                }
            }
            is DeviceLightManagedPlanReadResult.Failed -> {
                _uiState.update { it.copy(initialLoading = false, applying = false) }
                _effects.emit(DeviceLightQuickSetupEffect.ShowMessage(result.failure.messageRes()))
            }
        }
    }

    private suspend fun closeUnavailable(failure: DeviceLightQuickSetupTankFailure) {
        _uiState.update { it.copy(initialLoading = false) }
        _effects.emit(DeviceLightQuickSetupEffect.CloseUnavailable(failure.messageRes()))
    }

    private suspend fun handleMutationFailure(failure: DeviceLightManagedPlanFailure) {
        if (failure == DeviceLightManagedPlanFailure.STALE_AUTHORITY) {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    managedPlanSnapshot = null,
                    contentEnabled = false,
                    applying = false
                )
            }
            when (val refreshed = managedPlanOperations.read(boundDeviceUid)) {
                is DeviceLightManagedPlanReadResult.Available -> {
                    _uiState.update {
                        it.copy(
                            managedPlanSnapshot = refreshed.snapshot,
                            contentEnabled = true,
                            applying = false,
                            editingInstalledPlan = refreshed.snapshot.installed
                        )
                    }
                    recalculateAssessment()
                }
                is DeviceLightManagedPlanReadResult.Failed -> {
                    _effects.emit(
                        DeviceLightQuickSetupEffect.ShowMessage(refreshed.failure.messageRes())
                    )
                }
            }
            _effects.emit(
                DeviceLightQuickSetupEffect.ShowMessage(
                    R.string.device_light_quick_setup_stale_authority
                )
            )
        } else {
            _uiState.update { it.copy(applying = false) }
            _effects.emit(DeviceLightQuickSetupEffect.ShowMessage(failure.messageRes()))
        }
    }

    private fun recalculateAssessment() {
        val state = _uiState.value
        val tank = state.tank
        val ambientLight = state.ambientLight
        val algaeLevel = state.algaeLevel
        if (tank == null || ambientLight == null || algaeLevel == null) {
            _uiState.update { it.copy(plan = null, detailsExpanded = false) }
            return
        }
        val plan = runCatching {
            DeviceLightQuickSetupCalculator.calculate(
                tank = tank,
                input = tank.automaticInput(ambientLight, algaeLevel),
                todayEpochDay = state.todayEpochDay
            )
        }.getOrNull()
        _uiState.update { it.copy(plan = plan, detailsExpanded = false) }
        if (plan == null) {
            _effects.tryEmit(
                DeviceLightQuickSetupEffect.ShowMessage(
                    R.string.device_light_quick_setup_invalid_data
                )
            )
        }
    }
}

private fun DeviceLightQuickSetupTank.automaticInput(
    ambientLight: DeviceLightAmbientLight,
    algaeLevel: DeviceLightAlgaeLevel
): DeviceLightQuickSetupInput =
    DeviceLightQuickSetupInput(
        plantDemand = inferredPlantDemand,
        plantDensity = inferredPlantDensity,
        aquariumHeightCm = heightCm.coerceIn(
            QUICK_SETUP_AQUARIUM_HEIGHT_MIN_CM,
            QUICK_SETUP_AQUARIUM_HEIGHT_MAX_CM
        ),
        co2Installed = inferredCo2Installed,
        activeSoil = inferredActiveSoil,
        ambientLight = ambientLight,
        algaeLevel = algaeLevel,
        programEndMinute = QUICK_SETUP_AUTOMATIC_END_MINUTE
    )

internal sealed interface DeviceLightQuickSetupEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : DeviceLightQuickSetupEffect
    data class CloseUnavailable(@StringRes val messageRes: Int) : DeviceLightQuickSetupEffect
    data object Applied : DeviceLightQuickSetupEffect
}

@StringRes
private fun DeviceLightQuickSetupTankFailure.messageRes(): Int = when (this) {
    DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED ->
        R.string.device_light_quick_setup_tank_not_assigned
    DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND ->
        R.string.device_light_quick_setup_tank_not_found
    DeviceLightQuickSetupTankFailure.INVALID_DEVICE,
    DeviceLightQuickSetupTankFailure.DEVICE_NOT_FOUND,
    DeviceLightQuickSetupTankFailure.UNAVAILABLE ->
        R.string.device_light_quick_setup_unavailable
}

@StringRes
private fun DeviceLightManagedPlanFailure.messageRes(): Int = when (this) {
    DeviceLightManagedPlanFailure.NOT_CONNECTED ->
        R.string.device_light_quick_setup_not_connected
    DeviceLightManagedPlanFailure.UNSUPPORTED ->
        R.string.device_light_quick_setup_firmware_required
    DeviceLightManagedPlanFailure.STALE_AUTHORITY ->
        R.string.device_light_quick_setup_stale_authority
    DeviceLightManagedPlanFailure.NOT_FOUND,
    DeviceLightManagedPlanFailure.SELECTED,
    DeviceLightManagedPlanFailure.REJECTED,
    DeviceLightManagedPlanFailure.INVALID_DATA,
    DeviceLightManagedPlanFailure.UNAVAILABLE ->
        R.string.device_light_quick_setup_operation_error
}
