package com.aqua.aqualight.data.feedback

import android.content.Context
import org.json.JSONObject

internal data class PendingFeedbackUpload(
    val documentId: String,
    val ownerUid: String,
    val storagePath: String
)

/**
 * Durable local journal for a feedback screenshot transaction.
 *
 * An entry is committed to disk before remote work starts and is removed only after either:
 * - Firestore is confirmed to contain the matching owner/path as committed, or
 * - the matching server fence is aborted and the Storage object is confirmed deleted.
 */
internal interface FeedbackSubmissionJournalStore {
    fun pendingEntries(): List<PendingFeedbackUpload>
    fun put(entry: PendingFeedbackUpload)
    fun remove(documentId: String)
}

internal class SharedPreferencesFeedbackSubmissionJournalStore(
    context: Context
) : FeedbackSubmissionJournalStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    override fun pendingEntries(): List<PendingFeedbackUpload> = synchronized(lock) {
        preferences.all.entries
            .asSequence()
            .filter { (key, value) -> key.startsWith(ENTRY_PREFIX) && value is String }
            .mapNotNull { (key, value) ->
                val documentId = key.removePrefix(ENTRY_PREFIX).takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                runCatching {
                    val json = JSONObject(value as String)
                    PendingFeedbackUpload(
                        documentId = documentId,
                        ownerUid = json.getString(JSON_OWNER_UID).takeIf(String::isNotBlank)
                            ?: error("ownerUid is blank"),
                        storagePath = json.getString(JSON_STORAGE_PATH).takeIf(String::isNotBlank)
                            ?: error("storagePath is blank")
                    )
                }.getOrNull()
            }
            .sortedBy(PendingFeedbackUpload::documentId)
            .toList()
    }

    override fun put(entry: PendingFeedbackUpload) {
        require(entry.documentId.isNotBlank()) { "documentId must not be blank" }
        require(entry.ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        require(entry.storagePath.isNotBlank()) { "storagePath must not be blank" }
        val encoded = JSONObject()
            .put(JSON_OWNER_UID, entry.ownerUid)
            .put(JSON_STORAGE_PATH, entry.storagePath)
            .toString()

        synchronized(lock) {
            commitOrThrow(
                preferences.edit().putString(
                    ENTRY_PREFIX + entry.documentId,
                    encoded
                )
            )
        }
    }

    override fun remove(documentId: String) {
        if (documentId.isBlank()) return
        synchronized(lock) {
            commitOrThrow(preferences.edit().remove(ENTRY_PREFIX + documentId))
        }
    }

    private fun commitOrThrow(editor: android.content.SharedPreferences.Editor) {
        check(editor.commit()) { "Feedback submission journal could not be committed." }
    }

    private companion object {
        const val PREFERENCES_NAME = "feedback_submission_journal_v2"
        const val ENTRY_PREFIX = "pending."
        const val JSON_OWNER_UID = "ownerUid"
        const val JSON_STORAGE_PATH = "storagePath"
    }
}
