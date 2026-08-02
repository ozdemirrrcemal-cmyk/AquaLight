package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid

internal class DeviceRuntimeExecutionContext(
    val sessionProvider: (DeviceUid) -> DeviceRuntimeCommandSession?,
    val supportChecker: (DeviceUid, String, String) -> Boolean,
    val pendingRequests: DeviceRuntimePendingRequestRegistry
)
