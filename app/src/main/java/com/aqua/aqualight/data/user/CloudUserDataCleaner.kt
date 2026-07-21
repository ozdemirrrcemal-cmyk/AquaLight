package com.aqua.aqualight.data.user

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun interface OwnerCloudDataCleaner {
    suspend fun deleteAll(ownerUid: String)
}

class CloudUserDataCleaner internal constructor(
    private val feedbackDocumentCleaner: OwnerCloudDataCleaner
) {

    data class CleanupResult(
        val error: Throwable? = null
    ) {
        val hasError: Boolean
            get() = error != null

        companion object {
            val Success = CleanupResult()
        }
    }

    companion object {
        fun create(): CloudUserDataCleaner {
            return CloudUserDataCleaner(
                feedbackDocumentCleaner = FirebaseFeedbackDocumentCleaner(
                    firestore = FirebaseFirestore.getInstance()
                )
            )
        }
    }

    suspend fun clearCloudUserData(ownerUid: String): CleanupResult {
        val uid = ownerUid.trim()
        if (!uid.isSafeFirebaseIdentifier()) {
            return CleanupResult(
                error = IllegalArgumentException("Owner uid is invalid.")
            )
        }

        return runCatching {
            feedbackDocumentCleaner.deleteAll(uid)
            CleanupResult.Success
        }.getOrElse { error ->
            CleanupResult(error = error)
        }
    }

    private fun String.isSafeFirebaseIdentifier(): Boolean {
        return isNotBlank() &&
            length <= 128 &&
            this != "." &&
            this != ".." &&
            '/' !in this &&
            '\\' !in this
    }
}

private class FirebaseFeedbackDocumentCleaner(
    private val firestore: FirebaseFirestore
) : OwnerCloudDataCleaner {

    override suspend fun deleteAll(ownerUid: String) {
        val feedbackSnapshot = firestore.collection(FEEDBACK_COLLECTION)
            .whereEqualTo(FIELD_USER_ID, ownerUid)
            .get()
            .awaitResult()

        feedbackSnapshot.documents
            .chunked(FIRESTORE_DELETE_BATCH_SIZE)
            .forEach { documents ->
                val batch = firestore.batch()
                documents.forEach { document -> batch.delete(document.reference) }
                batch.commit().awaitCompletion()
            }
    }

    private companion object {
        const val FEEDBACK_COLLECTION = "feedback_items"
        const val FIELD_USER_ID = "userId"
        const val FIRESTORE_DELETE_BATCH_SIZE = 400
    }
}

private suspend fun Task<Void>.awaitCompletion() {
    suspendCancellableCoroutine<Unit> { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener

            val exception = task.exception
            if (task.isSuccessful && exception == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    exception ?: IllegalStateException("Firebase task failed.")
                )
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener

            val exception = task.exception
            if (task.isSuccessful && exception == null) {
                val result = task.result
                if (result != null) {
                    continuation.resume(result)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException("Firebase task returned null result.")
                    )
                }
            } else {
                continuation.resumeWithException(
                    exception ?: IllegalStateException("Firebase task failed.")
                )
            }
        }
    }
}
