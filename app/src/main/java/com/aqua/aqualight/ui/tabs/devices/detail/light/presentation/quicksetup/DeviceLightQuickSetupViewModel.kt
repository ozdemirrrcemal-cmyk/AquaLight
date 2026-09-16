package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
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

    fun updatePreference(change: DeviceLightQuickSetupPreferenceChange) = updateDraft {
        when (change) {
            is DeviceLightQuickSetupPreferenceChange.PlantDemand ->
                copy(plantDemand = change.value)
            is DeviceLightQuickSetupPreferenceChange.PlantDensity ->
                copy(plantDensity = change.value)
            is DeviceLightQuickSetupPreferenceChange.WaterDepth -> copy(
                waterDepthCm = change.value.coerceIn(
                    QUICK_SETUP_WATER_DEPTH_MIN_CM,
                    QUICK_SETUP_WATER_DEPTH_MAX_CM
                )
            )
            is DeviceLightQuickSetupPreferenceChange.FixtureHeight -> copy(
                fixtureHeightCm = change.value.coerceIn(
                    QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM,
                    QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM
                )
            )
            is DeviceLightQuickSetupPreferenceChange.AmbientLevel ->
                copy(ambientLevel = change.value)
            is DeviceLightQuickSetupPreferenceChange.Co2Ready -> copy(co2Ready = change.value)
            is DeviceLightQuickSetupPreferenceChange.ActiveSoil -> copy(activeSoil = change.value)
            is DeviceLightQuickSetupPreferenceChange.LightsOffMinute -> copy(
                preferredLightsOffMinute = change.value.coerceIn(
                    QUICK_SETUP_LIGHTS_OFF_MINUTE_MIN,
                    QUICK_SETUP_LIGHTS_OFF_MINUTE_MAX
                )
            )
            is DeviceLightQuickSetupPreferenceChange.MeasuredPpfd -> copy(
                measuredFullProfilePpfd = change.value?.coerceIn(
                    QUICK_SETUP_PPFD_MIN,
                    QUICK_SETUP_PPFD_MAX
                )
            )
        }
    }

    fun continueToPreferences() {
        if (!_uiState.value.canContinueTankData) return
        _uiState.update { it.copy(step = DeviceLightQuickSetupStep.PREFERENCES) }
    }

    fun backStep() {
        _uiState.update { state ->
            state.copy(
                step = when (state.step) {
                    DeviceLightQuickSetupStep.TANK_DATA -> DeviceLightQuickSetupStep.TANK_DATA
                    DeviceLightQuickSetupStep.PREFERENCES -> DeviceLightQuickSetupStep.TANK_DATA
                    DeviceLightQuickSetupStep.PLAN -> DeviceLightQuickSetupStep.PREFERENCES
                }
            )
        }
    }

    fun calculate() {
        val state = _uiState.value
        val tank = state.tank
        val fixtureHeight = state.fixtureHeightCm
        if (tank != null && fixtureHeight != null && state.canCalculate) {
            val result = runCatching {
                DeviceLightQuickSetupCalculator.calculate(
                    tank = tank,
                    input = state.calculationInput(fixtureHeight),
                    todayEpochDay = state.todayEpochDay
                )
            }.getOrNull()
            if (result == null) {
                _effects.tryEmit(
                    DeviceLightQuickSetupEffect.ShowMessage(
                        R.string.device_light_quick_setup_invalid_data
                    )
                )
            } else {
                _uiState.update { it.copy(plan = result, step = DeviceLightQuickSetupStep.PLAN) }
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
                            it.copy(managedPlanSnapshot = result.snapshot, applying = false)
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
                is DeviceLightQuickSetupTankReadResult.Failed -> {
                    _uiState.update { it.copy(initialLoading = false) }
                    _effects.emit(
                        DeviceLightQuickSetupEffect.CloseUnavailable(tankResult.failure.messageRes())
                    )
                }
                is DeviceLightQuickSetupTankReadResult.Available -> {
                    val tank = tankResult.tank
                    _uiState.update {
                        it.copy(
                            tank = tank,
                            plantDemand = tank.inferredPlantDemand,
                            plantDensity = tank.inferredPlantDensity,
                            waterDepthCm = (tank.heightCm -
                                QUICK_SETUP_WATER_SURFACE_CLEARANCE_CM).coerceIn(
                                QUICK_SETUP_WATER_DEPTH_MIN_CM,
                                QUICK_SETUP_WATER_DEPTH_MAX_CM
                            ),
                            co2Ready = tank.inferredCo2Installed,
                            activeSoil = tank.inferredActiveSoil
                        )
                    }
                    loadAuthority()
                }
            }
        }
    }

    private suspend fun loadAuthority() {
        when (val result = managedPlanOperations.read(boundDeviceUid)) {
            is DeviceLightManagedPlanReadResult.Available -> _uiState.update {
                it.copy(
                    managedPlanSnapshot = result.snapshot,
                    contentEnabled = true,
                    initialLoading = false,
                    applying = false
                )
            }
            is DeviceLightManagedPlanReadResult.Failed -> {
                _uiState.update { it.copy(initialLoading = false, applying = false) }
                _effects.emit(DeviceLightQuickSetupEffect.ShowMessage(result.failure.messageRes()))
            }
        }
    }

    private suspend fun handleMutationFailure(failure: DeviceLightManagedPlanFailure) {
        _uiState.update { it.copy(applying = false) }
        if (failure == DeviceLightManagedPlanFailure.STALE_AUTHORITY) {
            _uiState.update {
                it.copy(
                    step = DeviceLightQuickSetupStep.PREFERENCES,
                    plan = null,
                    contentEnabled = false
                )
            }
            loadAuthority()
            _effects.emit(
                DeviceLightQuickSetupEffect.ShowMessage(
                    R.string.device_light_quick_setup_stale_authority
                )
            )
        } else {
            _effects.emit(DeviceLightQuickSetupEffect.ShowMessage(failure.messageRes()))
        }
    }

    private fun updateDraft(block: DeviceLightQuickSetupUiState.() -> DeviceLightQuickSetupUiState) {
        _uiState.update { state -> state.block().copy(plan = null) }
    }
}

private fun DeviceLightQuickSetupUiState.calculationInput(
    fixtureHeightCm: Int
): DeviceLightQuickSetupInput = DeviceLightQuickSetupInput(
    plantDemand = plantDemand,
    plantDensity = plantDensity,
    waterDepthCm = waterDepthCm,
    fixtureHeightCm = fixtureHeightCm,
    ambientLevel = ambientLevel,
    co2Ready = co2Ready,
    activeSoil = activeSoil,
    preferredLightsOffMinute = preferredLightsOffMinute,
    measuredFullProfilePpfd = measuredFullProfilePpfd
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
