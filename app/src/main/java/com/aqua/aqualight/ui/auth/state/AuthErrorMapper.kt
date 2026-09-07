package com.aqua.aqualight.ui.auth.state

import com.aqua.aqualight.R
import com.aqua.aqualight.application.auth.AuthOperationException
import com.aqua.aqualight.application.auth.AuthOperationFailure

object AuthErrorMapper {

    enum class Operation {
        SIGN_IN,
        REGISTER,
        GOOGLE_SIGN_IN,
        RESET_PASSWORD,
        CHANGE_PASSWORD,
        CHANGE_EMAIL,
        REAUTH,
        SESSION
    }

    fun titleFor(
        operation: Operation
    ): AuthUiText.Resource {
        val titleRes = when (operation) {
            Operation.SIGN_IN -> R.string.signin_failed_title
            Operation.REGISTER -> R.string.register_failed_title
            Operation.GOOGLE_SIGN_IN -> R.string.login_firebase_failed
            Operation.RESET_PASSWORD -> R.string.reset_failed_title
            Operation.CHANGE_PASSWORD -> R.string.change_password_error_title
            Operation.CHANGE_EMAIL -> R.string.change_email_update_failed_title
            Operation.REAUTH -> R.string.re_auth_verification_failed_title
            Operation.SESSION -> R.string.session_save_error_title
        }

        return AuthUiText.Resource(titleRes)
    }

    fun messageFor(
        error: Throwable?,
        operation: Operation
    ): AuthUiText {
        val failure = (error as? AuthOperationException)?.failure
            ?: AuthOperationFailure.UNKNOWN
        return AuthUiText.Resource(messageResource(failure, operation))
    }

    private fun messageResource(
        failure: AuthOperationFailure,
        operation: Operation
    ): Int = when (failure) {
        AuthOperationFailure.NO_AUTHENTICATED_USER -> noUserMessage(operation)
        AuthOperationFailure.MISSING_EMAIL -> missingEmailMessage(operation)
        AuthOperationFailure.CURRENT_EMAIL_MISMATCH -> R.string.change_email_old_incorrect
        AuthOperationFailure.EMAIL_ALREADY_IN_USE ->
            R.string.change_email_error_email_already_in_use_message
        AuthOperationFailure.PROVIDER_USER_MISSING -> R.string.login_user_info_unavailable
        AuthOperationFailure.INVALID_CREDENTIALS -> invalidCredentialsMessage(operation)
        AuthOperationFailure.USER_COLLISION -> userCollisionMessage(operation)
        AuthOperationFailure.WEAK_PASSWORD -> R.string.invalid_password
        AuthOperationFailure.NETWORK -> R.string.auth_error_network
        AuthOperationFailure.RATE_LIMITED -> R.string.auth_error_too_many_requests
        AuthOperationFailure.RECENT_LOGIN_REQUIRED -> R.string.re_auth_session_expired
        AuthOperationFailure.UNKNOWN -> unknownMessage(operation)
    }

    private fun noUserMessage(operation: Operation): Int = when (operation) {
        Operation.CHANGE_EMAIL -> R.string.change_email_user_not_found_message
        Operation.CHANGE_PASSWORD -> R.string.change_password_error_not_logged_in
        else -> R.string.login_user_info_unavailable
    }

    private fun missingEmailMessage(operation: Operation): Int = when (operation) {
        Operation.CHANGE_PASSWORD -> R.string.change_password_error_no_email
        else -> R.string.change_email_user_not_found_message
    }

    private fun invalidCredentialsMessage(operation: Operation): Int = when (operation) {
        Operation.CHANGE_PASSWORD -> R.string.change_password_error_current_wrong
        Operation.CHANGE_EMAIL -> R.string.change_email_error_incorrect_password
        Operation.SIGN_IN -> R.string.signin_failed_invalid_credentials_friendly
        Operation.REAUTH -> R.string.re_auth_wrong_password
        else -> R.string.auth_error_invalid_credentials
    }

    private fun userCollisionMessage(operation: Operation): Int = when (operation) {
        Operation.REGISTER -> R.string.register_failed_email_already_in_use_friendly
        Operation.CHANGE_EMAIL -> R.string.change_email_error_email_already_in_use_message
        else -> R.string.auth_error_email_already_in_use
    }

    private fun unknownMessage(operation: Operation): Int = when (operation) {
        Operation.SIGN_IN -> R.string.signin_failed_default
        Operation.REGISTER -> R.string.register_failed_message
        Operation.GOOGLE_SIGN_IN -> R.string.auth_error_google_sign_in
        Operation.RESET_PASSWORD -> R.string.reset_error_message
        Operation.CHANGE_PASSWORD -> R.string.change_password_error_generic
        Operation.CHANGE_EMAIL -> R.string.change_email_update_failed
        Operation.REAUTH -> R.string.re_auth_unknown_error
        Operation.SESSION -> R.string.session_save_error_fallback
    }
}
