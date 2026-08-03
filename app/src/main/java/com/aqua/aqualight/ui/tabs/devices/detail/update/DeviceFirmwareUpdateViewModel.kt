package com.aqua.aqualight.ui.tabs.devices.detail.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Presentation owner for the commercial, full-screen OTA experience. */
class DeviceFirmwareUpdateViewModel(
    private val rootOperations: DeviceRootOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations,
    private val manifestUrl: String = DEVICE_FIRMWARE_MANIFEST_URL
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFirmwareUpdateUiState())
    val uiState: StateFlow<DeviceFirmwareUpdateUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var deviceObservationJob: Job? = null
    private var firmwareObservationJob: Job? = null
    private var operationJob: Job? = null
    private val stateMapper = DeviceFirmwareUpdateStateMapper()

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Software update requires a non-blank device UID." }
        if (boundDeviceUid == deviceUid) return

        cancelBoundJobs()
        boundDeviceUid = deviceUid
        stateMapper.reset()
        _uiState.value = DeviceFirmwareUpdateUiState(deviceUid = deviceUid)

        rootOperations.current(deviceUid)?.let(::applyDeviceSnapshot)
        rootOperations.connect(deviceUid)
        deviceObservationJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                snapshot?.let(::applyDeviceSnapshot)
            }
        }

        val otaStates = firmwareUpdateOperations.observe(deviceUid)
        applyOtaState(otaStates.value)
        firmwareObservationJob = viewModelScope.launch {
            otaStates.collect(::applyOtaState)
        }
        if (otaStates.value is DeviceOtaState.Idle) refreshAndRecover()
    }

    fun installUpdate() {
        val plan = stateMapper.selectedPlan ?: return
        if (
            _uiState.value.mode != DeviceFirmwareUpdateMode.AVAILABLE ||
            operationJob?.isActive == true
        ) return

        operationJob = viewModelScope.launch {
            val result = firmwareUpdateOperations.startUpdate(plan)
            if (!result.isSuccess && _uiState.value.mode == DeviceFirmwareUpdateMode.AVAILABLE) {
                result.failure?.let(::publishLocalFailure)
            }
        }
    }

    fun retry() {
        if (operationJob?.isActive == true) return
        refreshAndRecover()
    }

    fun refreshActiveStatus() {
        if (!_uiState.value.mode.isActive || operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            firmwareUpdateOperations.requestStatus(boundDeviceUid)
        }
    }

    private fun refreshAndRecover() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank() || operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            val availability = firmwareUpdateOperations.checkAvailability(
                deviceUid = deviceUid,
                manifestUrl = manifestUrl,
                applyNow = true
            ).getOrNull()
            if (availability is DeviceOtaState.UpdateAvailable) {
                firmwareUpdateOperations.requestStatus(deviceUid)
            }
        }
    }

    private fun applyDeviceSnapshot(snapshot: DeviceRootSnapshot) {
        if (snapshot.deviceUid != boundDeviceUid) return
        _uiState.update { current ->
            current.copy(
                deviceName = snapshot.title.ifBlank { current.deviceName },
                currentVersion = current.currentVersion.ifBlank { snapshot.firmwareLabel }
            )
        }
    }

    private fun applyOtaState(state: DeviceOtaState) {
        if (state.deviceUid != boundDeviceUid) return
        _uiState.update { current -> stateMapper.map(state, current) }
    }

    private fun publishLocalFailure(failure: DeviceOtaFailure) {
        _uiState.update { current ->
            current.copy(
                mode = DeviceFirmwareUpdateMode.FAILED,
                failure = failure
            )
        }
    }

    private fun cancelBoundJobs() {
        deviceObservationJob?.cancel()
        firmwareObservationJob?.cancel()
        operationJob?.cancel()
    }

    override fun onCleared() {
        cancelBoundJobs()
        super.onCleared()
    }
}

private class DeviceFirmwareUpdateStateMapper {
    var selectedPlan: PreparedDeviceFirmwareUpdate? = null
        private set

    private var retainedReleaseContent = DeviceFirmwareReleaseContent.EMPTY

    fun reset() {
        selectedPlan = null
        retainedReleaseContent = DeviceFirmwareReleaseContent.EMPTY
    }

    fun map(
        state: DeviceOtaState,
        current: DeviceFirmwareUpdateUiState
    ): DeviceFirmwareUpdateUiState {
        retainPlanAndContent(state)
        val common = current.copy(
            deviceUid = state.deviceUid,
            releaseContent = retainedReleaseContent,
            failure = null
        )
        return when (state) {
            is DeviceOtaState.Idle -> common.copy(mode = DeviceFirmwareUpdateMode.LOADING)
            is DeviceOtaState.Checking -> common.copy(
                mode = DeviceFirmwareUpdateMode.CHECKING,
                currentVersion = state.currentVersion.ifBlank { common.currentVersion }
            )
            is DeviceOtaState.Unsupported -> common.copy(
                mode = DeviceFirmwareUpdateMode.UNSUPPORTED,
                failure = DeviceOtaFailure(
                    reason = DeviceOtaFailureReason.UNSUPPORTED,
                    recoverable = false
                )
            )
            is DeviceOtaState.UpToDate -> common.copy(
                mode = DeviceFirmwareUpdateMode.UP_TO_DATE,
                currentVersion = state.currentVersion,
                targetVersion = state.latestVersion,
                progressPermille = COMPLETE_PROGRESS_PERMILLE
            )
            is DeviceOtaState.UpdateAvailable -> common.withPlan(
                plan = state.plan,
                mode = DeviceFirmwareUpdateMode.AVAILABLE
            )
            is DeviceOtaState.Starting -> common.withPlan(
                plan = state.plan,
                mode = DeviceFirmwareUpdateMode.STARTING
            )
            else -> mapExecutionState(state, common)
        }
    }

    private fun mapExecutionState(
        state: DeviceOtaState,
        common: DeviceFirmwareUpdateUiState
    ): DeviceFirmwareUpdateUiState = when (state) {
        is DeviceOtaState.InProgress -> common.copy(
            mode = DeviceFirmwareUpdateMode.IN_PROGRESS,
            targetVersion = state.targetVersion,
            phase = state.phase,
            progressPermille = state.progressPermille,
            bytesWritten = state.bytesWritten,
            contentLength = state.contentLength
        )
        is DeviceOtaState.Recovering -> common.copy(
            mode = DeviceFirmwareUpdateMode.RECOVERING,
            targetVersion = state.targetVersion,
            phase = null,
            progressPermille = state.progressPermille
        )
        is DeviceOtaState.RestartRequired -> common.copy(
            mode = DeviceFirmwareUpdateMode.RESTARTING,
            targetVersion = state.targetVersion,
            phase = null,
            progressPermille = COMPLETE_PROGRESS_PERMILLE
        )
        is DeviceOtaState.Succeeded -> common.copy(
            mode = DeviceFirmwareUpdateMode.SUCCEEDED,
            currentVersion = state.targetVersion,
            targetVersion = state.targetVersion,
            phase = null,
            progressPermille = COMPLETE_PROGRESS_PERMILLE
        )
        is DeviceOtaState.Failed -> common.copy(
            mode = DeviceFirmwareUpdateMode.FAILED,
            phase = null,
            failure = state.failure
        )
        else -> error("OTA execution state mapping is incomplete: ${state::class.simpleName}.")
    }

    private fun retainPlanAndContent(state: DeviceOtaState) {
        when (state) {
            is DeviceOtaState.UpdateAvailable -> selectedPlan = state.plan
            is DeviceOtaState.Starting -> selectedPlan = state.plan
            else -> Unit
        }
        val content = when (state) {
            is DeviceOtaState.UpToDate -> state.releaseContent
            is DeviceOtaState.UpdateAvailable -> state.plan.releaseContent
            is DeviceOtaState.Starting -> state.plan.releaseContent
            is DeviceOtaState.InProgress -> state.releaseContent
            is DeviceOtaState.RestartRequired -> state.releaseContent
            is DeviceOtaState.Succeeded -> state.releaseContent
            else -> DeviceFirmwareReleaseContent.EMPTY
        }
        if (content.isPresent) retainedReleaseContent = content
    }

    private fun DeviceFirmwareUpdateUiState.withPlan(
        plan: PreparedDeviceFirmwareUpdate,
        mode: DeviceFirmwareUpdateMode
    ): DeviceFirmwareUpdateUiState = copy(
        mode = mode,
        deviceName = plan.displayName.ifBlank { deviceName },
        currentVersion = plan.currentVersion,
        targetVersion = plan.targetVersion,
        releaseContent = plan.releaseContent,
        phase = if (mode == DeviceFirmwareUpdateMode.STARTING) {
            DeviceOtaProgressPhase.STARTING
        } else {
            null
        },
        progressPermille = 0,
        bytesWritten = 0L,
        contentLength = plan.sizeBytes.toLong()
    )
}

enum class DeviceFirmwareUpdateMode {
    LOADING,
    CHECKING,
    AVAILABLE,
    STARTING,
    IN_PROGRESS,
    RECOVERING,
    RESTARTING,
    SUCCEEDED,
    UP_TO_DATE,
    FAILED,
    UNSUPPORTED;

    val isActive: Boolean
        get() = this == STARTING ||
            this == IN_PROGRESS ||
            this == RECOVERING ||
            this == RESTARTING
}

data class DeviceFirmwareUpdateUiState(
    val deviceUid: String = "",
    val mode: DeviceFirmwareUpdateMode = DeviceFirmwareUpdateMode.LOADING,
    val deviceName: String = "",
    val currentVersion: String = "",
    val targetVersion: String = "",
    val releaseContent: DeviceFirmwareReleaseContent = DeviceFirmwareReleaseContent.EMPTY,
    val phase: DeviceOtaProgressPhase? = null,
    val progressPermille: Int = 0,
    val bytesWritten: Long = 0L,
    val contentLength: Long = 0L,
    val failure: DeviceOtaFailure? = null
) {
    val progressPercent: Int
        get() = progressPermille.coerceIn(
            0,
            COMPLETE_PROGRESS_PERMILLE
        ) / PERMILLE_PER_PERCENT
}

private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
