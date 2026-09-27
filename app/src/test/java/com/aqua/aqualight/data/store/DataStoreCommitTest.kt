package com.aqua.aqualight.data.store

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.auth.OwnerSessionMutationBarrier
import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreCommitTest {
    @Test
    fun `raw acknowledgement cancellation leaves an admitted disk write running`() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                val writer = launch { fixture.store.updateData { 1 } }
                fixture.serializer.entered.await()
                writer.cancelAndJoin()
                assertFalse(fixture.file.exists())
                fixture.serializer.release.complete(Unit)
                // Queue an acknowledgement behind the admitted write. Reading the flow while
                // the file/version update is in flight adds an unrelated DataStore read race.
                assertEquals(1, fixture.store.updateData { it })
                assertEquals(1, DataInputStream(fixture.file.inputStream()).use { it.readInt() })
            }
        }
    }

    @Test
    fun `cancelled caller retains session barrier until durable acknowledgement`() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                val writer = launch {
                    fixture.lease.withWrite { fixture.store.updateDataAwaitingCommit { 1 } }
                }
                fixture.serializer.entered.await()
                writer.cancel()
                val transition = async(start = CoroutineStart.UNDISPATCHED) {
                    fixture.barrier.withTransition {
                        assertEquals(1, DataInputStream(fixture.file.inputStream()).use { it.readInt() })
                        fixture.state.close()
                    }
                }
                assertFalse(transition.isCompleted)
                assertFalse(writer.isCompleted)
                fixture.serializer.release.complete(Unit)
                writer.join()
                transition.await()
                assertTrue(writer.isCancelled)
                assertEquals(1, fixture.store.data.first())
            }
        }
    }

    @Test
    fun `disk failure settles before the session barrier is released`() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                fixture.serializer.failWrite = true
                val writer = async {
                    runCatching { fixture.lease.withWrite { fixture.store.updateDataAwaitingCommit { 1 } } }
                }
                fixture.serializer.entered.await()
                val transition = async(start = CoroutineStart.UNDISPATCHED) {
                    fixture.barrier.withTransition { fixture.state.close() }
                }
                assertFalse(transition.isCompleted)
                fixture.serializer.release.complete(Unit)
                assertTrue(writer.await().exceptionOrNull() is IOException)
                transition.await()
                assertFalse(fixture.file.exists())
                assertEquals(0, fixture.store.data.first())
            }
        }
    }

    @Test
    fun `cancellation before admission does not dispatch a store mutation`() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                var transformed = false
                launch(Job().also { it.cancel() }, start = CoroutineStart.UNDISPATCHED) {
                    fixture.store.updateDataAwaitingCommit {
                        transformed = true
                        1
                    }
                }.join()
                assertFalse(transformed)
                assertFalse(fixture.serializer.entered.isCompleted)
                assertEquals(0, fixture.store.data.first())
            }
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) = withTimeout(5_000L) {
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            withContext(NonCancellable) {
                fixture.serializer.release.complete(Unit)
                fixture.storeJob.cancelAndJoin()
                fixture.directory.deleteRecursively()
            }
        }
    }

    private class Fixture {
        val state = OwnerSessionStateMachine()
        val barrier = OwnerSessionMutationBarrier(state)
        private val transition = state.begin("commit-owner").also { check(state.commit(it)) }
        val lease = barrier.bind("commit-owner", transition.generation)
        val directory: File = Files.createTempDirectory("aql-commit-test").toFile()
        val file = File(directory, "data.pb")
        val storeJob = SupervisorJob()
        val serializer = PausingSerializer { lease.requireCurrent() }
        val store = DataStoreFactory.create(
            serializer = serializer,
            scope = CoroutineScope(storeJob + Dispatchers.IO),
            produceFile = { file }
        )
    }

    private class PausingSerializer(private val validateSession: () -> Unit) : Serializer<Int> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var failWrite = false
        override val defaultValue = 0

        override suspend fun readFrom(input: InputStream): Int = DataInputStream(input).readInt()

        override suspend fun writeTo(t: Int, output: OutputStream) {
            entered.complete(Unit)
            release.await()
            validateSession()
            if (failWrite) throw IOException("Injected write failure")
            DataOutputStream(output).writeInt(t)
        }
    }
}
