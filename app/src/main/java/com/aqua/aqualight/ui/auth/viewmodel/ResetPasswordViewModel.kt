package com.aqua.aqualight.ui.auth.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.auth.AuthOperations
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.state.AuthErrorMapper
import com.aqua.aqualight.ui.auth.state.AuthUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    private val authOperations: AuthOperations
) : ViewModel() {

    private val _state = MutableStateFlow<AuthActionState>(
        AuthActionState.Idle
    )
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    fun sendResetEmail(
        email: String
    ) {
        val normalizedEmail = email.trim()

        val validationMessage = when {
            normalizedEmail.isEmpty() -> {
                R.string.reset_empty_email_title to R.string.reset_empty_email_message
            }

            !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> {
                R.string.reset_invalid_email_title to R.string.reset_invalid_email
            }

            else -> null
        }

        if (validationMessage != null) {
            _state.value = AuthActionState.Message(
                kind = AuthActionState.Kind.WARNING,
                title = AuthUiText.Resource(validationMessage.first),
                message = AuthUiText.Resource(validationMessage.second)
            )
            return
        }

        viewModelScope.launch {
            _state.value = AuthActionState.Loading

            runCatching {
                authOperations.sendPasswordResetEmail(
                    email = normalizedEmail
                )
            }.onSuccess {
                _state.value = AuthActionState.PasswordResetEmailSent
            }.onFailure { error ->
                _state.value = AuthActionState.Message(
                    kind = AuthActionState.Kind.ERROR,
                    title = AuthErrorMapper.titleFor(
                        AuthErrorMapper.Operation.RESET_PASSWORD
                    ),
                    message = AuthErrorMapper.messageFor(
                        error = error,
                        operation = AuthErrorMapper.Operation.RESET_PASSWORD
                    )
                )
            }
        }
    }

    fun resetState() {
        _state.value = AuthActionState.Idle
    }
}
