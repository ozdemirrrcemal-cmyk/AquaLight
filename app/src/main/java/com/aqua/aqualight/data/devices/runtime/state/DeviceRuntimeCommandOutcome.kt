package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONObject

sealed interface DeviceRuntimeCommandOutcome {
    val deviceUid: DeviceUid
    val module: String
    val action: String
    val messageId: String

    data class Success(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String,
        val statusCode: Int,
        val data: JSONObject
    ) : DeviceRuntimeCommandOutcome

    data class FirmwareError(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String,
        val statusCode: Int,
        val code: String,
        val message: String,
        val field: String
    ) : DeviceRuntimeCommandOutcome

    data class Timeout(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String,
        val timeoutMillis: Long
    ) : DeviceRuntimeCommandOutcome

    data class NotConnected(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String = ""
    ) : DeviceRuntimeCommandOutcome

    data class SendFailed(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String
    ) : DeviceRuntimeCommandOutcome

    data class Cancelled(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        override val messageId: String,
        val reason: String
    ) : DeviceRuntimeCommandOutcome
}
