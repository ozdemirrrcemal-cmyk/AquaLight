package com.aqua.aqualight.data.feedback

import android.content.Context

internal data class PendingFeedbackUpload(
    val documentId: String,
    val storagePath: String
)

/**
 * Durable local journal for a feedback screenshot transaction.
 *
 * An entry is committed to disk before Storage upload starts and is removed only after either:
 * - Firestore is confirmed to contain the matching screenshot path, or
 * - the Storage object is confirmed deleted.
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
                val storagePath = (value as? String)?.takeIf(String::isNotBlank)
                if (documentId == null || storagePath == null) null
                else PendingFeedbackUpload(documentId, storagePath)
            }
            .sortedBy(PendingFeedbackUpload::documentId)
            .toList()
    }

    override fun put(entry: PendingFeedbackUpload) {
        require(entry.documentId.isNotBlank()) { "documentId must not be blank" }
        require(entry.storagePath.isNotBlank()) { "storagePath must not be blank" }
        synchronized(lock) {
            commitOrThrow(
                preferences.edit().putString(
                    ENTRY_PREFIX + entry.documentId,
                    entry.storagePath
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
        const val PREFERENCES_NAME = "feedback_submission_journal_v1"
        const val ENTRY_PREFIX = "pending."
    }
}
