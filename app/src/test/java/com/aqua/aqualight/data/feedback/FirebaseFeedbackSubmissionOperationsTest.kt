package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun firestoreFailureAfterUploadDeletesStorageObject() = runTest {
        val documentStore = FakeDocumentStore().apply {
            saveError = IllegalStateException("firestore failed")
        }
        val screenshotStore = FakeScreenshotStore()
        val orphanStore = FakeOrphanStore()
        val repository = repository(documentStore, screenshotStore, orphanStore)

        val result = repository.submit(request(), File("feedback.jpg"))

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
        assertEquals(
            listOf("feedback_screenshots/owner/document-1.jpg"),
            screenshotStore.deletedPaths
        )
        assertTrue(orphanStore.pendingPaths().isEmpty())
    }

    @Test
    fun rollbackFailureQueuesStoragePathForRetry() = runTest {
        val documentStore = FakeDocumentStore().apply {
            saveError = IllegalStateException("firestore failed")
        }
        val screenshotStore = FakeScreenshotStore().apply {
            deleteError = IllegalStateException("delete failed")
        }
        val orphanStore = FakeOrphanStore()
        val repository = repository(documentStore, screenshotStore, orphanStore)

        val result = repository.submit(request(), File("feedback.jpg"))

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.ROLLBACK, failure.kind)
        assertEquals("feedback_screenshots/owner/document-1.jpg", failure.storagePath)
        assertTrue(failure.rollbackCause is IllegalStateException)
        assertTrue(orphanStore.pendingPaths().contains(failure.storagePath))
    }

    @Test
    fun orphanCleanupDeletesQueuedPathsAndKeepsFailures() = runTest {
        val documentStore = FakeDocumentStore()
        val screenshotStore = FakeScreenshotStore().apply {
            failingDeletePaths += "failed.jpg"
        }
        val orphanStore = FakeOrphanStore().apply {
            add("deleted.jpg")
            add("failed.jpg")
        }
        val repository = repository(documentStore, screenshotStore, orphanStore)

        val result = repository.cleanupOrphans()

        assertEquals(2, result.attemptedCount)
        assertEquals(1, result.deletedCount)
        assertEquals(1, result.remainingCount)
        assertFalse(orphanStore.pendingPaths().contains("deleted.jpg"))
        assertTrue(orphanStore.pendingPaths().contains("failed.jpg"))
    }

    private fun repository(
        documentStore: FakeDocumentStore,
        screenshotStore: FakeScreenshotStore,
        orphanStore: FakeOrphanStore
    ): FirebaseFeedbackSubmissionOperations {
        return FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { "owner" },
            documentStore = documentStore,
            screenshotStore = screenshotStore,
            orphanStore = orphanStore
        )
    }

    private fun request(): FeedbackSubmissionRequest {
        return FeedbackSubmissionRequest(
            category = "Bug",
            email = "user@example.com",
            message = "A reproducible issue",
            appVersion = "1.0",
            localeTag = "tr-TR"
        )
    }

    private class FakeDocumentStore : FeedbackDocumentStore {
        var saveError: Throwable? = null

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(
            documentId: String,
            data: Map<String, Any?>
        ) {
            saveError?.let { throw it }
        }
    }

    private class FakeScreenshotStore : FeedbackScreenshotStore {
        val deletedPaths = mutableListOf<String>()
        val failingDeletePaths = mutableSetOf<String>()
        var deleteError: Throwable? = null

        override suspend fun upload(
            ownerUid: String,
            documentId: String,
            file: File
        ): FeedbackScreenshotUpload {
            return FeedbackScreenshotUpload(
                storagePath = "feedback_screenshots/$ownerUid/$documentId.jpg",
                downloadUrl = "https://example.invalid/$documentId.jpg"
            )
        }

        override suspend fun delete(storagePath: String) {
            deletedPaths += storagePath
            deleteError?.let { throw it }
            if (storagePath in failingDeletePaths) {
                throw IllegalStateException("delete failed")
            }
        }
    }

    private class FakeOrphanStore : FeedbackOrphanStore {
        private val paths = linkedSetOf<String>()

        override fun pendingPaths(): Set<String> = paths.toSet()

        override fun add(path: String) {
            paths += path
        }

        override fun remove(path: String) {
            paths -= path
        }
    }
}
