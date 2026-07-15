package com.aqua.aqualight.platform.auth

import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.auth.GoogleSignInClientFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface GoogleIdentityClient {
    fun signInIntent(): Intent

    suspend fun clearPreviousSession()

    fun parseIdToken(
        resultData: Intent?
    ): GoogleIdentityTokenResult
}

sealed interface GoogleIdentityTokenResult {
    data class Success(
        val idToken: String
    ) : GoogleIdentityTokenResult

    data object MissingToken : GoogleIdentityTokenResult

    data class Failure(
        val error: Throwable
    ) : GoogleIdentityTokenResult
}

internal class DefaultGoogleIdentityClient(
    context: Context
) : GoogleIdentityClient {

    private val client: GoogleSignInClient =
        GoogleSignInClientFactory.create(context.applicationContext)

    override fun signInIntent(): Intent {
        return client.signInIntent
    }

    override suspend fun clearPreviousSession() {
        client.signOut().awaitCompletion()
    }

    override fun parseIdToken(
        resultData: Intent?
    ): GoogleIdentityTokenResult {
        return try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(resultData)
                .getResult(ApiException::class.java)
            val token = account.idToken

            if (token.isNullOrBlank()) {
                GoogleIdentityTokenResult.MissingToken
            } else {
                GoogleIdentityTokenResult.Success(token)
            }
        } catch (error: Throwable) {
            GoogleIdentityTokenResult.Failure(error)
        }
    }

    private suspend fun Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val error = task.exception
                if (task.isSuccessful && error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        error ?: IllegalStateException(
                            "Google identity task failed."
                        )
                    )
                }
            }
        }
    }
}
