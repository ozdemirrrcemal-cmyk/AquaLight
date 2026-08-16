package com.aqua.aqualight.platform.text

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertCopy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertTextResolver

/** Android localization adapter for Dosing low-reservoir device alerts. */
internal class AndroidDeviceDosingLowLevelAlertTextResolver(
    context: Context
) : DeviceDosingLowLevelAlertTextResolver {
    private val appContext = context.applicationContext

    override fun resolve(channelTitle: String): DeviceDosingLowLevelAlertCopy {
        val safeChannelTitle = channelTitle.trim().ifBlank {
            appContext.getString(R.string.device_dosing_low_level_notification_channel_fallback)
        }
        return DeviceDosingLowLevelAlertCopy(
            title = appContext.getString(R.string.device_dosing_low_level_notification_title),
            message = appContext.getString(
                R.string.device_dosing_low_level_notification_message,
                safeChannelTitle
            )
        )
    }
}
