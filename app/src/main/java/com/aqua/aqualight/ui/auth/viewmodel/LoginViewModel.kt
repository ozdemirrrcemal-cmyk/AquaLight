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

class LoginViewModel(
    private val authOperations: AuthOperations
) : ViewModel() {

    private val _state = MutableStateFlow<AuthActionState>(
        AuthActionState.Idle
    )
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    fun signInWithGoogleToken(
        idToken: String
    ) {
        if (idToken.isBlank()) {
            _state.value = AuthActionState.Message(
                kind = AuthActionState.Kind.ERROR,
                title = AuthUiText.Resource(R.string.login_google_failed),
                message = AuthUiText.Resource(R.string.login_google_account_not_selected)
            )
            return
        }

        viewModelScope.launch {
            _state.value = AuthActionState.Loading

            runCatching {
                authOperations.signInWithGoogleToken(
                    idToken = idToken
                )
            }.onSuccess {
                _state.value = AuthActionState.Authenticated
            }.onFailure { error ->
                _state.value = AuthActionState.Message(
                    kind = AuthActionState.Kind.ERROR,
                    title = AuthErrorMapper.titleFor(
                        AuthErrorMapper.Operation.GOOGLE_SIGN_IN
                    ),
                    message = AuthErrorMapper.messageFor(
                        error = error,
                        operation = AuthErrorMapper.Operation.GOOGLE_SIGN_IN
                    )
                )
            }
        }
    }

    fun resetState() {
        _state.value = AuthActionState.Idle
    }
}
