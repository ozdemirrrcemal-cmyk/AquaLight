package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceFeatureAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeMutationResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightSystemSummary
import com.aqua.aqualight.application.devices.light.dashboard.matchesLightControlSurface
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    private val rootOperations: DeviceRootOperations,
    private val lightControlOperations: DeviceLightControlOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations,
    private val featureAccessOperations: DeviceFeatureAccessOperations? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()
    private val modeChangeFailureEventChannel = Channel<DeviceLightControlFailure>(
        capacity = Channel.BUFFERED
    )
    val modeChangeFailureEvents: Flow<DeviceLightControlFailure> =
        modeChangeFailureEventChannel.receiveAsFlow()
    private val quickSetupAccessEventChannel = Channel<DeviceAccessDecision>(Channel.BUFFERED)
    val quickSetupAccessEvents: Flow<DeviceAccessDecision> =
        quickSetupAccessEventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var currentControlSnapshot: DeviceLightControlSnapshot? = null
    private var pendingMode: DeviceLightControlMode? = null
    private var committedMode: DeviceLightControlMode? = null
    private var surfacePreparationPending = false
    private var rootObserveJob: Job? = null
    private var controlObserveJob: Job? = null
    private var surfacePreparationJob: Job? = null
    private var modeChangeJob: Job? = null
    private var quickSetupAccessJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) {
            renderBoundState()
            return
        }

        val preparedHandoff = controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.LIGHT
        )
        cancelJobs()
        boundDeviceUid = deviceUid
        currentControlSnapshot = null
        pendingMode = null
        committedMode = null
        latestRootSnapshot = rootOperations.current(deviceUid)
        acceptControlResult(lightControlOperations.currentControl(deviceUid))
        val preparedSurfaceStillCurrent = preparedHandoff &&
            currentControlSnapshot.matchesLightControlSurface(
                deviceUid,
                latestRootSnapshot
            )
        surfacePreparationPending = !preparedSurfaceStillCurrent
        renderBoundState()

        rootOperations.connect(deviceUid)
        rootObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rootOperations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        controlObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            lightControlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                acceptControlResult(result)
                renderBoundState()
            }
        }
        if (surfacePreparationPending) prepareRestoredSurface(deviceUid)
    }

    private fun prepareRestoredSurface(deviceUid: String) {
        if (surfacePreparationJob?.isActive == true) return
        surfacePreparationPending = true
        renderBoundState()
        surfacePreparationJob = viewModelScope.launch {
            val result = runCatching {
                controlSurfacePreparationOperations.prepare(
                    DeviceControlSurfacePreparationRequest(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.LIGHT
                    )
                )
            }.getOrElse {
                DeviceControlSurfacePreparationResult.Unavailable(
                    DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
                )
            }
            if (boundDeviceUid != deviceUid) return@launch

            when (result) {
                DeviceControlSurfacePreparationResult.Ready -> finishReadyPreparation(deviceUid)
                is DeviceControlSurfacePreparationResult.Unavailable ->
                    finishUnavailablePreparation(result.reason)
            }
        }
    }

    private suspend fun finishReadyPreparation(deviceUid: String) {
        controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.LIGHT
        )
        latestRootSnapshot = rootOperations.current(deviceUid)
        val current = lightControlOperations.currentControl(deviceUid)
        acceptControlResult(current)
        when (current) {
            is DeviceLightControlResult.Failed -> finishUnavailablePreparation(
                DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
            )
            is DeviceLightControlResult.Available -> {
                if (current.snapshot.matchesLightControlSurface(deviceUid, latestRootSnapshot)) {
                    surfacePreparationPending = false
                    renderBoundState()
                } else {
                    finishUnavailablePreparation(
                        DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
                    )
                }
            }
        }
    }

    private suspend fun finishUnavailablePreparation(reason: DeviceMenuUnavailableReason) {
        surfacePreparationPending = false
        renderBoundState()
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun acceptControlResult(result: DeviceLightControlResult) {
        if (result is DeviceLightControlResult.Available) {
            val previousMode = currentControlSnapshot?.hero?.mode
            currentControlSnapshot = result.snapshot
            if (
                committedMode != null &&
                result.snapshot.hero.mode != previousMode
            ) {
                committedMode = null
            }
        }
    }

    private fun renderBoundState() {
        val root = latestRootSnapshot
        val controlAvailable = currentControlSnapshot.matchesLightControlSurface(
            boundDeviceUid,
            root
        )
        val rootAvailable = root.isLightControlRootAvailable(boundDeviceUid)
        val surfaceAvailable = rootAvailable && controlAvailable
        _uiState.value = DeviceLightRootUiState(
            title = root?.title.orEmpty(),
            deviceUid = boundDeviceUid,
            connectionVisualState = root.toDeviceConnectionVisualState(),
            contentEnabled = surfaceAvailable && !surfacePreparationPending,
            // Match Dosing: a refresh blocks only a cold surface. A validated frame already on
            // screen remains visible until its complete replacement is atomically published.
            showBlockingPreparation = surfacePreparationPending && !controlAvailable,
            activeAutomaticProgramId = currentControlSnapshot?.activeAutomaticProgramId,
            hero = currentControlSnapshot?.hero ?: DeviceLightHeroSnapshot(),
            selectedMode = pendingMode ?: committedMode ?: currentControlSnapshot?.hero?.mode,
            adaptation = currentControlSnapshot?.adaptation ?: DeviceLightAdaptationSummary(),
            systemSupported = currentControlSnapshot?.systemSupported == true,
            system = currentControlSnapshot?.system,
            channels = currentControlSnapshot?.channels.orEmpty(),
            plan = currentControlSnapshot?.plan,
            automaticProgramCount = currentControlSnapshot?.automaticProgramCount,
            customCurvePointCount = currentControlSnapshot?.customCurvePointCount
        )
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        latestRootSnapshot = null
        currentControlSnapshot = null
        pendingMode = null
        committedMode = null
        surfacePreparationPending = false
        modeChangeJob = null
        _uiState.value = DeviceLightRootUiState()
    }

    fun requestQuickSetupAccess() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank() || !_uiState.value.contentEnabled) return
        if (quickSetupAccessJob?.isActive == true) return

        val featureAccess = featureAccessOperations
        if (featureAccess == null) {
            quickSetupAccessEventChannel.trySend(DeviceAccessDecision.Allowed)
            return
        }

        quickSetupAccessJob = viewModelScope.launch {
            quickSetupAccessEventChannel.send(
                featureAccess.resolve(
                    deviceUid = deviceUid,
                    feature = DeviceRootMenuFeature.LIGHT_QUICK_SETUP
                )
            )
        }
    }

    fun setMode(mode: DeviceLightControlMode) {
        val deviceUid = boundDeviceUid
        val state = _uiState.value
        if (deviceUid.isBlank() || !state.contentEnabled) return
        if (state.selectedMode == mode || modeChangeJob?.isActive == true) return

        pendingMode = mode
        renderBoundState()
        modeChangeJob = viewModelScope.launch {
            when (val result = lightControlOperations.setMode(deviceUid, mode)) {
                is DeviceLightModeMutationResult.Reconciled -> {
                    if (boundDeviceUid == deviceUid) {
                        pendingMode = null
                        committedMode = null
                        acceptControlResult(DeviceLightControlResult.Available(result.snapshot))
                        renderBoundState()
                    }
                }
                is DeviceLightModeMutationResult.Committed -> {
                    if (boundDeviceUid == deviceUid) {
                        pendingMode = null
                        committedMode = result.mode
                        renderBoundState()
                    }
                }
                is DeviceLightModeMutationResult.Failed -> if (boundDeviceUid == deviceUid) {
                    pendingMode = null
                    renderBoundState()
                    modeChangeFailureEventChannel.send(result.failure)
                }
            }
        }
    }

    private fun cancelJobs() {
        rootObserveJob?.cancel()
        controlObserveJob?.cancel()
        surfacePreparationJob?.cancel()
        modeChangeJob?.cancel()
        quickSetupAccessJob?.cancel()
        rootObserveJob = null
        controlObserveJob = null
        surfacePreparationJob = null
        modeChangeJob = null
        quickSetupAccessJob = null
    }
}

data class DeviceLightRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false,
    val activeAutomaticProgramId: String? = null,
    val hero: DeviceLightHeroSnapshot = DeviceLightHeroSnapshot(),
    val selectedMode: DeviceLightControlMode? = null,
    val adaptation: DeviceLightAdaptationSummary = DeviceLightAdaptationSummary(),
    val systemSupported: Boolean = false,
    val system: DeviceLightSystemSummary? = null,
    val channels: List<DeviceLightChannelOutputSnapshot> = emptyList(),
    val plan: DeviceLightPlanSnapshot? = null,
    val automaticProgramCount: Int? = null,
    val customCurvePointCount: Int? = null
)

private fun DeviceRootSnapshot?.isLightControlRootAvailable(deviceUid: String): Boolean = when {
    this == null -> false
    this.deviceUid != deviceUid -> false
    availability != OwnerDeviceAvailability.REACHABLE -> false
    catalogState != DeviceRootCatalogState.VALID -> false
    else -> family == OwnerDeviceFamily.LIGHT
}
