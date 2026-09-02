package com.aqua.aqualight.application.user

/**
 * Pure owner-identity rules shared by application, presentation and persistence adapters.
 *
 * The policy is intentionally independent from Firebase and Android storage. Resolving the
 * current owner remains a data concern; comparing an explicit owner identity does not.
 */
object OwnerIdentityPolicy {
    fun normalize(ownerUid: String?): String = ownerUid.orEmpty().trim()

    fun belongsToOwner(
        recordOwnerUid: String,
        ownerUid: String
    ): Boolean {
        val normalizedOwnerUid = normalize(ownerUid)
        if (normalizedOwnerUid.isBlank()) {
            return false
        }

        val normalizedRecordOwnerUid = normalize(recordOwnerUid)
        return normalizedRecordOwnerUid.isNotBlank() &&
            normalizedRecordOwnerUid == normalizedOwnerUid
    }
}
