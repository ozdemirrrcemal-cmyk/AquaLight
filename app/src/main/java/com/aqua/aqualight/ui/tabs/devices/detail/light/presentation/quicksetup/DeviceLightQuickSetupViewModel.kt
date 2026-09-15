package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupOperations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupReadResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("MagicNumber", "ReturnCount", "TooManyFunctions")
internal class DeviceLightQuickSetupViewModel(
    private val operations: SmartSetupOperations,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightQuickSetupUiState())
    val uiState: StateFlow<DeviceLightQuickSetupUiState> = _uiState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<DeviceLightQuickSetupEffect>(
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightQuickSetupEffect> = mutableEffects.asSharedFlow()

    private var boundDeviceUid = ""
    private var loadJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Quick Setup destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        loadJob?.cancel()
        boundDeviceUid = deviceUid
        _uiState.value = DeviceLightQuickSetupUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        load(showLoading = true, preserveApplied = false)
    }

    fun refresh() {
        if (boundDeviceUid.isBlank() || _uiState.value.operationInProgress) return
        load(showLoading = _uiState.value.snapshot == null, preserveApplied = true)
    }

    fun startEditing() {
        if (_uiState.value.snapshot == null || _uiState.value.operationInProgress) return
        _uiState.update { state -> state.copy(editMode = true) }
    }

    fun cancelEditing() {
        val snapshot = _uiState.value.snapshot ?: return
        clearDraftState()
        _uiState.update { state ->
            state.copy(
                editor = DeviceLightQuickSetupEditor.from(snapshot.input),
                editMode = false,
                draftDirty = false
            )
        }
    }

    fun selectAquariumAge(days: Int) {
        val evaluation = _uiState.value.snapshot?.input?.evaluationEpochDay ?: return
        if (days <= 0 || days.toLong() > evaluation - MIN_EPOCH_DAY + 1L) return
        updateEditor { editor ->
            editor.copy(setupDateEpochDay = evaluation - days + 1L)
        }
    }

    fun adjustAquariumAge(deltaDays: Int) {
        val state = _uiState.value
        val current = state.editor.aquariumAgeDays(state.snapshot?.input?.evaluationEpochDay)
            ?: return
        selectAquariumAge((current + deltaDays).coerceAtLeast(1))
    }

    fun selectPlantDensity(value: PlantDensity) = updateEditor { editor ->
        editor.copy(plantDensity = value)
    }

    fun selectPlantLightDemand(value: PlantLightDemand) = updateEditor { editor ->
        editor.copy(highestPlantLightDemand = value)
    }

    fun selectCo2Status(value: Co2Status) = updateEditor { editor ->
        editor.copy(co2Status = value)
    }

    fun selectActiveSoil(value: Boolean) = updateEditor { editor ->
        editor.copy(isActiveSoil = value)
    }

    fun selectWaterDepth(value: Int) = updateEditor { editor ->
        editor.copy(waterDepthCm = value.takeIf { it in 5..200 })
    }

    fun adjustWaterDepth(deltaCm: Int) {
        val current = _uiState.value.editor.waterDepthCm ?: return
        selectWaterDepth((current + deltaCm).coerceIn(5, 200))
    }

    fun selectMountHeight(value: Int) = updateEditor { editor ->
        editor.copy(fixtureMountHeightCm = value.takeIf { it in 0..200 })
    }

    fun adjustMountHeight(deltaCm: Int) {
        val current = _uiState.value.editor.fixtureMountHeightCm ?: return
        selectMountHeight((current + deltaCm).coerceIn(0, 200))
    }

    fun selectViewingWindow(startMinute: Int, endMinute: Int) {
        if (startMinute !in MINUTE_RANGE || endMinute !in MINUTE_RANGE) return
        if (endMinute - startMinute < MINIMUM_VIEWING_MINUTES) return
        updateEditor { editor ->
            editor.copy(
                preferredViewingStartMinuteOfDay = startMinute,
                preferredViewingEndMinuteOfDay = endMinute
            )
        }
    }

    fun adjustViewingStart(deltaMinutes: Int) {
        val editor = _uiState.value.editor
        val start = editor.preferredViewingStartMinuteOfDay ?: return
        val end = editor.preferredViewingEndMinuteOfDay ?: return
        selectViewingWindow(
            (start + deltaMinutes).coerceIn(0, end - MINIMUM_VIEWING_MINUTES),
            end
        )
    }

    fun adjustViewingEnd(deltaMinutes: Int) {
        val editor = _uiState.value.editor
        val start = editor.preferredViewingStartMinuteOfDay ?: return
        val end = editor.preferredViewingEndMinuteOfDay ?: return
        selectViewingWindow(
            start,
            (end + deltaMinutes).coerceIn(
                start + MINIMUM_VIEWING_MINUTES,
                MINUTE_RANGE.last
            )
        )
    }

    fun selectAlgaeObservation(value: AquariumObservationSeverity) = updateEditor { editor ->
        editor.copy(algaeObservation = value)
    }

    fun selectPlantStressObservation(value: AquariumObservationSeverity) =
        updateEditor { editor -> editor.copy(plantStressObservation = value) }

    fun recordObservationsToday() {
        val evaluation = _uiState.value.snapshot?.input?.evaluationEpochDay ?: return
        updateEditor { editor -> editor.copy(observationDateEpochDay = evaluation) }
    }

    fun saveProfile() {
        val state = _uiState.value
        val setupDate = state.editor.setupDateEpochDay ?: return
        val profile = state.editor.toLightingProfileOrNull(state.isPlanted) ?: return
        if (!state.canSave) return
        _uiState.update { current -> current.copy(operationInProgress = true) }
        viewModelScope.launch {
            val result = try {
                operations.saveProfile(boundDeviceUid, setupDate, profile)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SmartSetupProfileSaveResult.Failed(SmartSetupProfileSaveFailure.UNAVAILABLE)
            }
            when (result) {
                is SmartSetupProfileSaveResult.Saved -> {
                    clearDraftState()
                    applySnapshot(
                        snapshot = result.snapshot,
                        preserveApplied = false,
                        preserveDraft = false
                    )
                    emitMessage(R.string.device_light_smart_setup_profile_saved, success = true)
                }
                is SmartSetupProfileSaveResult.Failed -> {
                    _uiState.update { current -> current.copy(operationInProgress = false) }
                    emitMessage(result.failure.messageRes(), success = false)
                }
            }
        }
    }

    fun applyPlan() {
        val state = _uiState.value
        val ready = state.decision as? SmartSetupDecision.Ready ?: return
        if (!state.canApply) return
        _uiState.update { current -> current.copy(operationInProgress = true) }
        viewModelScope.launch {
            val result = try {
                operations.apply(
                    deviceUid = boundDeviceUid,
                    expectedProfileFingerprint = ready.recommendation.profileFingerprint
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SmartSetupApplyResult.Failed(SmartSetupApplyFailure.UNAVAILABLE)
            }
            when (result) {
                is SmartSetupApplyResult.Applied -> {
                    _uiState.update { current ->
                        current.copy(
                            operationInProgress = false,
                            appliedResult = result
                        )
                    }
                    emitMessage(R.string.device_light_smart_setup_applied, success = true)
                    load(showLoading = false, preserveApplied = true)
                }
                is SmartSetupApplyResult.Failed -> handleApplyFailure(result.failure)
            }
        }
    }

    private fun load(showLoading: Boolean, preserveApplied: Boolean) {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showLoading) {
                _uiState.update { state -> state.copy(initialLoading = true, loadFailure = null) }
            }
            try {
                val result = operations.read(deviceUid)
                if (boundDeviceUid != deviceUid) return@launch
                when (result) {
                    is SmartSetupReadResult.Available ->
                        applySnapshot(result.snapshot, preserveApplied)
                    else -> applyReadFailure(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (boundDeviceUid == deviceUid) {
                    applyReadFailure(SmartSetupReadResult.Unavailable)
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: SmartSetupSnapshot,
        preserveApplied: Boolean,
        preserveDraft: Boolean = true
    ) {
        _uiState.update { state ->
            val restored = restoredEditor(snapshot).takeIf { preserveDraft }
            val keepCurrent = preserveDraft &&
                state.draftDirty &&
                state.deviceUid == snapshot.deviceUid
            state.copy(
                deviceUid = snapshot.deviceUid,
                connectionVisualState = DeviceConnectionVisualState.ONLINE,
                snapshot = snapshot,
                editor = when {
                    keepCurrent -> state.editor
                    restored != null -> restored
                    else -> DeviceLightQuickSetupEditor.from(snapshot.input)
                },
                editMode = when {
                    keepCurrent || restored != null -> true
                    snapshot.decision is SmartSetupDecision.MissingData -> true
                    else -> false
                },
                draftDirty = keepCurrent || restored != null,
                initialLoading = false,
                operationInProgress = false,
                loadFailure = null,
                appliedResult = state.appliedResult.takeIf { preserveApplied }
            )
        }
    }

    private fun applyReadFailure(result: SmartSetupReadResult) {
        val failure = when (result) {
            SmartSetupReadResult.DeviceNotAssigned ->
                DeviceLightQuickSetupLoadFailure.DEVICE_NOT_ASSIGNED
            SmartSetupReadResult.AquariumNotFound ->
                DeviceLightQuickSetupLoadFailure.AQUARIUM_NOT_FOUND
            SmartSetupReadResult.NotConnected -> DeviceLightQuickSetupLoadFailure.NOT_CONNECTED
            SmartSetupReadResult.InvalidDevice -> DeviceLightQuickSetupLoadFailure.INVALID_DEVICE
            SmartSetupReadResult.InvalidFirmwareData ->
                DeviceLightQuickSetupLoadFailure.INVALID_FIRMWARE_DATA
            SmartSetupReadResult.Unavailable -> DeviceLightQuickSetupLoadFailure.UNAVAILABLE
            is SmartSetupReadResult.Available -> error("Available is not a read failure.")
        }
        _uiState.update { state ->
            state.copy(
                connectionVisualState = if (failure == DeviceLightQuickSetupLoadFailure.NOT_CONNECTED) {
                    DeviceConnectionVisualState.OFFLINE
                } else {
                    DeviceConnectionVisualState.WARNING
                },
                initialLoading = false,
                operationInProgress = false,
                loadFailure = failure
            )
        }
    }

    private fun handleApplyFailure(failure: SmartSetupApplyFailure) {
        _uiState.update { state -> state.copy(operationInProgress = false) }
        emitMessage(failure.messageRes(), success = false)
        if (failure == SmartSetupApplyFailure.STALE_AUTHORITY ||
            failure == SmartSetupApplyFailure.PREVIEW_CHANGED
        ) {
            load(showLoading = false, preserveApplied = false)
        }
    }

    private fun updateEditor(transform: (DeviceLightQuickSetupEditor) -> DeviceLightQuickSetupEditor) {
        if (_uiState.value.operationInProgress || _uiState.value.snapshot == null) return
        _uiState.update { state ->
            state.copy(
                editor = transform(state.editor),
                editMode = true,
                draftDirty = true,
                appliedResult = null
            )
        }
        persistDraftState()
    }

    private fun restoredEditor(snapshot: SmartSetupSnapshot): DeviceLightQuickSetupEditor? {
        if (savedStateHandle.get<String>(KEY_DEVICE_UID) != snapshot.deviceUid ||
            savedStateHandle.get<Boolean>(KEY_DIRTY) != true
        ) {
            return null
        }
        return DeviceLightQuickSetupEditor(
            setupDateEpochDay = savedStateHandle[KEY_SETUP_DATE],
            plantDensity = savedEnum(KEY_PLANT_DENSITY, PlantDensity.entries),
            highestPlantLightDemand = savedEnum(KEY_PLANT_DEMAND, PlantLightDemand.entries),
            co2Status = savedEnum(KEY_CO2, Co2Status.entries),
            isActiveSoil = savedStateHandle[KEY_ACTIVE_SOIL],
            waterDepthCm = savedStateHandle[KEY_WATER_DEPTH],
            fixtureMountHeightCm = savedStateHandle[KEY_MOUNT_HEIGHT],
            preferredViewingStartMinuteOfDay = savedStateHandle[KEY_VIEW_START],
            preferredViewingEndMinuteOfDay = savedStateHandle[KEY_VIEW_END],
            algaeObservation = savedEnum(KEY_ALGAE, AquariumObservationSeverity.entries),
            plantStressObservation = savedEnum(
                KEY_PLANT_STRESS,
                AquariumObservationSeverity.entries
            ),
            observationDateEpochDay = savedStateHandle[KEY_OBSERVATION_DATE]
        )
    }

    private fun persistDraftState() {
        val state = _uiState.value
        val editor = state.editor
        savedStateHandle[KEY_DEVICE_UID] = state.deviceUid
        savedStateHandle[KEY_DIRTY] = state.draftDirty
        savedStateHandle[KEY_SETUP_DATE] = editor.setupDateEpochDay
        savedStateHandle[KEY_PLANT_DENSITY] = editor.plantDensity?.name
        savedStateHandle[KEY_PLANT_DEMAND] = editor.highestPlantLightDemand?.name
        savedStateHandle[KEY_CO2] = editor.co2Status?.name
        savedStateHandle[KEY_ACTIVE_SOIL] = editor.isActiveSoil
        savedStateHandle[KEY_WATER_DEPTH] = editor.waterDepthCm
        savedStateHandle[KEY_MOUNT_HEIGHT] = editor.fixtureMountHeightCm
        savedStateHandle[KEY_VIEW_START] = editor.preferredViewingStartMinuteOfDay
        savedStateHandle[KEY_VIEW_END] = editor.preferredViewingEndMinuteOfDay
        savedStateHandle[KEY_ALGAE] = editor.algaeObservation?.name
        savedStateHandle[KEY_PLANT_STRESS] = editor.plantStressObservation?.name
        savedStateHandle[KEY_OBSERVATION_DATE] = editor.observationDateEpochDay
    }

    private fun clearDraftState() {
        SAVED_KEYS.forEach { key -> savedStateHandle.remove<Any?>(key) }
    }

    private fun <T : Enum<T>> savedEnum(key: String, entries: Iterable<T>): T? {
        val name = savedStateHandle.get<String>(key) ?: return null
        return entries.singleOrNull { entry -> entry.name == name }
    }

    private fun emitMessage(@StringRes messageRes: Int, success: Boolean) {
        mutableEffects.tryEmit(DeviceLightQuickSetupEffect.ShowMessage(messageRes, success))
    }

    private companion object {
        const val MIN_EPOCH_DAY = 10_957L
        val MINUTE_RANGE = 0 until 24 * 60
        const val MINIMUM_VIEWING_MINUTES = 60
        const val KEY_DEVICE_UID = "smartSetup.deviceUid"
        const val KEY_DIRTY = "smartSetup.dirty"
        const val KEY_SETUP_DATE = "smartSetup.setupDate"
        const val KEY_PLANT_DENSITY = "smartSetup.plantDensity"
        const val KEY_PLANT_DEMAND = "smartSetup.plantDemand"
        const val KEY_CO2 = "smartSetup.co2"
        const val KEY_ACTIVE_SOIL = "smartSetup.activeSoil"
        const val KEY_WATER_DEPTH = "smartSetup.waterDepth"
        const val KEY_MOUNT_HEIGHT = "smartSetup.mountHeight"
        const val KEY_VIEW_START = "smartSetup.viewStart"
        const val KEY_VIEW_END = "smartSetup.viewEnd"
        const val KEY_ALGAE = "smartSetup.algae"
        const val KEY_PLANT_STRESS = "smartSetup.plantStress"
        const val KEY_OBSERVATION_DATE = "smartSetup.observationDate"
        val SAVED_KEYS = listOf(
            KEY_DEVICE_UID,
            KEY_DIRTY,
            KEY_SETUP_DATE,
            KEY_PLANT_DENSITY,
            KEY_PLANT_DEMAND,
            KEY_CO2,
            KEY_ACTIVE_SOIL,
            KEY_WATER_DEPTH,
            KEY_MOUNT_HEIGHT,
            KEY_VIEW_START,
            KEY_VIEW_END,
            KEY_ALGAE,
            KEY_PLANT_STRESS,
            KEY_OBSERVATION_DATE
        )
    }
}

internal sealed interface DeviceLightQuickSetupEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val success: Boolean
    ) : DeviceLightQuickSetupEffect
}

@StringRes
private fun SmartSetupProfileSaveFailure.messageRes(): Int = when (this) {
    SmartSetupProfileSaveFailure.DEVICE_NOT_ASSIGNED ->
        R.string.device_light_smart_setup_assignment_error
    SmartSetupProfileSaveFailure.AQUARIUM_NOT_FOUND ->
        R.string.device_light_smart_setup_aquarium_error
    SmartSetupProfileSaveFailure.INVALID_PROFILE ->
        R.string.device_light_smart_setup_invalid_profile_error
    SmartSetupProfileSaveFailure.UNAVAILABLE ->
        R.string.device_light_smart_setup_operation_error
}

@StringRes
private fun SmartSetupApplyFailure.messageRes(): Int = when (this) {
    SmartSetupApplyFailure.NOT_READY -> R.string.device_light_smart_setup_not_ready_error
    SmartSetupApplyFailure.PREVIEW_CHANGED ->
        R.string.device_light_smart_setup_preview_changed_error
    SmartSetupApplyFailure.NOT_CONNECTED ->
        R.string.device_light_smart_setup_not_connected_error
    SmartSetupApplyFailure.UNSUPPORTED -> R.string.device_light_smart_setup_unsupported_error
    SmartSetupApplyFailure.STALE_AUTHORITY -> R.string.device_light_smart_setup_stale_error
    SmartSetupApplyFailure.REJECTED -> R.string.device_light_smart_setup_rejected_error
    SmartSetupApplyFailure.INVALID_FIRMWARE_DATA ->
        R.string.device_light_smart_setup_firmware_error
    SmartSetupApplyFailure.COMMIT_UNCONFIRMED ->
        R.string.device_light_smart_setup_unconfirmed_error
    SmartSetupApplyFailure.UNAVAILABLE -> R.string.device_light_smart_setup_operation_error
}
