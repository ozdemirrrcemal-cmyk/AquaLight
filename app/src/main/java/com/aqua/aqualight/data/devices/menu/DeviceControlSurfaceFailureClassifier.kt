package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure

/**
 * One family-neutral classification point for root-surface read failures.
 *
 * These mappings never alter canonical device presence. A protocol/domain failure after current
 * liveness has been proven is a compatibility/state problem, not evidence that the device is
 * physically Offline.
 */
internal object DeviceControlSurfaceFailureClassifier {

    fun classify(failure: DeviceLightControlFailure): DeviceMenuUnavailableReason = when (failure) {
        DeviceLightControlFailure.NOT_CONNECTED ->
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        DeviceLightControlFailure.UNSUPPORTED,
        DeviceLightControlFailure.INVALID_DATA ->
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE
        DeviceLightControlFailure.UNAVAILABLE,
        DeviceLightControlFailure.REJECTED ->
            DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
    }

    fun classify(failure: DeviceTimerControlFailure): DeviceMenuUnavailableReason = when (failure) {
        DeviceTimerControlFailure.NotConnected ->
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        DeviceTimerControlFailure.Unsupported,
        DeviceTimerControlFailure.InvalidData ->
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE
        DeviceTimerControlFailure.Unavailable,
        is DeviceTimerControlFailure.Rejected ->
            DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
    }

    fun classify(failure: DeviceCoolingControlFailure): DeviceMenuUnavailableReason = when (failure) {
        DeviceCoolingControlFailure.NotConnected ->
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        DeviceCoolingControlFailure.Unsupported,
        DeviceCoolingControlFailure.InvalidData ->
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE
        DeviceCoolingControlFailure.Unavailable,
        is DeviceCoolingControlFailure.Rejected ->
            DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
    }

    fun dosingRefreshFailure(): DeviceMenuUnavailableReason =
        DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
}
