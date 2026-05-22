package com.aqua.aqualight.lan

import android.content.Context
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor

object LanMonitor {

    fun start(
        context: Context
    ) {
        DevicePresenceMonitor.start(
            context = context
        )
    }

    fun stop() {
        DevicePresenceMonitor.stop()
    }
}