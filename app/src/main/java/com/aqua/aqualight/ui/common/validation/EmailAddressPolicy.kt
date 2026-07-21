package com.aqua.aqualight.ui.common.validation

import androidx.core.util.PatternsCompat

/** One email-address policy shared by every user-input flow. */
object EmailAddressPolicy {
    const val MAX_LENGTH = 320

    fun isValid(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            normalized.length <= MAX_LENGTH &&
            PatternsCompat.EMAIL_ADDRESS.matcher(normalized).matches()
    }
}
