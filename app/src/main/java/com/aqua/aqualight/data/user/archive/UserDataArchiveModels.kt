package com.aqua.aqualight.data.user.archive

import java.io.File
import com.google.gson.annotations.SerializedName

internal const val USER_DATA_BACKUP_FORMAT = "aqualight-user-backup"
internal const val USER_DATA_BACKUP_SCHEMA_VERSION = 2
internal const val USER_DATA_EXPORT_FORMAT = "aqualight-portable-data-export"
internal const val USER_DATA_EXPORT_SCHEMA_VERSION = 2
internal const val USER_DATA_BACKUP_MIME_TYPE = "application/zip"
internal const val USER_DATA_EXPORT_MIME_TYPE = "application/json"

internal data class UserDataBackupManifest(
    @field:SerializedName("format")
    val format: String,
    @field:SerializedName("schemaVersion")
    val schemaVersion: Int,
    @field:SerializedName("createdAtMillis")
    val createdAtMillis: Long,
    @field:SerializedName("sourceAppVersion")
    val sourceAppVersion: String,
    @field:SerializedName("aquariums")
    val aquariums: List<ArchiveAquarium>,
    @field:SerializedName("careTasks")
    val careTasks: List<ArchiveCareTask>,
    @field:SerializedName("deviceAssignments")
    val deviceAssignments: List<ArchiveDeviceAssignment>
)

internal data class ArchiveAquarium(
    @field:SerializedName("id")
    val id: Long,
    @field:SerializedName("name")
    val name: String,
    @field:SerializedName("description")
    val description: String,
    @field:SerializedName("photo")
    val photo: ArchiveMediaReference?,
    @field:SerializedName("setupDateEpochDay")
    val setupDateEpochDay: Long?,
    @field:SerializedName("widthCm")
    val widthCm: Int,
    @field:SerializedName("lengthCm")
    val lengthCm: Int,
    @field:SerializedName("heightCm")
    val heightCm: Int,
    @field:SerializedName("sizeUnit")
    val sizeUnit: String,
    @field:SerializedName("volumeUnit")
    val volumeUnit: String,
    @field:SerializedName("tankType")
    val tankType: String,
    @field:SerializedName("tankStyle")
    val tankStyle: String,
    @field:SerializedName("createdAtMillis")
    val createdAtMillis: Long,
    @field:SerializedName("smartCareEnabled")
    val smartCareEnabled: Boolean,
    @field:SerializedName("careRemindersEnabled")
    val careRemindersEnabled: Boolean,
    @field:SerializedName("plants")
    val plants: List<ArchivePlant>,
    @field:SerializedName("materials")
    val materials: List<ArchiveMaterial>,
    @field:SerializedName("livestock")
    val livestock: List<ArchiveLivestock>
)

internal data class ArchiveMediaReference(
    @field:SerializedName("entryName")
    val entryName: String,
    @field:SerializedName("byteSize")
    val byteSize: Int,
    @field:SerializedName("sha256")
    val sha256: String
)

internal data class ArchivePlant(
    @field:SerializedName("id")
    val id: Long,
    @field:SerializedName("catalogId")
    val catalogId: String,
    @field:SerializedName("plantName")
    val plantName: String,
    @field:SerializedName("category")
    val category: String,
    @field:SerializedName("markerX")
    val markerX: Float,
    @field:SerializedName("markerY")
    val markerY: Float,
    @field:SerializedName("photo")
    val photo: ArchiveMediaReference? = null
)

internal data class ArchiveMaterial(
    @field:SerializedName("id")
    val id: Long,
    @field:SerializedName("productId")
    val productId: String,
    @field:SerializedName("categoryKey")
    val categoryKey: String,
    @field:SerializedName("categoryTitle")
    val categoryTitle: String,
    @field:SerializedName("name")
    val name: String,
    @field:SerializedName("brand")
    val brand: String,
    @field:SerializedName("note")
    val note: String
)

internal data class ArchiveLivestock(
    @field:SerializedName("id")
    val id: Long,
    @field:SerializedName("name")
    val name: String,
    @field:SerializedName("category")
    val category: String,
    @field:SerializedName("quantity")
    val quantity: Int,
    @field:SerializedName("addedDateEpochDay")
    val addedDateEpochDay: Long?,
    @field:SerializedName("note")
    val note: String,
    @field:SerializedName("catalogEntryId")
    val catalogEntryId: String,
    @field:SerializedName("photo")
    val photo: ArchiveMediaReference? = null
)

internal data class ArchiveCareTask(
    @field:SerializedName("id")
    val id: Long,
    @field:SerializedName("tankId")
    val tankId: Long,
    @field:SerializedName("title")
    val title: String,
    @field:SerializedName("description")
    val description: String,
    @field:SerializedName("type")
    val type: String,
    @field:SerializedName("source")
    val source: String,
    @field:SerializedName("status")
    val status: String,
    @field:SerializedName("dueAtMillis")
    val dueAtMillis: Long,
    @field:SerializedName("completedAtMillis")
    val completedAtMillis: Long?,
    @field:SerializedName("repeatEnabled")
    val repeatEnabled: Boolean,
    @field:SerializedName("repeatIntervalDays")
    val repeatIntervalDays: Int,
    @field:SerializedName("reminderEnabled")
    val reminderEnabled: Boolean,
    @field:SerializedName("missedReminderEnabled")
    val missedReminderEnabled: Boolean,
    @field:SerializedName("missedReminderDays")
    val missedReminderDays: Int,
    @field:SerializedName("waterChangePercent")
    val waterChangePercent: Int?,
    @field:SerializedName("note")
    val note: String,
    @field:SerializedName("generatedRuleKey")
    val generatedRuleKey: String,
    @field:SerializedName("createdAtMillis")
    val createdAtMillis: Long,
    @field:SerializedName("updatedAtMillis")
    val updatedAtMillis: Long
)

internal data class ArchiveDeviceAssignment(
    @field:SerializedName("tankId")
    val tankId: Long,
    @field:SerializedName("deviceUid")
    val deviceUid: String,
    @field:SerializedName("assignedAtMillis")
    val assignedAtMillis: Long
)

internal data class DecodedUserDataBackup(
    val manifest: UserDataBackupManifest,
    val mediaByEntryName: Map<String, File>
)

internal data class PortableUserDataExport(
    @field:SerializedName("format")
    val format: String,
    @field:SerializedName("schemaVersion")
    val schemaVersion: Int,
    @field:SerializedName("exportedAtMillis")
    val exportedAtMillis: Long,
    @field:SerializedName("sourceAppVersion")
    val sourceAppVersion: String,
    @field:SerializedName("account")
    val account: PortableAccountData,
    @field:SerializedName("appPreferences")
    val appPreferences: PortableAppPreferences,
    @field:SerializedName("usage")
    val usage: PortableUsageData,
    @field:SerializedName("aquariumData")
    val aquariumData: PortableAquariumData
)

internal data class PortableAccountData(
    @field:SerializedName("accountId")
    val accountId: String,
    @field:SerializedName("email")
    val email: String,
    @field:SerializedName("username")
    val username: String,
    @field:SerializedName("fullName")
    val fullName: String,
    @field:SerializedName("firstName")
    val firstName: String,
    @field:SerializedName("lastName")
    val lastName: String,
    @field:SerializedName("city")
    val city: String,
    @field:SerializedName("addressLine")
    val addressLine: String,
    @field:SerializedName("postCode")
    val postCode: String,
    @field:SerializedName("phoneNumber")
    val phoneNumber: String,
    @field:SerializedName("country")
    val country: String,
    @field:SerializedName("hasProfilePhoto")
    val hasProfilePhoto: Boolean
)

internal data class PortableAppPreferences(
    @field:SerializedName("themeMode")
    val themeMode: String,
    @field:SerializedName("languageCode")
    val languageCode: String,
    @field:SerializedName("automaticDeviceUpdateChecksEnabled")
    val automaticDeviceUpdateChecksEnabled: Boolean,
    @field:SerializedName("loginAlertsEnabled")
    val loginAlertsEnabled: Boolean,
    @field:SerializedName("twoFactorEnabled")
    val twoFactorEnabled: Boolean
)

internal data class PortableUsageData(
    @field:SerializedName("weeklyAutomationCount")
    val weeklyAutomationCount: Int,
    @field:SerializedName("weeklyAlertCount")
    val weeklyAlertCount: Int,
    @field:SerializedName("todayAutomationCount")
    val todayAutomationCount: Int,
    @field:SerializedName("todayManualActionCount")
    val todayManualActionCount: Int,
    @field:SerializedName("lastEventTimeMillis")
    val lastEventTimeMillis: Long,
    @field:SerializedName("lastEventDescription")
    val lastEventDescription: String
)

internal data class PortableAquariumData(
    @field:SerializedName("aquariums")
    val aquariums: List<ArchiveAquarium>,
    @field:SerializedName("careTasks")
    val careTasks: List<ArchiveCareTask>,
    @field:SerializedName("deviceAssignments")
    val deviceAssignments: List<ArchiveDeviceAssignment>,
    @field:SerializedName("archivedPhotoCount")
    val archivedPhotoCount: Int
)
