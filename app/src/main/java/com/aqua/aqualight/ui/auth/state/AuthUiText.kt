package com.aqua.aqualight.ui.auth.state

import android.content.Context
import androidx.annotation.StringRes

sealed class AuthUiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<String> = emptyList()
    ) : AuthUiText()

    data class Plain(
        val value: String
    ) : AuthUiText()

    fun resolve(
        context: Context
    ): String {
        return when (this) {
            is Resource -> {
                if (args.isEmpty()) {
                    context.getString(resId)
                } else {
                    context.getString(resId, *args.toTypedArray())
                }
            }

            is Plain -> value
        }
    }
}
