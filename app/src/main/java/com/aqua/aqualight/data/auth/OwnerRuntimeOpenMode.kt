package com.aqua.aqualight.data.auth

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/** Coroutine-local marker that prevents a bounded background runtime open from starting services. */
internal object OwnerRuntimeOpenMode {
    private val backgroundOpen = ThreadLocal<Boolean?>()

    fun isBackgroundOpen(): Boolean = backgroundOpen.get() == true

    suspend fun <T> withBackgroundOpen(block: suspend () -> T): T {
        return withContext(backgroundOpen.asContextElement(true)) {
            block()
        }
    }
}
