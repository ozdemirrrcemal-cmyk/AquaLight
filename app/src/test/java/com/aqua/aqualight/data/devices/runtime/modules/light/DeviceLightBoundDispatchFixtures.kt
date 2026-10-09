package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandExecutor
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandSession
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.core.withGenerationBoundSend
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import org.json.JSONObject

/** Exercises the real executor, including the gap after repository validation and before capture. */
internal class DeviceLightBoundDispatchFixtures(beginGeneration: Boolean = true) {
    val owner = DeviceLightRuntimeStateOwner()
    var generation = G1
    var supported = true
    var automaticReply = true
    var beforeCapture: () -> Unit = {}
    var responseData: (String) -> JSONObject = ::defaultResponse
    val sent = mutableListOf<AqlWsOutgoingMessage.Command>()
    val expectedGenerations = mutableListOf<DeviceRuntimeConnectionGeneration?>()
    private val sessionLock = Any()
    val executor = DeviceRuntimeCommandExecutor(
        sessionProvider = {
            synchronized(sessionLock) {
                DeviceRuntimeCommandSession(UID, generation, true, ::send)
                    .withGenerationBoundSend(sessionLock) { generation }
            }
        },
        supportChecker = { _, _, _ -> supported }
    )
    private val gateway = object : DeviceRuntimeCommandGateway {
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            expectedGenerations += command.expectedGeneration
            beforeCapture()
            return executor.execute(deviceUid, command, timeoutMillis)
        }
    }
    private val gate = DeviceLightDeviceOperationGate()
    val light = DeviceLightRuntimeRepository(gateway, owner, gate)
    val thermal = DeviceLightThermalRuntimeRepository(gateway, owner, gate)

    init {
        if (beginGeneration) owner.beginGeneration(UID, G1)
    }

    fun hydrate() {
        owner.recordStatus(UID, generation, DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status()))
        owner.recordThermalStatus(
            UID, generation,
            DeviceLightThermalV1ResponseParser.parseStatus(DeviceLightRefreshTestFixtures.thermalStatus())
        )
    }

    fun replaceConnection() {
        generation = G2
        owner.beginGeneration(UID, G2)
        hydrate()
    }

    fun reply(command: AqlWsOutgoingMessage.Command, dispatchedGeneration: DeviceRuntimeConnectionGeneration) {
        executor.complete(
            UID, dispatchedGeneration,
            AqlWsIncomingMessage.Response(
                id = command.id,
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData(command.action),
                ok = true,
                statusCode = 200
            )
        )
    }

    private fun send(message: AqlWsOutgoingMessage): Boolean {
        val command = message as AqlWsOutgoingMessage.Command
        sent += command
        if (automaticReply) reply(command, generation)
        return true
    }

    companion object {
        val UID = DeviceUid("light-bound-dispatch")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
        val CONFIG = DeviceLightThermalConfigApplyPayload(mode = DeviceLightThermalMode.AUTO)

        fun defaultResponse(action: String): JSONObject = when (action) {
            DeviceLightThermalV1Contract.Action.STATUS_GET -> DeviceLightRefreshTestFixtures.thermalStatus()
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY -> DeviceLightRefreshTestFixtures.thermalConfigApply()
            DeviceLightRuntimeContract.Action.STATUS_GET -> DeviceLightRuntimeFixtures.status()
            DeviceLightRuntimeContract.Action.MANUAL_OFF -> JSONObject().put(
                "scene", DeviceLightRuntimeFixtures.status().getJSONObject("manual").getJSONObject("scene")
            )
            else -> error("Unexpected test action: $action")
        }
    }
}
