package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FirebaseFeedbackRepositoryTest {

    @Test
    fun authenticatedSubmissionPersistsExactFeedbackSchema() = runTest {
        val documentStore = FakeDocumentStore()
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        val result = repository.submit(request())

        assertEquals(FeedbackSubmissionResult.Success("document-1"), result)
        assertEquals("document-1", documentStore.savedDocumentId)
        assertEquals("owner", documentStore.savedData?.get("userId"))
        assertEquals("Bug", documentStore.savedData?.get("category"))
        assertEquals("user@example.com", documentStore.savedData?.get("email"))
        assertEquals("A reproducible issue", documentStore.savedData?.get("message"))
        assertEquals("android", documentStore.savedData?.get("platform"))
        assertEquals("1.0", documentStore.savedData?.get("appVersion"))
        assertEquals("tr-TR", documentStore.savedData?.get("locale"))
        assertEquals("new", documentStore.savedData?.get("status"))
        assertEquals(
            setOf(
                "category",
                "email",
                "message",
                "platform",
                "appVersion",
                "locale",
                "status",
                "userId"
            ),
            documentStore.savedData?.keys
        )
    }

    @Test
    fun blankOptionalEmailIsPersistedAsNull() = runTest {
        val documentStore = FakeDocumentStore()
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        val result = repository.submit(request(email = ""))

        assertEquals(FeedbackSubmissionResult.Success("document-1"), result)
        assertNull(documentStore.savedData?.get("email"))
    }

    @Test
    fun missingAuthenticatedOwnerFailsClosedWithoutCreatingDocument() = runTest {
        val documentStore = FakeDocumentStore()
        val repository = repository(ownerUid = null, documentStore = documentStore)

        val result = repository.submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.AUTHENTICATION, failure.kind)
        assertNull(failure.cause)
        assertNull(documentStore.savedDocumentId)
        assertNull(documentStore.savedData)
    }

    @Test
    fun persistenceFailureReturnsTypedFailure() = runTest {
        val error = IllegalStateException("firestore write failed")
        val documentStore = FakeDocumentStore().apply { saveError = error }
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        val result = repository.submit(request())

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
        assertSame(error, failure.cause)
    }

    @Test
    fun cancellationFromPersistenceIsPropagated() = runTest {
        val documentStore = FakeDocumentStore().apply {
            saveError = CancellationException("caller cancelled")
        }
        val repository = repository(ownerUid = "owner", documentStore = documentStore)

        var cancellation: CancellationException? = null
        try {
            repository.submit(request())
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertEquals("caller cancelled", cancellation?.message)
    }

    private fun repository(
        ownerUid: String?,
        documentStore: FakeDocumentStore
    ) = FirebaseFeedbackRepository(
        ownerUidProvider = { ownerUid },
        documentStore = documentStore,
        dispatcher = Dispatchers.Unconfined
    )

    private fun request(
        email: String = "user@example.com"
    ) = FeedbackSubmissionRequest(
        category = "Bug",
        email = email,
        message = "A reproducible issue",
        appVersion = "1.0",
        localeTag = "tr-TR"
    )

    private class FakeDocumentStore : FeedbackDocumentStore {
        var savedDocumentId: String? = null
        var savedData: Map<String, Any?>? = null
        var saveError: Throwable? = null

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) {
            saveError?.let { throw it }
            savedDocumentId = documentId
            savedData = data.toMap()
        }
    }
}
