package com.aqua.aqualight.data.user

import com.aqua.aqualight.data.feedback.FeedbackFirestoreProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

class CloudUserDataCleaner private constructor(
    private val firestore: FirebaseFirestore
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
        private const val ROOT_COLLECTION = "feedback_items"
        private const val SUBMISSIONS_COLLECTION = "submissions"
        private const val DELETE_CHUNK_SIZE = 100

        fun create(): CloudUserDataCleaner {
            return CloudUserDataCleaner(
                firestore = FeedbackFirestoreProvider.get()
            )
        }
    }

    suspend fun clearCloudUserData(
        ownerUid: String
    ): CleanupResult {
        val uid = ownerUid.trim()

        if (uid.isBlank()) {
            return CleanupResult(
                error = IllegalArgumentException("Owner uid is blank.")
            )
        }

        return try {
            // Server-only prevents account deletion from trusting stale cached feedback.
            val feedbackSnapshot = firestore.collection(ROOT_COLLECTION)
                .document(uid)
                .collection(SUBMISSIONS_COLLECTION)
                .get(Source.SERVER)
                .awaitNonNull()

            feedbackSnapshot.documents
                .chunked(DELETE_CHUNK_SIZE)
                .forEach { documents ->
                    firestore.runTransaction { transaction ->
                        // Firestore transactions require every read before the first delete.
                        val snapshots = documents.map { document ->
                            transaction.get(document.reference)
                        }
                        snapshots.forEach { snapshot ->
                            if (snapshot.exists()) {
                                check(snapshot.getString("userId") == uid) {
                                    "Feedback cleanup encountered an owner mismatch."
                                }
                                transaction.delete(snapshot.reference)
                            }
                        }
                        true
                    }.awaitNonNull()
                }

            CleanupResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            CleanupResult(error = error)
        }
    }
}

/** Coroutine cancellation stops waiting; Firestore keeps its own authoritative task lifecycle. */
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
                task.exception ?: IllegalStateException("Firebase task failed without an exception.")
            )
        }
    }
    return deferred.await()
}
