package com.aqua.aqualight.application.user

/** Owner-scoped backup, restore and portable export boundary. */
interface UserDataArchiveOperations {
    suspend fun createBackup(): Result<UserDataArchiveArtifact>

    suspend fun inspectBackup(content: ByteArray): Result<UserDataBackupInspection>

    suspend fun restoreBackup(content: ByteArray): Result<UserDataRestoreResult>

    suspend fun createPortableExport(): Result<UserDataArchiveArtifact>
}

data class UserDataArchiveArtifact(
    val suggestedFileName: String,
    val mimeType: String,
    val content: ByteArray
)

data class UserDataBackupInspection(
    val createdAtMillis: Long,
    val sourceAppVersion: String,
    val aquariumCount: Int,
    val careTaskCount: Int,
    val deviceAssignmentCount: Int,
    val photoCount: Int
)

data class UserDataRestoreResult(
    val restoredAquariumCount: Int,
    val restoredCareTaskCount: Int,
    val restoredDeviceAssignmentCount: Int,
    val skippedDeviceAssignmentCount: Int,
    val reminderReconciliationWarning: Boolean
)

/** Opaque document-handle I/O used by the UI without exposing ContentResolver or streams. */
interface UserDataDocumentOperations {
    suspend fun read(documentHandle: String): Result<ByteArray>

    suspend fun write(documentHandle: String, content: ByteArray): Result<Unit>
}
