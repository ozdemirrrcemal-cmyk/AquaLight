package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
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

    suspend fun collectAquariumData(): UserDataAquariumSnapshot {
        requireOwner()
        val tanks = dataSources.aquariumStore.tanksSnapshotForOwner(ownerUid)
        val tankIds = tanks.mapTo(mutableSetOf()) { tank -> tank.id }
        val tasks = dataSources.careTaskStore.tasksFlow.first()
            .filter { task -> task.tankId in tankIds }
            .map { task -> task.toArchiveCareTask() }
        val assignments = collectAssignments(tankIds)
        val media = linkedMapOf<String, ByteArray>()
        val aquariums = tanks.map { tank ->
            val photoReference = mediaGateway.snapshotTankPhoto(tank.photoUri)?.let { bytes ->
                val entryName = "${UserDataBackupLimits.MEDIA_PREFIX}${tank.id}.jpg"
                media[entryName] = bytes
                ArchiveMediaReference(
                    entryName = entryName,
                    byteSize = bytes.size,
                    sha256 = sha256(bytes)
                )
            }
            tank.toArchiveAquarium(photoReference)
        }
        requireOwner()
        return UserDataAquariumSnapshot(
            aquariums = aquariums,
            careTasks = tasks,
            deviceAssignments = assignments,
            mediaByEntryName = media.toMap()
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
    val mediaByEntryName: Map<String, ByteArray>
)

internal data class PortableProfileSnapshot(
    val account: PortableAccountData,
    val preferences: PortableAppPreferences,
    val usage: PortableUsageData
)
