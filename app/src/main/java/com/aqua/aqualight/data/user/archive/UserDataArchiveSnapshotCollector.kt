package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import java.io.File
import kotlinx.coroutines.flow.first

internal data class UserDataArchiveDataSources(
    val aquariumStore: AquariumTankDataStoreManager,
    val careTaskStore: CareTaskDataStoreManager,
    val assignmentRepository: TankDeviceAssignmentRepository
)

internal class UserDataArchiveSnapshotCollector(
    private val ownerUid: String,
    private val dataSources: UserDataArchiveDataSources,
    private val devicesRepository: DevicesRepository,
    private val preferences: UserPreferencesManager,
    private val mediaGateway: UserDataArchiveMediaGateway
) {

    suspend fun collectAquariumData(
        mediaDirectory: File? = null
    ): UserDataAquariumSnapshot {
        requireOwner()
        if (mediaDirectory != null) {
            require(mediaDirectory.isDirectory || mediaDirectory.mkdirs()) {
                "Backup media staging directory could not be created."
            }
            require(mediaDirectory.listFiles().isNullOrEmpty()) {
                "Backup media staging directory must be empty."
            }
        }
        val tanks = dataSources.aquariumStore.tanksSnapshotForOwner(ownerUid)
        val tankIds = tanks.mapTo(mutableSetOf()) { tank -> tank.id }
        val tasks = dataSources.careTaskStore.tasksFlow.first()
            .filter { task -> task.tankId in tankIds }
            .map { task -> task.toArchiveCareTask() }
        val assignments = collectAssignments(tankIds)
        val media = linkedMapOf<String, File>()
        var archivedPhotoCount = 0
        val aquariums = tanks.map { tank ->
            val photoReference = if (mediaDirectory == null) {
                if (mediaGateway.canSnapshotTankPhoto(tank.photoUri)) archivedPhotoCount += 1
                null
            } else {
                val entryName = "${UserDataBackupLimits.MEDIA_PREFIX}${tank.id}.jpg"
                val destination = File(mediaDirectory, "tank_${tank.id}.media")
                mediaGateway.snapshotTankPhoto(tank.photoUri, destination)?.let { staged ->
                    archivedPhotoCount += 1
                    media[entryName] = staged
                    ArchiveMediaReference(
                        entryName = entryName,
                        byteSize = staged.length().toInt(),
                        sha256 = sha256(staged)
                    )
                }
            }
            tank.toArchiveAquarium(photoReference)
        }
        requireOwner()
        return UserDataAquariumSnapshot(
            aquariums = aquariums,
            careTasks = tasks,
            deviceAssignments = assignments,
            mediaByEntryName = media.toMap(),
            archivedPhotoCount = archivedPhotoCount
        )
    }

    suspend fun collectPortableProfile(): PortableProfileSnapshot {
        requireOwner()
        val prefs = preferences.userPrefsFlow.first()
        check(prefs.isLoggedIn && UserDataScope.normalizeOwnerUid(prefs.uid) == ownerUid) {
            "User preferences do not belong to the active archive owner."
        }
        return PortableProfileSnapshot(
            account = PortableAccountData(
                accountId = ownerUid,
                email = prefs.email,
                username = prefs.username,
                fullName = prefs.fullName,
                firstName = prefs.firstName,
                lastName = prefs.lastName,
                city = prefs.city,
                addressLine = prefs.addressLine,
                postCode = prefs.postCode,
                phoneNumber = prefs.phoneNumber,
                country = prefs.country,
                hasProfilePhoto = prefs.profilePhotoUrl.isNotBlank()
            ),
            preferences = PortableAppPreferences(
                themeMode = prefs.themeMode,
                languageCode = prefs.languageCode,
                automaticDeviceUpdateChecksEnabled = prefs.autoUpdateEnabled,
                loginAlertsEnabled = prefs.loginAlertsEnabled,
                twoFactorEnabled = prefs.twoFactorEnabled
            ),
            usage = PortableUsageData(
                weeklyAutomationCount = prefs.weeklyAutomationCount,
                weeklyAlertCount = prefs.weeklyAlertCount,
                todayAutomationCount = prefs.todayAutomationCount,
                todayManualActionCount = prefs.todayManualActionCount,
                lastEventTimeMillis = prefs.lastEventTimeMillis,
                lastEventDescription = prefs.lastEventDescription
            )
        )
    }

    private suspend fun collectAssignments(tankIds: Set<Long>): List<ArchiveDeviceAssignment> {
        val currentDeviceUids = devicesRepository.currentDevices()
            .map { snapshot -> snapshot.deviceUid }
        return currentDeviceUids.mapNotNull { deviceUid ->
            dataSources.assignmentRepository.assignmentForDevice(deviceUid)
                ?.takeIf { assignment -> assignment.tankId in tankIds }
                ?.toArchiveAssignment()
        }
    }

    private fun requireOwner() {
        check(UserDataScope.requireCurrentUid() == ownerUid) {
            "Authenticated owner changed during user-data archive operation."
        }
    }
}

internal data class UserDataAquariumSnapshot(
    val aquariums: List<ArchiveAquarium>,
    val careTasks: List<ArchiveCareTask>,
    val deviceAssignments: List<ArchiveDeviceAssignment>,
    val mediaByEntryName: Map<String, File>,
    val archivedPhotoCount: Int
)

internal data class PortableProfileSnapshot(
    val account: PortableAccountData,
    val preferences: PortableAppPreferences,
    val usage: PortableUsageData
)
