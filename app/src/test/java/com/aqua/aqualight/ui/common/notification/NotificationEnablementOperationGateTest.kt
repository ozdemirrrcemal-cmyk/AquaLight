package com.aqua.aqualight.ui.common.notification

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEnablementOperationGateTest {

    @Test
    fun `duplicate active intent remains single flight`() {
        val gate = NotificationEnablementOperationGate()
        val first = requireNotNull(gate.begin("enable-care"))
        val firstJob = Job()
        gate.attach(first, firstJob)

        assertNull(gate.begin("enable-care"))
        assertTrue(firstJob.isActive)
        assertTrue(gate.isCurrent(first))
    }

    @Test
    fun `latest different intent cancels and invalidates previous job`() {
        val gate = NotificationEnablementOperationGate()
        val first = requireNotNull(gate.begin("enable-care"))
        val firstJob = Job()
        gate.attach(first, firstJob)

        val latest = requireNotNull(gate.begin("enable-device-alert"))

        assertTrue(firstJob.isCancelled)
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(latest))
    }

    @Test
    fun `cancel invalidates ticket and cancels attached work`() {
        val gate = NotificationEnablementOperationGate()
        val ticket = requireNotNull(gate.begin("enable-care"))
        val job = Job()
        gate.attach(ticket, job)

        gate.cancel()

        assertTrue(job.isCancelled)
        assertFalse(gate.isCurrent(ticket))
        assertFalse(gate.hasActiveOperation)
    }

    @Test
    fun `lifecycle cancellation releases the active intent`() {
        val gate = NotificationEnablementOperationGate()
        val ticket = requireNotNull(gate.begin("enable-care"))
        val job = Job()
        gate.attach(ticket, job)

        job.cancel()

        assertFalse(gate.isCurrent(ticket))
        assertFalse(gate.hasActiveOperation)
    }

    @Test
    fun `stale completion cannot clear resumed continuation`() {
        val gate = NotificationEnablementOperationGate()
        val initial = requireNotNull(gate.begin("enable-care"))
        val initialJob = Job()
        gate.attach(initial, initialJob)
        val resumed = gate.resume("enable-care")

        gate.complete(initial)

        assertTrue(initialJob.isCancelled)
        assertFalse(gate.isCurrent(initial))
        assertTrue(gate.isCurrent(resumed))
        assertTrue(gate.hasActiveOperation)
    }
}
