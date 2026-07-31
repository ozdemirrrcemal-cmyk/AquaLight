package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceSettingsOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameSetResponse
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameSetResponseParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandFactory
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/** Owner-scoped implementation shared by Light, Timer, Dosing and Cooling Settings. */
internal class DefaultDeviceSettingsOperations(
    private val devicesRepository: DevicesRepository
) : DeviceSettingsOperations {

    override suspend fun updateCustomName(
        deviceUid: String,
        customName: String
    ): Result<Unit> = runCatching {
        val normalizedUid = deviceUid.trim()
        require(normalizedUid.isNotBlank()) { "Device uid is missing." }

        val uid = DeviceUid(normalizedUid)
        val current = requireNotNull(devicesRepository.currentDevice(uid)) {
            "Device is not registered."
        }
        require(devicesRepository.currentRuntimeConnectionState(uid) is AqlWsConnectionState.Authenticated) {
            "Device must be authenticated before changing its name."
        }

        val commandClient = requireNotNull(devicesRepository.commandClient(uid)) {
            "Device runtime command channel is unavailable."
        }
        val runtimeEvents = requireNotNull(devicesRepository.runtimeEvents()) {
            "Device runtime response channel is unavailable."
        }
        val payload = DeviceNameSetPayload(
            customName = customName.trim().ifEmpty { null },
            save = true
        )
        val command = AqlWsCommandFactory.deviceNameSet(payload.toJson())
        val response = awaitCommandResponse(
            deviceUid = uid,
            command = command,
            events = runtimeEvents,
            send = { commandClient.command(command) != null }
        )
        val result = when (response) {
            is AqlWsIncomingMessage.Response -> {
                require(response.ok && response.statusCode in 200..299) {
                    "Device rejected the name update."
                }
                DeviceNameSetResponseParser.parse(response.data).getOrThrow()
            }
            is AqlWsIncomingMessage.Error -> throw DeviceNameUpdateException(
                code = response.code,
                field = response.field,
                detail = response.message
            )
            is AqlWsIncomingMessage.Event -> error(
                "A device-name command cannot complete with an event frame."
            )
        }
        persistConfirmedName(uid, current.product.displayName, result)
    }

    private suspend fun awaitCommandResponse(
        deviceUid: DeviceUid,
        command: AqlWsOutgoingMessage.Command,
        events: SharedFlow<AqlWsEvent>,
        send: () -> Boolean
    ): AqlWsIncomingMessage = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(COMMAND_TIMEOUT_MILLIS) {
                events
                    .filterIsInstance<AqlWsEvent.Message>()
                    .filterDevice(deviceUid)
                    .map(AqlWsEvent.Message::parsed)
                    .first { message ->
                        message.id == command.id &&
                            message.module == DeviceNameRuntimeContract.MODULE &&
                            message.action == DeviceNameRuntimeContract.ACTION_SET &&
                            (message is AqlWsIncomingMessage.Response ||
                                message is AqlWsIncomingMessage.Error)
                    }
            }
        }

        if (!send()) {
            response.cancel()
            error("Device name command could not be queued on the authenticated socket.")
        }
        response.await()
    }

    private suspend fun persistConfirmedName(
        deviceUid: DeviceUid,
        expectedProductDisplayName: String,
        result: DeviceNameSetResponse
    ) {
        require(result.saved && result.saveRequested) {
            "Firmware did not confirm persistent device-name storage."
        }
        require(
            expectedProductDisplayName.isBlank() ||
                expectedProductDisplayName == result.status.productDisplayName
        ) {
            "Firmware device-name response changed immutable product identity."
        }
        val latest = requireNotNull(devicesRepository.currentDevice(deviceUid)) {
            "Device disappeared before the confirmed name could be persisted."
        }
        devicesRepository.commitProvisioningSnapshot(
            latest.copy(
                identity = latest.identity.copy(
                    displayName = result.status.productDisplayName,
                    customName = result.status.customName
                )
            )
        )
    }

    private fun kotlinx.coroutines.flow.Flow<AqlWsEvent.Message>.filterDevice(
        deviceUid: DeviceUid
    ) = kotlinx.coroutines.flow.filter { event -> event.deviceUid == deviceUid }

    private class DeviceNameUpdateException(
        code: String,
        field: String,
        detail: String
    ) : IllegalStateException(
        buildString {
            append("Firmware rejected device.name.set")
            if (code.isNotBlank()) append(" [").append(code).append(']')
            if (field.isNotBlank()) append(" field=").append(field)
            if (detail.isNotBlank()) append(": ").append(detail)
        }
    )

    private companion object {
        const val COMMAND_TIMEOUT_MILLIS = 10_000L
    }
}
