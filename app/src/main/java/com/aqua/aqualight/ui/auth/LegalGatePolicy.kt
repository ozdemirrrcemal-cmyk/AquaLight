package com.aqua.aqualight.ui.auth

object LegalGatePolicy {
    enum class Failure {
        TERMS_NOT_ACCEPTED
    }

    fun validate(termsAccepted: Boolean): Failure? {
        return if (termsAccepted) null else Failure.TERMS_NOT_ACCEPTED
    }
}
