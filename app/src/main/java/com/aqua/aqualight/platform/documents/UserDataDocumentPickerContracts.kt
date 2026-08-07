package com.aqua.aqualight.platform.documents

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

internal data class UserDataDocumentCreateRequest(
    val suggestedFileName: String,
    val mimeType: String
)

/** Central SAF create-document contract used by data-management presentation. */
internal class UserDataCreateDocumentContract :
    ActivityResultContract<UserDataDocumentCreateRequest, String?>() {

    override fun createIntent(
        context: Context,
        input: UserDataDocumentCreateRequest
    ): Intent {
        require(input.suggestedFileName.isNotBlank())
        require(input.mimeType.isNotBlank())
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.suggestedFileName)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data?.toString()
    }
}

/** Central SAF backup-selection contract; only archive-like documents are offered. */
internal class UserDataOpenBackupDocumentContract : ActivityResultContract<Unit, String?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(BACKUP_MIME_TYPE)
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(BACKUP_MIME_TYPE, GENERIC_BINARY_MIME_TYPE)
            )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data?.toString()
    }

    private companion object {
        const val BACKUP_MIME_TYPE = "application/zip"
        const val GENERIC_BINARY_MIME_TYPE = "application/octet-stream"
    }
}
