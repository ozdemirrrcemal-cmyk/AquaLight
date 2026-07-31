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
    fun `pending request is registered before send and synchronous exact response completes`() = runBlocking {
        lateinit var executor: DeviceRuntimeCommandExecutor
        val session = DeviceRuntimeCommandSession(
            deviceUid = deviceUid,
            generation = generationOne,
            authenticated = true,
            send = { outgoing ->
                val command = outgoing as AqlWsOutgoingMessage.Command
                assertEquals(1, executor.pendingCount())
                assertTrue(
                    executor.complete(
                        deviceUid = deviceUid,
                        generation = generationOne,
                        message = success(command, value = "ready")
                    )
                )
                true
            }
        )
        executor = executor(session = session)

        val outcome = executor.execute(deviceUid, EchoCommand())
        val success = outcome as DeviceRuntimeCommandOutcome.Success
        assertEquals("ready", success.value)
        assertEquals(0, executor.pendingCount())
    }

    @Test
    fun `old generation response is ignored and exact current generation completes`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = executor(
            session = session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        val command = requireNotNull(sent)

        assertFalse(
            executor.complete(
                deviceUid = deviceUid,
                generation = generationTwo,
                message = success(command, value = "stale")
            )
        )
        assertEquals(1, executor.pendingCount())
        assertTrue(
            executor.complete(
                deviceUid = deviceUid,
                generation = generationOne,
                message = success(command, value = "current")
            )
        )

        val result = awaiting.await() as DeviceRuntimeCommandOutcome.Success
        assertEquals("current", result.value)
        assertEquals(0, executor.pendingCount())
    }

    @Test
    fun `same id with different module action is protocol error`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = executor(
            session = session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        val command = requireNotNull(sent)
        assertTrue(
            executor.complete(
                deviceUid = deviceUid,
                generation = generationOne,
                message = AqlWsIncomingMessage.Response(
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
        assertEquals(0, executor.pendingCount())
    }

    @Test
    fun `firmware error remains distinct from protocol error`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = executor(
            session = session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        val command = requireNotNull(sent)
        executor.complete(
            deviceUid = deviceUid,
            generation = generationOne,
            message = AqlWsIncomingMessage.Error(
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
    fun `disconnect cancellation completes only matching generation`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = executor(
            session = session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        requireNotNull(sent)

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
        val notConnected = executor(session = null).execute(deviceUid, command)
        assertTrue(notConnected is DeviceRuntimeCommandOutcome.NotConnected)

        val notAuthenticated = executor(
            session = session(authenticated = false)
        ).execute(deviceUid, command)
        assertTrue(notAuthenticated is DeviceRuntimeCommandOutcome.NotAuthenticated)

        val unsupported = DeviceRuntimeCommandExecutor(
            sessionProvider = { session() },
            supportChecker = { _, _, _ -> false }
        ).execute(deviceUid, command)
        assertTrue(unsupported is DeviceRuntimeCommandOutcome.UnsupportedByDevice)

        val sendFailed = executor(
            session = session(send = { false })
        ).execute(deviceUid, command)
        assertTrue(sendFailed is DeviceRuntimeCommandOutcome.SendFailed)
    }

    @Test
    fun `invalid successful payload becomes protocol error`() = runBlocking {
        var sent: AqlWsOutgoingMessage.Command? = null
        val executor = executor(
            session = session(send = { outgoing ->
                sent = outgoing as AqlWsOutgoingMessage.Command
                true
            })
        )
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(deviceUid, EchoCommand())
        }
        val command = requireNotNull(sent)
        executor.complete(
            deviceUid = deviceUid,
            generation = generationOne,
            message = AqlWsIncomingMessage.Response(
                id = command.id,
                type = AqlWsContract.TYPE_RESPONSE,
                module = command.module,
                action = command.action,
                data = JSONObject().put("legacy", true),
                ok = true,
                statusCode = 200
            )
        )

        assertTrue(awaiting.await() is DeviceRuntimeCommandOutcome.ProtocolError)
    }

    private fun executor(
        session: DeviceRuntimeCommandSession?
    ): DeviceRuntimeCommandExecutor = DeviceRuntimeCommandExecutor(
        sessionProvider = { requested -> session?.takeIf { it.deviceUid == requested } },
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

    private class EchoCommand : DeviceRuntimeCommand<String> {
        override val module: String = AqlWsContract.MODULE_NETWORK
        override val action: String = AqlWsContract.ACTION_NETWORK_STATUS_GET

        override fun encodeData(): JSONObject = JSONObject()

        override fun parseSuccess(response: AqlWsIncomingMessage.Response): String {
            require(response.statusCode == 200)
            val keys = response.data.keys().asSequence().toSet()
            require(keys == setOf("value"))
            val value = response.data.get("value")
            require(value is String && value.isNotBlank())
            return value
        }
    }
}
