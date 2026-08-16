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
import com.aqua.aqualight.application.devices.dosing.requiresLowReservoirAttention
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
    val reservoirNeedsAttention: Boolean = false,
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

    private val jobs = ReservoirEditorJobs()
    private var boundDeviceUid = ""
    private var boundSlotId = ""
    private var restoredDraft: DeviceDosingReservoirDraft? = null

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

        jobs.cancelAll()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        this.restoredDraft = restoredDraft
        mutableEditorState.value = DeviceDosingReservoirEditorState()

        jobs.observe = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                if (snapshot == null) {
                    mutableEditorState.value = mutableEditorState.value.asUnavailable()
                } else {
                    applySnapshot(snapshot)
                }
            }
        }
        jobs.refresh = viewModelScope.launch {
            when (val result = operations.refresh(deviceUid, slotId)) {
                is DeviceDosingChannelOperationResult.Success -> applySnapshot(result.snapshot)
                else -> if (!mutableEditorState.value.initialized) {
                    mutableEditorState.value = mutableEditorState.value.asUnavailable()
                }
            }
        }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        mutableEditorState.value = mutableEditorState.value.withUpdatedDraft { draft ->
            draft.copy(trackingEnabled = enabled)
        }
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) {
        mutableEditorState.value = mutableEditorState.value.withUpdatedDraft { draft ->
            if (enabled && !draft.trackingEnabled) draft else draft.copy(lowLevelAlertEnabled = enabled)
        }
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
        jobs.mutation?.cancel()
        jobs.mutation = viewModelScope.launch {
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
        jobs.mutation?.cancel()
        jobs.mutation = viewModelScope.launch {
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
        if (snapshot.deviceUid != boundDeviceUid || snapshot.slotId != boundSlotId) return

        val current = mutableEditorState.value
        val reservoir = snapshot.reservoir
        val resolution = resolveReservoirDraft(
            current = current,
            authoritative = snapshot.toReservoirDraft(),
            restored = restoredDraft,
            forceAuthoritative = forceAuthoritativeDraft
        )
        restoredDraft = null
        mutableEditorState.value = current.copy(
            draft = resolution.draft,
            remainingMicroliters = reservoir.remainingMicroliters.takeIf {
                reservoir.trackingEnabled
            },
            remainingAccountingCertain = reservoir.accountingCertain,
            reservoirNeedsAttention = reservoir.requiresLowReservoirAttention,
            editable = snapshot.controls.reservoirEditable,
            refillSupported = snapshot.controls.refillSupported,
            initialized = true,
            dirty = resolution.dirty,
            capacityRejection = null,
            notificationAvailability = if (!resolution.draft.lowLevelAlertEnabled) {
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            } else {
                current.notificationAvailability
            }
        )
    }

    private fun clearBinding() {
        jobs.cancelAll()
        boundDeviceUid = ""
        boundSlotId = ""
        restoredDraft = null
        mutableEditorState.value = DeviceDosingReservoirEditorState()
    }
}

private class ReservoirEditorJobs {
    var observe: Job? = null
    var refresh: Job? = null
    var mutation: Job? = null

    fun cancelAll() {
        observe?.cancel()
        refresh?.cancel()
        mutation?.cancel()
    }
}

private data class ReservoirDraftResolution(
    val draft: DeviceDosingReservoirDraft,
    val dirty: Boolean
)

private fun resolveReservoirDraft(
    current: DeviceDosingReservoirEditorState,
    authoritative: DeviceDosingReservoirDraft,
    restored: DeviceDosingReservoirDraft?,
    forceAuthoritative: Boolean
): ReservoirDraftResolution {
    if (forceAuthoritative) return ReservoirDraftResolution(authoritative, dirty = false)
    if (!current.initialized) {
        val initialDraft = restored ?: authoritative
        return ReservoirDraftResolution(
            draft = initialDraft,
            dirty = restored != null && initialDraft != authoritative
        )
    }
    if (!current.dirty) return ReservoirDraftResolution(authoritative, dirty = false)
    return ReservoirDraftResolution(current.draft, dirty = true)
}

private fun DeviceDosingReservoirEditorState.asUnavailable(): DeviceDosingReservoirEditorState =
    copy(
        editable = false,
        refillSupported = false,
        operationInProgress = false
    )

private inline fun DeviceDosingReservoirEditorState.withUpdatedDraft(
    transform: (DeviceDosingReservoirDraft) -> DeviceDosingReservoirDraft
): DeviceDosingReservoirEditorState {
    if (!editable || operationInProgress) return this
    val updated = transform(draft)
    if (updated == draft) return this
    return copy(
        draft = updated,
        dirty = true,
        capacityRejection = null,
        notificationAvailability = if (!updated.lowLevelAlertEnabled) {
            DeviceDosingReservoirNotificationAvailability.AVAILABLE
        } else {
            notificationAvailability
        }
    )
}

private fun DeviceDosingChannelSnapshot.toReservoirDraft(): DeviceDosingReservoirDraft =
    DeviceDosingReservoirDraft(
        reservoirCapacityMicroliters = DeviceDosingReservoirCapacityPolicy
            .normalizePersistedMicroliters(reservoir.capacityMicroliters),
        trackingEnabled = reservoir.trackingEnabled,
        lowLevelAlertEnabled = reservoir.lowLevelAlertEnabled
    )
