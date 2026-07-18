package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFeedbackSubmissionOperationsTest {

    @Test
    fun successfulSubmissionReservesOwnerBeforeUploadAndCommitsAtomically() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events)
            val screenshotStore = FakeScreenshotStore(events)
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            assertTrue(result is FeedbackSubmissionResult.Success)
            assertEquals(listOf("reserve", "upload", "commit"), events)
            assertEquals("owner", documentStore.reservedOwnerUid)
            assertEquals("owner", documentStore.committedOwnerUid)
            assertTrue(journalStore.pendingEntries().isEmpty())
            assertEquals(
                "feedback_screenshots/owner/document-1.jpg",
                screenshotStore.uploadedPath
            )
        }
    }

    @Test
    fun reservationFailurePreventsStorageUpload() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                reserveError = IllegalStateException("reservation failed")
            }
            val screenshotStore = FakeScreenshotStore(events)
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
            assertEquals(listOf("reserve"), events)
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun uploadFailureAbortsFenceAndDeletesAnyPartialObject() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                resolution = FeedbackDocumentResolution.ABORTED
            }
            val screenshotStore = FakeScreenshotStore(events).apply {
                uploadError = IllegalStateException("upload failed")
            }
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.UPLOAD, failure.kind)
            assertEquals(listOf("reserve", "upload", "resolve", "delete"), events)
            assertEquals("owner", documentStore.resolvedOwnerUid)
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun firestoreFailureAfterUploadAbortsFenceAndDeletesStorageObject() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                commitError = IllegalStateException("firestore failed")
                resolution = FeedbackDocumentResolution.ABORTED
            }
            val screenshotStore = FakeScreenshotStore(events)
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.PERSISTENCE, failure.kind)
            assertEquals(
                listOf("reserve", "upload", "commit", "resolve", "delete"),
                events
            )
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun ambiguousCommitErrorReturnsSuccessWhenServerFenceIsCommitted() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                commitError = IllegalStateException("client lost acknowledgement")
                resolution = FeedbackDocumentResolution.COMMITTED
            }
            val screenshotStore = FakeScreenshotStore(events)
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            assertTrue(result is FeedbackSubmissionResult.Success)
            assertTrue(screenshotStore.deletedPaths.isEmpty())
            assertTrue(journalStore.pendingEntries().isEmpty())
        }
    }

    @Test
    fun rollbackFailureKeepsOwnerAwareJournalEntryForRetry() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                commitError = IllegalStateException("firestore failed")
                resolution = FeedbackDocumentResolution.ABORTED
            }
            val screenshotStore = FakeScreenshotStore(events).apply {
                deleteError = IllegalStateException("delete failed")
            }
            val journalStore = FakeJournalStore()
            val repository = repository(documentStore, screenshotStore, journalStore)

            val result = repository.submit(request(), screenshot)

            val failure = (result as FeedbackSubmissionResult.Failure).failure
            assertEquals(FeedbackSubmissionFailureKind.ROLLBACK, failure.kind)
            assertEquals("feedback_screenshots/owner/document-1.jpg", failure.storagePath)
            assertTrue(failure.rollbackCause is IllegalStateException)
            assertEquals("owner", journalStore.pendingEntries().single().ownerUid)
        }
    }

    @Test
    fun cancellationDuringCommitKeepsJournalAndDoesNotGuessRemoteOutcome() = runTest {
        withScreenshot { screenshot ->
            val events = mutableListOf<String>()
            val documentStore = FakeDocumentStore(events).apply {
                commitError = CancellationException("screen left")
            }
            val screenshotStore = FakeScreenshotStore(events)
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
    fun cleanupDeletesStorageOnlyAfterMatchingOwnerFenceIsAborted() = runTest {
        val events = mutableListOf<String>()
        val documentStore = FakeDocumentStore(events).apply {
            resolution = FeedbackDocumentResolution.ABORTED
        }
        val screenshotStore = FakeScreenshotStore(events)
        val journalStore = FakeJournalStore().apply { put(pending()) }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(1, result.attemptedCount)
        assertEquals(1, result.deletedCount)
        assertEquals(0, result.remainingCount)
        assertEquals("owner", documentStore.resolvedOwnerUid)
        assertEquals(listOf("resolve", "delete"), events)
    }

    @Test
    fun cleanupPreservesStorageWhenServerFenceIsCommitted() = runTest {
        val events = mutableListOf<String>()
        val documentStore = FakeDocumentStore(events).apply {
            resolution = FeedbackDocumentResolution.COMMITTED
        }
        val screenshotStore = FakeScreenshotStore(events)
        val journalStore = FakeJournalStore().apply { put(pending()) }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(1, result.attemptedCount)
        assertEquals(0, result.deletedCount)
        assertEquals(0, result.remainingCount)
        assertEquals(listOf("resolve"), events)
        assertTrue(screenshotStore.deletedPaths.isEmpty())
    }

    @Test
    fun cleanupFailsSafeForConflictOrUnverifiedServerState() = runTest {
        val events = mutableListOf<String>()
        val documentStore = FakeDocumentStore(events).apply {
            resolutions["conflict"] = FeedbackDocumentResolution.CONFLICT
            resolutionErrors["unverified"] = IllegalStateException("offline")
        }
        val screenshotStore = FakeScreenshotStore(events)
        val journalStore = FakeJournalStore().apply {
            put(
                PendingFeedbackUpload(
                    "conflict",
                    "owner",
                    "feedback_screenshots/owner/conflict.jpg"
                )
            )
            put(
                PendingFeedbackUpload(
                    "unverified",
                    "owner",
                    "feedback_screenshots/owner/unverified.jpg"
                )
            )
        }
        val repository = repository(documentStore, screenshotStore, journalStore)

        val result = repository.cleanupOrphans()

        assertEquals(2, result.attemptedCount)
        assertEquals(0, result.deletedCount)
        assertEquals(2, result.remainingCount)
        assertTrue(screenshotStore.deletedPaths.isEmpty())
    }

    @Test
    fun missingOrEmptyScreenshotIsRejectedBeforeReservationOrUpload() = runTest {
        val events = mutableListOf<String>()
        val screenshotStore = FakeScreenshotStore(events)
        val journalStore = FakeJournalStore()
        val repository = repository(FakeDocumentStore(events), screenshotStore, journalStore)

        val result = repository.submit(request(), File("does-not-exist.jpg"))

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.UPLOAD, failure.kind)
        assertTrue(journalStore.pendingEntries().isEmpty())
        assertTrue(events.isEmpty())
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
        ownerUid = "owner",
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

    private class FakeDocumentStore(
        private val events: MutableList<String>
    ) : FeedbackDocumentStore {
        var saveError: Throwable? = null
        var reserveError: Throwable? = null
        var commitError: Throwable? = null
        var resolution: FeedbackDocumentResolution = FeedbackDocumentResolution.ABORTED
        val resolutions = mutableMapOf<String, FeedbackDocumentResolution>()
        val resolutionErrors = mutableMapOf<String, Throwable>()
        var reservedOwnerUid: String? = null
        var committedOwnerUid: String? = null
        var resolvedOwnerUid: String? = null

        override fun newDocumentId(): String = "document-1"

        override suspend fun save(documentId: String, data: Map<String, Any?>) {
            saveError?.let { throw it }
        }

        override suspend fun reservePending(
            documentId: String,
            ownerUid: String,
            storagePath: String
        ) {
            events += "reserve"
            reservedOwnerUid = ownerUid
            reserveError?.let { throw it }
        }

        override suspend fun commitPending(
            documentId: String,
            ownerUid: String,
            storagePath: String,
            data: Map<String, Any?>
        ) {
            events += "commit"
            committedOwnerUid = ownerUid
            commitError?.let { throw it }
        }

        override suspend fun resolveForCleanup(
            documentId: String,
            ownerUid: String,
            storagePath: String
        ): FeedbackDocumentResolution {
            events += "resolve"
            resolvedOwnerUid = ownerUid
            resolutionErrors[documentId]?.let { throw it }
            return resolutions[documentId] ?: resolution
        }
    }

    private class FakeScreenshotStore(
        private val events: MutableList<String>
    ) : FeedbackScreenshotStore {
        val deletedPaths = mutableListOf<String>()
        var deleteError: Throwable? = null
        var uploadError: Throwable? = null
        var uploadedPath: String? = null

        override suspend fun upload(storagePath: String, file: File): FeedbackScreenshotUpload {
            events += "upload"
            uploadedPath = storagePath
            uploadError?.let { throw it }
            return FeedbackScreenshotUpload(
                storagePath = storagePath,
                downloadUrl = "https://example.invalid/document-1.jpg"
            )
        }

        override suspend fun delete(storagePath: String) {
            events += "delete"
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
