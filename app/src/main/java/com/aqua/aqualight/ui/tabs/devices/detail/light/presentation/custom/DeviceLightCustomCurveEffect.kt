package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.annotation.StringRes
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal sealed interface DeviceLightCustomCurveEffect {
    data class OpenTimePicker(
        val purpose: DeviceLightCustomTimePickerPurpose
    ) : DeviceLightCustomCurveEffect
    data class OpenPointActions(val timeMs: Long) : DeviceLightCustomCurveEffect
    data class OpenSaveAs(val usedCustomNames: List<String>) : DeviceLightCustomCurveEffect
    data object OpenDeviceProgramActions : DeviceLightCustomCurveEffect
    data class ShowSuccess(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowError(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowPointLimit(val maxPoints: Int) : DeviceLightCustomCurveEffect
}

internal sealed interface DeviceLightCustomTimePickerPurpose {
    data class Add(val preferredTimeMs: Long) : DeviceLightCustomTimePickerPurpose
    data class Move(val originalTimeMs: Long) : DeviceLightCustomTimePickerPurpose
}

internal fun DeviceLightCustomChannelId.toLibraryChannel(): DeviceLightLibraryChannel = when (this) {
    DeviceLightCustomChannelId.RED -> DeviceLightLibraryChannel.RED
    DeviceLightCustomChannelId.GREEN -> DeviceLightLibraryChannel.GREEN
    DeviceLightCustomChannelId.BLUE -> DeviceLightLibraryChannel.BLUE
    DeviceLightCustomChannelId.WHITE -> DeviceLightLibraryChannel.WHITE
}

internal fun DeviceLightCustomFailure.connectionState(): DeviceConnectionVisualState? = when (this) {
    DeviceLightCustomFailure.NOT_CONNECTED -> DeviceConnectionVisualState.OFFLINE
    else -> null
}
