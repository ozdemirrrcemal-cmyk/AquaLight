package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPersistenceResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecordedOutcome
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.application.devices.light.quicksetup.notBelow
import java.util.concurrent.atomic.AtomicBoolean
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
    private val managedPlanOperations: DeviceLightManagedPlanOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightQuickSetupUiState())
    val uiState: StateFlow<DeviceLightQuickSetupUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<DeviceLightQuickSetupEffect>(
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightQuickSetupEffect> = _effects.asSharedFlow()
    private var boundDeviceUid = ""
    private val applyInFlight = AtomicBoolean(false)

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Quick setup deviceUid must not be blank." }
        if (deviceUid == boundDeviceUid) return
        boundDeviceUid = deviceUid
        load()
    }

    fun setWaterDepthCm(value: Int) = updateInput {
        val max = tank?.tankHeightCm ?: return@updateInput this
        copy(waterDepthCm = value.takeIf { it in QUICK_SETUP_WATER_DEPTH_MIN_CM..max })
    }

    fun setFixtureHeightAboveWaterCm(value: Int) = updateInput {
        copy(
            fixtureHeightAboveWaterCm = value.takeIf {
                it in QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM..QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM
            }
        )
    }

    fun setProgramEndMinute(value: Int) = updateInput {
        copy(
            programEndMinute = value.takeIf {
                it in QUICK_SETUP_MINIMUM_END_MINUTE until MINUTES_PER_DAY
            }
        )
    }

    fun setDaylightStartMinute(value: Int) = updateInput {
        copy(daylightStartMinute = value.takeIf { it in 0 until MINUTES_PER_DAY })
    }

    fun setDaylightEndMinute(value: Int) = updateInput {
        copy(daylightEndMinute = value.takeIf { it in 0 until MINUTES_PER_DAY })
    }

    fun selectPlantDemand(value: DeviceLightPlantDemand) = updateInput {
        copy(
            plantDemand = value.notBelow(
                tank?.reviewedPlantDemandFloor ?: DeviceLightPlantDemand.UNKNOWN
            )
        )
    }

    fun selectPlantCoverage(value: AquariumPlantCoverage) = updateInput {
        copy(plantCoverage = value)
    }

    fun selectCo2Readiness(value: AquariumCo2Readiness) = updateInput {
        val accepted = if (tank?.co2ComponentPresent == true) {
            value.takeUnless { it == AquariumCo2Readiness.NOT_INSTALLED }
        } else {
            AquariumCo2Readiness.NOT_INSTALLED
        }
        copy(co2Readiness = accepted)
    }

    fun selectDaylight(value: AquariumDaylightExposure) = updateInput {
        if (value == AquariumDaylightExposure.DIRECT) {
            copy(daylightExposure = value)
        } else {
            copy(
                daylightExposure = value,
                daylightStartMinute = null,
                daylightEndMinute = null
            )
        }
    }

    fun selectSurfaceGrowth(value: AquariumSurfaceGrowth) = updateInput {
        copy(surfaceGrowth = value)
    }

    fun selectShelter(value: AquariumShelterAvailability) = updateInput {
        copy(shelterAvailability = value)
    }

    fun toggleDetails() {
        _uiState.update { state -> state.copy(detailsExpanded = !state.detailsExpanded) }
    }

    fun setInstalledPlanEditing(editing: Boolean) {
        _uiState.update { state ->
            val tank = state.tank
            if (!state.hasInstalledPlan || tank == null) {
                state
            } else if (editing) {
                state.copy(
                    co2Readiness = if (tank.co2ComponentPresent) {
                        null
                    } else {
                        AquariumCo2Readiness.NOT_INSTALLED
                    },
                    surfaceGrowth = null,
                    plan = null,
                    editingInstalledPlan = true,
                    detailsExpanded = false
                )
            } else {
                state.withStoredInputs(tank, state.managedPlanSnapshot)
                    .copy(
                        plan = null,
                        editingInstalledPlan = false,
                        detailsExpanded = false
                    )
            }
        }
        if (editing) recalculateAssessment()
    }

    fun apply() {
        val state = _uiState.value
        val plan = state.plan ?: return
        val snapshot = state.managedPlanSnapshot ?: return
        val input = state.toInputOrNull() ?: return
        if (!state.canApply) return
        if (!applyInFlight.compareAndSet(false, true)) return
        val targetDeviceUid = boundDeviceUid
        if (targetDeviceUid.isBlank()) {
            applyInFlight.set(false)
            return
        }
        // Claim the action before launching so two taps cannot prepare two firmware mutations.
        _uiState.update { current ->
            if (current.applying) current else current.copy(applying = true)
        }
        viewModelScope.launch {
            try {
                when (
                    val preparation = tankOperations.prepareRecommendation(
                        targetDeviceUid,
                        input,
                        plan
                    )
                ) {
                    is DeviceLightQuickSetupPersistenceResult.Failed -> {
                        if (targetDeviceUid == boundDeviceUid) {
                            _uiState.update { it.copy(applying = false) }
                            _effects.emit(
                                DeviceLightQuickSetupEffect.ShowMessage(
                                    R.string.device_light_quick_setup_profile_save_error
                                )
                            )
                        }
                    }
                    is DeviceLightQuickSetupPersistenceResult.Prepared -> applyPrepared(
                        deviceUid = targetDeviceUid,
                        plan = plan,
                        snapshot = snapshot,
                        auditId = preparation.auditId
                    )
                    DeviceLightQuickSetupPersistenceResult.Saved ->
                        error("Prepare must return an immutable audit id.")
                }
            } finally {
                applyInFlight.set(false)
                _uiState.update { current ->
                    if (current.applying) current.copy(applying = false) else current
                }
            }
        }
    }

    private suspend fun applyPrepared(
        deviceUid: String,
        plan: com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan,
        snapshot: DeviceLightManagedPlanSnapshot,
        auditId: String
    ) {
        when (
            val result = managedPlanOperations.apply(
                deviceUid = deviceUid,
                authority = snapshot.authority,
                draft = plan.toManagedPlanDraft()
            )
        ) {
            is DeviceLightManagedPlanMutationResult.Applied -> {
                val audit = recordRecommendationOutcomeReliably(
                    deviceUid = deviceUid,
                    auditId = auditId,
                    outcome = DeviceLightQuickSetupRecordedOutcome.Applied(result.snapshot)
                )
                if (deviceUid == boundDeviceUid) {
                    _uiState.update {
                        it.copy(
                            managedPlanSnapshot = result.snapshot,
                            editingInstalledPlan = false,
                            applying = false
                        )
                    }
                    _effects.emit(DeviceLightQuickSetupEffect.Applied)
                    if (audit is DeviceLightQuickSetupPersistenceResult.Failed) {
                        _effects.emit(
                            DeviceLightQuickSetupEffect.ShowMessage(
                                R.string.device_light_quick_setup_audit_update_error
                            )
                        )
                    }
                }
            }
            is DeviceLightManagedPlanMutationResult.Failed -> {
                recordRecommendationOutcomeReliably(
                    deviceUid = deviceUid,
                    auditId = auditId,
                    outcome = if (result.failure == DeviceLightManagedPlanFailure.UNAVAILABLE) {
                        DeviceLightQuickSetupRecordedOutcome.Indeterminate
                    } else {
                        DeviceLightQuickSetupRecordedOutcome.Failed
                    }
                )
                if (deviceUid == boundDeviceUid) {
                    handleMutationFailure(deviceUid, result.failure)
                }
            }
            DeviceLightManagedPlanMutationResult.Deleted ->
                error("Apply cannot return Deleted.")
        }
    }

    private suspend fun recordRecommendationOutcomeReliably(
        deviceUid: String,
        auditId: String,
        outcome: DeviceLightQuickSetupRecordedOutcome
    ): DeviceLightQuickSetupPersistenceResult {
        var result: DeviceLightQuickSetupPersistenceResult =
            DeviceLightQuickSetupPersistenceResult.Failed()
        repeat(AUDIT_WRITE_ATTEMPTS) {
            result = tankOperations.recordRecommendationOutcome(
                deviceUid = deviceUid,
                auditId = auditId,
                outcome = outcome
            )
            if (result == DeviceLightQuickSetupPersistenceResult.Saved) return result
        }
        return result
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = DeviceLightQuickSetupUiState(
                deviceUid = boundDeviceUid,
                initialLoading = true
            )
            when (val tankResult = tankOperations.readForDevice(boundDeviceUid)) {
                is DeviceLightQuickSetupTankReadResult.Failed ->
                    closeUnavailable(tankResult.failure)
                is DeviceLightQuickSetupTankReadResult.Available -> {
                    _uiState.update { state ->
                        state.copy(
                            tank = tankResult.tank,
                            todayEpochDay = tankResult.tank.deviceLocalEpochDay
                        )
                            .withStoredInputs(tankResult.tank, null)
                    }
                    loadAuthorityAndPlan()
                }
            }
        }
    }

    private suspend fun loadAuthorityAndPlan() {
        when (val result = managedPlanOperations.read(boundDeviceUid)) {
            is DeviceLightManagedPlanReadResult.Available -> {
                _uiState.update { state ->
                    val tank = requireNotNull(state.tank)
                    state.copy(
                        managedPlanSnapshot = result.snapshot,
                        plan = null,
                        editingInstalledPlan = false,
                        contentEnabled = true,
                        initialLoading = false,
                        applying = false
                    ).withStoredInputs(tank, result.snapshot)
                }
                if (!_uiState.value.hasInstalledPlan) recalculateAssessment()
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

    private suspend fun handleMutationFailure(
        deviceUid: String,
        failure: DeviceLightManagedPlanFailure
    ) {
        if (failure == DeviceLightManagedPlanFailure.STALE_AUTHORITY) {
            _uiState.update {
                it.copy(
                    managedPlanSnapshot = null,
                    contentEnabled = false,
                    applying = false
                )
            }
            when (val refreshed = managedPlanOperations.read(deviceUid)) {
                is DeviceLightManagedPlanReadResult.Available -> {
                    if (deviceUid != boundDeviceUid) return
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
                    if (deviceUid == boundDeviceUid) {
                        _effects.emit(
                            DeviceLightQuickSetupEffect.ShowMessage(
                                refreshed.failure.messageRes()
                            )
                        )
                    }
                }
            }
            if (deviceUid == boundDeviceUid) {
                _effects.emit(
                    DeviceLightQuickSetupEffect.ShowMessage(
                        R.string.device_light_quick_setup_stale_authority
                    )
                )
            }
        } else {
            _uiState.update { it.copy(applying = false) }
            _effects.emit(DeviceLightQuickSetupEffect.ShowMessage(failure.messageRes()))
        }
    }

    private fun updateInput(transform: DeviceLightQuickSetupUiState.() -> DeviceLightQuickSetupUiState) {
        _uiState.update { state -> state.transform().copy(detailsExpanded = false) }
        recalculateAssessment()
    }

    private fun recalculateAssessment() {
        val state = _uiState.value
        val tank = state.tank
        val input = state.toInputOrNull()
        if (tank == null || input == null || state.mode == DeviceLightQuickSetupMode.ACTIVE) {
            _uiState.update { it.copy(plan = null, detailsExpanded = false) }
            return
        }
        val plan = runCatching {
            DeviceLightQuickSetupCalculator.calculate(
                tank = tank,
                input = input,
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

private fun DeviceLightQuickSetupUiState.withStoredInputs(
    tank: DeviceLightQuickSetupTank,
    snapshot: DeviceLightManagedPlanSnapshot?
): DeviceLightQuickSetupUiState {
    val installedPlan = snapshot?.installed == true
    val installedEndMinute = snapshot?.phases?.firstOrNull()
        ?.endTimeMs
        ?.div(MINUTE_MS)
        ?.toInt()
    return copy(
        waterDepthCm = tank.waterDepthCm,
        fixtureHeightAboveWaterCm = tank.fixtureHeightAboveWaterCm,
        plantDemand = tank.plantDemand.takeUnless { it == DeviceLightPlantDemand.UNKNOWN },
        plantCoverage = tank.plantCoverage.takeUnless {
            it == AquariumPlantCoverage.UNKNOWN
        },
        co2Readiness = if (tank.co2ComponentPresent) {
            tank.co2Readiness
                .takeIf { installedPlan }
                ?.takeUnless { it == AquariumCo2Readiness.UNKNOWN }
        } else {
            AquariumCo2Readiness.NOT_INSTALLED
        },
        daylightExposure = tank.daylightExposure.takeUnless {
            it == AquariumDaylightExposure.UNKNOWN
        },
        daylightStartMinute = tank.daylightStartMinute,
        daylightEndMinute = tank.daylightEndMinute,
        surfaceGrowth = tank.surfaceGrowth
            .takeIf { installedPlan }
            ?.takeUnless { it == AquariumSurfaceGrowth.UNKNOWN },
        shelterAvailability = if (tank.hasShrimp) {
            tank.shelterAvailability.takeUnless {
                it == AquariumShelterAvailability.UNKNOWN ||
                    it == AquariumShelterAvailability.NOT_REQUIRED
            }
        } else {
            AquariumShelterAvailability.NOT_REQUIRED
        },
        programEndMinute = tank.preferredLightEndMinute ?: installedEndMinute
    )
}

private fun DeviceLightQuickSetupUiState.toInputOrNull(): DeviceLightQuickSetupInput? {
    val currentTank = tank ?: return null
    if (!assessmentComplete) return null
    return DeviceLightQuickSetupInput(
        plantDemand = requireNotNull(plantDemand),
        plantCoverage = requireNotNull(plantCoverage),
        waterDepthCm = requireNotNull(waterDepthCm),
        fixtureHeightAboveWaterCm = requireNotNull(fixtureHeightAboveWaterCm),
        co2Readiness = if (currentTank.co2ComponentPresent) {
            requireNotNull(co2Readiness)
        } else {
            AquariumCo2Readiness.NOT_INSTALLED
        },
        substrateSemantic = currentTank.substrateSemantic,
        daylightExposure = requireNotNull(daylightExposure),
        daylightStartMinute = daylightStartMinute,
        daylightEndMinute = daylightEndMinute,
        surfaceGrowth = requireNotNull(surfaceGrowth),
        shelterAvailability = if (currentTank.hasShrimp) {
            requireNotNull(shelterAvailability)
        } else {
            AquariumShelterAvailability.NOT_REQUIRED
        },
        programEndMinute = requireNotNull(programEndMinute)
    )
}

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
    DeviceLightQuickSetupTankFailure.DEVICE_IDENTITY_UNVERIFIED ->
        R.string.device_light_quick_setup_identity_unverified
    DeviceLightQuickSetupTankFailure.DEVICE_RUNTIME_UNVERIFIED ->
        R.string.device_light_quick_setup_runtime_unverified
    DeviceLightQuickSetupTankFailure.DEVICE_TIME_UNVERIFIED ->
        R.string.device_light_quick_setup_time_unverified
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

private const val MINUTES_PER_DAY = 1_440
private const val MINUTE_MS = 60_000L
private const val AUDIT_WRITE_ATTEMPTS = 3
