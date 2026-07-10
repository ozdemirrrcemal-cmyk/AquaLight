package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.user.UserDataScope

internal data class OwnerSessionDecision(
    val ownerUid: String,
    val generation: Long,
    val requiresRestart: Boolean
)

/**
 * Pure state machine for authenticated device-runtime ownership transitions.
 *
 * A generation token prevents an older asynchronous transition from starting after logout or after
 * a newer account has already become active.
 */
internal class OwnerSessionStateMachine {

    private var activeOwnerUid: String = ""
    private var generation: Long = 0L

    @Synchronized
    fun start(
        ownerUid: String
    ): OwnerSessionDecision? {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return null
        }

        if (activeOwnerUid == normalizedOwnerUid) {
            return OwnerSessionDecision(
                ownerUid = normalizedOwnerUid,
                generation = generation,
                requiresRestart = false
            )
        }

        generation += 1L
        activeOwnerUid = normalizedOwnerUid

        return OwnerSessionDecision(
            ownerUid = normalizedOwnerUid,
            generation = generation,
            requiresRestart = true
        )
    }

    @Synchronized
    fun stop() {
        generation += 1L
        activeOwnerUid = ""
    }

    @Synchronized
    fun isCurrent(
        ownerUid: String,
        expectedGeneration: Long
    ): Boolean {
        return activeOwnerUid == UserDataScope.normalizeOwnerUid(ownerUid) &&
            generation == expectedGeneration
    }
}
