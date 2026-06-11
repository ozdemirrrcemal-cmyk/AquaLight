package com.aqua.aqualight.ui.auth.state

import com.aqua.aqualight.data.auth.AuthUiText

sealed interface AuthActionState {
    data object Idle : AuthActionState
    data object Loading : AuthActionState
    data object Authenticated : AuthActionState
    data object PasswordResetEmailSent : AuthActionState
    data object PasswordChanged : AuthActionState

    data class EmailVerificationSent(
        val newEmail: String
    ) : AuthActionState

    data class Message(
        val kind: Kind,
        val title: AuthUiText,
        val message: AuthUiText
    ) : AuthActionState

    enum class Kind {
        WARNING,
        ERROR,
        SUCCESS
    }
}
