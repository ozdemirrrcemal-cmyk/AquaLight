package com.aqua.aqualight.data.care.reminder

import android.net.Uri
import com.aqua.aqualight.data.user.UserDataScope

/** Stable owner + task identity used by alarm and notification PendingIntents. */
object CareReminderIdentity {

    private const val SCHEME = "aqualight"
    private const val ALARM_AUTHORITY = "care-reminder-alarm"
    private const val CONTENT_AUTHORITY = "care-reminder-content"

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
        require(taskId > 0L) {
            "taskId must be positive"
        }
        return "$owner\u001F$taskId"
    }

    private fun buildUri(
        authority: String,
        ownerUid: String,
        taskId: Long
    ): Uri {
        val owner = requireOwnerUid(ownerUid)
        require(taskId > 0L) {
            "taskId must be positive"
        }

        return Uri.Builder()
            .scheme(SCHEME)
            .authority(authority)
            .appendPath(owner)
            .appendPath(taskId.toString())
            .build()
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }
    }
}
