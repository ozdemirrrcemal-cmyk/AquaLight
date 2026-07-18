package com.aqua.aqualight.data.care.reminder

import android.net.Uri
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID

/** Stable owner + task identity for alarms, notifications, deep links and work. */
object CareReminderIdentity {

    private const val SCHEME = "aqualight"
    private const val ALARM_AUTHORITY = "care-reminder-alarm"
    private const val CONTENT_AUTHORITY = "care-reminder-content"
    private const val DELIVERY_WORK_PREFIX = "care_reminder_delivery_"
    private const val OWNER_WORK_TAG_PREFIX = "care_reminder_owner_"

    fun alarmData(
        ownerUid: String,
        taskId: Long
    ): Uri = buildUri(
        authority = ALARM_AUTHORITY,
        ownerUid = ownerUid,
        taskId = taskId
    )

    fun contentData(
        ownerUid: String,
        taskId: Long
    ): Uri = buildUri(
        authority = CONTENT_AUTHORITY,
        ownerUid = ownerUid,
        taskId = taskId
    )

    internal fun stableKey(
        ownerUid: String,
        taskId: Long
    ): String {
        val owner = requireOwnerUid(ownerUid)
        requireTaskId(taskId)
        return "$owner\u001F$taskId"
    }

    internal fun deliveryWorkName(
        ownerUid: String,
        taskId: Long,
        occurrence: CareReminderOccurrence
    ): String {
        val identity = stableKey(ownerUid, taskId) + "\u001F${occurrence.name}"
        return DELIVERY_WORK_PREFIX + UUID.nameUUIDFromBytes(identity.toByteArray())
    }

    internal fun ownerWorkTag(ownerUid: String): String {
        val owner = requireOwnerUid(ownerUid)
        return OWNER_WORK_TAG_PREFIX + UUID.nameUUIDFromBytes(owner.toByteArray())
    }

    private fun buildUri(
        authority: String,
        ownerUid: String,
        taskId: Long
    ): Uri {
        val owner = requireOwnerUid(ownerUid)
        requireTaskId(taskId)

        return Uri.Builder()
            .scheme(SCHEME)
            .authority(authority)
            .appendPath(owner)
            .appendPath(taskId.toString())
            .build()
    }

    private fun requireTaskId(taskId: Long) {
        require(taskId > 0L) {
            "taskId must be positive"
        }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }
    }
}
