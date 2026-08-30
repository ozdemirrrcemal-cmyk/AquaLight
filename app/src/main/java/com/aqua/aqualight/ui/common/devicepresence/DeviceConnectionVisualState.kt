package com.aqua.aqualight.ui.common.devicepresence

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.header.AquaHeaderStatusIcon

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
