package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeGenerationBoundDispatchTest {
    @Test
    fun `reconnect before gateway capture rejects intent before serialization or send`() = runTest {
        val fixture = Fixture()
        fixture.generation = G2
        var encoded = false
        val outcome = fixture.executor.execute(
            UID,
            command { encoded = true }.boundToGeneration(G1)
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Cancelled)
        assertEquals(G1, (outcome as DeviceRuntimeCommandOutcome.Cancelled).generation)
        assertEquals(false, encoded)
        assertTrue(fixture.sent.isEmpty())
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `reused transport generation changes after capture cannot retarget dispatch`() = runTest {
        val fixture = Fixture()
        val outcome = fixture.executor.execute(
            UID,
            command { fixture.generation = G2 }.boundToGeneration(G1)
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.SendFailed)
        assertEquals(G1, (outcome as DeviceRuntimeCommandOutcome.SendFailed).generation)
        assertTrue(fixture.sent.isEmpty())
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `detaching captured session rejects send even when generation has not changed`() = runTest {
        val fixture = Fixture()
        val outcome = fixture.executor.execute(
            UID,
            command { fixture.current = false }.boundToGeneration(G1)
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.SendFailed)
        assertTrue(fixture.sent.isEmpty())
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `unbound bootstrap request uses captured replacement generation`() = runTest {
        val fixture = Fixture()
        fixture.generation = G2
        fixture.synchronousReply = true
        val outcome = fixture.executor.execute(UID, command())
            as DeviceRuntimeCommandOutcome.Success

        assertEquals(G2, outcome.generation)
        assertEquals("ready", outcome.value)
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `late old success and old cancellation cannot complete replacement request`() = runTest {
        val fixture = Fixture()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.executor.execute(UID, command().boundToGeneration(G1))
        }
        val oldCommand = fixture.sent.single()
        fixture.executor.cancelGeneration(UID, G1, "reconnect")
        fixture.generation = G2
        val replacement = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.executor.execute(UID, command().boundToGeneration(G2))
        }
        val newCommand = fixture.sent.last()

        assertEquals(
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE,
            fixture.executor.complete(UID, G1, success(oldCommand))
        )
        fixture.executor.cancelGeneration(UID, G1, "late close")
        assertEquals(1, fixture.executor.pendingCount())
        assertEquals(
            DeviceRuntimeCompletionDisposition.UNMATCHED,
            fixture.executor.complete(UID, G1, success(newCommand))
        )
        fixture.executor.complete(UID, G2, success(newCommand))

        assertEquals(G1, (first.await() as DeviceRuntimeCommandOutcome.Cancelled).generation)
        assertEquals(G2, (replacement.await() as DeviceRuntimeCommandOutcome.Success).generation)
    }

    @Test
    fun `timeout stays stamped with dispatch generation and late reply is consumed`() = runTest {
        val fixture = Fixture()
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.executor.execute(UID, command().boundToGeneration(G1), DEVICE_RUNTIME_MIN_TIMEOUT_MILLIS)
        }
        val sent = fixture.sent.single()
        fixture.generation = G2
        val outcome = awaiting.await() as DeviceRuntimeCommandOutcome.Timeout

        assertEquals(G1, outcome.generation)
        assertEquals(0, fixture.executor.pendingCount())
        assertEquals(
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE,
            fixture.executor.complete(UID, G1, success(sent))
        )
    }

    @Test
    fun `malformed old reply is correlated only to old generation`() = runTest {
        val fixture = Fixture()
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.executor.execute(UID, command().boundToGeneration(G1))
        }
        val malformed = success(fixture.sent.single()).copy(data = JSONObject())
        fixture.generation = G2
        assertEquals(
            DeviceRuntimeCompletionDisposition.UNMATCHED,
            fixture.executor.complete(UID, G2, malformed)
        )
        fixture.executor.complete(UID, G1, malformed)
        val outcome = awaiting.await() as DeviceRuntimeCommandOutcome.ProtocolError
        assertEquals(G1, outcome.generation)
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `serialization cancellation propagates without a pending request`() = runTest {
        val fixture = Fixture()
        val outcome = runCatching {
            fixture.executor.execute(UID, command { throw CancellationException("cancel encode") })
        }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertTrue(fixture.sent.isEmpty())
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `send cancellation propagates and removes registered request`() = runTest {
        val fixture = Fixture()
        fixture.cancelSend = true
        val outcome = runCatching { fixture.executor.execute(UID, command().boundToGeneration(G1)) }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `caller cancellation clears pending and consumes late success`() = runTest {
        val fixture = Fixture()
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.executor.execute(UID, command().boundToGeneration(G1))
        }
        awaiting.cancel()
        awaiting.join()
        assertEquals(0, fixture.executor.pendingCount())
        assertEquals(
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE,
            fixture.executor.complete(UID, G1, success(fixture.sent.single()))
        )
    }

    private class Fixture {
        val sessionLock = Any()
        var generation = G1
        var current = true
        var synchronousReply = false
        var cancelSend = false
        val sent = mutableListOf<AqlWsOutgoingMessage.Command>()
        val executor = DeviceRuntimeCommandExecutor(
            sessionProvider = { captureSession() },
            supportChecker = { _, _, _ -> true }
        )

        private fun captureSession(): DeviceRuntimeCommandSession = synchronized(sessionLock) {
            DeviceRuntimeCommandSession(UID, generation, true, ::send)
                .withGenerationBoundSend(sessionLock) { generation.takeIf { current } }
        }

        private fun send(outgoing: AqlWsOutgoingMessage): Boolean {
            if (cancelSend) throw CancellationException("cancel send")
            val command = outgoing as AqlWsOutgoingMessage.Command
            sent += command
            if (synchronousReply) executor.complete(UID, generation, success(command))
            return true
        }
    }

    private companion object {
        val UID = DeviceUid("generation-bound-command")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)

        fun command(beforeEncode: () -> Unit = {}): DeviceRuntimeCommand<String> =
            object : DeviceRuntimeCommand<String> {
                override val module = AqlWsContract.MODULE_NETWORK
                override val action = AqlWsContract.ACTION_NETWORK_STATUS_GET
                override fun encodeData(): JSONObject {
                    beforeEncode()
                    return JSONObject()
                }
                override fun parseSuccess(response: AqlWsIncomingMessage.Response): String =
                    response.data.getString("value")
            }

        fun success(command: AqlWsOutgoingMessage.Command) = AqlWsIncomingMessage.Response(
            id = command.id,
            type = AqlWsContract.TYPE_RESPONSE,
            module = command.module,
            action = command.action,
            data = JSONObject().put("value", "ready"),
            ok = true,
            statusCode = 200
        )
    }
}
