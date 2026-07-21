package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun successfulSubmissionPersistsTextFeedbackWithOwnerMetadata() = runTest {
        val documentStore = FakeDocumentStore()
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        val result = repository.submit(request())

        assertEquals(FeedbackSubmissionResult.Success("document-1"), result)
        assertEquals("document-1", documentStore.savedDocumentId)
        assertEquals("owner", documentStore.savedData["userId"])
        assertEquals("Bug", documentStore.savedData["category"])
        assertEquals("user@example.com", documentStore.savedData["email"])
        assertEquals("A reproducible issue", documentStore.savedData["message"])
        assertEquals("android", documentStore.savedData["platform"])
        assertEquals("1.0", documentStore.savedData["appVersion"])
        assertEquals("tr-TR", documentStore.savedData["locale"])
        assertEquals("new", documentStore.savedData["status"])
        assertFalse(documentStore.savedData.keys.any { it.contains("media", ignoreCase = true) })
    }

    @Test
    fun missingOrBlankOwnerUsesAnonymousIdentity() = runTest {
        listOf<String?>(null, "").forEach { ownerUid ->
            val documentStore = FakeDocumentStore()
            val repository = repository(ownerUid = ownerUid, documentStore = documentStore)

            val result = repository.submit(request(email = ""))

            assertTrue(result is FeedbackSubmissionResult.Success)
            assertEquals("anonymous", documentStore.savedData["userId"])
            assertTrue(documentStore.savedData.containsKey("email"))
            assertEquals(null, documentStore.savedData["email"])
        }
    }

    @Test
    fun persistenceFailureReturnsTypedFailure() = runTest {
        val expected = IllegalStateException("firestore unavailable")
        val documentStore = FakeDocumentStore().apply { saveError = expected }
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        val result = repository.submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
        assertSame(expected, failure.cause)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagated() = runTest {
        val documentStore = FakeDocumentStore().apply {
            saveError = CancellationException("screen left")
        }

        repository(ownerUid = "owner", documentStore = documentStore).submit(request())
    }

    private fun repository(
        ownerUid: String?,
        documentStore: FakeDocumentStore
    ): FirebaseFeedbackSubmissionOperations {
        return FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { ownerUid },
            documentStore = documentStore,
            dispatcher = Dispatchers.Unconfined
        )
    }

    private fun request(email: String = "user@example.com") = FeedbackSubmissionRequest(
        category = "Bug",
        email = email,
        message = "A reproducible issue",
        appVersion = "1.0",
        localeTag = "tr-TR"
    )

    private class FakeDocumentStore : FeedbackDocumentStore {
        var saveError: Throwable? = null
        var savedDocumentId: String? = null
        var savedData: Map<String, Any?> = emptyMap()

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) {
            saveError?.let { throw it }
            savedDocumentId = documentId
            savedData = data.toMap()
        }
    }
}
