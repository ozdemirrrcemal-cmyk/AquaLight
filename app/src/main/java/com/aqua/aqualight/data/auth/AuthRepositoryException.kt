package com.aqua.aqualight.data.auth

sealed class AuthRepositoryException(
    message: String
) : Exception(message) {

    object NoAuthenticatedUser : AuthRepositoryException(
        "No authenticated Firebase user."
    )

    object MissingEmail : AuthRepositoryException(
        "The authenticated user does not have an email address."
    )

    object CurrentEmailMismatch : AuthRepositoryException(
        "The provided current email does not match the authenticated user."
    )

    object EmailAlreadyInUse : AuthRepositoryException(
        "The requested email address is already registered."
    )

    object NoFirebaseUserFromResult : AuthRepositoryException(
        "Firebase completed the auth operation but did not return a user."
    )
}
