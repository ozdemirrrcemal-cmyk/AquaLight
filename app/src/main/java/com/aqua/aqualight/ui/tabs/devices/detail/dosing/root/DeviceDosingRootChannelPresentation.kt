package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.validatedDosingChannelSetOrNull
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toDosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toInitialDosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toPumpVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState

internal data class DeviceDosingRootChannelPresentation(
    val pumpCount: Int,
    val channels: List<DosingChannelCardUiState>,
    val pumpStates: List<DosingPumpVisualState>,
    val authoritative: Boolean
)

/** Atomically switches the root from topology-only catalog bootstrap to central firmware state. */
internal fun resolveDosingRootChannelPresentation(
    deviceUid: String,
    catalogChannels: List<DeviceDosingChannelSlot>,
    snapshots: Collection<DeviceDosingChannelSnapshot>
): DeviceDosingRootChannelPresentation {
    val pumpCount = resolveDosingPumpCount(catalogChannels.size)
    if (pumpCount == UNKNOWN_DOSING_PUMP_COUNT) return EMPTY_DOSING_CHANNEL_PRESENTATION

    val authoritative = validatedDosingChannelSetOrNull(
        deviceUid = deviceUid,
        catalogChannels = catalogChannels,
        snapshots = snapshots
    )
    return if (authoritative != null) {
        DeviceDosingRootChannelPresentation(
            pumpCount = pumpCount,
            channels = authoritative.map { channel ->
                channel.toDosingChannelCardUiState()
            },
            pumpStates = authoritative.map(DeviceDosingChannelSnapshot::toPumpVisualState),
            authoritative = true
        )
    } else {
        DeviceDosingRootChannelPresentation(
            pumpCount = pumpCount,
            channels = catalogChannels.map(DeviceDosingChannelSlot::toInitialDosingChannelCardUiState),
            pumpStates = emptyList(),
            authoritative = false
        )
    }
}

private val EMPTY_DOSING_CHANNEL_PRESENTATION = DeviceDosingRootChannelPresentation(
    pumpCount = UNKNOWN_DOSING_PUMP_COUNT,
    channels = emptyList(),
    pumpStates = emptyList(),
    authoritative = false
)
