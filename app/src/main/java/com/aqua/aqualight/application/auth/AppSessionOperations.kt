package com.aqua.aqualight.application.auth

import kotlinx.coroutines.flow.StateFlow

/** UI-facing process session boundary. */
interface AppSessionOperations {
    val state: StateFlow<AppSessionState>

    fun start()

    fun requestReconcile()
}

sealed interface AppSessionState {
    data object Starting : AppSessionState

    data class Authenticated(
        val ownerUid: String
    ) : AppSessionState

    data object Unauthenticated : AppSessionState

    data class Failure(
        val ownerUid: String?,
        val error: Throwable
    ) : AppSessionState
}
