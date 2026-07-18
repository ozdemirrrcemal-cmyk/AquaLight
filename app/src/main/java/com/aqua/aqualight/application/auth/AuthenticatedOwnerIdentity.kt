package com.aqua.aqualight.application.auth

/**
 * Fail-closed access to the currently committed authenticated owner.
 *
 * Implementations must resolve through the active owner-session barrier at call time;
 * callers must never retain an owner UID across account transitions.
 */
fun interface AuthenticatedOwnerIdentity {
    fun requireOwnerUid(): String
}
