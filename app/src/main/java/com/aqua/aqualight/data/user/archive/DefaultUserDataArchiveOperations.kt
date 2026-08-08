package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.user.UserDataArchiveArtifact
import com.aqua.aqualight.application.user.UserDataArchiveOperations
import com.aqua.aqualight.application.user.UserDataBackupCandidate
import com.aqua.aqualight.application.user.UserDataBackupInspection
import com.aqua.aqualight.application.user.UserDataRestoreResult
import com.aqua.aqualight.platform.documents.AndroidUserDataDocumentOperations
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class UserDataArchiveRuntimeDependencies(
    val staging: UserDataArchiveStaging,
    val documents: AndroidUserDataDocumentOperations,
    val codec: UserDataBackupCodec = UserDataBackupCodec(),
    val nowMillis: () -> Long = System::currentTimeMillis
)

/** Single owner-scoped coordinator for backup, restore, document streaming and portable export. */
internal class DefaultUserDataArchiveOperations(
    private val sourceAppVersion: String,
    private val snapshotCollector: UserDataArchiveSnapshotCollector,
    private val restorer: UserDataBackupRestorer,
    private val runtime: UserDataArchiveRuntimeDependencies
) : UserDataArchiveOperations {

    private val mutationMutex = Mutex()

    override suspend fun createBackup(): Result<UserDataArchiveArtifact> =
        operationResult {
            mutationMutex.withLock {
                createStagedArtifact(
                    prefix = "AquaLight-backup",
                    extension = "aqlbackup",
                    mimeType = USER_DATA_BACKUP_MIME_TYPE
                ) { handle, destination ->
                    val mediaDirectory = runtime.staging.createScratchDirectory(handle)
                    try {
                        val createdAt = runtime.nowMillis()
                        val snapshot = snapshotCollector.collectAquariumData(mediaDirectory)
                        val manifest = UserDataBackupManifest(
                            format = USER_DATA_BACKUP_FORMAT,
                            schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION,
                            createdAtMillis = createdAt,
                            sourceAppVersion = sourceAppVersion,
                            aquariums = snapshot.aquariums,
                            careTasks = snapshot.careTasks,
                            deviceAssignments = snapshot.deviceAssignments
                        )
                        runtime.codec.encode(manifest, snapshot.mediaByEntryName, destination)
                        createdAt
                    } finally {
                        runtime.staging.discardScratch(mediaDirectory)
                    }
                }
            }
        }

    override suspend fun saveArtifact(
        artifactHandle: String,
        documentHandle: String
    ): Result<Unit> = operationResult {
        val source = runtime.staging.payload(artifactHandle)
        runtime.documents.exportDocument(documentHandle, source).getOrThrow()
    }

    override suspend fun inspectBackupDocument(
        documentHandle: String
    ): Result<UserDataBackupCandidate> = operationResult {
        val session = runtime.staging.createSession()
        var retainSession = false
        try {
            runtime.documents.importDocument(documentHandle, session.payload).getOrThrow()
            val inspection = withDecodedBackup(session.handle) { backup ->
                val manifest = backup.manifest
                UserDataBackupInspection(
                    createdAtMillis = manifest.createdAtMillis,
                    sourceAppVersion = manifest.sourceAppVersion,
                    aquariumCount = manifest.aquariums.size,
                    careTaskCount = manifest.careTasks.size,
                    deviceAssignmentCount = manifest.deviceAssignments.size,
                    photoCount = manifest.aquariums.count { aquarium -> aquarium.photo != null }
                )
            }
            UserDataBackupCandidate(
                handle = session.handle,
                inspection = inspection
            ).also { retainSession = true }
        } finally {
            if (!retainSession) runtime.staging.discard(session.handle)
        }
    }

    override suspend fun restoreBackup(restoreHandle: String): Result<UserDataRestoreResult> =
        operationResult {
            mutationMutex.withLock {
                withDecodedBackup(restoreHandle, restorer::restore)
            }
        }

    override suspend fun createPortableExport(): Result<UserDataArchiveArtifact> =
        operationResult {
            mutationMutex.withLock {
                createStagedArtifact(
                    prefix = "AquaLight-data-export",
                    extension = "json",
                    mimeType = USER_DATA_EXPORT_MIME_TYPE
                ) { _, destination ->
                    val exportedAt = runtime.nowMillis()
                    val aquarium = snapshotCollector.collectAquariumData()
                    val profile = snapshotCollector.collectPortableProfile()
                    val export = PortableUserDataExport(
                        format = USER_DATA_EXPORT_FORMAT,
                        schemaVersion = USER_DATA_EXPORT_SCHEMA_VERSION,
                        exportedAtMillis = exportedAt,
                        sourceAppVersion = sourceAppVersion,
                        account = profile.account,
                        appPreferences = profile.preferences,
                        usage = profile.usage,
                        aquariumData = PortableAquariumData(
                            aquariums = aquarium.aquariums.map { item -> item.copy(photo = null) },
                            careTasks = aquarium.careTasks,
                            deviceAssignments = aquarium.deviceAssignments,
                            archivedPhotoCount = aquarium.archivedPhotoCount
                        )
                    )
                    runtime.codec.encodePortableExport(export, destination)
                    exportedAt
                }
            }
        }

    override fun discard(handle: String) {
        runtime.staging.discard(handle)
    }

    private suspend fun createStagedArtifact(
        prefix: String,
        extension: String,
        mimeType: String,
        writer: suspend (String, File) -> Long
    ): UserDataArchiveArtifact {
        val session = runtime.staging.createSession()
        var retainSession = false
        try {
            val createdAt = writer(session.handle, session.payload)
            return UserDataArchiveArtifact(
                handle = session.handle,
                suggestedFileName = datedFileName(prefix, createdAt, extension),
                mimeType = mimeType
            ).also { retainSession = true }
        } finally {
            if (!retainSession) runtime.staging.discard(session.handle)
        }
    }

    private suspend fun <T> withDecodedBackup(
        handle: String,
        block: suspend (DecodedUserDataBackup) -> T
    ): T {
        val scratch = runtime.staging.createScratchDirectory(handle)
        return try {
            val mediaDirectory = File(scratch, "media")
            val decoded = runtime.codec.decode(runtime.staging.payload(handle), mediaDirectory)
            block(decoded)
        } finally {
            runtime.staging.discardScratch(scratch)
        }
    }

    private suspend fun <T> operationResult(block: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            runCatching {
                runtime.staging.cleanupExpired()
                block()
            }.rethrowCancellation()
        }
    }
}

private fun <T> Result<T>.rethrowCancellation(): Result<T> {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
    return this
}

private fun datedFileName(prefix: String, timeMillis: Long, extension: String): String {
    val date = Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return "$prefix-$date.$extension"
}
