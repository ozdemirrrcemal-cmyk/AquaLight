package com.aqua.aqualight.platform.permissions

import android.content.Context

/**
 * Installation-scoped record of runtime permissions already requested by AquaLight.
 *
 * Android does not expose a durable "asked before" flag. Persisting it centrally lets
 * [PermissionPolicy] distinguish a first request from a permanent denial without
 * allowing individual screens to invent their own heuristics.
 */
class PermissionRequestHistoryStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun wereAllRequested(permissions: Collection<String>): Boolean {
        return permissions.isNotEmpty() && permissions.all(::wasRequested)
    }

    fun markRequested(permissions: Collection<String>) {
        if (permissions.isEmpty()) return

        preferences.edit().apply {
            permissions.forEach { permission ->
                putBoolean(key(permission), true)
            }
        }.apply()
    }

    internal fun wasRequested(permission: String): Boolean {
        return preferences.getBoolean(key(permission), false)
    }

    private fun key(permission: String): String = "requested:$permission"

    private companion object {
        const val PREFERENCES_NAME = "central_permission_request_history_v1"
    }
}
