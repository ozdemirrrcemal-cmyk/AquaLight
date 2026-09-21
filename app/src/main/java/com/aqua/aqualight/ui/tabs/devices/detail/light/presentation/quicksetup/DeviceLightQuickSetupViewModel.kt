package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedAutoPlanOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendationEngine
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class DeviceLightQuickSetupViewModel(
    savedStateHandle: SavedStateHandle,
    contextOperations: DeviceLightQuickSetupContextOperations,
    managedPlanOperations: DeviceLightManagedAutoPlanOperations,
    controlOperations: DeviceLightControlOperations,
    calibration: DeviceLightFixtureCalibration
) : ViewModel() {

    private val controller = DeviceLightQuickSetupController(
        contextOperations = contextOperations,
        managedPlanOperations = managedPlanOperations,
        controlOperations = controlOperations
    )
    private val engine = DeviceLightQuickSetupRecommendationEngine(calibration)
    private val savedState = DeviceLightQuickSetupSavedState(savedStateHandle)
    private val _uiState = MutableStateFlow(DeviceLightQuickSetupUiState())
    internal val uiState: StateFlow<DeviceLightQuickSetupUiState> = _uiState.asStateFlow()
    private var boundDeviceUid: String? = null

    fun bind(deviceUid: String) {
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = savedState.bindDevice(deviceUid)
        load()
    }

    internal fun dispatch(action: DeviceLightQuickSetupAction) {
        when (action) {
            is DeviceLightQuickSetupAction.WaterHeightChanged -> {
                val value = sanitizeMeasurementInput(action.value)
                savedState.saveWaterHeight(value)
                _uiState.update { it.copy(waterHeightText = value, blockReason = null) }
            }
            is DeviceLightQuickSetupAction.FixtureHeightChanged -> {
                val value = sanitizeMeasurementInput(action.value)
                savedState.saveFixtureHeight(value)
                _uiState.update { it.copy(fixtureHeightText = value, blockReason = null) }
            }
            is DeviceLightQuickSetupAction.FirstLightTimeChanged -> {
                savedState.saveFirstLightMinute(action.minuteOfDay)
                _uiState.update {
                    it.copy(firstLightOnMinuteOfDay = action.minuteOfDay, blockReason = null)
                }
            }
            is DeviceLightQuickSetupAction.Co2PrechargedChanged -> {
                savedState.saveCo2Precharged(action.enabled)
                _uiState.update { it.copy(co2Precharged = action.enabled, blockReason = null) }
            }
            DeviceLightQuickSetupAction.Next -> advance()
            DeviceLightQuickSetupAction.Back -> goBack()
            DeviceLightQuickSetupAction.Apply -> applyRecommendation()
            DeviceLightQuickSetupAction.Retry -> load()
            DeviceLightQuickSetupAction.Edit -> moveTo(DeviceLightQuickSetupStage.WATER_HEIGHT)
            DeviceLightQuickSetupAction.DisablePlan -> disableManagedPlan()
        }
    }

    private fun load() {
        val deviceUid = boundDeviceUid ?: return
        _uiState.update { it.copy(loading = true, blockReason = null) }
        viewModelScope.launch {
            when (val result = controller.load(deviceUid)) {
                is DeviceLightQuickSetupLoadResult.Available -> {
                    val restoredStage = savedState.restoredStage(result.context.co2Present)
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            context = result.context,
                            plantProfile = result.plantProfile,
                            managedPlan = result.managedPlan,
                            livePlan = result.livePlan,
                            stage = if (result.managedPlan?.installed == true) {
                                DeviceLightQuickSetupStage.LIVE
                            } else {
                                restoredStage
                            },
                            blockReason = null
                        )
                    }
                    if (
                        result.managedPlan?.installed != true &&
                        restoredStage == DeviceLightQuickSetupStage.REVIEW
                    ) {
                        calculateRecommendation()
                    }
                }
                is DeviceLightQuickSetupLoadResult.Blocked -> _uiState.update {
                    it.copy(loading = false, blockReason = result.reason)
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun advance() {
        val state = _uiState.value
        val context = state.context ?: return
        when (state.stage) {
            DeviceLightQuickSetupStage.PROFILE -> moveTo(DeviceLightQuickSetupStage.WATER_HEIGHT)
            DeviceLightQuickSetupStage.WATER_HEIGHT -> {
                val water = state.waterHeightText.toIntOrNull()
                if (water != null && water > 0 && water <= context.tankHeightCm) {
                    moveTo(DeviceLightQuickSetupStage.FIXTURE_HEIGHT)
                } else {
                    showInvalidInput()
                }
            }
            DeviceLightQuickSetupStage.FIXTURE_HEIGHT -> {
                val fixture = state.fixtureHeightText.toIntOrNull()
                if (fixture != null && fixture >= 0) {
                    moveTo(DeviceLightQuickSetupStage.LIGHT_TIME)
                } else {
                    showInvalidInput()
                }
            }
            DeviceLightQuickSetupStage.LIGHT_TIME -> {
                if (validFirstLightTime(state.firstLightOnMinuteOfDay)) {
                    if (context.co2Present) {
                        moveTo(DeviceLightQuickSetupStage.CO2_CONFIRMATION)
                    } else {
                        calculateRecommendation()
                    }
                } else {
                    showInvalidInput()
                }
            }
            DeviceLightQuickSetupStage.CO2_CONFIRMATION -> calculateRecommendation()
            else -> Unit
        }
    }

    private fun calculateRecommendation() {
        val state = _uiState.value
        val context = state.context ?: return
        val input = state.toInputOrNull() ?: return showInvalidInput()
        _uiState.update {
            it.copy(stage = DeviceLightQuickSetupStage.CALCULATING, blockReason = null)
        }
        viewModelScope.launch {
            yield()
            when (val result = engine.recommend(context, input)) {
                is DeviceLightQuickSetupRecommendationResult.Available -> {
                    savedState.persistStage(DeviceLightQuickSetupStage.REVIEW)
                    _uiState.update {
                        it.copy(
                            stage = DeviceLightQuickSetupStage.REVIEW,
                            recommendation = result.recommendation,
                            blockReason = null,
                            reviewRequiredAfterStale = false
                        )
                    }
                }
                is DeviceLightQuickSetupRecommendationResult.Blocked -> {
                    val fallbackStage = if (context.co2Present) {
                        DeviceLightQuickSetupStage.CO2_CONFIRMATION
                    } else {
                        DeviceLightQuickSetupStage.LIGHT_TIME
                    }
                    savedState.persistStage(fallbackStage)
                    _uiState.update {
                        it.copy(stage = fallbackStage, blockReason = result.reason)
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun applyRecommendation() {
        val state = _uiState.value
        if (state.stage != DeviceLightQuickSetupStage.REVIEW) return
        val context = state.context ?: return
        val recommendation = state.recommendation ?: return
        _uiState.update { it.copy(stage = DeviceLightQuickSetupStage.APPLYING, blockReason = null) }
        viewModelScope.launch {
            when (val result = controller.apply(state.deviceUid, context, recommendation)) {
                is DeviceLightQuickSetupApplyResult.Applied -> _uiState.update {
                    it.copy(
                        stage = DeviceLightQuickSetupStage.LIVE,
                        managedPlan = result.managedPlan,
                        livePlan = result.livePlan,
                        blockReason = null,
                        reviewRequiredAfterStale = false
                    )
                }
                is DeviceLightQuickSetupApplyResult.ContextChanged -> {
                    savedState.persistStage(DeviceLightQuickSetupStage.REVIEW)
                    val input = _uiState.value.toInputOrNull()
                    val rebuilt = if (input == null) null else engine.recommend(result.context, input)
                    _uiState.update {
                        it.copy(
                            context = result.context,
                            plantProfile = result.plantProfile,
                            recommendation = (rebuilt as? DeviceLightQuickSetupRecommendationResult.Available)
                                ?.recommendation,
                            stage = DeviceLightQuickSetupStage.REVIEW,
                            blockReason = if (rebuilt is DeviceLightQuickSetupRecommendationResult.Available) {
                                DeviceLightQuickSetupBlockReason.STALE_CONTEXT
                            } else {
                                (rebuilt as? DeviceLightQuickSetupRecommendationResult.Blocked)?.reason
                                    ?: DeviceLightQuickSetupBlockReason.STALE_CONTEXT
                            },
                            reviewRequiredAfterStale = true
                        )
                    }
                }
                is DeviceLightQuickSetupApplyResult.Stale -> {
                    savedState.persistStage(DeviceLightQuickSetupStage.REVIEW)
                    _uiState.update {
                    it.copy(
                        stage = DeviceLightQuickSetupStage.REVIEW,
                        managedPlan = result.latest,
                        blockReason = DeviceLightQuickSetupBlockReason.STALE_CONTEXT,
                        reviewRequiredAfterStale = true
                        )
                    }
                }
                is DeviceLightQuickSetupApplyResult.Failed -> {
                    savedState.persistStage(DeviceLightQuickSetupStage.REVIEW)
                    _uiState.update {
                        it.copy(stage = DeviceLightQuickSetupStage.REVIEW, blockReason = result.reason)
                    }
                }
            }
        }
    }

    private fun disableManagedPlan() {
        val state = _uiState.value
        if (state.stage != DeviceLightQuickSetupStage.LIVE) return
        val managedPlan = state.managedPlan ?: return
        _uiState.update { it.copy(stage = DeviceLightQuickSetupStage.APPLYING, blockReason = null) }
        viewModelScope.launch {
            when (val result = controller.disable(state.deviceUid, managedPlan)) {
                is DeviceLightQuickSetupDisableResult.Disabled,
                DeviceLightQuickSetupDisableResult.NotInstalled -> {
                    savedState.persistStage(DeviceLightQuickSetupStage.PROFILE)
                    _uiState.update {
                    it.copy(
                        stage = DeviceLightQuickSetupStage.PROFILE,
                        managedPlan = null,
                        livePlan = null,
                        recommendation = null,
                        blockReason = null
                        )
                    }
                }
                is DeviceLightQuickSetupDisableResult.Stale -> _uiState.update {
                    it.copy(
                        stage = DeviceLightQuickSetupStage.LIVE,
                        managedPlan = result.latest ?: managedPlan,
                        blockReason = DeviceLightQuickSetupBlockReason.STALE_CONTEXT
                    )
                }
                is DeviceLightQuickSetupDisableResult.Failed -> _uiState.update {
                    it.copy(stage = DeviceLightQuickSetupStage.LIVE, blockReason = result.reason)
                }
            }
        }
    }

    private fun moveTo(stage: DeviceLightQuickSetupStage) {
        savedState.persistStage(stage)
        _uiState.update { it.copy(stage = stage, blockReason = null) }
    }

    private fun goBack() {
        val previous = previousState(_uiState.value)
        savedState.persistStage(previous.stage)
        _uiState.value = previous
    }

    private fun showInvalidInput() {
        _uiState.update { it.copy(blockReason = DeviceLightQuickSetupBlockReason.INVALID_INPUT) }
    }
}

private fun previousState(state: DeviceLightQuickSetupUiState): DeviceLightQuickSetupUiState {
    val previous = when (state.stage) {
        DeviceLightQuickSetupStage.WATER_HEIGHT -> DeviceLightQuickSetupStage.PROFILE
        DeviceLightQuickSetupStage.FIXTURE_HEIGHT -> DeviceLightQuickSetupStage.WATER_HEIGHT
        DeviceLightQuickSetupStage.LIGHT_TIME -> DeviceLightQuickSetupStage.FIXTURE_HEIGHT
        DeviceLightQuickSetupStage.CO2_CONFIRMATION -> DeviceLightQuickSetupStage.LIGHT_TIME
        DeviceLightQuickSetupStage.REVIEW -> if (state.co2Present) {
            DeviceLightQuickSetupStage.CO2_CONFIRMATION
        } else {
            DeviceLightQuickSetupStage.LIGHT_TIME
        }
        else -> state.stage
    }
    return state.copy(stage = previous, blockReason = null)
}

private fun validFirstLightTime(minuteOfDay: Int): Boolean =
    minuteOfDay in 0 until LATEST_VALID_START_EXCLUSIVE &&
        minuteOfDay % TIME_STEP_MINUTES == 0

private const val TIME_STEP_MINUTES = 5
private const val LATEST_VALID_START_EXCLUSIVE = 16 * 60
