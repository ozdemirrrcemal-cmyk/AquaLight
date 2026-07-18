package com.aqua.aqualight.data.feedback

import android.content.Context

internal interface FeedbackOrphanStore {
    fun pendingPaths(): Set<String>
    fun add(path: String)
    fun remove(path: String)
}

internal class SharedPreferencesFeedbackOrphanStore(
    context: Context
) : FeedbackOrphanStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    override fun pendingPaths(): Set<String> = synchronized(lock) {
        preferences.getStringSet(KEY_PENDING_PATHS, emptySet())?.toSet().orEmpty()
    }

    override fun add(path: String) {
        if (path.isBlank()) return
        synchronized(lock) {
            val updated = pendingPaths().toMutableSet().apply { add(path) }
            preferences.edit().putStringSet(KEY_PENDING_PATHS, updated).apply()
        }
    }

    override fun remove(path: String) {
        synchronized(lock) {
            val updated = pendingPaths().toMutableSet().apply { remove(path) }
            preferences.edit().putStringSet(KEY_PENDING_PATHS, updated).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "feedback_orphan_cleanup"
        const val KEY_PENDING_PATHS = "pending_storage_paths"
    }
}
