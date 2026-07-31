package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNameSetCommandTest {

    @Test
    fun `serializes trimmed name and exact durable changed result`() {
        val command = DeviceNameSetCommand(
            DeviceNameSetRequest(customName = "  Salon Işığı  ", save = true)
        )
        val request = command.encodeData()
        assertEquals(setOf("customName", "save"), request.keys().asSequence().toSet())
        assertEquals("Salon Işığı", request.getString("customName"))
        assertTrue(request.getBoolean("save"))

        val result = command.parseSuccess(
            response(
                JSONObject()
                    .put("operation", "updated")
                    .put("changed", true)
                    .put("saveRequested", true)
                    .put("saved", true)
                    .put("event", "device.status.changed")
                    .put("status", status(customName = "Salon Işığı"))
            )
        )
        assertTrue(result.changed)
        assertTrue(result.saved)
        assertEquals("device.status.changed", result.event)
        assertEquals("Salon Işığı", result.status.effectiveDisplayName)
    }

    @Test
    fun `blank input uses canonical null clear and unchanged result has no event`() {
        val command = DeviceNameSetCommand(
            DeviceNameSetRequest(customName = "   ", save = true)
        )
        val request = command.encodeData()
        assertTrue(request.isNull("customName"))

        val result = command.parseSuccess(
            response(
                JSONObject()
                    .put("operation", "updated")
                    .put("changed", false)
                    .put("saveRequested", true)
                    .put("saved", true)
                    .put("status", status(customName = ""))
            )
        )
        assertFalse(result.changed)
        assertNull(result.event)
        assertEquals("", result.status.customName)
        assertEquals(PRODUCT_NAME, result.status.effectiveDisplayName)
    }

    @Test
    fun `enforces 64 UTF-8 bytes and rejects control characters`() {
        val exactly64Bytes = "ü".repeat(32)
        assertEquals(64, exactly64Bytes.toByteArray(Charsets.UTF_8).size)
        DeviceNameSetRequest(exactly64Bytes)

        assertTrue(
            runCatching { DeviceNameSetRequest("ü".repeat(33)) }.isFailure
        )
        assertTrue(
            runCatching { DeviceNameSetRequest("bad\nname") }.isFailure
        )
    }

    @Test
    fun `rejects event save status and key contract mismatches`() {
        val command = DeviceNameSetCommand(DeviceNameSetRequest("Tank", save = true))
        val wrongEvent = validChanged("Tank").put("event", "status.changed")
        val missingEvent = validChanged("Tank").apply { remove("event") }
        val eventWhenUnchanged = validUnchanged("Tank").put("event", "device.status.changed")
        val unsaved = validChanged("Tank").put("saved", false)
        val wrongName = validChanged("Different")
        val wrongMaxBytes = validChanged("Tank").apply {
            getJSONObject("status").put("maxBytes", 32)
        }
        val unknownField = validChanged("Tank").put("legacy", true)

        listOf(
            wrongEvent,
            missingEvent,
            eventWhenUnchanged,
            unsaved,
            wrongName,
            wrongMaxBytes,
            unknownField
        ).forEach { invalid ->
            assertTrue(runCatching { command.parseSuccess(response(invalid)) }.isFailure)
        }
    }

    private fun validChanged(customName: String): JSONObject = JSONObject()
        .put("operation", "updated")
        .put("changed", true)
        .put("saveRequested", true)
        .put("saved", true)
        .put("event", "device.status.changed")
        .put("status", status(customName))

    private fun validUnchanged(customName: String): JSONObject = JSONObject()
        .put("operation", "updated")
        .put("changed", false)
        .put("saveRequested", true)
        .put("saved", true)
        .put("status", status(customName))

    private fun status(customName: String): JSONObject = JSONObject()
        .put("productDisplayName", PRODUCT_NAME)
        .put("customName", customName)
        .put("effectiveDisplayName", customName.ifEmpty { PRODUCT_NAME })
        .put("editable", true)
        .put("maxBytes", 64)

    private fun response(data: JSONObject): AqlWsIncomingMessage.Response =
        AqlWsIncomingMessage.Response(
            id = "name-1",
            type = AqlWsContract.TYPE_RESPONSE,
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_NAME_SET,
            data = data,
            ok = true,
            statusCode = 200
        )

    private companion object {
        const val PRODUCT_NAME = "WRGB Pro Elite 120"
    }
}
