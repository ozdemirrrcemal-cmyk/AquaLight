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
    fun payloadUsesExactFirmwareFieldsAndRejectsInvalidThresholds() {
        val payload = DeviceLightTemperatureProtectionSetPayload(
            thresholdC = DeviceLightRuntimeContract.Limit.DEFAULT_TEMPERATURE_PROTECTION_C,
            save = false
        ).toJson()

        assertEquals(setOf("thresholdC", "save"), payload.keySet())
        assertEquals(60.0, payload.getDouble("thresholdC"), 0.0)
        assertFalse(payload.getBoolean("save"))
        assertEquals(50.0, DeviceLightRuntimeContract.Limit.MIN_TEMPERATURE_PROTECTION_C, 0.0)
        assertEquals(60.0, DeviceLightRuntimeContract.Limit.DEFAULT_TEMPERATURE_PROTECTION_C, 0.0)
        assertEquals(70.0, DeviceLightRuntimeContract.Limit.MAX_TEMPERATURE_PROTECTION_C, 0.0)

        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(49.9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(70.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightTemperatureProtectionSetPayload(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun parsesSupportedStatusWithExactRuntimeAndLimits() {
        val parsed = DeviceLightTemperatureProtectionParser.parseStatus(
            supportedStatus(thresholdC = 62.5, active = true)
        ).getOrThrow()

        assertTrue(parsed.supported)
        assertTrue(parsed.temperatureProtection.supported)
        assertTrue(parsed.temperatureProtection.active)
        assertTrue(parsed.temperatureProtection.thresholdEditable)
        assertEquals(62.5, checkNotNull(parsed.temperatureProtection.thresholdC), 0.0)
        assertEquals(50.0, checkNotNull(parsed.temperatureProtection.minimumC), 0.0)
        assertEquals(70.0, checkNotNull(parsed.temperatureProtection.maximumC), 0.0)
        assertTrue(parsed.runtime.supportsStatusGet)
        assertTrue(parsed.runtime.supportsSet)
    }

    @Test
    fun parsesUnsupportedStatusOnlyWhenThresholdFieldsAreNull() {
        val parsed = DeviceLightTemperatureProtectionParser.parseStatus(
            unsupportedStatus()
        ).getOrThrow()

        assertFalse(parsed.supported)
        assertFalse(parsed.temperatureProtection.active)
        assertFalse(parsed.temperatureProtection.thresholdEditable)
        assertNull(parsed.temperatureProtection.thresholdC)
        assertNull(parsed.temperatureProtection.minimumC)
        assertNull(parsed.temperatureProtection.maximumC)
        assertFalse(parsed.runtime.supportsSet)

        val invalid = unsupportedStatus().also { status ->
            status.getJSONObject("temperatureProtection").put("thresholdC", 60.0)
        }
        assertTrue(DeviceLightTemperatureProtectionParser.parseStatus(invalid).isFailure)
    }

    @Test
    fun parsesSetResultAndEnforcesPersistenceEcho() {
        val result = DeviceLightTemperatureProtectionParser.parseSetResult(
            setResult(saved = true, saveRequested = true)
        ).getOrThrow()

        assertTrue(result.changed)
        assertTrue(result.saved)
        assertTrue(result.saveRequested)
        assertEquals(60.0, checkNotNull(result.status.temperatureProtection.thresholdC), 0.0)

        assertTrue(
            DeviceLightTemperatureProtectionParser.parseSetResult(
                setResult(saved = false, saveRequested = true)
            ).isFailure
        )
    }

    @Test
    fun parserRejectsUnknownFieldsAndSupportDrift() {
        val extraField = supportedStatus().put("unexpected", true)
        assertTrue(DeviceLightTemperatureProtectionParser.parseStatus(extraField).isFailure)

        val runtimeDrift = supportedStatus().also { status ->
            status.getJSONObject("runtime").put("supportsSet", false)
        }
        assertTrue(DeviceLightTemperatureProtectionParser.parseStatus(runtimeDrift).isFailure)

        val rangeDrift = supportedStatus().also { status ->
            status.getJSONObject("temperatureProtection").put("maximumC", 71.0)
        }
        assertTrue(DeviceLightTemperatureProtectionParser.parseStatus(rangeDrift).isFailure)
    }

    @Test
    fun repositorySendsExactTemperatureProtectionActions() = runBlocking {
        val gateway = ParsingGateway(
            mutableMapOf(
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET to
                    supportedStatus(),
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET to
                    setResult(saved = true, saveRequested = true)
            )
        )
        val repository = DeviceLightTemperatureProtectionRuntimeRepository(gateway)
        val deviceUid = DeviceUid("device-light-temperature")

        val status = repository.requestStatus(deviceUid)
        val set = repository.setThreshold(
            deviceUid = deviceUid,
            payload = DeviceLightTemperatureProtectionSetPayload(
                thresholdC = 62.5,
                save = true
            )
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
        assertTrue(gateway.payloads[1].getBoolean("save"))
        assertEquals(60.0, repository.currentStatus(deviceUid)
            ?.temperatureProtection?.thresholdC ?: Double.NaN, 0.0)
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
        saveRequested: Boolean
    ): JSONObject = JSONObject()
        .put("operation", "temperatureProtectionSet")
        .put("changed", true)
        .put("saved", saved)
        .put("saveRequested", saveRequested)
        .put("runtimeTransport", "websocket")
        .put("command", "light.temperature-protection.set")
        .put("event", "light.status.changed")
        .put("status", supportedStatus(thresholdC = 60.0))

    private fun runtime(supportsSet: Boolean): JSONObject = JSONObject()
        .put("module", "light")
        .put("readOnly", false)
        .put("supportsStatusGet", true)
        .put("supportsSet", supportsSet)
        .put("event", "light.status.changed")

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

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
