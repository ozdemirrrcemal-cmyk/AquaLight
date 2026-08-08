package com.aqua.aqualight.application.user

/** Owner-scoped backup, restore and portable export boundary. */
interface UserDataArchiveOperations {
    suspend fun createBackup(): Result<UserDataArchiveArtifact>

    suspend fun saveArtifact(
        artifactHandle: String,
        documentHandle: String
    ): Result<Unit>

    suspend fun inspectBackupDocument(documentHandle: String): Result<UserDataBackupCandidate>

    suspend fun restoreBackup(restoreHandle: String): Result<UserDataRestoreResult>

    suspend fun createPortableExport(): Result<UserDataArchiveArtifact>

    /** Releases one opaque app-owned staging handle after save, restore or cancellation. */
    fun discard(handle: String)
}

data class UserDataArchiveArtifact(
    val handle: String,
    val suggestedFileName: String,
    val mimeType: String
)

data class UserDataBackupCandidate(
    val handle: String,
    val inspection: UserDataBackupInspection
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
