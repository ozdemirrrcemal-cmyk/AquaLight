package com.aqua.aqualight.ui.auth

object LegalGatePolicy {
    enum class Failure {
        TERMS_NOT_ACCEPTED,
        ADULT_NOT_CONFIRMED
    }

    fun validate(
        termsAccepted: Boolean,
        adultConfirmed: Boolean
    ): Failure? {
        return when {
            !termsAccepted -> Failure.TERMS_NOT_ACCEPTED
            !adultConfirmed -> Failure.ADULT_NOT_CONFIRMED
            else -> null
        }
    }
}
