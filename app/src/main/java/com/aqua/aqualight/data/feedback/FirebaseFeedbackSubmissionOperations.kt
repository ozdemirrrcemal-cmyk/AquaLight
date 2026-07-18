package com.aqua.aqualight.data.feedback

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.application.feedback.FeedbackOrphanCleanupResult
import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Firebase feedback repository with a serialized, process-death-recoverable screenshot transaction.
 */
class FirebaseFeedbackSubmissionOperations internal constructor(
    private val ownerUidProvider: () -> String?,
    private val documentStore: FeedbackDocumentStore,
    private val screenshotStore: FeedbackScreenshotStore,
    private val journalStore: FeedbackSubmissionJournalStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeedbackRepository {

    private val transactionMutex = Mutex()

    override suspend fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?
    ): FeedbackSubmissionResult = withContext(dispatcher) {
        transactionMutex.withLock {
            cleanupPendingLocked()
            submitLocked(request, screenshotFile)
        }
    }

    override suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult = withContext(dispatcher) {
        transactionMutex.withLock {
            cleanupPendingLocked()
        }
    }

    private suspend fun submitLocked(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?
    ): FeedbackSubmissionResult {
        val ownerUid = ownerUidProvider()?.takeIf(String::isNotBlank) ?: ANONYMOUS_OWNER_UID
        val documentId = documentStore.newDocumentId()
        val data = linkedMapOf<String, Any?>(
            FIELD_CATEGORY to request.category,
            FIELD_EMAIL to request.email.ifBlank { null },
            FIELD_MESSAGE to request.message,
            FIELD_PLATFORM to PLATFORM_ANDROID,
            FIELD_APP_VERSION to request.appVersion,
            FIELD_LOCALE to request.localeTag,
            FIELD_STATUS to STATUS_NEW,
            FIELD_USER_ID to ownerUid
        )

        if (screenshotFile == null) return saveDocument(documentId, data)
        if (!screenshotFile.isFile || screenshotFile.length() <= 0L) {
            return failure(
                FeedbackSubmissionFailureKind.UPLOAD,
                IllegalArgumentException("Feedback screenshot is missing or empty.")
            )
        }

        val pending = PendingFeedbackUpload(
            documentId = documentId,
            storagePath = screenshotPath(ownerUid, documentId)
        )
        try {
            // Synchronous disk commit is intentional: upload must never start without recovery state.
            journalStore.put(pending)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return failure(FeedbackSubmissionFailureKind.GENERIC, error)
        }

        val upload = try {
            screenshotStore.upload(pending.storagePath, screenshotFile)
        } catch (error: FeedbackStorageUploadException) {
            if (error.storagePath == null) {
                journalStore.remove(documentId)
            }
            return FeedbackSubmissionResult.Failure(
                FeedbackSubmissionFailure(
                    kind = if (error.rollbackCause == null) {
                        FeedbackSubmissionFailureKind.UPLOAD
                    } else {
                        FeedbackSubmissionFailureKind.ROLLBACK
                    },
                    cause = error.uploadError,
                    storagePath = error.storagePath,
                    rollbackCause = error.rollbackCause
                )
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            // The production adapter emits an untyped error only before object creation.
            journalStore.remove(documentId)
            return failure(FeedbackSubmissionFailureKind.UPLOAD, error)
        }

        data[FIELD_SCREENSHOT_URL] = upload.downloadUrl
        data[FIELD_SCREENSHOT_PATH] = upload.storagePath
        return try {
            documentStore.save(documentId, data)
            journalStore.remove(documentId)
            FeedbackSubmissionResult.Success(documentId)
        } catch (persistenceError: Throwable) {
            persistenceError.throwIfCancellation()
            try {
                screenshotStore.delete(upload.storagePath)
                journalStore.remove(documentId)
                FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                        cause = persistenceError,
                        storagePath = upload.storagePath
                    )
                )
            } catch (rollbackError: Throwable) {
                rollbackError.throwIfCancellation()
                FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.ROLLBACK,
                        cause = persistenceError,
                        storagePath = upload.storagePath,
                        rollbackCause = rollbackError
                    )
                )
            }
        }
    }

    /**
     * Reconciles every durable journal entry against Firestore before deleting Storage data.
     * Verification failure or a conflicting document is fail-safe: the entry is retained.
     */
    private suspend fun cleanupPendingLocked(): FeedbackOrphanCleanupResult {
        val pending = journalStore.pendingEntries()
        var deleted = 0

        pending.forEach { entry ->
            when (
                runCatching {
                    documentStore.commitState(entry.documentId, entry.storagePath)
                }.getOrElse { error ->
                    error.throwIfCancellation()
                    FeedbackDocumentCommitState.UNVERIFIED
                }
            ) {
                FeedbackDocumentCommitState.COMMITTED -> {
                    // Firestore is authoritative; never delete its matching screenshot.
                    journalStore.remove(entry.documentId)
                }
                FeedbackDocumentCommitState.ABSENT -> {
                    try {
                        screenshotStore.delete(entry.storagePath)
                        journalStore.remove(entry.documentId)
                        deleted += 1
                    } catch (error: Throwable) {
                        error.throwIfCancellation()
                    }
                }
                FeedbackDocumentCommitState.CONFLICT,
                FeedbackDocumentCommitState.UNVERIFIED -> Unit
            }
        }

        return FeedbackOrphanCleanupResult(
            attemptedCount = pending.size,
            deletedCount = deleted,
            remainingCount = journalStore.pendingEntries().size
        )
    }

    private suspend fun saveDocument(
        documentId: String,
        data: Map<String, Any?>
    ): FeedbackSubmissionResult {
        return try {
            documentStore.save(documentId, data)
            FeedbackSubmissionResult.Success(documentId)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            failure(FeedbackSubmissionFailureKind.PERSISTENCE, error)
        }
    }

    private fun failure(
        kind: FeedbackSubmissionFailureKind,
        error: Throwable
    ): FeedbackSubmissionResult.Failure {
        return FeedbackSubmissionResult.Failure(FeedbackSubmissionFailure(kind, error))
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
    }

    companion object {
        private const val SCREENSHOT_ROOT = "feedback_screenshots"
        private const val ANONYMOUS_OWNER_UID = "anonymous"
        private const val PLATFORM_ANDROID = "android"
        private const val STATUS_NEW = "new"
        internal const val FIELD_CATEGORY = "category"
        internal const val FIELD_EMAIL = "email"
        internal const val FIELD_MESSAGE = "message"
        internal const val FIELD_PLATFORM = "platform"
        internal const val FIELD_APP_VERSION = "appVersion"
        internal const val FIELD_LOCALE = "locale"
        internal const val FIELD_STATUS = "status"
        internal const val FIELD_USER_ID = "userId"
        internal const val FIELD_CREATED_AT = "createdAt"
        internal const val FIELD_SCREENSHOT_URL = "screenshotUrl"
        internal const val FIELD_SCREENSHOT_PATH = "screenshotPath"

        internal fun screenshotPath(ownerUid: String, documentId: String): String {
            return "$SCREENSHOT_ROOT/$ownerUid/$documentId.jpg"
        }

        fun create(context: Context): FirebaseFeedbackSubmissionOperations {
            val appContext = context.applicationContext
            return FirebaseFeedbackSubmissionOperations(
                ownerUidProvider = { FirebaseAuth.getInstance().currentUser?.uid },
                documentStore = FirebaseFeedbackDocumentStore(FirebaseFirestore.getInstance()),
                screenshotStore = FirebaseFeedbackScreenshotStore(Firebase.storage),
                journalStore = SharedPreferencesFeedbackSubmissionJournalStore(appContext)
            )
        }
    }
}

internal enum class FeedbackDocumentCommitState {
    ABSENT,
    COMMITTED,
    CONFLICT,
    UNVERIFIED
}

internal interface FeedbackDocumentStore {
    fun newDocumentId(): String
    suspend fun save(documentId: String, data: Map<String, Any?>)
    suspend fun commitState(documentId: String, storagePath: String): FeedbackDocumentCommitState
}

internal interface FeedbackScreenshotStore {
    suspend fun upload(storagePath: String, file: File): FeedbackScreenshotUpload
    suspend fun delete(storagePath: String)
}

internal data class FeedbackScreenshotUpload(
    val storagePath: String,
    val downloadUrl: String
)

internal class FeedbackStorageUploadException(
    val uploadError: Throwable,
    val storagePath: String?,
    val rollbackCause: Throwable? = null
) : Exception(uploadError)

private class FirebaseFeedbackDocumentStore(
    private val firestore: FirebaseFirestore
) : FeedbackDocumentStore {
    override fun newDocumentId(): String = firestore.collection(COLLECTION).document().id

    override suspend fun save(documentId: String, data: Map<String, Any?>) {
        val stored = HashMap(data).apply {
            put(FirebaseFeedbackSubmissionOperations.FIELD_CREATED_AT, FieldValue.serverTimestamp())
        }
        firestore.collection(COLLECTION).document(documentId).set(stored).awaitResult()
    }

    override suspend fun commitState(
        documentId: String,
        storagePath: String
    ): FeedbackDocumentCommitState {
        val snapshot = firestore.collection(COLLECTION)
            .document(documentId)
            .get(Source.SERVER)
            .awaitResult()
        if (!snapshot.exists()) return FeedbackDocumentCommitState.ABSENT
        return if (
            snapshot.getString(FirebaseFeedbackSubmissionOperations.FIELD_SCREENSHOT_PATH) == storagePath
        ) {
            FeedbackDocumentCommitState.COMMITTED
        } else {
            FeedbackDocumentCommitState.CONFLICT
        }
    }

    private companion object {
        const val COLLECTION = "feedback_items"
    }
}

private class FirebaseFeedbackScreenshotStore(
    private val storage: FirebaseStorage
) : FeedbackScreenshotStore {
    override suspend fun upload(
        storagePath: String,
        file: File
    ): FeedbackScreenshotUpload {
        val reference = storage.reference.child(storagePath)
        var uploaded = false
        return try {
            reference.putFile(Uri.fromFile(file)).awaitResult()
            uploaded = true
            FeedbackScreenshotUpload(
                storagePath = storagePath,
                downloadUrl = reference.downloadUrl.awaitResult().toString()
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            if (!uploaded) throw FeedbackStorageUploadException(error, null)
            val rollback = runCatching { reference.delete().awaitResult() }.exceptionOrNull()
            throw FeedbackStorageUploadException(
                uploadError = error,
                storagePath = if (rollback == null) null else storagePath,
                rollbackCause = rollback
            )
        }
    }

    override suspend fun delete(storagePath: String) {
        try {
            storage.reference.child(storagePath).delete().awaitResult()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
    }
}

/** Firebase Tasks are not cancellable; wait for their authoritative terminal result. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(
            task.exception ?: IllegalStateException("Firebase task failed without an exception.")
        )
    }
}
