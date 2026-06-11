package com.aqua.aqualight.ui.auth.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.data.auth.AuthRepository

class AuthViewModelFactory(
    context: Context
) : ViewModelProvider.Factory {

    private val repository: AuthRepository = AuthRepository.create(
        context.applicationContext
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(repository) as T
            }

            modelClass.isAssignableFrom(SignInViewModel::class.java) -> {
                SignInViewModel(repository) as T
            }

            modelClass.isAssignableFrom(RegisterViewModel::class.java) -> {
                RegisterViewModel(repository) as T
            }

            modelClass.isAssignableFrom(ResetPasswordViewModel::class.java) -> {
                ResetPasswordViewModel(repository) as T
            }

            modelClass.isAssignableFrom(ChangeEmailViewModel::class.java) -> {
                ChangeEmailViewModel(repository) as T
            }

            modelClass.isAssignableFrom(ChangePasswordViewModel::class.java) -> {
                ChangePasswordViewModel(repository) as T
            }

            else -> throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}
