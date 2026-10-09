package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

internal object DeviceLightProtectionOrderingFixtures {
    val uid = DeviceUid("temperature-protection-order")
    val firstGeneration = DeviceRuntimeConnectionGeneration(1L)
    val nextGeneration = DeviceRuntimeConnectionGeneration(2L)

    fun status(thresholdC: Double = 60.0, supported: Boolean = true): JSONObject = JSONObject()
        .put("supported", supported)
        .put(
            "temperatureProtection",
            JSONObject()
                .put("supported", supported)
                .put("active", false)
                .put("thresholdEditable", supported)
                .put("thresholdC", if (supported) thresholdC else JSONObject.NULL)
                .put("minimumC", if (supported) 50.0 else JSONObject.NULL)
                .put("maximumC", if (supported) 70.0 else JSONObject.NULL)
        )
        .put(
            "runtime",
            JSONObject()
                .put("module", "light")
                .put("readOnly", false)
                .put("supportsStatusGet", true)
                .put("supportsSet", supported)
                .put("event", if (supported) "light.thermal.status.changed" else "light.status.changed")
        )

    fun snapshot(thresholdC: Double = 60.0, supported: Boolean = true) =
        DeviceLightTemperatureProtectionParser.parseStatus(status(thresholdC, supported)).getOrThrow()

    fun acknowledgement(thresholdC: Double = 65.0): JSONObject = JSONObject()
        .put("operation", "temperatureProtectionSet")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("runtimeTransport", "websocket")
        .put("command", "light.temperature-protection.set")
        .put("event", "light.thermal.status.changed")
        .put("status", status(thresholdC))

    fun thermalStatus() = DeviceLightThermalStatus(
        schema = DeviceLightThermalV1Contract.SCHEMA,
        schemaVersion = DeviceLightThermalV1Contract.SCHEMA_VERSION,
        productKey = DeviceLightThermalV1Contract.PRODUCT_KEY,
        uptimeMs = 100L,
        topology = DeviceLightThermalTopology(2, 1),
        config = DeviceLightThermalConfig(DeviceLightThermalMode.AUTO, 30.0, 50.0),
        temperature = DeviceLightThermalTemperature(
            DeviceLightThermalV1Contract.FIXTURE_SENSOR_KEY, 0, true, 42.0, 100L
        ),
        lightProtection = DeviceLightThermalProtection(true, false, 60.0),
        fans = listOf(fan("fan1", 0), fan("fan2", 1)),
        runtime = DeviceLightThermalRuntime(
            "light.thermal.status.changed", "light.thermal.status.changed",
            false, true, false, false, false
        )
    )

    private fun fan(key: String, index: Int) = DeviceLightThermalFan(
        fanKey = key,
        index = index,
        name = key,
        regime = "AUTO",
        valueNow = 0.35,
        valueAuto = 0.35,
        percentNow = 35.0,
        percentAuto = 35.0,
        hardware = DeviceLightThermalFanHardware(
            editable = false, gpio = null, ledcChannel = null, pwmFrequencyHz = null,
            pwmResolutionBits = null, invert = null, pwmOutputHealth = "OK", health = "OK",
            physicalFeedbackAvailable = false
        )
    )
}

internal class DeviceLightProtectionOrderingFixture(authoritative: Boolean = true) {
    val owner = DeviceLightRuntimeStateOwner()
    val gateway = DeviceLightProtectionOrderingGateway()
    val gate = DeviceLightDeviceOperationGate()
    val repository = DeviceLightTemperatureProtectionRuntimeRepository(gateway, owner, gate)

    init {
        if (authoritative) {
            owner.beginGeneration(
                DeviceLightProtectionOrderingFixtures.uid,
                DeviceLightProtectionOrderingFixtures.firstGeneration
            )
            owner.recordTemperatureProtection(
                DeviceLightProtectionOrderingFixtures.uid,
                DeviceLightProtectionOrderingFixtures.firstGeneration,
                DeviceLightProtectionOrderingFixtures.snapshot()
            )
        }
    }
}

/** Responses retain the socket generation captured at dispatch, even after reconnect. */
internal class DeviceLightProtectionOrderingGateway : DeviceRuntimeCommandGateway {
    var generation = DeviceLightProtectionOrderingFixtures.firstGeneration
    var beforeDispatch: () -> Unit = {}
    val calls = mutableListOf<PendingProtectionResponse>()

    override suspend fun <T> execute(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long
    ): DeviceRuntimeCommandOutcome<T> {
        beforeDispatch()
        val dispatchedGeneration = generation
        if (command.expectedGeneration != null && command.expectedGeneration != dispatchedGeneration) {
            return DeviceRuntimeCommandOutcome.Cancelled(
                deviceUid, command.module, command.action, "rejected", dispatchedGeneration,
                "The checked connection was replaced before dispatch."
            )
        }
        val pending = PendingProtectionResponse(command, dispatchedGeneration)
        calls += pending
        val response = AqlWsIncomingMessage.Response(
            id = "response-${calls.size}", type = "res", module = command.module,
            action = command.action, data = pending.data.await(), ok = true, statusCode = 200
        )
        val parsed = runCatching { command.parseSuccess(response) }
        return parsed.fold(
            onSuccess = { value ->
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid, command.module, command.action, response.id,
                    dispatchedGeneration, response.statusCode, value
                )
            },
            onFailure = { failure ->
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid, command.module, command.action, response.id,
                    dispatchedGeneration, failure.message.orEmpty()
                )
            }
        )
    }
}

internal class PendingProtectionResponse(
    val command: DeviceRuntimeCommand<*>,
    val generation: DeviceRuntimeConnectionGeneration,
    val data: CompletableDeferred<JSONObject> = CompletableDeferred()
)
