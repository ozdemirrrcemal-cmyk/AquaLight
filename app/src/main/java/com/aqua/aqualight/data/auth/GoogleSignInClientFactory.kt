package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

object GoogleSignInClientFactory {

    fun create(
        context: Context
    ): GoogleSignInClient {
        val appContext = context.applicationContext

        val options = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(
                appContext.getString(
                    R.string.default_web_client_id
                )
            )
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(
            appContext,
            options
        )
    }
}
