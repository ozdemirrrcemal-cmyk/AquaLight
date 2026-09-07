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

class ChangeEmailViewModel(
    private val authOperations: AuthOperations
) : ViewModel() {

    private val _state = MutableStateFlow<AuthActionState>(
        AuthActionState.Idle
    )
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    fun isGoogleUser(): Boolean {
        return authOperations.isGoogleUser()
    }

    fun currentEmail(): String {
        return authOperations.currentEmail()
    }

    fun requestEmailChange(
        currentEmail: String,
        newEmail: String,
        password: String
    ) {
        val current = currentEmail.trim()
        val new = newEmail.trim()
        val currentPassword = password.trim()

        val validationMessage = when {
            current.isEmpty() -> {
                R.string.change_email_update_failed_title to R.string.change_email_error_current_required
            }

            new.isEmpty() -> {
                R.string.change_email_update_failed_title to R.string.change_email_error_new_required
            }

            currentPassword.isEmpty() -> {
                R.string.change_email_update_failed_title to R.string.change_email_error_password_required
            }

            !Patterns.EMAIL_ADDRESS.matcher(new).matches() -> {
                R.string.change_email_update_failed_title to R.string.change_email_error_invalid_format
            }

            current.equals(new, ignoreCase = true) -> {
                R.string.change_email_update_failed_title to R.string.change_email_same_email
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
                authOperations.requestEmailChangeVerification(
                    currentEmail = current,
                    password = currentPassword,
                    newEmail = new
                )
            }.onSuccess {
                _state.value = AuthActionState.EmailVerificationSent(
                    newEmail = new
                )
            }.onFailure { error ->
                _state.value = AuthActionState.Message(
                    kind = AuthActionState.Kind.ERROR,
                    title = AuthErrorMapper.titleFor(
                        AuthErrorMapper.Operation.CHANGE_EMAIL
                    ),
                    message = AuthErrorMapper.messageFor(
                        error = error,
                        operation = AuthErrorMapper.Operation.CHANGE_EMAIL
                    )
                )
            }
        }
    }

    fun resetState() {
        _state.value = AuthActionState.Idle
    }
}
