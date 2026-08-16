package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityValidation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DeviceDosingReservoirDraft(
    val reservoirCapacityMicroliters: Long =
        DeviceDosingReservoirCapacityPolicy.DEFAULT_CAPACITY_MICROLITERS,
    val trackingEnabled: Boolean = false,
    val lowLevelAlertEnabled: Boolean = false
)

internal enum class DeviceDosingReservoirNotificationAvailability {
    AVAILABLE,
    OWNER_PREFERENCE_DISABLED,
    ANDROID_BLOCKED
}

internal data class DeviceDosingReservoirEditorState(
    val draft: DeviceDosingReservoirDraft = DeviceDosingReservoirDraft(),
    val remainingMicroliters: Long? = null,
    val remainingAccountingCertain: Boolean = true,
    val lowLevelActive: Boolean = false,
    val editable: Boolean = false,
    val refillSupported: Boolean = false,
    val operationInProgress: Boolean = false,
    val initialized: Boolean = false,
    val dirty: Boolean = false,
    val capacityRejection: DeviceDosingReservoirCapacityRejection? = null,
    val notificationAvailability: DeviceDosingReservoirNotificationAvailability =
        DeviceDosingReservoirNotificationAvailability.AVAILABLE
) {
    val canSave: Boolean
        get() = initialized && editable && dirty && !operationInProgress && capacityRejection == null

    val canRefill: Boolean
        get() = initialized && refillSupported && draft.trackingEnabled && !operationInProgress

    val settingsIntent: DeviceDosingReservoirSettings
        get() = DeviceDosingReservoirSettings(
            trackingEnabled = draft.trackingEnabled,
            capacityMicroliters = draft.reservoirCapacityMicroliters.takeIf {
                draft.trackingEnabled
            },
            lowLevelAlertEnabled = draft.trackingEnabled && draft.lowLevelAlertEnabled
        )
}

internal sealed interface DeviceDosingReservoirEvent {
    data object Saved : DeviceDosingReservoirEvent
    data object SaveFailed : DeviceDosingReservoirEvent
    data object Refilled : DeviceDosingReservoirEvent
    data object RefillFailed : DeviceDosingReservoirEvent
}

/** Reservoir editor backed only by the central channel application boundary. */
internal class DeviceDosingReservoirViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableEditorState = MutableStateFlow(DeviceDosingReservoirEditorState())
    val editorState: StateFlow<DeviceDosingReservoirEditorState> = mutableEditorState.asStateFlow()

    private val eventChannel = Channel<DeviceDosingReservoirEvent>(Channel.BUFFERED)
    val events: Flow<DeviceDosingReservoirEvent> = eventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var boundSlotId = ""
    private var restoredDraft: DeviceDosingReservoirDraft? = null
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null

    fun currentEditorState(): DeviceDosingReservoirEditorState = mutableEditorState.value

    fun currentDraft(): DeviceDosingReservoirDraft = mutableEditorState.value.draft

    fun bind(
        deviceUidText: String,
        slotIdText: String,
        restoredDraft: DeviceDosingReservoirDraft?
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) return

        cancelJobs()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        this.restoredDraft = restoredDraft
        mutableEditorState.value = DeviceDosingReservoirEditorState()

        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                if (snapshot == null) applyUnavailable() else applySnapshot(snapshot)
            }
        }
        refreshJob = viewModelScope.launch {
            when (val result = operations.refresh(deviceUid, slotId)) {
                is DeviceDosingChannelOperationResult.Success -> applySnapshot(result.snapshot)
                else -> if (!mutableEditorState.value.initialized) applyUnavailable()
            }
        }
    }

    fun setTrackingEnabled(enabled: Boolean) = updateDraft { draft ->
        draft.copy(trackingEnabled = enabled)
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) = updateDraft { draft ->
        if (enabled && !draft.trackingEnabled) draft else draft.copy(lowLevelAlertEnabled = enabled)
    }

    fun setNotificationAvailability(
        availability: DeviceDosingReservoirNotificationAvailability
    ) {
        mutableEditorState.value = mutableEditorState.value.copy(
            notificationAvailability = availability
        )
    }

    fun setCapacityInput(rawValue: String, locale: Locale) {
        val state = mutableEditorState.value
        if (!state.editable || state.operationInProgress) return
        when (val validation = DeviceDosingReservoirCapacityPolicy.validate(rawValue, locale)) {
            is DeviceDosingReservoirCapacityValidation.Accepted -> {
                mutableEditorState.value = state.copy(
                    draft = state.draft.copy(
                        reservoirCapacityMicroliters = validation.capacityMicroliters
                    ),
                    capacityRejection = null,
                    dirty = true
                )
            }
            is DeviceDosingReservoirCapacityValidation.Rejected -> {
                mutableEditorState.value = state.copy(capacityRejection = validation.reason)
            }
        }
    }

    fun save() {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        val state = mutableEditorState.value
        if (deviceUid.isBlank() || slotId.isBlank() || !state.canSave) return
        val settings = runCatching { state.settingsIntent }.getOrNull() ?: return

        mutableEditorState.value = state.copy(operationInProgress = true)
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val result = runCatching {
                operations.applyReservoirSettings(deviceUid, slotId, settings)
            }.getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            when (result) {
                is DeviceDosingChannelOperationResult.Success -> {
                    applySnapshot(result.snapshot, forceAuthoritativeDraft = true)
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false,
                        dirty = false
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.Saved)
                }
                else -> {
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.SaveFailed)
                }
            }
        }
    }

    fun refill() {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        val state = mutableEditorState.value
        if (deviceUid.isBlank() || slotId.isBlank() || !state.canRefill) return

        mutableEditorState.value = state.copy(operationInProgress = true)
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val result = runCatching { operations.refillReservoir(deviceUid, slotId) }
                .getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            when (result) {
                is DeviceDosingChannelOperationResult.Success -> {
                    applySnapshot(result.snapshot)
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.Refilled)
                }
                else -> {
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.RefillFailed)
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: DeviceDosingChannelSnapshot,
        forceAuthoritativeDraft: Boolean = false
    ) {
        if (
            snapshot.deviceUid != boundDeviceUid ||
            snapshot.slotId != boundSlotId
        ) return
        val current = mutableEditorState.value
        val authoritativeDraft = snapshot.toReservoirDraft()
        val restored = restoredDraft
        val shouldUseAuthoritative = forceAuthoritativeDraft || (current.initialized && !current.dirty)
        val nextDraft = when {
            forceAuthoritativeDraft -> authoritativeDraft
            !current.initialized && restored != null -> restored
            !current.initialized -> authoritativeDraft
            shouldUseAuthoritative -> authoritativeDraft
            else -> current.draft
        }
        val nextDirty = when {
            forceAuthoritativeDraft -> false
            !current.initialized && restored != null -> restored != authoritativeDraft
            current.initialized && !current.dirty -> false
            else -> current.dirty
        }
        restoredDraft = null
        mutableEditorState.value = current.copy(
            draft = nextDraft,
            remainingMicroliters = snapshot.reservoir.remainingMicroliters.takeIf {
                snapshot.reservoir.trackingEnabled
            },
            remainingAccountingCertain = snapshot.reservoir.accountingCertain,
            lowLevelActive = snapshot.reservoir.lowLevelActive,
            editable = snapshot.controls.reservoirEditable,
            refillSupported = snapshot.controls.refillSupported,
            initialized = true,
            dirty = nextDirty,
            capacityRejection = null,
            notificationAvailability = if (!nextDraft.lowLevelAlertEnabled) {
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            } else {
                current.notificationAvailability
            }
        )
    }

    private fun applyUnavailable() {
        mutableEditorState.value = mutableEditorState.value.copy(
            editable = false,
            refillSupported = false,
            operationInProgress = false
        )
    }

    private inline fun updateDraft(
        transform: (DeviceDosingReservoirDraft) -> DeviceDosingReservoirDraft
    ) {
        val state = mutableEditorState.value
        if (!state.editable || state.operationInProgress) return
        val updated = transform(state.draft)
        if (updated == state.draft) return
        mutableEditorState.value = state.copy(
            draft = updated,
            dirty = true,
            capacityRejection = null,
            notificationAvailability = if (!updated.lowLevelAlertEnabled) {
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            } else {
                state.notificationAvailability
            }
        )
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        boundSlotId = ""
        restoredDraft = null
        mutableEditorState.value = DeviceDosingReservoirEditorState()
    }

    private fun cancelJobs() {
        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
    }
}

private fun DeviceDosingChannelSnapshot.toReservoirDraft(): DeviceDosingReservoirDraft =
    DeviceDosingReservoirDraft(
        reservoirCapacityMicroliters = DeviceDosingReservoirCapacityPolicy
            .normalizePersistedMicroliters(reservoir.capacityMicroliters),
        trackingEnabled = reservoir.trackingEnabled,
        lowLevelAlertEnabled = reservoir.lowLevelAlertEnabled
    )
