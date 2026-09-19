package com.aqua.aqualight.ui.common.devicepresence

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.ui.common.header.AquaHeaderStatusIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Shared binary connection presentation used by cards and device-control headers. */
enum class DeviceConnectionVisualState(
    @ColorRes val tintColorRes: Int,
    @StringRes val statusLabelRes: Int,
    @StringRes val accessibilityLabelRes: Int
) {
    ONLINE(
        tintColorRes = R.color.aqua_device_connection_online,
        statusLabelRes = R.string.device_online,
        accessibilityLabelRes = R.string.device_connection_online_content_description
    ),
    CONNECTING(
        tintColorRes = R.color.aqua_device_connection_offline,
        statusLabelRes = R.string.device_offline,
        accessibilityLabelRes = R.string.device_connection_offline_content_description
    ),
    WARNING(
        tintColorRes = R.color.aqua_device_connection_offline,
        statusLabelRes = R.string.device_offline,
        accessibilityLabelRes = R.string.device_connection_offline_content_description
    ),
    OFFLINE(
        tintColorRes = R.color.aqua_device_connection_offline,
        statusLabelRes = R.string.device_offline,
        accessibilityLabelRes = R.string.device_connection_offline_content_description
    );

    fun toWifiHeaderStatusIcon(context: Context) = AquaHeaderStatusIcon(
        iconRes = R.drawable.ic_status_wifi,
        tintColorRes = tintColorRes,
        contentDescription = context.getString(accessibilityLabelRes)
    )
}


/**
 * Canonical device-presence projection for cards and control-surface headers.
 *
 * Connectivity is owned by DeviceRoot/DevicesRepository availability. Feature read/write
 * authority must never be used to infer whether the physical device is online.
 */
internal fun OwnerDeviceAvailability?.toDeviceConnectionVisualState(): DeviceConnectionVisualState =
    if (this == OwnerDeviceAvailability.REACHABLE) {
        DeviceConnectionVisualState.ONLINE
    } else {
        DeviceConnectionVisualState.OFFLINE
    }

internal fun DeviceRootSnapshot?.toDeviceConnectionVisualState(): DeviceConnectionVisualState =
    this?.availability.toDeviceConnectionVisualState()

internal fun DeviceRootOperations.observeConnectionVisualState(
    deviceUid: String
): Flow<DeviceConnectionVisualState> = observe(deviceUid)
    .map { snapshot -> snapshot.toDeviceConnectionVisualState() }
    .distinctUntilChanged()
