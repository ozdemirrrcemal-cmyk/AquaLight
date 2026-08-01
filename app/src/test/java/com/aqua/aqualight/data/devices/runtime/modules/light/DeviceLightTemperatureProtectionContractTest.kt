package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightTemperatureProtectionContractTest {

    @Test
    fun `payload uses exact firmware fields and rejects invalid thresholds`() {
        val payload = DeviceLightTemperatureProtectionSetPayload(
            thresholdC = DeviceLightRuntimeContract.Limit.DEFAULT_TEMPERATURE_PROTECTION_C,
            save = false
        ).toJson()

        assertEquals(setOf("thresholdC", "save"), payload.keySet())
        assertEquals(60.0, payload.getDouble("thresholdC"), 0.0)
        assertFalse(payload.getBoolean("save"))
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(49.9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(70.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(Double.NaN)
        }
    }

    @Test
    fun `status parser enforces supported and unsupported exact shapes`() {
        val supported = DeviceLightTemperatureProtectionParser.parseStatus(
            supportedStatus(thresholdC = 62.5, active = true)
        ).getOrThrow()
        assertTrue(supported.supported)
        assertTrue(supported.temperatureProtection.active)
        assertEquals(62.5, checkNotNull(supported.temperatureProtection.thresholdC), 0.0)
        assertEquals(50.0, checkNotNull(supported.temperatureProtection.minimumC), 0.0)
        assertEquals(70.0, checkNotNull(supported.temperatureProtection.maximumC), 0.0)
        assertTrue(supported.runtime.supportsSet)

        val unsupported = DeviceLightTemperatureProtectionParser.parseStatus(
            unsupportedStatus()
        ).getOrThrow()
        assertFalse(unsupported.supported)
        assertFalse(unsupported.temperatureProtection.thresholdEditable)
        assertNull(unsupported.temperatureProtection.thresholdC)
        assertFalse(unsupported.runtime.supportsSet)

        val invalid = unsupportedStatus().also { status ->
            status.getJSONObject("temperatureProtection").put("thresholdC", 60.0)
        }
        assertTrue(DeviceLightTemperatureProtectionParser.parseStatus(invalid).isFailure)
        assertTrue(
            DeviceLightTemperatureProtectionParser.parseStatus(
                supportedStatus().put("unexpected", true)
            ).isFailure
        )
    }

    @Test
    fun `set result enforces persistence echo`() {
        val result = DeviceLightTemperatureProtectionParser.parseSetResult(
            setResult(saved = true, saveRequested = true, thresholdC = 60.0)
        ).getOrThrow()

        assertTrue(result.changed)
        assertTrue(result.saved)
        assertEquals(60.0, checkNotNull(result.status.temperatureProtection.thresholdC), 0.0)
        assertTrue(
            DeviceLightTemperatureProtectionParser.parseSetResult(
                setResult(saved = false, saveRequested = true, thresholdC = 60.0)
            ).isFailure
        )
    }

    @Test
    fun `repository correlates both actions and validates requested threshold`() = runBlocking {
        val gateway = ParsingGateway(
            mutableMapOf(
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET to
                    supportedStatus(),
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET to
                    setResult(saved = true, saveRequested = true, thresholdC = 62.5)
            )
        )
        val repository = DeviceLightTemperatureProtectionRuntimeRepository(gateway)
        val deviceUid = DeviceUid("device-light-temperature")

        val status = repository.requestStatus(deviceUid)
        val set = repository.setThreshold(
            deviceUid,
            DeviceLightTemperatureProtectionSetPayload(thresholdC = 62.5, save = true)
        )

        assertTrue(status is DeviceRuntimeCommandOutcome.Success)
        assertTrue(set is DeviceRuntimeCommandOutcome.Success)
        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET,
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
            ),
            gateway.actions
        )
        assertEquals(0, gateway.payloads[0].length())
        assertEquals(setOf("thresholdC", "save"), gateway.payloads[1].keySet())
        assertEquals(62.5, gateway.payloads[1].getDouble("thresholdC"), 0.0)
        assertEquals(
            62.5,
            repository.currentStatus(deviceUid)?.temperatureProtection?.thresholdC
                ?: Double.NaN,
            0.0
        )
    }

    private fun supportedStatus(
        thresholdC: Double = DeviceLightRuntimeContract.Limit.DEFAULT_TEMPERATURE_PROTECTION_C,
        active: Boolean = false
    ): JSONObject = JSONObject()
        .put("supported", true)
        .put(
            "temperatureProtection",
            JSONObject()
                .put("supported", true)
                .put("active", active)
                .put("thresholdEditable", true)
                .put("thresholdC", thresholdC)
                .put("minimumC", 50.0)
                .put("maximumC", 70.0)
        )
        .put("runtime", runtime(supportsSet = true))

    private fun unsupportedStatus(): JSONObject = JSONObject()
        .put("supported", false)
        .put(
            "temperatureProtection",
            JSONObject()
                .put("supported", false)
                .put("active", false)
                .put("thresholdEditable", false)
                .put("thresholdC", JSONObject.NULL)
                .put("minimumC", JSONObject.NULL)
                .put("maximumC", JSONObject.NULL)
        )
        .put("runtime", runtime(supportsSet = false))

    private fun setResult(
        saved: Boolean,
        saveRequested: Boolean,
        thresholdC: Double
    ): JSONObject = JSONObject()
        .put("operation", "temperatureProtectionSet")
        .put("changed", true)
        .put("saved", saved)
        .put("saveRequested", saveRequested)
        .put("runtimeTransport", "websocket")
        .put("command", "light.temperature-protection.set")
        .put("event", "light.status.changed")
        .put("status", supportedStatus(thresholdC = thresholdC))

    private fun runtime(supportsSet: Boolean): JSONObject = JSONObject()
        .put("module", "light")
        .put("readOnly", false)
        .put("supportsStatusGet", true)
        .put("supportsSet", supportsSet)
        .put("event", "light.status.changed")

    private class ParsingGateway(
        private val responses: MutableMap<String, JSONObject>
    ) : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val payloads = mutableListOf<JSONObject>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            payloads += command.encodeData()
            val response = AqlWsIncomingMessage.Response(
                id = "response-${actions.size}",
                type = "res",
                module = command.module,
                action = command.action,
                data = requireNotNull(responses[command.action]),
                ok = true,
                statusCode = 200
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }
}
