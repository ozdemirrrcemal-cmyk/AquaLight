package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFeedbackJournalFailureTest {

    @Test
    fun journalWriteFailurePreventsEveryRemoteOperation() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val journal = FailureJournalStore().apply {
                putError = IllegalStateException("journal disk full")
            }
            val repository = repository(events, journal)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
            assertTrue(events.isEmpty())
            assertTrue(journal.pendingEntries().isEmpty())
        }
    }

    @Test
    fun remoteCommitSuccessIsNotDowngradedWhenLocalJournalCleanupFails() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val journal = FailureJournalStore().apply {
                removeError = IllegalStateException("journal cleanup failed")
            }
            val screenshotStore = RecordingScreenshotStore(events)
            val repository = FirebaseFeedbackSubmissionOperations(
                ownerUidProvider = { "owner" },
                documentStore = RecordingDocumentStore(events),
                screenshotStore = screenshotStore,
                journalStore = journal,
                dispatcher = Dispatchers.Unconfined
            )

            val result = repository.submit(request(), screenshot)

            assertTrue(result is FeedbackSubmissionResult.Success)
            assertEquals(listOf("reserve", "upload", "commit"), events)
            assertEquals(1, journal.pendingEntries().size)
            assertTrue(screenshotStore.deletedPaths.isEmpty())
        }
    }

    private fun repository(
        events: MutableList<String>,
        journal: FailureJournalStore
    ): FirebaseFeedbackSubmissionOperations = FirebaseFeedbackSubmissionOperations(
        ownerUidProvider = { "owner" },
        documentStore = RecordingDocumentStore(events),
        screenshotStore = RecordingScreenshotStore(events),
        journalStore = journal,
        dispatcher = Dispatchers.Unconfined
    )

    private fun request() = FeedbackSubmissionRequest(
        category = "Bug",
        email = "user@example.com",
        message = "A reproducible issue",
        appVersion = "1.0",
        localeTag = "tr-TR"
    )

    private suspend inline fun withScreenshot(crossinline block: suspend (File) -> Unit) {
        val file = File.createTempFile("feedback-journal-failure-", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            block(file)
        } finally {
            file.delete()
        }
    }

    private class RecordingDocumentStore(
        private val events: MutableList<String>
    ) : FeedbackDocumentStore {
        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) = Unit

        override suspend fun reservePending(
            documentId: String,
            ownerUid: String,
            storagePath: String
        ) {
            events += "reserve"
        }

        override suspend fun commitPending(
            documentId: String,
            ownerUid: String,
            storagePath: String,
            data: Map<String, Any?>
        ) {
            events += "commit"
        }

        override suspend fun resolveForCleanup(
            documentId: String,
            ownerUid: String,
            storagePath: String
        ): FeedbackDocumentResolution {
            events += "resolve"
            return FeedbackDocumentResolution.COMMITTED
        }
    }

    private class RecordingScreenshotStore(
        private val events: MutableList<String>
    ) : FeedbackScreenshotStore {
        val deletedPaths = mutableListOf<String>()

        override suspend fun upload(storagePath: String, file: File): FeedbackScreenshotUpload {
            events += "upload"
            return FeedbackScreenshotUpload(
                storagePath = storagePath,
                downloadUrl = "https://example.invalid/document-1.jpg"
            )
        }

        override suspend fun delete(storagePath: String) {
            events += "delete"
            deletedPaths += storagePath
        }
    }

    private class FailureJournalStore : FeedbackSubmissionJournalStore {
        private val entries = linkedMapOf<String, PendingFeedbackUpload>()
        var putError: Throwable? = null
        var removeError: Throwable? = null

        override fun pendingEntries(): List<PendingFeedbackUpload> = entries.values.toList()

        override fun put(entry: PendingFeedbackUpload) {
            putError?.let { throw it }
            entries[entry.documentId] = entry
        }

        override fun remove(documentId: String) {
            removeError?.let { throw it }
            entries.remove(documentId)
        }
    }
}
