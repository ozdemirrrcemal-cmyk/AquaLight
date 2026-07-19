package com.aqua.aqualight.ui.common.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A user-facing text value that remains unresolved until it reaches an Android UI boundary.
 *
 * Static product copy must use [Resource] or [Plural]. [Dynamic] is reserved for user data,
 * identifiers, firmware-provided values, and external diagnostic details.
 */
sealed interface AquaUiText {

    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : AquaUiText

    data class Plural(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = listOf(quantity)
    ) : AquaUiText

    data class Dynamic(
        val value: CharSequence
    ) : AquaUiText

    data class Joined(
        val parts: List<AquaUiText>,
        @StringRes val separatorRes: Int
    ) : AquaUiText
}

fun Context.resolve(uiText: AquaUiText): CharSequence {
    return when (uiText) {
        is AquaUiText.Resource -> getString(
            uiText.resId,
            *uiText.args.map(::resolveArgument).toTypedArray()
        )

        is AquaUiText.Plural -> resources.getQuantityString(
            uiText.resId,
            uiText.quantity,
            *uiText.args.map(::resolveArgument).toTypedArray()
        )

        is AquaUiText.Dynamic -> uiText.value
        is AquaUiText.Joined -> uiText.parts.joinToString(getString(uiText.separatorRes)) { part ->
            resolve(part)
        }
    }
}

private fun Context.resolveArgument(argument: Any): Any {
    return if (argument is AquaUiText) resolve(argument) else argument
}
