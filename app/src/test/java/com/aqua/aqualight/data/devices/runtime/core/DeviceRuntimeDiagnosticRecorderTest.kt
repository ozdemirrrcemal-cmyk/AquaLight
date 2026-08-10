package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceRuntimeDiagnosticRecorderTest {

    @Before
    fun setUp() {
        DeviceRuntimeDiagnosticRecorder.resetForTests()
    }

    @After
    fun tearDown() {
        DeviceRuntimeDiagnosticRecorder.resetForTests()
    }

    @Test
    fun `status trace keeps size and parser reason without keeping payload`() = runTest {
        val data = JSONObject()
            .put("channelCount", 4)
            .put("calibration", JSONObject().put("state", "idle"))

        DeviceRuntimeDiagnosticRecorder.recordConnection(UID, "AUTHENTICATED", true)
        DeviceRuntimeDiagnosticRecorder.recordPreparation(
            deviceUid = UID,
            module = MODULE,
            action = ACTION,
            authenticated = true,
            generation = GENERATION
        )
        DeviceRuntimeDiagnosticRecorder.recordRequestReady(UID, MODULE, ACTION)
        DeviceRuntimeDiagnosticRecorder.recordSend(UID, MODULE, ACTION, sent = true)
        DeviceRuntimeDiagnosticRecorder.recordReply(
            deviceUid = UID,
            module = MODULE,
            action = ACTION,
            message = AqlWsIncomingMessage.Response(
                id = "not-published",
                type = "response",
                module = MODULE,
                action = ACTION,
                data = data,
                ok = true,
                statusCode = 200
            )
        )
        DeviceRuntimeDiagnosticRecorder.recordParserFailure(
            UID,
            MODULE,
            ACTION,
            IllegalArgumentException("calibration keys differ")
        )
        DeviceRuntimeDiagnosticRecorder.recordOutcome(
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = UID,
                module = MODULE,
                action = ACTION,
                messageId = "not-published",
                generation = GENERATION,
                reason = "Typed contract mismatch."
            )
        )

        val snapshot = DeviceRuntimeDiagnosticRecorder.observe(UID).first()
        assertEquals("AUTHENTICATED", snapshot.connectionState)
        assertTrue(snapshot.authenticated)
        assertEquals("COMPLETED", snapshot.stage)
        assertEquals("PROTOCOL_ERROR", snapshot.outcome)
        assertEquals(1, snapshot.attempt)
        assertEquals(
            data.toString().toByteArray(StandardCharsets.UTF_8).size,
            snapshot.responseDataBytes
        )
        assertEquals(200, snapshot.responseStatusCode)
        assertTrue(snapshot.detail.orEmpty().contains("calibration keys differ"))
        assertFalse(snapshot.toString().contains(data.toString()))
        assertFalse(snapshot.toString().contains("not-published"))
    }

    @Test
    fun `reservoir trace exposes safe value types without retaining arbitrary strings`() = runTest {
        val privateValue = "secret-device-name"
        val data = JSONObject().put(
            "channels",
            JSONArray()
                .put(dosingChannel(JSONObject.NULL))
                .put(dosingChannel(12.5))
                .put(dosingChannel(" 12.5 "))
                .put(dosingChannel(privateValue))
        )

        DeviceRuntimeDiagnosticRecorder.recordReply(
            deviceUid = UID,
            module = MODULE,
            action = ACTION,
            message = AqlWsIncomingMessage.Response(
                id = "not-published",
                type = "response",
                module = MODULE,
                action = ACTION,
                data = data,
                ok = true,
                statusCode = 200
            )
        )

        val snapshot = DeviceRuntimeDiagnosticRecorder.observe(UID).first()
        val detail = snapshot.detail.orEmpty()
        assertTrue(detail.contains("ch0=json-null"))
        assertTrue(detail.contains("ch1=number:12.5"))
        assertTrue(detail.contains("ch2=string-numeric-trimmed:12.5"))
        assertTrue(detail.contains("ch3=string(length=18)"))
        assertFalse(snapshot.toString().contains(privateValue))
        assertFalse(snapshot.toString().contains("not-published"))
    }

    @Test
    fun `socket close marks transport stage without inventing response size`() = runTest {
        DeviceRuntimeDiagnosticRecorder.recordPreparation(
            deviceUid = UID,
            module = MODULE,
            action = ACTION,
            authenticated = true,
            generation = GENERATION
        )
        DeviceRuntimeDiagnosticRecorder.recordSend(UID, MODULE, ACTION, sent = true)
        DeviceRuntimeDiagnosticRecorder.recordSocketClosed(UID, 1009, "message too big")

        val snapshot = DeviceRuntimeDiagnosticRecorder.observe(UID).first()
        assertEquals("CLOSED", snapshot.connectionState)
        assertFalse(snapshot.authenticated)
        assertEquals("SOCKET_CLOSED_AFTER_REQUEST_SENT", snapshot.stage)
        assertEquals(1009, snapshot.socketCloseCode)
        assertEquals("message too big", snapshot.socketCloseReason)
        assertEquals(null, snapshot.responseDataBytes)
    }

    private fun dosingChannel(reservoirRemainingMl: Any): JSONObject = JSONObject().put(
        "dosing",
        JSONObject().put("reservoirRemainingMl", reservoirRemainingMl)
    )

    private companion object {
        val UID = DeviceUid("device-1")
        val GENERATION = DeviceRuntimeConnectionGeneration(7L)
        const val MODULE = "dosing"
        const val ACTION = "status.get"
    }
}
