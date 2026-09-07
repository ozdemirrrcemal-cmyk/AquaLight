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

class RegisterViewModel(
    private val authOperations: AuthOperations
) : ViewModel() {

    private val _state = MutableStateFlow<AuthActionState>(
        AuthActionState.Idle
    )
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    fun register(
        email: String,
        password: String,
        repeatPassword: String
    ) {
        val normalizedEmail = email.trim()
        val normalizedPassword = password.trim()
        val normalizedRepeatPassword = repeatPassword.trim()

        val validationMessage = when {
            normalizedEmail.isEmpty() ||
                normalizedPassword.isEmpty() ||
                normalizedRepeatPassword.isEmpty() -> {
                R.string.register_empty_fields_title to R.string.register_empty_fields_message
            }

            !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> {
                R.string.invalid_email_title to R.string.invalid_email
            }

            normalizedPassword.length < 6 -> {
                R.string.invalid_password_title to R.string.invalid_password
            }

            normalizedPassword != normalizedRepeatPassword -> {
                R.string.passwords_do_not_match_title to R.string.passwords_do_not_match
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
                authOperations.registerWithEmail(
                    email = normalizedEmail,
                    password = normalizedPassword
                )
            }.onSuccess {
                _state.value = AuthActionState.Authenticated
            }.onFailure { error ->
                _state.value = AuthActionState.Message(
                    kind = AuthActionState.Kind.ERROR,
                    title = AuthErrorMapper.titleFor(
                        AuthErrorMapper.Operation.REGISTER
                    ),
                    message = AuthErrorMapper.messageFor(
                        error = error,
                        operation = AuthErrorMapper.Operation.REGISTER
                    )
                )
            }
        }
    }

    fun resetState() {
        _state.value = AuthActionState.Idle
    }
}
