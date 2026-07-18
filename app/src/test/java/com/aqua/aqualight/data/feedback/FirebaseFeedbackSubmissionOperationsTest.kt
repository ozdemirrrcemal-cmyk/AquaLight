package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun successfulSubmissionRemovesDurableJournalEntry() = runTest {
        withScreenshot { screenshot ->
            val documentStore = FakeDocumentStore()
            val screenshotStore = FakeScreenshotStore()
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            assertTrue(result is FeedbackSubmissionResult.Success)
            assertTrue(journalStore.pendingEntries().isEmpty())
            assertEquals(
                "feedback_screenshots/owner/document-1.jpg",
                screenshotStore.uploadedPath
            )
        }
    }

    @Test
    fun uploadFailureBeforeObjectExistsRemovesJournalEntry() = runTest {
        withScreenshot { screenshot ->
            val screenshotStore = FakeScreenshotStore().apply {
                uploadError = FeedbackStorageUploadException(
                    uploadError = IllegalStateException("upload failed"),
                    storagePath = null
                )
            }
            val journalStore = FakeJournalStore()
            val repository = repository(FakeDocumentStore(), screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.UPLOAD, failure.kind)
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun firestoreFailureAfterUploadDeletesStorageObject() = runTest {
        withScreenshot { screenshot ->
            val documentStore = FakeDocumentStore().apply {
                saveError = IllegalStateException("firestore failed")
            }
            val screenshotStore = FakeScreenshotStore()
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
            assertEquals(
                listOf("feedback_screenshots/owner/document-1.jpg"),
                screenshotStore.deletedPaths
            )
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun rollbackFailureKeepsJournalEntryForRetry() = runTest {
        withScreenshot { screenshot ->
            val documentStore = FakeDocumentStore().apply {
                saveError = IllegalStateException("firestore failed")
            }
            val screenshotStore = FakeScreenshotStore().apply {
                deleteError = IllegalStateException("delete failed")
            }
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.ROLLBACK, failure.kind)
            assertEquals("feedback_screenshots/owner/document-1.jpg", failure.storagePath)
            assertTrue(failure.rollbackCause is IllegalStateException)
            assertEquals(1, journalStore.pendingEntries().size)
        }
    }

    @Test
    fun cancellationAfterUploadKeepsJournalAndDoesNotGuessRollbackOutcome() = runTest {
        withScreenshot { screenshot ->
            val documentStore = FakeDocumentStore().apply {
                saveError = CancellationException("screen left")
            }
            val screenshotStore = FakeScreenshotStore()
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            var cancelled = false
            try {
                repository.submit(request(), screenshot)
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertTrue(screenshotStore.deletedPaths.isEmpty())
            assertEquals(1, journalStore.pendingEntries().size)
        }
    }

    @Test
    fun cleanupDeletesStorageOnlyWhenServerConfirmsDocumentAbsent() = runTest {
        val documentStore = FakeDocumentStore().apply {
            commitStates["document-1"] = FeedbackDocumentCommitState.ABSENT
        }
        val screenshotStore = FakeScreenshotStore()
        val journalStore = FakeJournalStore().apply { put(pending()) }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(1, result.attemptedCount)
        assertEquals(1, result.deletedCount)
        assertEquals(0, result.remainingCount)
        assertEquals(listOf(pending().storagePath), screenshotStore.deletedPaths)
    }

    @Test
    fun cleanupPreservesStorageWhenFirestoreAlreadyCommittedMatchingPath() = runTest {
        val documentStore = FakeDocumentStore().apply {
            commitStates["document-1"] = FeedbackDocumentCommitState.COMMITTED
        }
        val screenshotStore = FakeScreenshotStore()
        val journalStore = FakeJournalStore().apply { put(pending()) }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(1, result.attemptedCount)
        assertEquals(0, result.deletedCount)
        assertEquals(0, result.remainingCount)
        assertTrue(screenshotStore.deletedPaths.isEmpty())
    }

    @Test
    fun cleanupFailsSafeForConflictOrUnverifiedServerState() = runTest {
        val documentStore = FakeDocumentStore().apply {
            commitStates["conflict"] = FeedbackDocumentCommitState.CONFLICT
            verificationErrors["unverified"] = IllegalStateException("offline")
        }
        val screenshotStore = FakeScreenshotStore()
        val journalStore = FakeJournalStore().apply {
            put(PendingFeedbackUpload("conflict", "feedback_screenshots/owner/conflict.jpg"))
            put(PendingFeedbackUpload("unverified", "feedback_screenshots/owner/unverified.jpg"))
        }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(2, result.attemptedCount)
        assertEquals(0, result.deletedCount)
        assertEquals(2, result.remainingCount)
        assertTrue(screenshotStore.deletedPaths.isEmpty())
    }

    @Test
    fun missingOrEmptyScreenshotIsRejectedBeforeJournalOrUpload() = runTest {
        val screenshotStore = FakeScreenshotStore()
        val journalStore = FakeJournalStore()
        val repository = repository(FakeDocumentStore(), screenshotStore, journalStore)

        val result = repository.submit(request(), File("does-not-exist.jpg"))

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.UPLOAD, failure.kind)
        assertTrue(journalStore.pendingEntries().isEmpty())
        assertEquals(null, screenshotStore.uploadedPath)
    }

    private fun repository(
        documentStore: FakeDocumentStore,
        screenshotStore: FakeScreenshotStore,
        journalStore: FakeJournalStore
    ): FirebaseFeedbackSubmissionOperations {
        return FirebaseFeedbackSubmissionOperations(
            ownerUidProvider = { "owner" },
            documentStore = documentStore,
            screenshotStore = screenshotStore,
            journalStore = journalStore,
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

    private fun pending() = PendingFeedbackUpload(
        documentId = "document-1",
        storagePath = "feedback_screenshots/owner/document-1.jpg"
    )

    private suspend inline fun withScreenshot(crossinline block: suspend (File) -> Unit) {
        val file = File.createTempFile("feedback-test-", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            block(file)
        } finally {
            file.delete()
        }
    }

    private class FakeDocumentStore : FeedbackDocumentStore {
        var saveError: Throwable? = null
        val commitStates = mutableMapOf<String, FeedbackDocumentCommitState>()
        val verificationErrors = mutableMapOf<String, Throwable>()

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) {
            saveError?.let { throw it }
        }

        override suspend fun commitState(
            documentId: String,
            storagePath: String
        ): FeedbackDocumentCommitState {
            verificationErrors[documentId]?.let { throw it }
            return commitStates[documentId] ?: FeedbackDocumentCommitState.ABSENT
        }
    }

    private class FakeScreenshotStore : FeedbackScreenshotStore {
        val deletedPaths = mutableListOf<String>()
        var deleteError: Throwable? = null
        var uploadError: Throwable? = null
        var uploadedPath: String? = null

        override suspend fun upload(storagePath: String, file: File): FeedbackScreenshotUpload {
            uploadedPath = storagePath
            uploadError?.let { throw it }
            return FeedbackScreenshotUpload(
                storagePath = storagePath,
                downloadUrl = "https://example.invalid/document-1.jpg"
            )
        }

        override suspend fun delete(storagePath: String) {
            deletedPaths += storagePath
            deleteError?.let { throw it }
        }
    }

    private class FakeJournalStore : FeedbackSubmissionJournalStore {
        private val entries = linkedMapOf<String, PendingFeedbackUpload>()

        override fun pendingEntries(): List<PendingFeedbackUpload> = entries.values.toList()
        override fun put(entry: PendingFeedbackUpload) { entries[entry.documentId] = entry }
        override fun remove(documentId: String) { entries.remove(documentId) }
    }
}
