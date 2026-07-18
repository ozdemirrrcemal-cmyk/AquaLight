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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import java.io.File
import java.util.Date
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
 * Firebase feedback repository with a serialized, process-death-recoverable media transaction.
 *
 * The Firestore document itself is the owner-scoped server transaction fence:
 * pending -> committed, or pending/absent -> aborted. A delayed writer can never overwrite an
 * aborted fence because every state transition is performed in a Firestore transaction.
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
        val data = baseDocument(request, ownerUid)

        if (screenshotFile == null) {
            data[FIELD_TRANSACTION_STATE] = TRANSACTION_COMMITTED
            return saveDocument(documentId, data)
        }
        if (!screenshotFile.isFile || screenshotFile.length() <= 0L) {
            return failure(
                FeedbackSubmissionFailureKind.UPLOAD,
                IllegalArgumentException("Feedback screenshot is missing or empty.")
            )
        }

        val pending = PendingFeedbackUpload(
            documentId = documentId,
            ownerUid = ownerUid,
            storagePath = screenshotPath(ownerUid, documentId)
        )
        try {
            // Synchronous disk commit is intentional: remote work never starts without recovery state.
            journalStore.put(pending)
            documentStore.reservePending(
                documentId = documentId,
                ownerUid = ownerUid,
                storagePath = pending.storagePath
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            runCatching { journalStore.remove(documentId) }
            return failure(FeedbackSubmissionFailureKind.PERSISTENCE, error)
        }

        val upload = try {
            screenshotStore.upload(pending.storagePath, screenshotFile)
        } catch (uploadError: Throwable) {
            uploadError.throwIfCancellation()
            return when (val resolution = reconcileEntryLocked(pending)) {
                ReconcileOutcome.Committed -> FeedbackSubmissionResult.Success(documentId)
                ReconcileOutcome.Cleaned -> failure(
                    FeedbackSubmissionFailureKind.UPLOAD,
                    uploadError
                )
                is ReconcileOutcome.Retained -> FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.ROLLBACK,
                        cause = uploadError,
                        storagePath = pending.storagePath,
                        rollbackCause = resolution.error
                    )
                )
            }
        }

        data[FIELD_SCREENSHOT_URL] = upload.downloadUrl
        data[FIELD_SCREENSHOT_PATH] = upload.storagePath
        data[FIELD_TRANSACTION_STATE] = TRANSACTION_COMMITTED

        return try {
            documentStore.commitPending(
                documentId = documentId,
                ownerUid = ownerUid,
                storagePath = upload.storagePath,
                data = data
            )
            // The remote committed fence is authoritative. Local cleanup is safely retryable.
            runCatching { journalStore.remove(documentId) }
            FeedbackSubmissionResult.Success(documentId)
        } catch (persistenceError: Throwable) {
            persistenceError.throwIfCancellation()
            when (val resolution = reconcileEntryLocked(pending)) {
                ReconcileOutcome.Committed -> FeedbackSubmissionResult.Success(documentId)
                ReconcileOutcome.Cleaned -> FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                        cause = persistenceError,
                        storagePath = upload.storagePath
                    )
                )
                is ReconcileOutcome.Retained -> FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.ROLLBACK,
                        cause = persistenceError,
                        storagePath = upload.storagePath,
                        rollbackCause = resolution.error
                    )
                )
            }
        }
    }

    private fun baseDocument(
        request: FeedbackSubmissionRequest,
        ownerUid: String
    ): LinkedHashMap<String, Any?> = linkedMapOf(
        FIELD_CATEGORY to request.category,
        FIELD_EMAIL to request.email.ifBlank { null },
        FIELD_MESSAGE to request.message,
        FIELD_PLATFORM to PLATFORM_ANDROID,
        FIELD_APP_VERSION to request.appVersion,
        FIELD_LOCALE to request.localeTag,
        FIELD_STATUS to STATUS_NEW,
        FIELD_USER_ID to ownerUid
    )

    /** Reconciles every durable local entry through the owner-scoped server transaction fence. */
    private suspend fun cleanupPendingLocked(): FeedbackOrphanCleanupResult {
        val pending = journalStore.pendingEntries()
        var deleted = 0

        pending.forEach { entry ->
            when (reconcileEntryLocked(entry)) {
                ReconcileOutcome.Committed -> Unit
                ReconcileOutcome.Cleaned -> deleted += 1
                is ReconcileOutcome.Retained -> Unit
            }
        }

        return FeedbackOrphanCleanupResult(
            attemptedCount = pending.size,
            deletedCount = deleted,
            remainingCount = journalStore.pendingEntries().size
        )
    }

    private suspend fun reconcileEntryLocked(
        entry: PendingFeedbackUpload
    ): ReconcileOutcome {
        val state = try {
            documentStore.resolveForCleanup(
                documentId = entry.documentId,
                ownerUid = entry.ownerUid,
                storagePath = entry.storagePath
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return ReconcileOutcome.Retained(error)
        }

        return when (state) {
            FeedbackDocumentResolution.COMMITTED -> {
                runCatching { journalStore.remove(entry.documentId) }
                ReconcileOutcome.Committed
            }
            FeedbackDocumentResolution.ABORTED -> {
                try {
                    screenshotStore.delete(entry.storagePath)
                    journalStore.remove(entry.documentId)
                    ReconcileOutcome.Cleaned
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    ReconcileOutcome.Retained(error)
                }
            }
            FeedbackDocumentResolution.CONFLICT,
            FeedbackDocumentResolution.UNVERIFIED -> ReconcileOutcome.Retained(
                IllegalStateException(
                    "Feedback transaction could not be safely reconciled: $state"
                )
            )
        }
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

    private sealed interface ReconcileOutcome {
        data object Committed : ReconcileOutcome
        data object Cleaned : ReconcileOutcome
        data class Retained(val error: Throwable) : ReconcileOutcome
    }

    companion object {
        private const val SCREENSHOT_ROOT = "feedback_screenshots"
        private const val ANONYMOUS_OWNER_UID = "anonymous"
        private const val PLATFORM_ANDROID = "android"
        private const val STATUS_NEW = "new"
        internal const val STATUS_MEDIA_PENDING = "media_pending"
        internal const val STATUS_MEDIA_ABORTED = "media_aborted"
        internal const val TRANSACTION_PENDING = "pending"
        internal const val TRANSACTION_COMMITTED = "committed"
        internal const val TRANSACTION_ABORTED = "aborted"
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
        internal const val FIELD_TRANSACTION_STATE = "mediaTransactionState"
        internal const val FIELD_TRANSACTION_EXPIRES_AT = "mediaTransactionExpiresAt"

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

internal enum class FeedbackDocumentResolution {
    COMMITTED,
    ABORTED,
    CONFLICT,
    UNVERIFIED
}

internal interface FeedbackDocumentStore {
    fun newDocumentId(): String
    suspend fun save(documentId: String, data: Map<String, Any?>)
    suspend fun reservePending(
        documentId: String,
        ownerUid: String,
        storagePath: String
    )
    suspend fun commitPending(
        documentId: String,
        ownerUid: String,
        storagePath: String,
        data: Map<String, Any?>
    )
    suspend fun resolveForCleanup(
        documentId: String,
        ownerUid: String,
        storagePath: String
    ): FeedbackDocumentResolution
}

internal interface FeedbackScreenshotStore {
    suspend fun upload(storagePath: String, file: File): FeedbackScreenshotUpload
    suspend fun delete(storagePath: String)
}

internal data class FeedbackScreenshotUpload(
    val storagePath: String,
    val downloadUrl: String
)

private class FirebaseFeedbackDocumentStore(
    private val firestore: FirebaseFirestore,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : FeedbackDocumentStore {

    override fun newDocumentId(): String = firestore.collection(COLLECTION).document().id

    override suspend fun save(documentId: String, data: Map<String, Any?>) {
        val stored = HashMap(data).apply {
            put(FirebaseFeedbackSubmissionOperations.FIELD_CREATED_AT, FieldValue.serverTimestamp())
        }
        firestore.collection(COLLECTION).document(documentId).set(stored).awaitResult()
    }

    override suspend fun reservePending(
        documentId: String,
        ownerUid: String,
        storagePath: String
    ) {
        val reference = firestore.collection(COLLECTION).document(documentId)
        firestore.runTransaction { transaction ->
            check(!transaction.get(reference).exists()) {
                "Feedback transaction document already exists."
            }
            transaction.set(reference, pendingMarker(ownerUid, storagePath))
        }.awaitResult()
    }

    override suspend fun commitPending(
        documentId: String,
        ownerUid: String,
        storagePath: String,
        data: Map<String, Any?>
    ) {
        val reference = firestore.collection(COLLECTION).document(documentId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            check(snapshot.exists()) { "Feedback transaction reservation is missing." }
            check(
                snapshot.getString(FirebaseFeedbackSubmissionOperations.FIELD_USER_ID) == ownerUid
            ) { "Feedback transaction owner changed." }
            check(
                snapshot.getString(FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_STATE) ==
                    FirebaseFeedbackSubmissionOperations.TRANSACTION_PENDING
            ) { "Feedback transaction is not pending." }
            check(
                snapshot.getString(FirebaseFeedbackSubmissionOperations.FIELD_SCREENSHOT_PATH) ==
                    storagePath
            ) { "Feedback transaction storage path changed." }

            val stored = HashMap(data).apply {
                put(FirebaseFeedbackSubmissionOperations.FIELD_CREATED_AT, FieldValue.serverTimestamp())
                remove(FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_EXPIRES_AT)
            }
            transaction.set(reference, stored)
        }.awaitResult()
    }

    override suspend fun resolveForCleanup(
        documentId: String,
        ownerUid: String,
        storagePath: String
    ): FeedbackDocumentResolution {
        val reference = firestore.collection(COLLECTION).document(documentId)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) {
                transaction.set(reference, abortedMarker(ownerUid, storagePath))
                return@runTransaction FeedbackDocumentResolution.ABORTED
            }

            val storedOwner = snapshot.getString(
                FirebaseFeedbackSubmissionOperations.FIELD_USER_ID
            )
            val storedPath = snapshot.getString(
                FirebaseFeedbackSubmissionOperations.FIELD_SCREENSHOT_PATH
            )
            if (storedOwner != ownerUid || storedPath != storagePath) {
                return@runTransaction FeedbackDocumentResolution.CONFLICT
            }

            when (
                snapshot.getString(
                    FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_STATE
                )
            ) {
                FirebaseFeedbackSubmissionOperations.TRANSACTION_COMMITTED ->
                    FeedbackDocumentResolution.COMMITTED

                FirebaseFeedbackSubmissionOperations.TRANSACTION_PENDING -> {
                    transaction.update(
                        reference,
                        mapOf(
                            FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_STATE to
                                FirebaseFeedbackSubmissionOperations.TRANSACTION_ABORTED,
                            FirebaseFeedbackSubmissionOperations.FIELD_STATUS to
                                FirebaseFeedbackSubmissionOperations.STATUS_MEDIA_ABORTED,
                            FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_EXPIRES_AT to
                                transactionExpiry()
                        )
                    )
                    FeedbackDocumentResolution.ABORTED
                }

                FirebaseFeedbackSubmissionOperations.TRANSACTION_ABORTED ->
                    FeedbackDocumentResolution.ABORTED

                else -> FeedbackDocumentResolution.CONFLICT
            }
        }.awaitResult()
    }

    private fun pendingMarker(ownerUid: String, storagePath: String): Map<String, Any> = mapOf(
        FirebaseFeedbackSubmissionOperations.FIELD_USER_ID to ownerUid,
        FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_STATE to
            FirebaseFeedbackSubmissionOperations.TRANSACTION_PENDING,
        FirebaseFeedbackSubmissionOperations.FIELD_STATUS to
            FirebaseFeedbackSubmissionOperations.STATUS_MEDIA_PENDING,
        FirebaseFeedbackSubmissionOperations.FIELD_SCREENSHOT_PATH to storagePath,
        FirebaseFeedbackSubmissionOperations.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_EXPIRES_AT to transactionExpiry()
    )

    private fun abortedMarker(ownerUid: String, storagePath: String): Map<String, Any> = mapOf(
        FirebaseFeedbackSubmissionOperations.FIELD_USER_ID to ownerUid,
        FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_STATE to
            FirebaseFeedbackSubmissionOperations.TRANSACTION_ABORTED,
        FirebaseFeedbackSubmissionOperations.FIELD_STATUS to
            FirebaseFeedbackSubmissionOperations.STATUS_MEDIA_ABORTED,
        FirebaseFeedbackSubmissionOperations.FIELD_SCREENSHOT_PATH to storagePath,
        FirebaseFeedbackSubmissionOperations.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FirebaseFeedbackSubmissionOperations.FIELD_TRANSACTION_EXPIRES_AT to transactionExpiry()
    )

    private fun transactionExpiry(): Date = Date(clockMillis() + TRANSACTION_TTL_MILLIS)

    private companion object {
        const val COLLECTION = "feedback_items"
        const val TRANSACTION_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
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
        reference.putFile(Uri.fromFile(file)).awaitResult()
        return FeedbackScreenshotUpload(
            storagePath = storagePath,
            downloadUrl = reference.downloadUrl.awaitResult().toString()
        )
    }

    override suspend fun delete(storagePath: String) {
        try {
            storage.reference.child(storagePath).delete().awaitResult()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
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
