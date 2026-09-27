package com.aqua.aqualight.data.aquarium

/**
 * Orders whole backup/restore and tank deletion/recovery coordinators for one owner.
 * Acquire after the session lease and before any tank gate. This is a separate lock
 * namespace: its single slot is an owner lock, never an actual aquarium identity.
 */
internal class OwnerArchiveMutationGate {
    private val owners = OwnerTankMutationGate()

    internal val reservedOwnerCount: Int get() = owners.reservedTankCount

    suspend fun <T> withOwner(ownerUid: String, block: suspend () -> T): T =
        owners.withTanks(ownerUid, listOf(OWNER_SLOT), block)

    companion object {
        private const val OWNER_SLOT = 1L
        val shared = OwnerArchiveMutationGate()
    }
}
