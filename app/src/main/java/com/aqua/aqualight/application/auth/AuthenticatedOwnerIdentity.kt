package com.aqua.aqualight.application.auth

/** Fail-closed access to the currently committed authenticated owner. */
fun interface AuthenticatedOwnerIdentity {
    fun requireOwnerUid(): String
}
