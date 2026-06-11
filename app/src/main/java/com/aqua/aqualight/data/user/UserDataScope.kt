package com.aqua.aqualight.data.user

import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Central ownership rules for local user-scoped data.
 *
 * Empty ownerUid values are legacy records created before UID scoping existed.
 * They are temporarily treated as belonging to the active user and migrated to
 * that user's uid as soon as the session is opened.
 */
object UserDataScope {

    const val LEGACY_OWNER_UID = ""

    fun currentUid(): String {
        return Firebase.auth.currentUser?.uid.orEmpty()
    }

    fun requireCurrentUid(): String {
        return currentUid().ifBlank {
            throw IllegalStateException(
                "User-scoped data requires an authenticated Firebase user."
            )
        }
    }

    fun normalizeOwnerUid(
        ownerUid: String?
    ): String {
        return ownerUid.orEmpty().trim()
    }

    fun belongsToOwner(
        recordOwnerUid: String,
        ownerUid: String,
        includeLegacy: Boolean = true
    ): Boolean {
        val normalizedOwnerUid = normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return false
        }

        val normalizedRecordOwnerUid = normalizeOwnerUid(recordOwnerUid)

        return normalizedRecordOwnerUid == normalizedOwnerUid ||
            (includeLegacy && normalizedRecordOwnerUid.isBlank())
    }

    fun belongsToCurrentUser(
        recordOwnerUid: String,
        includeLegacy: Boolean = true
    ): Boolean {
        return belongsToOwner(
            recordOwnerUid = recordOwnerUid,
            ownerUid = currentUid(),
            includeLegacy = includeLegacy
        )
    }

    fun notificationRequestCode(
        taskId: Long,
        ownerUid: String
    ): Int {
        val normalizedOwnerUid = normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return legacyNotificationRequestCode(taskId)
        }

        val mixed = 31L * taskId + normalizedOwnerUid.hashCode().toLong()
        val safe = positiveRequestCode(mixed)

        return if (safe == 0) {
            1
        } else {
            safe
        }
    }

    fun legacyNotificationRequestCode(
        taskId: Long
    ): Int {
        val value = positiveRequestCode(taskId)

        return if (value == 0) {
            1
        } else {
            value
        }
    }

    private fun positiveRequestCode(
        value: Long
    ): Int {
        val modulus = Int.MAX_VALUE.toLong()
        return (((value % modulus) + modulus) % modulus).toInt()
    }
}
