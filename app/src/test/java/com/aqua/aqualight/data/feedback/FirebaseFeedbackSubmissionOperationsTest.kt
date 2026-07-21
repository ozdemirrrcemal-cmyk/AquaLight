package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun `authenticated text feedback is stored without media fields`() = runTest {
        val store = FakeDocumentStore()
        val repository = repository(ownerUid = "owner-1", store = store)

        val result = repository.submit(request())

        assertTrue(result is FeedbackSubmissionResult.Success)
        assertEquals("owner-1", store.data?.get("userId"))
        assertEquals("Bug", store.data?.get("category"))
        assertEquals("user@example.com", store.data?.get("email"))
        assertEquals("A reproducible issue", store.data?.get("message"))
        assertEquals("android", store.data?.get("platform"))
        assertEquals("new", store.data?.get("status"))
        assertEquals("1.0", store.data?.get("appVersion"))
        assertEquals("tr-TR", store.data?.get("locale"))
        assertEquals(
            setOf("category", "email", "message", "platform", "appVersion", "locale", "status", "userId"),
            store.data?.keys
        )
    }

    @Test
    fun `missing session uses anonymous marker and blank email stays optional`() = runTest {
        val store = FakeDocumentStore()
        val repository = repository(ownerUid = null, store = store)

        val result = repository.submit(request().copy(email = "   "))

        assertTrue(result is FeedbackSubmissionResult.Success)
        assertEquals("anonymous", store.data?.get("userId"))
        assertEquals("", store.data?.get("email"))
    }

    @Test
    fun `firestore failure is reported as persistence failure`() = runTest {
        val store = FakeDocumentStore().apply {
            error = IllegalStateException("write failed")
        }
        val repository = repository(ownerUid = "owner-1", store = store)

        val result = repository.submit(request()) as FeedbackSubmissionResult.Failure

        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, result.failure.kind)
        assertTrue(result.failure.cause is IllegalStateException)
    }

    @Test
    fun `stalled firestore write reaches a terminal timeout failure`() = runTest {
        val store = FakeDocumentStore().apply { blockForever = true }
        val repository = FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { "owner-1" },
            documentStore = store,
            dispatcher = StandardTestDispatcher(testScheduler),
            writeTimeoutMillis = 100L
        )

        val deferred = async { repository.submit(request()) }
        advanceTimeBy(100L)
        runCurrent()

        val result = deferred.await() as FeedbackSubmissionResult.Failure
        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, result.failure.kind)
    }

    @Test
    fun `external cancellation is never converted to feedback failure`() = runTest {
        val store = FakeDocumentStore().apply {
            error = CancellationException("cancelled")
        }
        val repository = repository(ownerUid = "owner-1", store = store)

        var cancelled = false
        try {
            repository.submit(request())
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private fun repository(
        ownerUid: String?,
        store: FakeDocumentStore
    ): FirebaseFeedbackSubmissionOperations {
        return FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { ownerUid },
            documentStore = store,
            dispatcher = Dispatchers.Unconfined
        )
    }

    private fun request() = FeedbackSubmissionRequest(
        category = "Bug",
        email = "user@example.com",
        message = "A reproducible issue",
        appVersion = "1.0",
        localeTag = "tr-TR"
    )

    private class FakeDocumentStore : FeedbackDocumentStore {
        var data: Map<String, Any?>? = null
        var error: Throwable? = null
        var blockForever: Boolean = false

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) {
            error?.let { throw it }
            if (blockForever) awaitCancellation()
            this.data = data
        }
    }
}
