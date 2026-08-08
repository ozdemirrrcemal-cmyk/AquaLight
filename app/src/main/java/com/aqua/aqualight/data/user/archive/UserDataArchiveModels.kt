package com.aqua.aqualight.data.user.archive

import java.io.File

internal const val USER_DATA_BACKUP_FORMAT = "aqualight-user-backup"
internal const val USER_DATA_BACKUP_SCHEMA_VERSION = 1
internal const val USER_DATA_EXPORT_FORMAT = "aqualight-portable-data-export"
internal const val USER_DATA_EXPORT_SCHEMA_VERSION = 1
internal const val USER_DATA_BACKUP_MIME_TYPE = "application/zip"
internal const val USER_DATA_EXPORT_MIME_TYPE = "application/json"

internal data class UserDataBackupManifest(
    val format: String,
    val schemaVersion: Int,
    val createdAtMillis: Long,
    val sourceAppVersion: String,
    val aquariums: List<ArchiveAquarium>,
    val careTasks: List<ArchiveCareTask>,
    val deviceAssignments: List<ArchiveDeviceAssignment>
)

internal data class ArchiveAquarium(
    val id: Long,
    val name: String,
    val description: String,
    val photo: ArchiveMediaReference?,
    val setupDateEpochDay: Long?,
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val sizeUnit: String,
    val volumeUnit: String,
    val tankType: String,
    val tankStyle: String,
    val createdAtMillis: Long,
    val smartCareEnabled: Boolean,
    val careRemindersEnabled: Boolean,
    val plants: List<ArchivePlant>,
    val materials: List<ArchiveMaterial>,
    val livestock: List<ArchiveLivestock>
)

internal data class ArchiveMediaReference(
    val entryName: String,
    val byteSize: Int,
    val sha256: String
)

internal data class ArchivePlant(
    val id: Long,
    val plantName: String,
    val category: String,
    val markerX: Float,
    val markerY: Float
)

internal data class ArchiveMaterial(
    val id: Long,
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String,
    val note: String
)

internal data class ArchiveLivestock(
    val id: Long,
    val name: String,
    val category: String,
    val quantity: Int,
    val addedDateEpochDay: Long?,
    val note: String
)

internal data class ArchiveCareTask(
    val id: Long,
    val tankId: Long,
    val title: String,
    val description: String,
    val type: String,
    val source: String,
    val status: String,
    val dueAtMillis: Long,
    val completedAtMillis: Long?,
    val repeatEnabled: Boolean,
    val repeatIntervalDays: Int,
    val reminderEnabled: Boolean,
    val missedReminderEnabled: Boolean,
    val missedReminderDays: Int,
    val waterChangePercent: Int?,
    val note: String,
    val generatedRuleKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

internal data class ArchiveDeviceAssignment(
    val tankId: Long,
    val deviceUid: String,
    val assignedAtMillis: Long
)

internal data class DecodedUserDataBackup(
    val manifest: UserDataBackupManifest,
    val mediaByEntryName: Map<String, File>
)

internal data class PortableUserDataExport(
    val format: String,
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val sourceAppVersion: String,
    val account: PortableAccountData,
    val appPreferences: PortableAppPreferences,
    val usage: PortableUsageData,
    val aquariumData: PortableAquariumData
)

internal data class PortableAccountData(
    val accountId: String,
    val email: String,
    val username: String,
    val fullName: String,
    val firstName: String,
    val lastName: String,
    val city: String,
    val addressLine: String,
    val postCode: String,
    val phoneNumber: String,
    val country: String,
    val hasProfilePhoto: Boolean
)

internal data class PortableAppPreferences(
    val themeMode: String,
    val languageCode: String,
    val automaticDeviceUpdateChecksEnabled: Boolean,
    val loginAlertsEnabled: Boolean,
    val twoFactorEnabled: Boolean
)

internal data class PortableUsageData(
    val weeklyAutomationCount: Int,
    val weeklyAlertCount: Int,
    val todayAutomationCount: Int,
    val todayManualActionCount: Int,
    val lastEventTimeMillis: Long,
    val lastEventDescription: String
)

internal data class PortableAquariumData(
    val aquariums: List<ArchiveAquarium>,
    val careTasks: List<ArchiveCareTask>,
    val deviceAssignments: List<ArchiveDeviceAssignment>,
    val archivedPhotoCount: Int
)
