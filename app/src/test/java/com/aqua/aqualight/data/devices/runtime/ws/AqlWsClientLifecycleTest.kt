package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlWsClientLifecycleTest {

    @Test
    fun callbacksFromDisconnectedSocketCannotPublishStateOrEvents() {
        val factory = RecordingWebSocketFactory()
        val client = AqlWsClient(webSocketFactory = factory)
        val observedEvents = CopyOnWriteArrayList<AqlWsEvent>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        observerScope.launch {
            client.events.collect { event ->
                observedEvents += event
            }
        }

        val deviceUid = DeviceUid("device-stale-disconnect")
        client.connect(deviceUid, endpoint("192.168.1.10")).getOrThrow()
        val staleConnection = factory.connections.single()

        client.disconnect()
        staleConnection.listener.onOpen(
            staleConnection.socket,
            responseFor(staleConnection.request)
        )
        staleConnection.listener.onMessage(staleConnection.socket, "{}")
        staleConnection.listener.onFailure(
            staleConnection.socket,
            IllegalStateException("late failure"),
            null
        )

        assertEquals(AqlWsConnectionState.Disconnected, client.connectionState.value)
        assertTrue(observedEvents.isEmpty())
        assertEquals(2, staleConnection.socket.closeCount.get())

        client.close()
        observerScope.cancel()
    }

    @Test
    fun reconnectRejectsCallbacksFromPreviousSocketForSameDevice() {
        val factory = RecordingWebSocketFactory()
        val client = AqlWsClient(webSocketFactory = factory)
        val observedEvents = CopyOnWriteArrayList<AqlWsEvent>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        observerScope.launch {
            client.events.collect { event ->
                observedEvents += event
            }
        }

        val deviceUid = DeviceUid("device-reconnect")
        client.connect(deviceUid, endpoint("192.168.1.10")).getOrThrow()
        val firstConnection = factory.connections[0]

        client.connect(deviceUid, endpoint("192.168.1.11")).getOrThrow()
        val secondConnection = factory.connections[1]
        secondConnection.listener.onOpen(
            secondConnection.socket,
            responseFor(secondConnection.request)
        )

        firstConnection.listener.onOpen(
            firstConnection.socket,
            responseFor(firstConnection.request)
        )
        firstConnection.listener.onMessage(firstConnection.socket, "{}")
        firstConnection.listener.onClosed(firstConnection.socket, 1000, "late close")

        val state = client.connectionState.value as AqlWsConnectionState.Connected
        assertEquals(deviceUid, state.deviceUid)
        assertTrue(state.url.contains("192.168.1.11"))
        assertEquals(1, observedEvents.size)
        assertTrue(observedEvents.single() is AqlWsEvent.Opened)

        client.close()
        observerScope.cancel()
    }

    private fun endpoint(ip: String): DeviceRuntimeEndpoint {
        return DeviceRuntimeEndpoint(
            ip = ip,
            wsPort = 80
        )
    }

    private fun responseFor(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
    }

    private class RecordingWebSocketFactory : WebSocket.Factory {
        val connections = CopyOnWriteArrayList<Connection>()

        override fun newWebSocket(
            request: Request,
            listener: WebSocketListener
        ): WebSocket {
            val socket = FakeWebSocket(request)
            connections += Connection(
                request = request,
                listener = listener,
                socket = socket
            )
            return socket
        }
    }

    private data class Connection(
        val request: Request,
        val listener: WebSocketListener,
        val socket: FakeWebSocket
    )

    private class FakeWebSocket(
        private val requestValue: Request
    ) : WebSocket {
        val closeCount = AtomicInteger(0)

        override fun request(): Request = requestValue

        override fun queueSize(): Long = 0L

        override fun send(text: String): Boolean = true

        override fun send(bytes: ByteString): Boolean = true

        override fun close(code: Int, reason: String?): Boolean {
            closeCount.incrementAndGet()
            return true
        }

        override fun cancel() = Unit
    }
}
