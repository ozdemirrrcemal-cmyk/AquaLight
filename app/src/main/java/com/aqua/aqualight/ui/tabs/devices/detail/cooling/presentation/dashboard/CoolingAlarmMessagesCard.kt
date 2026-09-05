package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingAlarmMessageRes
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

@Composable
internal fun CoolingAlarmMessagesCard(state: DeviceCoolingRootUiState) {
    val messages = mutableListOf<String>()
    for (code in state.activeAlarmCodes.distinct()) {
        val messageRes = code.toCommercialCoolingAlarmMessageRes() ?: continue
        messages += stringResource(messageRes)
    }
    if (messages.isEmpty()) return

    CoolingStateMessageCard(
        title = stringResource(R.string.device_cooling_status_alarm),
        message = messages.joinToString(separator = "\n")
    )
}
