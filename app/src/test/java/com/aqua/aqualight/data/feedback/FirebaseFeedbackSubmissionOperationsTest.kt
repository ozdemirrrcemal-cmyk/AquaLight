package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun successfulSubmissionUsesOwnerScopedTransactionStore() = runTest {
        val store = FakeDocumentStore()
        val repository = repository(ownerUid = " owner ", documentStore = store)

        val result = repository.submit(request())

        assertEquals(FeedbackSubmissionResult.Success(SUBMISSION_ID), result)
        assertEquals(1, store.saveCount)
        assertEquals("owner", store.ownerUid)
        assertEquals(SUBMISSION_ID, store.request?.submissionId)
    }

    @Test
    fun missingOwnerReturnsAuthenticationFailureWithoutWriting() = runTest {
        val store = FakeDocumentStore()
        val result = repository(ownerUid = null, documentStore = store).submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.AUTHENTICATION, failure.kind)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun transactionNetworkFailureReturnsTypedNetworkFailure() = runTest {
        val store = FakeDocumentStore().apply {
            saveError = FeedbackDocumentStoreException(
                kind = FeedbackDocumentStoreFailureKind.NETWORK,
                cause = IllegalStateException("offline")
            )
        }

        val result = repository(ownerUid = "owner", documentStore = store).submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.NETWORK, failure.kind)
        assertTrue(failure.cause is FeedbackDocumentStoreException)
    }

    @Test
    fun transactionPersistenceFailureReturnsTypedPersistenceFailure() = runTest {
        val store = FakeDocumentStore().apply {
            saveError = FeedbackDocumentStoreException(
                kind = FeedbackDocumentStoreFailureKind.PERSISTENCE,
                cause = IllegalStateException("write rejected")
            )
        }

        val result = repository(ownerUid = "owner", documentStore = store).submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
    }

    @Test
    fun nonTerminatingTransactionTimesOutAsNetworkFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDocumentStore().apply {
            pendingResult = CompletableDeferred()
        }
        val repository = repository(
            ownerUid = "owner",
            documentStore = store,
            dispatcher = dispatcher,
            timeoutMillis = 1_000L
        )

        val deferred = async(dispatcher) { repository.submit(request()) }
        advanceTimeBy(1_001L)
        advanceUntilIdle()

        val failure = (deferred.await() as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.NETWORK, failure.kind)
        assertEquals(1, store.saveCount)
    }

    @Test(expected = CancellationException::class)
    fun lifecycleCancellationIsPropagated() = runTest {
        val store = FakeDocumentStore().apply {
            saveError = CancellationException("screen left")
        }

        repository(ownerUid = "owner", documentStore = store).submit(request())
    }

    private fun repository(
        ownerUid: String?,
        documentStore: FakeDocumentStore,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        timeoutMillis: Long = 15_000L
    ): FirebaseFeedbackSubmissionOperations {
        return FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { ownerUid },
            documentStore = documentStore,
            dispatcher = dispatcher,
            timeoutMillis = timeoutMillis
        )
    }

    private fun request() = FeedbackSubmissionRequest(
        submissionId = SUBMISSION_ID,
        category = "Bug",
        email = "user@example.com",
        message = "A reproducible issue",
        appVersion = "1.0",
        localeTag = "tr-TR"
    )

    private class FakeDocumentStore : FeedbackDocumentStore {
        var saveError: Throwable? = null
        var pendingResult: CompletableDeferred<String>? = null
        var saveCount: Int = 0
        var ownerUid: String? = null
        var request: FeedbackSubmissionRequest? = null

        override suspend fun save(
            ownerUid: String,
            request: FeedbackSubmissionRequest
        ): String {
            saveCount += 1
            this.ownerUid = ownerUid
            this.request = request
            saveError?.let { throw it }
            return pendingResult?.await() ?: request.submissionId
        }
    }

    private companion object {
        const val SUBMISSION_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
