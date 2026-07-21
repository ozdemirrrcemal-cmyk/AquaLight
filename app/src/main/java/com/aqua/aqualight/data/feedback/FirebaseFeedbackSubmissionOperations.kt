package com.aqua.aqualight.data.feedback

import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Firebase-backed, text-only feedback repository. */
class FirebaseFeedbackSubmissionOperations internal constructor(
    private val ownerUidProvider: () -> String?,
    private val documentStore: FeedbackDocumentStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeedbackRepository {

    override suspend fun submit(
        request: FeedbackSubmissionRequest
    ): FeedbackSubmissionResult = withContext(dispatcher) {
        try {
            val ownerUid = ownerUidProvider()?.takeIf(String::isNotBlank) ?: ANONYMOUS_OWNER_UID
            val documentId = documentStore.newDocumentId()
            documentStore.save(
                documentId = documentId,
                data = baseDocument(request, ownerUid)
            )
            FeedbackSubmissionResult.Success(documentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            FeedbackSubmissionResult.Failure(
                FeedbackSubmissionFailure(
                    kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                    cause = error
                )
            )
        }
    }

    private fun baseDocument(
        request: FeedbackSubmissionRequest,
        ownerUid: String
    ): Map<String, Any?> = linkedMapOf(
        FIELD_CATEGORY to request.category,
        FIELD_EMAIL to request.email.ifBlank { null },
        FIELD_MESSAGE to request.message,
        FIELD_PLATFORM to PLATFORM_ANDROID,
        FIELD_APP_VERSION to request.appVersion,
        FIELD_LOCALE to request.localeTag,
        FIELD_STATUS to STATUS_NEW,
        FIELD_USER_ID to ownerUid
    )

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

        fun create(): FirebaseFeedbackSubmissionOperations {
            return FirebaseFeedbackSubmissionOperations(
                ownerUidProvider = { FirebaseAuth.getInstance().currentUser?.uid },
                documentStore = FirebaseFeedbackDocumentStore(FirebaseFirestore.getInstance())
            )
        }
    }
}

internal interface FeedbackDocumentStore {
    fun newDocumentId(): String
    suspend fun save(documentId: String, data: Map<String, Any?>)
}

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

/** Firebase Tasks are not cancellable; wait for their authoritative terminal result. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException(
                    "Firebase task failed without an exception."
                )
            )
        }
    }
}
