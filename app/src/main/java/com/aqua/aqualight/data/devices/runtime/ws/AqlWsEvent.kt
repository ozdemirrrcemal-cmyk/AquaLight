package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid

sealed interface AqlWsEvent {
    val deviceUid: DeviceUid

    data class Opened(
        override val deviceUid: DeviceUid
    ) : AqlWsEvent

    data class Message(
        override val deviceUid: DeviceUid,
        val parsed: AqlWsIncomingMessage?
    ) : AqlWsEvent

    data class Closed(
        override val deviceUid: DeviceUid,
        val code: Int,
        val reason: String
    ) : AqlWsEvent

    data class Failure(
        override val deviceUid: DeviceUid,
        val message: String,
        val throwable: Throwable? = null
    ) : AqlWsEvent
}
