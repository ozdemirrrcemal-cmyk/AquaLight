package com.aqua.aqualight.data.user

import com.aqua.aqualight.application.user.OwnerIdentityPolicy
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/**
 * Central ownership rules for local user-scoped data.
 *
 * AquaLight has no released ownerless local-data format. Every persisted record
 * must carry the exact authenticated owner UID; blank owner values are invalid
 * data and are never adopted by a later account.
 */
object UserDataScope {

    private val explicitOwnerUid = ThreadLocal<String?>()

    fun currentUid(): String {
        val scopedOwnerUid = normalizeOwnerUid(
            explicitOwnerUid.get()
        )
        if (scopedOwnerUid.isNotBlank()) {
            return scopedOwnerUid
        }
        return Firebase.auth.currentUser?.uid.orEmpty().trim()
    }

    fun requireCurrentUid(): String {
        return currentUid().ifBlank {
            throw IllegalStateException(
                "User-scoped data requires an authenticated owner UID."
            )
        }
    }

    /**
     * Runs a background DataStore operation with an immutable owner identity.
     * The value is propagated across coroutine dispatcher switches and cannot be
     * replaced by a concurrent Firebase account change.
     */
    suspend fun <T> withOwnerUid(
        ownerUid: String,
        block: suspend () -> T
    ): T {
        val normalizedOwnerUid = normalizeOwnerUid(ownerUid)
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        return withContext(
            explicitOwnerUid.asContextElement(normalizedOwnerUid)
        ) {
            block()
        }
    }

    fun normalizeOwnerUid(
        ownerUid: String?
    ): String {
        return OwnerIdentityPolicy.normalize(ownerUid)
    }

    fun belongsToOwner(
        recordOwnerUid: String,
        ownerUid: String,
        includeLegacy: Boolean = false
    ): Boolean {
        require(!includeLegacy) {
            "Ownerless legacy records are not supported by the commercial store contract."
        }

        return OwnerIdentityPolicy.belongsToOwner(
            recordOwnerUid = recordOwnerUid,
            ownerUid = ownerUid
        )
    }

    fun belongsToCurrentUser(
        recordOwnerUid: String,
        includeLegacy: Boolean = false
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
        require(taskId > 0L) {
            "taskId must be positive"
        }

        val normalizedOwnerUid = normalizeOwnerUid(ownerUid)
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        val mixed = 31L * taskId + normalizedOwnerUid.hashCode().toLong()
        val safe = positiveRequestCode(mixed)

        return if (safe == 0) {
            1
        } else {
            safe
        }
    }

    private fun positiveRequestCode(
        value: Long
    ): Int {
        val modulus = Int.MAX_VALUE.toLong()
        return (((value % modulus) + modulus) % modulus).toInt()
    }
}
