package com.aqua.aqualight.ui.auth.viewmodel

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.auth.AuthOperations
import com.aqua.aqualight.ui.auth.state.AuthActionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthViewModelFactoryTest {

    @Test
    fun createsEveryAuthViewModelFromOneFakeBoundary() {
        val factory = AuthViewModelFactory(FakeAuthOperations())

        assertTrue(factory.create(LoginViewModel::class.java) is LoginViewModel)
        assertTrue(factory.create(SignInViewModel::class.java) is SignInViewModel)
        assertTrue(factory.create(RegisterViewModel::class.java) is RegisterViewModel)
        assertTrue(factory.create(ResetPasswordViewModel::class.java) is ResetPasswordViewModel)
        assertTrue(factory.create(ChangeEmailViewModel::class.java) is ChangeEmailViewModel)
        assertTrue(factory.create(ChangePasswordViewModel::class.java) is ChangePasswordViewModel)
    }

    @Test
    fun blankGoogleTokenIsRejectedBeforeFakeBoundaryIsInvoked() {
        val fake = FakeAuthOperations()
        val viewModel = LoginViewModel(fake)

        viewModel.signInWithGoogleToken("   ")

        assertEquals(0, fake.googleSignInCalls)
        assertTrue(viewModel.state.value is AuthActionState.Message)
    }

    @Test
    fun accountMetadataComesFromInjectedFakeBoundary() {
        val fake = FakeAuthOperations(
            currentEmailValue = "owner@example.com",
            googleUser = true,
            passwordProvider = true
        )

        val changeEmailViewModel = ChangeEmailViewModel(fake)
        val changePasswordViewModel = ChangePasswordViewModel(fake)

        assertEquals("owner@example.com", changeEmailViewModel.currentEmail())
        assertTrue(changeEmailViewModel.isGoogleUser())
        assertTrue(changePasswordViewModel.hasPasswordProvider())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownViewModelTypeFailsClosed() {
        AuthViewModelFactory(FakeAuthOperations())
            .create(UnsupportedViewModel::class.java)
    }

    private class UnsupportedViewModel : ViewModel()

    private class FakeAuthOperations(
        private val currentEmailValue: String = "",
        private val googleUser: Boolean = false,
        private val passwordProvider: Boolean = false
    ) : AuthOperations {

        var googleSignInCalls: Int = 0
            private set

        override suspend fun signInWithEmail(
            email: String,
            password: String
        ) = Unit

        override suspend fun registerWithEmail(
            email: String,
            password: String
        ) = Unit

        override suspend fun signInWithGoogleToken(
            idToken: String
        ) {
            googleSignInCalls += 1
        }

        override suspend fun sendPasswordResetEmail(
            email: String
        ) = Unit

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String
        ) = Unit

        override suspend fun requestEmailChangeVerification(
            currentEmail: String,
            password: String,
            newEmail: String
        ) = Unit

        override fun hasPasswordProvider(): Boolean {
            return passwordProvider
        }

        override fun isGoogleUser(): Boolean {
            return googleUser
        }

        override fun currentEmail(): String {
            return currentEmailValue
        }
    }
}
