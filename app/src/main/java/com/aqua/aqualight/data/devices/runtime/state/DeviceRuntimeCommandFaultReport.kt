package com.aqua.aqualight.data.devices.runtime.state

data class DeviceRuntimeCommandFaultReport(
    val code: String,
    val message: String,
    val module: String,
    val action: String,
    val messageId: String = ""
)
