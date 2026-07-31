package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid

sealed interface DeviceRuntimeCommandOutcome<out T> {
    val deviceUid: DeviceUid
    val module: String
    val action: String

    data class Success<T>(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val statusCode: Int,
        val value: T
    ) : DeviceRuntimeCommandOutcome<T>

    data class NotConnected(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class NotAuthenticated(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val generation: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class UnsupportedByDevice(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class SendFailed(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val generation: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class Timeout(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val generation: DeviceRuntimeConnectionGeneration,
        val timeoutMillis: Long
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class FirmwareError(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val generation: DeviceRuntimeConnectionGeneration,
        val statusCode: Int,
        val code: String,
        val field: String,
        val message: String
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class ProtocolError(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val generation: DeviceRuntimeConnectionGeneration,
        val reason: String
    ) : DeviceRuntimeCommandOutcome<Nothing>

    data class Cancelled(
        override val deviceUid: DeviceUid,
        override val module: String,
        override val action: String,
        val messageId: String,
        val generation: DeviceRuntimeConnectionGeneration,
        val reason: String
    ) : DeviceRuntimeCommandOutcome<Nothing>
}
