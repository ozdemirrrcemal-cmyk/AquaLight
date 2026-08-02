package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommonRuntimeContractTest {
    @Test
    fun `name command sends exact payload and parses firmware result`() = runBlocking {
        val gateway = ParsingGateway(nameResult())
        val repository = DeviceCommonRuntimeRepository(gateway)

        val outcome = repository.setName(
            DEVICE_UID,
            DeviceNameSetRequest(customName = "Reef Tank", save = true)
        )

        val success = outcome as DeviceRuntimeCommandOutcome.Success
        assertEquals("device", gateway.module)
        assertEquals("name.set", gateway.action)
        assertEquals("Reef Tank", gateway.data?.getString("customName"))
        assertTrue(gateway.data?.getBoolean("save") == true)
        assertEquals("Reef Tank", success.value.status.customName)
        assertTrue(success.value.saved)
    }

    @Test
    fun `empty custom name is encoded as a supported reset value`() {
        val request = DeviceNameSetRequest(customName = "", save = true).toJson()

        assertEquals("", request.getString("customName"))
    }

    @Test
    fun `null custom name is encoded as JSON null`() {
        val request = DeviceNameSetRequest(customName = null, save = false).toJson()

        assertTrue(request.isNull("customName"))
        assertTrue(!request.getBoolean("save"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom name rejects more than sixty four UTF8 bytes`() {
        DeviceNameSetRequest(customName = "ş".repeat(33))
    }

    @Test
    fun `unchanged result does not require event field`() {
        val parsed = DeviceCommonRuntimeParser.parseNameSet(
            nameResult(changed = false).apply { remove("event") }
        )

        assertTrue(!parsed.changed)
        assertNull(parsed.status.customName.takeIf(String::isNotEmpty))
    }

    private class ParsingGateway(
        private val responseData: JSONObject
    ) : DeviceRuntimeCommandGateway {
        var module: String? = null
        var action: String? = null
        var data: JSONObject? = null

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            module = command.module
            action = command.action
            data = command.encodeData()
            val response = AqlWsIncomingMessage.Response(
                id = "response-id",
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData,
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

    private fun nameResult(changed: Boolean = true): JSONObject = JSONObject()
        .put("operation", "deviceNameSet")
        .put("changed", changed)
        .put("saved", true)
        .put("saveRequested", true)
        .put("event", "device.status.changed")
        .put(
            "status",
            JSONObject()
                .put("productDisplayName", "AquaLight Light")
                .put("customName", if (changed) "Reef Tank" else "")
                .put(
                    "effectiveDisplayName",
                    if (changed) "Reef Tank" else "AquaLight Light"
                )
                .put("editable", true)
                .put("maxBytes", 64)
        )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-COMMON-DEVICE")
    }
}
