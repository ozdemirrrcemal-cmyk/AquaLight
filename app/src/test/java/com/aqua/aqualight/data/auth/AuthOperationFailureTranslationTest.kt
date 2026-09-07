package com.aqua.aqualight.data.auth

import com.aqua.aqualight.application.auth.AuthOperationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthOperationFailureTranslationTest {
    @Test
    fun `repository failures are translated before crossing the application boundary`() {
        val cases = mapOf(
            AuthRepositoryException.NoAuthenticatedUser to
                AuthOperationFailure.NO_AUTHENTICATED_USER,
            AuthRepositoryException.MissingEmail to AuthOperationFailure.MISSING_EMAIL,
            AuthRepositoryException.CurrentEmailMismatch to
                AuthOperationFailure.CURRENT_EMAIL_MISMATCH,
            AuthRepositoryException.EmailAlreadyInUse to
                AuthOperationFailure.EMAIL_ALREADY_IN_USE,
            AuthRepositoryException.NoFirebaseUserFromResult to
                AuthOperationFailure.PROVIDER_USER_MISSING
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toAuthOperationFailure())
        }
    }

    @Test
    fun `unknown provider failures do not leak an implementation-specific category`() {
        assertEquals(
            AuthOperationFailure.UNKNOWN,
            IllegalStateException("provider detail").toAuthOperationFailure()
        )
    }
}
