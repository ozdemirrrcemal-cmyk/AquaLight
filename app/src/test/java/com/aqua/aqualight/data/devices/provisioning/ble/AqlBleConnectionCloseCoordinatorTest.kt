package com.aqua.aqualight.data.devices.provisioning.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlBleConnectionCloseCoordinatorTest {

    @Test
    fun `disconnect callback releases connection and cancels fallback`() {
        val harness = Harness()
        val connection = Any()
        var releasedCallbackCalls = 0

        harness.coordinator.beginGracefulClose(connection) {
            releasedCallbackCalls += 1
        }

        assertEquals(listOf(connection), harness.disconnected)
        assertEquals(1, harness.scheduled.size)
        assertTrue(
            harness.coordinator.handleConnectionState(
                connection = connection,
                disconnected = true,
                failed = false
            )
        )
        assertEquals(listOf(connection), harness.released)
        assertEquals(harness.scheduled, harness.cancelled)
        assertEquals(1, releasedCallbackCalls)

        harness.scheduled.single().run()
        assertEquals(1, harness.released.size)
        assertEquals(1, releasedCallbackCalls)
    }

    @Test
    fun `fallback releases connection when Android omits disconnect callback`() {
        val harness = Harness()
        val connection = Any()

        harness.coordinator.beginGracefulClose(connection)
        harness.scheduled.single().run()

        assertEquals(listOf(connection), harness.released)
        assertFalse(
            harness.coordinator.handleConnectionState(
                connection = connection,
                disconnected = true,
                failed = false
            )
        )
    }

    @Test
    fun `stale callback never owns a new or unrelated connection`() {
        val harness = Harness()
        val activeConnection = Any()
        val staleConnection = Any()

        harness.coordinator.beginGracefulClose(staleConnection)

        assertFalse(
            harness.coordinator.handleConnectionState(
                connection = activeConnection,
                disconnected = true,
                failed = false
            )
        )
        assertTrue(harness.released.isEmpty())
    }

    private class Harness {
        val scheduled = mutableListOf<Runnable>()
        val cancelled = mutableListOf<Runnable>()
        val disconnected = mutableListOf<Any>()
        val released = mutableListOf<Any>()

        val coordinator = AqlBleConnectionCloseCoordinator<Any>(
            schedule = { task, _ -> scheduled += task },
            cancel = { task -> cancelled += task },
            disconnect = { connection -> disconnected += connection },
            release = { connection -> released += connection },
            fallbackDelayMillis = CLOSE_FALLBACK_MS
        )
    }

    private companion object {
        const val CLOSE_FALLBACK_MS = 1_500L
    }
}
