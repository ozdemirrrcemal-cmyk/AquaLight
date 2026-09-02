package com.aqua.aqualight.ui.auth.state

import com.aqua.aqualight.R
import com.aqua.aqualight.application.auth.AuthOperationException
import com.aqua.aqualight.application.auth.AuthOperationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun `application failures map to operation-specific presentation copy`() {
        assertEquals(
            R.string.signin_failed_invalid_credentials_friendly,
            messageResource(
                failure = AuthOperationFailure.INVALID_CREDENTIALS,
                operation = AuthErrorMapper.Operation.SIGN_IN
            )
        )
        assertEquals(
            R.string.change_email_old_incorrect,
            messageResource(
                failure = AuthOperationFailure.CURRENT_EMAIL_MISMATCH,
                operation = AuthErrorMapper.Operation.CHANGE_EMAIL
            )
        )
        assertEquals(
            R.string.auth_error_network,
            messageResource(
                failure = AuthOperationFailure.NETWORK,
                operation = AuthErrorMapper.Operation.RESET_PASSWORD
            )
        )
    }

    @Test
    fun `unclassified failures remain fail-closed and provider-neutral`() {
        val message = AuthErrorMapper.messageFor(
            error = IllegalStateException("provider detail"),
            operation = AuthErrorMapper.Operation.REGISTER
        )

        assertEquals(
            R.string.register_failed_message,
            (message as AuthUiText.Resource).resId
        )
    }

    private fun messageResource(
        failure: AuthOperationFailure,
        operation: AuthErrorMapper.Operation
    ): Int {
        val message = AuthErrorMapper.messageFor(
            error = AuthOperationException(failure),
            operation = operation
        )
        return (message as AuthUiText.Resource).resId
    }
}
