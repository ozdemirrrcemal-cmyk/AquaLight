package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeCommandExecutorTest {

    private val deviceUid = DeviceUid("AQL-TEST-EXECUTOR")
    private val generationOne = DeviceRuntimeConnectionGeneration(1L)
    private val generationTwo = DeviceRuntimeConnectionGeneration(2L)

    @Test
    fun `pending request is registered before send and synchronous exact response completes`() =
        runBlocking {
            lateinit var executor: DeviceRuntimeCommandExecutor
            val activeSession = session(send = { outgoing ->
                val command = outgoing as AqlWsOutgoingMessage.Command
                assertEquals(1, executor.pendingCount())
                assertEquals(
                    DeviceRuntimeCompletionDisposition.COMPLETED,
                    executor.complete(
                        deviceUid,
                        generationOne,
                        success(command, "ready")
                    )
                )
                true
            })
            executor = newExecutor(activeSession)

            val outcome = executor.execute(deviceUid, EchoCommand())
                as DeviceRuntimeCommandOutcome.Success
            assertEquals("ready", outcome.value)
            assertEquals(0, executor.pendingCount())
        }

    @Test
    fun `old generation is ignored and exact current generation completes`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = newExecutor(
            session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        val command = requireNotNull(sent)

        assertEquals(
            DeviceRuntimeCompletionDisposition.UNMATCHED,
            executor.complete(deviceUid, generationTwo, success(command, "stale"))
        )
        assertEquals(1, executor.pendingCount())
        assertEquals(
            DeviceRuntimeCompletionDisposition.COMPLETED,
            executor.complete(deviceUid, generationOne, success(command, "current"))
        )
        assertEquals(
            "current",
            (awaiting.await() as DeviceRuntimeCommandOutcome.Success).value
        )
    }

    @Test
    fun `same id with different module action is protocol error`() = runBlocking {
        val (executor, awaiting, command) = pendingExecution()
        assertEquals(
            DeviceRuntimeCompletionDisposition.COMPLETED,
            executor.complete(
                deviceUid,
                generationOne,
                AqlWsIncomingMessage.Response(
                    id = command.id,
                    type = AqlWsContract.TYPE_RESPONSE,
                    module = AqlWsContract.MODULE_TIME,
                    action = AqlWsContract.ACTION_TIME_STATUS_GET,
                    data = JSONObject().put("value", "wrong"),
                    ok = true,
                    statusCode = 200
                )
            )
        )
        assertTrue(awaiting.await() is DeviceRuntimeCommandOutcome.ProtocolError)
    }

    @Test
    fun `firmware error remains distinct from protocol error`() = runBlocking {
        val (executor, awaiting, command) = pendingExecution()
        executor.complete(
            deviceUid,
            generationOne,
            AqlWsIncomingMessage.Error(
                id = command.id,
                type = AqlWsContract.TYPE_ERROR,
                module = command.module,
                action = command.action,
                data = JSONObject(),
                message = "Command rejected.",
                statusCode = 422,
                code = "invalid_field",
                field = "value"
            )
        )
        val error = awaiting.await() as DeviceRuntimeCommandOutcome.FirmwareError
        assertEquals(422, error.statusCode)
        assertEquals("invalid_field", error.code)
        assertEquals("value", error.field)
    }

    @Test
    fun `duplicate response is consumed as duplicate or late`() = runBlocking {
        val (executor, awaiting, command) = pendingExecution()
        val response = success(command, "done")
        assertEquals(
            DeviceRuntimeCompletionDisposition.COMPLETED,
            executor.complete(deviceUid, generationOne, response)
        )
        assertEquals("done", (awaiting.await() as DeviceRuntimeCommandOutcome.Success).value)
        assertEquals(
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE,
            executor.complete(deviceUid, generationOne, response)
        )
    }

    @Test
    fun `disconnect cancellation completes only matching generation`() = runBlocking {
        val (executor, awaiting, _) = pendingExecution()
        executor.cancelGeneration(deviceUid, generationTwo, "old socket")
        assertEquals(1, executor.pendingCount())
        executor.cancelGeneration(deviceUid, generationOne, "socket closed")

        val cancelled = awaiting.await() as DeviceRuntimeCommandOutcome.Cancelled
        assertEquals("socket closed", cancelled.reason)
        assertEquals(generationOne, cancelled.generation)
        assertEquals(0, executor.pendingCount())
    }

    @Test
    fun `connection authentication support and send failures are separate outcomes`() = runBlocking {
        val command = EchoCommand()
        assertTrue(
            newExecutor(null).execute(deviceUid, command) is
                DeviceRuntimeCommandOutcome.NotConnected
        )
        assertTrue(
            newExecutor(session(authenticated = false)).execute(deviceUid, command) is
                DeviceRuntimeCommandOutcome.NotAuthenticated
        )
        assertTrue(
            DeviceRuntimeCommandExecutor(
                sessionProvider = { session() },
                supportChecker = { _, _, _ -> false }
            ).execute(deviceUid, command) is DeviceRuntimeCommandOutcome.UnsupportedByDevice
        )
        assertTrue(
            newExecutor(session(send = { false })).execute(deviceUid, command) is
                DeviceRuntimeCommandOutcome.SendFailed
        )
    }

    @Test
    fun `invalid successful payload and ok false response become protocol errors`() = runBlocking {
        val invalidPayload = pendingExecution()
        invalidPayload.executor.complete(
            deviceUid,
            generationOne,
            AqlWsIncomingMessage.Response(
                id = invalidPayload.command.id,
                type = AqlWsContract.TYPE_RESPONSE,
                module = invalidPayload.command.module,
                action = invalidPayload.command.action,
                data = JSONObject().put("legacy", true),
                ok = true,
                statusCode = 200
            )
        )
        val invalidPayloadError = invalidPayload.awaiting.await()
            as DeviceRuntimeCommandOutcome.ProtocolError
        assertTrue(invalidPayloadError.reason.contains("cause=IllegalArgumentException"))
        assertTrue(invalidPayloadError.reason.contains("source=com.aqua.aqualight"))

        val falseSuccess = pendingExecution()
        falseSuccess.executor.complete(
            deviceUid,
            generationOne,
            AqlWsIncomingMessage.Response(
                id = falseSuccess.command.id,
                type = AqlWsContract.TYPE_RESPONSE,
                module = falseSuccess.command.module,
                action = falseSuccess.command.action,
                data = JSONObject().put("value", "ignored"),
                ok = false,
                statusCode = 500
            )
        )
        assertTrue(falseSuccess.awaiting.await() is DeviceRuntimeCommandOutcome.ProtocolError)
    }

    @Test
    fun `bounded timeout removes pending request and rejects late response`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = newExecutor(
            session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )

        val outcome = executor.execute(
            deviceUid = deviceUid,
            command = EchoCommand(),
            timeoutMillis = DeviceRuntimeCommandExecutor.MIN_TIMEOUT_MILLIS
        )
        assertTrue(outcome is DeviceRuntimeCommandOutcome.Timeout)
        assertEquals(0, executor.pendingCount())
        val command = requireNotNull(sent)
        assertEquals(
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE,
            executor.complete(deviceUid, generationOne, success(command, "late"))
        )
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.pendingExecution(): PendingExecution {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = newExecutor(
            session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        return PendingExecution(executor, awaiting, requireNotNull(sent))
    }

    private fun newExecutor(
        activeSession: DeviceRuntimeCommandSession?
    ): DeviceRuntimeCommandExecutor = DeviceRuntimeCommandExecutor(
        sessionProvider = { requested ->
            activeSession?.takeIf { session -> session.deviceUid == requested }
        },
        supportChecker = { _, _, _ -> true }
    )

    private fun session(
        authenticated: Boolean = true,
        send: (AqlWsOutgoingMessage) -> Boolean = { true }
    ): DeviceRuntimeCommandSession = DeviceRuntimeCommandSession(
        deviceUid = deviceUid,
        generation = generationOne,
        authenticated = authenticated,
        send = send
    )

    private fun success(
        command: AqlWsOutgoingMessage.Command,
        value: String
    ): AqlWsIncomingMessage.Response = AqlWsIncomingMessage.Response(
        id = command.id,
        type = AqlWsContract.TYPE_RESPONSE,
        module = command.module,
        action = command.action,
        data = JSONObject().put("value", value),
        ok = true,
        statusCode = 200
    )

    private data class PendingExecution(
        val executor: DeviceRuntimeCommandExecutor,
        val awaiting: kotlinx.coroutines.Deferred<DeviceRuntimeCommandOutcome<String>>,
        val command: AqlWsOutgoingMessage.Command
    )

    private class EchoCommand : DeviceRuntimeCommand<String> {
        override val module: String = AqlWsContract.MODULE_NETWORK
        override val action: String = AqlWsContract.ACTION_NETWORK_STATUS_GET

        override fun encodeData(): JSONObject = JSONObject()

        override fun parseSuccess(response: AqlWsIncomingMessage.Response): String {
            require(response.statusCode == 200)
            require(response.data.keys().asSequence().toSet() == setOf("value"))
            val value = response.data.get("value")
            require(value is String && value.isNotBlank())
            return value
        }
    }
}
