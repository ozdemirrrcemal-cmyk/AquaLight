package com.aqua.aqualight.data.auth

/**
 * Process-owned accounting for the bounded owner runtime opened by background firmware work.
 *
 * The coordinator serializes access to this registry. A foreground session can promote a
 * background-owned runtime, after which background releases become no-ops and cannot tear the
 * foreground session down.
 */
internal class OwnerBackgroundLeaseRegistry {

    data class Lease(
        val ownerUid: String,
        val generation: Long,
        val backgroundOwned: Boolean
    )

    private data class OwnedSession(
        val ownerUid: String,
        val generation: Long,
        val leaseCount: Int
    )

    private var ownedSession: OwnedSession? = null

    fun acquireExisting(ownerUid: String, generation: Long): Lease {
        val current = ownedSession
        return if (current?.matches(ownerUid, generation) == true) {
            ownedSession = current.copy(leaseCount = current.leaseCount + 1)
            Lease(ownerUid, generation, backgroundOwned = true)
        } else {
            Lease(ownerUid, generation, backgroundOwned = false)
        }
    }

    fun markCreated(ownerUid: String, generation: Long): Lease {
        check(ownedSession == null) {
            "A background-owned owner runtime is already registered."
        }
        ownedSession = OwnedSession(
            ownerUid = ownerUid,
            generation = generation,
            leaseCount = 1
        )
        return Lease(ownerUid, generation, backgroundOwned = true)
    }

    fun isOwned(ownerUid: String, generation: Long): Boolean {
        return ownedSession?.matches(ownerUid, generation) == true
    }

    fun promote(ownerUid: String, generation: Long): Boolean {
        val current = ownedSession
        return if (current?.matches(ownerUid, generation) == true) {
            ownedSession = null
            true
        } else {
            false
        }
    }

    /** Returns true only when the final background lease should close the owner runtime. */
    fun release(lease: Lease): Boolean {
        val current = ownedSession
        return when {
            !lease.backgroundOwned -> false
            current == null -> false
            !current.matches(lease.ownerUid, lease.generation) -> false
            else -> releaseOwnedSession(current)
        }
    }

    fun clear(ownerUid: String? = null) {
        ownedSession?.let { current ->
            if (ownerUid == null || current.ownerUid == ownerUid) {
                ownedSession = null
            }
        }
    }

    private fun releaseOwnedSession(current: OwnedSession): Boolean {
        check(current.leaseCount > 0) { "Background owner runtime lease count is invalid." }
        return if (current.leaseCount == 1) {
            ownedSession = null
            true
        } else {
            ownedSession = current.copy(leaseCount = current.leaseCount - 1)
            false
        }
    }

    private fun OwnedSession.matches(ownerUid: String, generation: Long): Boolean {
        return this.ownerUid == ownerUid && this.generation == generation
    }
}
