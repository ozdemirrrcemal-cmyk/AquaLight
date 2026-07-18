package com.aqua.aqualight.data.notifications

import android.net.Uri
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID

/** Stable, privacy-preserving identity shared by every visible notification category. */
object NotificationIdentity {
    private const val SCHEME = "aqualight"
    private const val AUTHORITY = "notification"
    private const val TAG_PREFIX = "aqualight_notification:"

    fun tag(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String
    ): String {
        val ownerToken = ownerToken(ownerUid)
        val entityToken = UUID.nameUUIDFromBytes(requireEntityId(entityId).toByteArray())
        return "$TAG_PREFIX$ownerToken:${category.name}:$entityToken"
    }

    fun ownerTagPrefix(ownerUid: String): String {
        return "$TAG_PREFIX${ownerToken(ownerUid)}:"
    }

    fun contentData(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String
    ): Uri {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(category.name.lowercase())
            .appendPath(ownerToken(ownerUid).toString())
            .appendPath(UUID.nameUUIDFromBytes(requireEntityId(entityId).toByteArray()).toString())
            .build()
    }

    fun requestCode(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String
    ): Int {
        return UUID.nameUUIDFromBytes(
            "${category.name}\u001F${requireOwnerUid(ownerUid)}\u001F${requireEntityId(entityId)}"
                .toByteArray()
        ).hashCode()
    }

    private fun ownerToken(ownerUid: String): UUID {
        return UUID.nameUUIDFromBytes(requireOwnerUid(ownerUid).toByteArray())
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    private fun requireEntityId(entityId: String): String {
        return entityId.trim().also { normalized ->
            require(normalized.isNotBlank()) { "entityId must not be blank" }
        }
    }
}
