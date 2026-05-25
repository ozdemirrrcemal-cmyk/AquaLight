package com.aqua.aqualight.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugAppInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                DebugDeviceSeeder.seedIfNeeded(appContext)
            }.onFailure { error ->
                Log.e(TAG, "Debug device seed failed.", error)
            }
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        private const val TAG = "DebugAppInitializer"
    }
}