package com.aqua.aqualight.data.auth

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Coroutine bridge for Firebase/Google Play Services Task APIs.
 *
 * The project intentionally keeps this small local extension instead of using
 * ad-hoc addOnCompleteListener blocks in fragments. That makes auth work
 * lifecycle-safe when it is called from ViewModels.
 */
suspend fun <T> Task<T>.awaitTask(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) {
                return@addOnCompleteListener
            }

            val exception = task.exception

            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else if (exception != null) {
                continuation.resumeWithException(exception)
            } else {
                continuation.resumeWithException(
                    IllegalStateException("Firebase task failed without an exception.")
                )
            }
        }
    }
}
