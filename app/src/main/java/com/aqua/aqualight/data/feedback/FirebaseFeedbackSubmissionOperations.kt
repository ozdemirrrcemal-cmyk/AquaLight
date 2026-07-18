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
import com.google.firebase.storage.storage
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Firebase feedback repository with deterministic upload rollback. */
class FirebaseFeedbackSubmissionOperations internal constructor(
    private val ownerUidProvider: () -> String?,
    private val documentStore: FeedbackDocumentStore,
    private val screenshotStore: FeedbackScreenshotStore,
    private val orphanStore: FeedbackOrphanStore
) : FeedbackRepository {

    override suspend fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?
    ): FeedbackSubmissionResult {
        cleanupOrphans()
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

        val upload = try {
            screenshotStore.upload(ownerUid, documentId, screenshotFile)
        } catch (error: FeedbackStorageUploadException) {
            error.storagePath?.let(orphanStore::add)
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
            return failure(FeedbackSubmissionFailureKind.UPLOAD, error)
        }

        data[FIELD_SCREENSHOT_URL] = upload.downloadUrl
        data[FIELD_SCREENSHOT_PATH] = upload.storagePath
        return try {
            documentStore.save(documentId, data)
            orphanStore.remove(upload.storagePath)
            FeedbackSubmissionResult.Success(documentId)
        } catch (persistenceError: Throwable) {
            try {
                screenshotStore.delete(upload.storagePath)
                orphanStore.remove(upload.storagePath)
                FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                        cause = persistenceError,
                        storagePath = upload.storagePath
                    )
                )
            } catch (rollbackError: Throwable) {
                orphanStore.add(upload.storagePath)
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

    override suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult {
        val pending = orphanStore.pendingPaths()
        var deleted = 0
        pending.forEach { path ->
            runCatching { screenshotStore.delete(path) }.onSuccess {
                orphanStore.remove(path)
                deleted += 1
            }
        }
        return FeedbackOrphanCleanupResult(
            attemptedCount = pending.size,
            deletedCount = deleted,
            remainingCount = orphanStore.pendingPaths().size
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
            failure(FeedbackSubmissionFailureKind.PERSISTENCE, error)
        }
    }

    private fun failure(
        kind: FeedbackSubmissionFailureKind,
        error: Throwable
    ): FeedbackSubmissionResult.Failure {
        return FeedbackSubmissionResult.Failure(FeedbackSubmissionFailure(kind, error))
    }

    companion object {
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

        fun create(context: Context): FirebaseFeedbackSubmissionOperations {
            val appContext = context.applicationContext
            return FirebaseFeedbackSubmissionOperations(
                ownerUidProvider = { FirebaseAuth.getInstance().currentUser?.uid },
                documentStore = FirebaseFeedbackDocumentStore(FirebaseFirestore.getInstance()),
                screenshotStore = FirebaseFeedbackScreenshotStore(Firebase.storage),
                orphanStore = SharedPreferencesFeedbackOrphanStore(appContext)
            )
        }
    }
}

internal interface FeedbackDocumentStore {
    fun newDocumentId(): String
    suspend fun save(documentId: String, data: Map<String, Any?>)
}

internal interface FeedbackScreenshotStore {
    suspend fun upload(ownerUid: String, documentId: String, file: File): FeedbackScreenshotUpload
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

    private companion object {
        const val COLLECTION = "feedback_items"
    }
}

private class FirebaseFeedbackScreenshotStore(
    private val storage: FirebaseStorage
) : FeedbackScreenshotStore {
    override suspend fun upload(
        ownerUid: String,
        documentId: String,
        file: File
    ): FeedbackScreenshotUpload {
        val path = "$ROOT/$ownerUid/$documentId.jpg"
        val reference = storage.reference.child(path)
        var uploaded = false
        return try {
            reference.putFile(Uri.fromFile(file)).awaitResult()
            uploaded = true
            FeedbackScreenshotUpload(path, reference.downloadUrl.awaitResult().toString())
        } catch (error: Throwable) {
            if (!uploaded) throw FeedbackStorageUploadException(error, null)
            val rollback = runCatching { reference.delete().awaitResult() }.exceptionOrNull()
            throw FeedbackStorageUploadException(
                uploadError = error,
                storagePath = if (rollback == null) null else path,
                rollbackCause = rollback
            )
        }
    }

    override suspend fun delete(storagePath: String) {
        storage.reference.child(storagePath).delete().awaitResult()
    }

    private companion object {
        const val ROOT = "feedback_screenshots"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(
            task.exception ?: IllegalStateException("Firebase task failed without an exception.")
        )
    }
}
