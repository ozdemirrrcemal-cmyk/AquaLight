package com.aqua.aqualight.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerSessionMutationBarrierTest {
    @Test
    fun `binding requires the exact committed owner and generation`() {
        val fixture = Fixture()
        assertEquals(OWNER, fixture.lease.ownerUid)
        assertThrows(OwnerSessionExpiredException::class.java) { fixture.barrier.bind("other", 1L) }
        assertThrows(OwnerSessionExpiredException::class.java) { fixture.barrier.bind(OWNER, 2L) }
        assertThrows(IllegalArgumentException::class.java) { fixture.barrier.bind(" ", 1L) }
        assertThrows(IllegalArgumentException::class.java) { fixture.barrier.bind(OWNER, 0L) }
        val pending = fixture.state.begin(OWNER)
        assertThrows(OwnerSessionExpiredException::class.java) {
            fixture.barrier.bind(OWNER, pending.generation)
        }
        fixture.state.abort(pending)
        assertThrows(OwnerSessionExpiredException::class.java) { fixture.lease.requireCurrent() }
    }

    @Test
    fun `admitted write keeps its generation through commit before close runs`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val fixture = Fixture()
            val releaseCommit = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val writer = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.lease.withWrite {
                    releaseCommit.await()
                    fixture.lease.requireCurrent()
                    events += "commit"
                }
            }
            val close = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.barrier.withTransition {
                    events += "close"
                    fixture.state.close()
                }
            }
            assertFalse(close.isCompleted)
            releaseCommit.complete(Unit)
            writer.await()
            close.await()
            assertEquals(listOf("commit", "close"), events)
            assertTrue(runCatching { fixture.lease.withWrite {} }.exceptionOrNull() is OwnerSessionExpiredException)
        }
    }

    @Test
    fun `queued old writers cannot enter after account switch or same owner reentry`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            listOf("other", OWNER).forEach { nextOwner ->
                val fixture = Fixture()
                val releaseTransition = CompletableDeferred<Unit>()
                val transition = launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.barrier.withTransition {
                        fixture.state.close()
                        releaseTransition.await()
                        val next = fixture.state.begin(nextOwner)
                        assertTrue(fixture.state.commit(next))
                    }
                }
                var entered = false
                val writer = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { fixture.lease.withWrite { entered = true } }
                }
                releaseTransition.complete(Unit)
                transition.join()
                assertTrue(writer.await().exceptionOrNull() is OwnerSessionExpiredException)
                assertFalse(entered)
            }
        }
    }

    @Test
    fun `failed write releases the barrier without invalidating its session`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val fixture = Fixture()
            val failure = IllegalStateException("injected")
            assertSame(failure, runCatching { fixture.lease.withWrite { throw failure } }.exceptionOrNull())
            fixture.lease.withWrite { fixture.lease.requireCurrent() }
            fixture.barrier.withTransition { fixture.state.close() }
            assertTrue(runCatching { fixture.lease.requireCurrent() }.exceptionOrNull() is OwnerSessionExpiredException)
        }
    }

    @Test
    fun `cancelled waiter never mutates and does not block the next transition`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val fixture = Fixture()
            val holder = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.lease.withWrite { awaitCancellation() }
            }
            var entered = false
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.lease.withWrite { entered = true }
            }
            waiting.cancelAndJoin()
            holder.cancelAndJoin()
            fixture.barrier.withTransition { fixture.state.close() }
            assertFalse(entered)
        }
    }

    @Test
    fun `close waits for non cancellable rollback before the write releases its lease`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val fixture = Fixture()
            val cleanupEntered = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val writer = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.lease.withWrite {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupEntered.complete(Unit)
                            releaseCleanup.await()
                            fixture.lease.requireCurrent()
                        }
                    }
                }
            }
            writer.cancel()
            cleanupEntered.await()
            val close = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.barrier.withTransition { fixture.state.close() }
            }
            assertFalse(close.isCompleted)
            releaseCleanup.complete(Unit)
            writer.join()
            close.await()
            assertTrue(writer.isCancelled)
        }
    }

    @Test
    fun `already cancelled writer cannot enter an unlocked barrier`() = runBlocking {
        val fixture = Fixture()
        var entered = false
        launch(Job().also { it.cancel() }, start = CoroutineStart.UNDISPATCHED) {
            fixture.lease.withWrite { entered = true }
        }.join()
        assertFalse(entered)
    }

    @Test
    fun `child writer does not inherit permission to bypass the session barrier`() = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            val fixture = Fixture()
            val child = fixture.lease.withWrite {
                async(start = CoroutineStart.UNDISPATCHED) { fixture.lease.withWrite { "child" } }
                    .also { assertFalse(it.isCompleted) }
            }
            assertEquals("child", child.await())
        }
    }

    private class Fixture {
        val state = OwnerSessionStateMachine()
        val barrier = OwnerSessionMutationBarrier(state)
        private val transition = state.begin(OWNER).also { check(state.commit(it)) }
        val lease = barrier.bind(OWNER, transition.generation)
    }

    private companion object {
        const val OWNER = "lease-owner"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
