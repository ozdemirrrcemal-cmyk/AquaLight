package com.aqua.aqualight.ui.auth.viewmodel

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

class ChangePasswordViewModel(
    private val authOperations: AuthOperations
) : ViewModel() {

    private val _state = MutableStateFlow<AuthActionState>(
        AuthActionState.Idle
    )
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    fun hasPasswordProvider(): Boolean {
        return authOperations.hasPasswordProvider()
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        val current = currentPassword.trim()
        val new = newPassword.trim()
        val confirm = confirmPassword.trim()

        val validationMessage = when {
            current.isEmpty() -> {
                R.string.change_password_error_title to R.string.change_password_error_current_empty
            }

            new.length < 6 -> {
                R.string.change_password_error_title to R.string.change_password_error_new_short
            }

            new != confirm -> {
                R.string.change_password_error_title to R.string.change_password_error_not_match
            }

            current == new -> {
                R.string.change_password_error_title to R.string.change_password_error_same_password
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
                authOperations.changePassword(
                    currentPassword = current,
                    newPassword = new
                )
            }.onSuccess {
                _state.value = AuthActionState.PasswordChanged
            }.onFailure { error ->
                _state.value = AuthActionState.Message(
                    kind = AuthActionState.Kind.ERROR,
                    title = AuthErrorMapper.titleFor(
                        AuthErrorMapper.Operation.CHANGE_PASSWORD
                    ),
                    message = AuthErrorMapper.messageFor(
                        error = error,
                        operation = AuthErrorMapper.Operation.CHANGE_PASSWORD
                    )
                )
            }
        }
    }

    fun resetState() {
        _state.value = AuthActionState.Idle
    }
}
