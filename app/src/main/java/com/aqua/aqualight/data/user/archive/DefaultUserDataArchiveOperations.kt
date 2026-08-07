package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.user.UserDataArchiveArtifact
import com.aqua.aqualight.application.user.UserDataArchiveOperations
import com.aqua.aqualight.application.user.UserDataBackupInspection
import com.aqua.aqualight.application.user.UserDataRestoreResult
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single owner-scoped coordinator for backup, restore and portable data export. */
internal class DefaultUserDataArchiveOperations(
    private val sourceAppVersion: String,
    private val snapshotCollector: UserDataArchiveSnapshotCollector,
    private val restorer: UserDataBackupRestorer,
    private val codec: UserDataBackupCodec = UserDataBackupCodec(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserDataArchiveOperations {

    private val mutationMutex = Mutex()

    override suspend fun createBackup(): Result<UserDataArchiveArtifact> =
        operationResult {
            mutationMutex.withLock {
                val createdAt = nowMillis()
                val snapshot = snapshotCollector.collectAquariumData()
                val manifest = UserDataBackupManifest(
                    format = USER_DATA_BACKUP_FORMAT,
                    schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION,
                    createdAtMillis = createdAt,
                    sourceAppVersion = sourceAppVersion,
                    aquariums = snapshot.aquariums,
                    careTasks = snapshot.careTasks,
                    deviceAssignments = snapshot.deviceAssignments
                )
                UserDataArchiveArtifact(
                    suggestedFileName = datedFileName("AquaLight-backup", createdAt, "aqlbackup"),
                    mimeType = USER_DATA_BACKUP_MIME_TYPE,
                    content = codec.encode(manifest, snapshot.mediaByEntryName)
                )
            }
        }

    override suspend fun inspectBackup(content: ByteArray): Result<UserDataBackupInspection> =
        operationResult {
            val manifest = codec.decode(content).manifest
            UserDataBackupInspection(
                createdAtMillis = manifest.createdAtMillis,
                sourceAppVersion = manifest.sourceAppVersion,
                aquariumCount = manifest.aquariums.size,
                careTaskCount = manifest.careTasks.size,
                deviceAssignmentCount = manifest.deviceAssignments.size,
                photoCount = manifest.aquariums.count { aquarium -> aquarium.photo != null }
            )
        }

    override suspend fun restoreBackup(content: ByteArray): Result<UserDataRestoreResult> =
        operationResult {
            mutationMutex.withLock {
                restorer.restore(codec.decode(content))
            }
        }

    override suspend fun createPortableExport(): Result<UserDataArchiveArtifact> =
        operationResult {
            mutationMutex.withLock {
                val exportedAt = nowMillis()
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
                        archivedPhotoCount = aquarium.mediaByEntryName.size
                    )
                )
                UserDataArchiveArtifact(
                    suggestedFileName = datedFileName(
                        "AquaLight-data-export",
                        exportedAt,
                        "json"
                    ),
                    mimeType = USER_DATA_EXPORT_MIME_TYPE,
                    content = codec.encodePortableExport(export)
                )
            }
        }

    private suspend fun <T> operationResult(block: suspend () -> T): Result<T> {
        return withContext(dispatcher) {
            try {
                Result.success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    private fun datedFileName(prefix: String, timeMillis: Long, extension: String): String {
        val date = Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return "$prefix-$date.$extension"
    }
}
