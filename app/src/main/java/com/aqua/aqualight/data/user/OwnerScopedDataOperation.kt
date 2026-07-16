package com.aqua.aqualight.data.user

/**
 * Pins a multi-step data operation to the authenticated owner observed at start.
 *
 * DataStore, reminder and assignment components resolve [UserDataScope] at their
 * own suspension points. Capturing once and propagating the UID through the
 * coroutine context prevents a concurrent account change from splitting one
 * business operation across two owners.
 */
internal suspend fun <T> withCurrentOwnerScope(
    block: suspend (ownerUid: String) -> T
): T {
    val ownerUid = UserDataScope.requireCurrentUid()
    return UserDataScope.withOwnerUid(ownerUid) {
        block(ownerUid)
    }
}
