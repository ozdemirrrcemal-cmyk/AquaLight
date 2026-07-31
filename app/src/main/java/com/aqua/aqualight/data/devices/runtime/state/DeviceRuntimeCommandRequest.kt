package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONObject

internal data class DeviceRuntimeCommandRequest(
    val deviceUid: DeviceUid,
    val module: String,
    val action: String,
    val data: JSONObject,
    val timeoutMillis: Long
)
