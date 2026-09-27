package com.aqua.aqualight.data.store

import androidx.datastore.core.DataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Once admitted, await the actor's durable result before releasing an external
 * session/tank lock. Cancelling updateData's acknowledgement waiter alone does
 * not stop an actor already writing to disk. Propagate caller cancellation only
 * after the write has settled; retries must retain their original request ID.
 */
internal suspend fun <T> DataStore<T>.updateDataAwaitingCommit(transform: suspend (T) -> T): T {
    currentCoroutineContext().ensureActive()
    val committed = withContext(NonCancellable) { updateData(transform) }
    currentCoroutineContext().ensureActive()
    return committed
}
