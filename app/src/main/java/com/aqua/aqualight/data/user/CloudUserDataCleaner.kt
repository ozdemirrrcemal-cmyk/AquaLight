package com.aqua.aqualight.data.user

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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
        fun create(): CloudUserDataCleaner {
            return CloudUserDataCleaner(
                firestore = FirebaseFirestore.getInstance()
            )
        }
    }

    suspend fun clearCloudUserData(
        ownerUid: String
    ): CleanupResult {
        val uid = ownerUid.trim()

        if (uid.isBlank()) {
            return CleanupResult(
                error = IllegalArgumentException(
                    "Owner uid is blank."
                )
            )
        }

        return runCatching {
            val feedbackSnapshot =
                firestore.collection("feedback_items")
                    .whereEqualTo("userId", uid)
                    .get()
                    .awaitResult()

            feedbackSnapshot.documents
                .chunked(400)
                .forEach { documents ->
                    val batch =
                        firestore.batch()

                    documents.forEach { document ->
                        batch.delete(
                            document.reference
                        )
                    }

                    batch.commit()
                        .awaitCompletion()
                }

            CleanupResult.Success
        }.getOrElse { error ->
            CleanupResult(
                error = error
            )
        }
    }

    private suspend fun Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val exception =
                    task.exception

                if (task.isSuccessful && exception == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        exception ?: IllegalStateException(
                            "Firebase task failed."
                        )
                    )
                }
            }
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val exception =
                    task.exception

                if (task.isSuccessful && exception == null) {
                    val result =
                        task.result

                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "Firebase task returned null result."
                            )
                        )
                    }
                } else {
                    continuation.resumeWithException(
                        exception ?: IllegalStateException(
                            "Firebase task failed."
                        )
                    )
                }
            }
        }
    }
}
