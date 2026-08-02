package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.model.DeviceUid

/** Lifecycle-only projection exposed to runtime coordinators; raw wire messages stay internal. */
sealed interface DeviceRuntimeLifecycleEvent {
    val deviceUid: DeviceUid

    data class Authenticated(
        override val deviceUid: DeviceUid
    ) : DeviceRuntimeLifecycleEvent

    data class Unavailable(
        override val deviceUid: DeviceUid
    ) : DeviceRuntimeLifecycleEvent
}
