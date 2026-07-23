package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Authenticated, text-only feedback persistence that remains compatible with the Firebase Spark plan.
 *
 * A Firestore transaction is intentional: transactions fail while offline instead of queuing a local
 * write. A stable submission UUID makes a late completion or retry idempotent.
 */
class FirebaseFeedbackSubmissionOperations internal constructor(
    private val ownerUidProvider: () -> String?,
    private val documentStore: FeedbackDocumentStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = SUBMISSION_TIMEOUT_MILLIS
) : FeedbackRepository {

    override suspend fun submit(
        request: FeedbackSubmissionRequest
    ): FeedbackSubmissionResult = withContext(dispatcher) {
        val ownerUid = ownerUidProvider()?.trim()?.takeIf(String::isNotBlank)
            ?: return@withContext failure(
                kind = FeedbackSubmissionFailureKind.AUTHENTICATION,
                cause = IllegalStateException("Authenticated feedback owner is unavailable.")
            )

        try {
            val documentId = withTimeout(timeoutMillis) {
                documentStore.save(
                    ownerUid = ownerUid,
                    request = request
                )
            }
            FeedbackSubmissionResult.Success(documentId)
        } catch (timeout: TimeoutCancellationException) {
            failure(
                kind = FeedbackSubmissionFailureKind.NETWORK,
                cause = timeout
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FeedbackDocumentStoreException) {
            failure(
                kind = when (error.kind) {
                    FeedbackDocumentStoreFailureKind.NETWORK ->
                        FeedbackSubmissionFailureKind.NETWORK
                    FeedbackDocumentStoreFailureKind.PERSISTENCE ->
                        FeedbackSubmissionFailureKind.PERSISTENCE
                },
                cause = error
            )
        } catch (error: IOException) {
            failure(
                kind = FeedbackSubmissionFailureKind.NETWORK,
                cause = error
            )
        } catch (error: Throwable) {
            failure(
                kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                cause = error
            )
        }
    }

    private fun failure(
        kind: FeedbackSubmissionFailureKind,
        cause: Throwable
    ): FeedbackSubmissionResult.Failure {
        return FeedbackSubmissionResult.Failure(
            FeedbackSubmissionFailure(
                kind = kind,
                cause = cause
            )
        )
    }

    companion object {
        internal const val SUBMISSION_TIMEOUT_MILLIS = 15_000L
        private const val PLATFORM_ANDROID = "android"
        private const val STATUS_NEW = "new"
        internal const val FIELD_SUBMISSION_ID = "submissionId"
        internal const val FIELD_CATEGORY = "category"
        internal const val FIELD_EMAIL = "email"
        internal const val FIELD_MESSAGE = "message"
        internal const val FIELD_PLATFORM = "platform"
        internal const val FIELD_APP_VERSION = "appVersion"
        internal const val FIELD_LOCALE = "locale"
        internal const val FIELD_STATUS = "status"
        internal const val FIELD_USER_ID = "userId"
        internal const val FIELD_CREATED_AT = "createdAt"

        fun create(): FirebaseFeedbackSubmissionOperations {
            return FirebaseFeedbackSubmissionOperations(
                ownerUidProvider = { FirebaseAuth.getInstance().currentUser?.uid },
                documentStore = FirebaseFeedbackDocumentStore(FeedbackFirestoreProvider.get())
            )
        }

        internal fun storedData(
            ownerUid: String,
            request: FeedbackSubmissionRequest
        ): Map<String, Any?> {
            return linkedMapOf(
                FIELD_SUBMISSION_ID to request.submissionId,
                FIELD_CATEGORY to request.category,
                FIELD_EMAIL to request.email.ifBlank { null },
                FIELD_MESSAGE to request.message,
                FIELD_PLATFORM to PLATFORM_ANDROID,
                FIELD_APP_VERSION to request.appVersion,
                FIELD_LOCALE to request.localeTag,
                FIELD_STATUS to STATUS_NEW,
                FIELD_USER_ID to ownerUid,
                FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
        }
    }
}

internal fun interface FeedbackDocumentStore {
    suspend fun save(
        ownerUid: String,
        request: FeedbackSubmissionRequest
    ): String
}

internal enum class FeedbackDocumentStoreFailureKind {
    NETWORK,
    PERSISTENCE
}

internal class FeedbackDocumentStoreException(
    val kind: FeedbackDocumentStoreFailureKind,
    cause: Throwable
) : Exception(cause)

private class FirebaseFeedbackDocumentStore(
    private val firestore: FirebaseFirestore
) : FeedbackDocumentStore {

    override suspend fun save(
        ownerUid: String,
        request: FeedbackSubmissionRequest
    ): String {
        val document = firestore.collection(ROOT_COLLECTION)
            .document(ownerUid)
            .collection(SUBMISSIONS_COLLECTION)
            .document(request.submissionId)

        return try {
            firestore.runTransaction { transaction ->
                val existing = transaction.get(document)
                if (existing.exists()) {
                    if (!existing.matches(ownerUid, request)) {
                        throw FeedbackDocumentStoreException(
                            kind = FeedbackDocumentStoreFailureKind.PERSISTENCE,
                            cause = IllegalStateException(
                                "Feedback submission identity already belongs to different content."
                            )
                        )
                    }
                } else {
                    transaction.set(
                        document,
                        FirebaseFeedbackSubmissionOperations.storedData(ownerUid, request)
                    )
                }
                request.submissionId
            }.awaitNonNull()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FeedbackDocumentStoreException) {
            throw error
        } catch (error: FirebaseFirestoreException) {
            throw FeedbackDocumentStoreException(
                kind = when (error.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        FeedbackDocumentStoreFailureKind.NETWORK
                    else -> FeedbackDocumentStoreFailureKind.PERSISTENCE
                },
                cause = error
            )
        } catch (error: Throwable) {
            throw FeedbackDocumentStoreException(
                kind = FeedbackDocumentStoreFailureKind.PERSISTENCE,
                cause = error
            )
        }
    }

    private fun DocumentSnapshot.matches(
        ownerUid: String,
        request: FeedbackSubmissionRequest
    ): Boolean {
        return getString(FirebaseFeedbackSubmissionOperations.FIELD_USER_ID) == ownerUid &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_SUBMISSION_ID) ==
            request.submissionId &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_CATEGORY) == request.category &&
            get(FirebaseFeedbackSubmissionOperations.FIELD_EMAIL) ==
            request.email.ifBlank { null } &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_MESSAGE) == request.message &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_PLATFORM) == "android" &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_APP_VERSION) ==
            request.appVersion &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_LOCALE) == request.localeTag &&
            getString(FirebaseFeedbackSubmissionOperations.FIELD_STATUS) == "new"
    }

    private companion object {
        const val ROOT_COLLECTION = "feedback_items"
        const val SUBMISSIONS_COLLECTION = "submissions"
    }
}

/**
 * Cancels only the coroutine wait. A Firestore task may complete later, which is safe because the
 * transaction uses one stable owner/submission document path and therefore cannot create a duplicate.
 */
private suspend fun <T : Any> Task<T>.awaitNonNull(): T {
    val deferred = CompletableDeferred<T>()
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val result = task.result
            if (result != null) {
                deferred.complete(result)
            } else {
                deferred.completeExceptionally(
                    IllegalStateException("Firebase task returned a null result.")
                )
            }
        } else {
            deferred.completeExceptionally(
                task.exception ?: IllegalStateException(
                    "Firebase task failed without an exception."
                )
            )
        }
    }
    return deferred.await()
}
