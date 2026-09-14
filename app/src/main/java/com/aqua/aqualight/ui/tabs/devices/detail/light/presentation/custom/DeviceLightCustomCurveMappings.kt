package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal sealed interface DeviceLightCustomCurveEffect {
    data class OpenTimePicker(val originalTimeMs: Long?) : DeviceLightCustomCurveEffect
    data class OpenSaveAs(val usedCustomNames: List<String>) : DeviceLightCustomCurveEffect
    data class ShowSuccess(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowError(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowPointLimit(val maxPoints: Int) : DeviceLightCustomCurveEffect
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

@StringRes
internal fun DeviceLightCustomFailure.messageRes(): Int = when (this) {
    DeviceLightCustomFailure.NOT_CONNECTED ->
        R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_custom_operation_error
}

@StringRes
internal fun DeviceLightLibraryFailure.messageRes(): Int = when (this) {
    DeviceLightLibraryFailure.DUPLICATE_NAME -> R.string.device_light_library_name_duplicate_error
    DeviceLightLibraryFailure.INVALID_NAME -> R.string.device_light_library_name_invalid_error
    DeviceLightLibraryFailure.NOT_CONNECTED ->
        R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_library_operation_error
}
