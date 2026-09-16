package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.aquarium.AquariumAutomationProfile
import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumLivestockCategory
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategory
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.data.aquarium.devices.TankLightInstallationProfile
import com.aqua.aqualight.data.aquarium.devices.TankLightRecommendationOutcome
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.io.File
import java.security.MessageDigest

internal object UserDataBackupLimits {
    const val MANIFEST_ENTRY = "manifest.json"
    const val MEDIA_PREFIX = "media/tanks/"
    const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
    const val MAX_UNCOMPRESSED_ARCHIVE_BYTES = 64 * 1024 * 1024
    const val MAX_MEDIA_ENTRY_BYTES = 8 * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 256
    const val MAX_AQUARIUMS = 100
    const val MAX_CARE_TASKS = 10_000
    const val MAX_DEVICE_ASSIGNMENTS = 500
    const val MAX_ITEMS_PER_AQUARIUM = 2_000
    const val BUFFER_SIZE = 8 * 1024

    val mediaEntryPattern = Regex("media/tanks/[1-9][0-9]*\\.jpg")
    val sha256Pattern = Regex("[0-9a-fA-F]{64}")
}

internal class UserDataBackupValidator {

    fun validate(
        manifest: UserDataBackupManifest,
        mediaByEntryName: Map<String, File>
    ) {
        validateEnvelope(manifest)
        val tankIds = validateAquariums(manifest.aquariums)
        validateCareTasks(manifest.careTasks, tankIds)
        validateAssignments(manifest.deviceAssignments, tankIds)
        validateMedia(manifest.aquariums, mediaByEntryName)
    }

    fun requireSafeEntryName(entryName: String) {
        requireSafeArchiveEntryName(entryName)
    }

    fun requireValidMediaEntryName(entryName: String) {
        requireValidArchiveMediaEntryName(entryName)
    }

    private fun validateAquariums(aquariums: List<ArchiveAquarium>): Set<Long> {
        val tankIds = aquariums.map(ArchiveAquarium::id)
        require(tankIds.all { tankId -> tankId > 0L }) {
            "Backup contains an invalid aquarium id."
        }
        require(tankIds.distinct().size == tankIds.size) {
            "Backup contains duplicate aquarium ids."
        }
        aquariums.forEach(::validateAquarium)
        return tankIds.toSet()
    }

    private fun validateAquarium(aquarium: ArchiveAquarium) {
        require(aquarium.name.isNotBlank()) { "Backup aquarium name is blank." }
        require(aquarium.widthCm > 0 && aquarium.lengthCm > 0 && aquarium.heightCm > 0) {
            "Backup aquarium dimensions are invalid."
        }
        require(aquarium.sizeUnit.isNotBlank() && aquarium.volumeUnit.isNotBlank()) {
            "Backup aquarium units are invalid."
        }
        require(aquarium.createdAtMillis > 0L) {
            "Backup aquarium creation time is invalid."
        }
        require(
            aquarium.automationProfile.contractRevision ==
                AquariumAutomationProfile.CONTRACT_REVISION
        ) { "Backup aquarium automation profile is unsupported." }
        aquarium.automationProfile.waterDepthCm?.let { depth ->
            require(depth in 1..aquarium.heightCm) {
                "Backup aquarium water depth is invalid."
            }
        }
        aquarium.automationProfile.substrateDepthCm?.let { depth ->
            require(depth in 1 until aquarium.heightCm) {
                "Backup aquarium substrate depth is invalid."
            }
        }
        val waterDepth = aquarium.automationProfile.waterDepthCm
        val substrateDepth = aquarium.automationProfile.substrateDepthCm
        require(waterDepth == null || substrateDepth == null ||
            waterDepth + substrateDepth <= aquarium.heightCm
        ) { "Backup aquarium geometry is inconsistent." }
        if (aquarium.automationProfile.daylightExposure == AquariumDaylightExposure.DIRECT) {
            val start = requireNotNull(aquarium.automationProfile.daylightStartMinute)
            val end = requireNotNull(aquarium.automationProfile.daylightEndMinute)
            require(start in 0 until end && end < MINUTES_PER_DAY) {
                "Backup aquarium daylight window is invalid."
            }
        } else {
            require(aquarium.automationProfile.daylightStartMinute == null &&
                aquarium.automationProfile.daylightEndMinute == null
            ) { "Only direct daylight can carry a backup observation window." }
        }
        validateArchiveItemIds(aquarium.plants.map(ArchivePlant::id))
        validateArchiveItemIds(aquarium.materials.map(ArchiveMaterial::id))
        validateArchiveItemIds(aquarium.livestock.map(ArchiveLivestock::id))
        require(
            aquarium.livestock.all { item ->
                AquariumLivestockCategory.isSupported(item.category)
            }
        ) { "Backup aquarium livestock category is not a supported stable code." }
        aquarium.plants.forEach { plant ->
            require(plant.catalogId.isNotBlank()) {
                "Backup aquarium plant catalog identity is missing."
            }
            require(
                plant.lightDemand == AquariumPlantLightCatalog.resolve(plant.catalogId)
            ) { "Backup aquarium plant demand does not match the reviewed catalog." }
        }
        aquarium.materials.forEach { material ->
            require(AquariumMaterialCategory.isSupported(material.categoryKey)) {
                "Backup aquarium material category is not a supported stable code."
            }
            require(
                AquariumSubstrateSemantics.matchesCatalogCategory(
                    productId = material.productId,
                    categoryKey = material.categoryKey
                )
            ) { "Backup aquarium material category conflicts with the reviewed catalog." }
            require(
                material.substrateSemantic == AquariumSubstrateSemantics.resolve(
                    productId = material.productId,
                    categoryKey = material.categoryKey
                )
            ) { "Backup aquarium substrate semantic does not match the reviewed catalog." }
        }
        val hasCo2Component = aquarium.materials.any { material ->
            material.categoryKey == MATERIAL_CATEGORY_CO2
        }
        require(
            if (hasCo2Component) {
                aquarium.automationProfile.co2Readiness != AquariumCo2Readiness.NOT_INSTALLED
            } else {
                aquarium.automationProfile.co2Readiness == AquariumCo2Readiness.NOT_INSTALLED
            }
        ) { "Backup aquarium CO2 readiness conflicts with the component inventory." }
        val hasSurfaceObservation = aquarium.automationProfile.latestSurfaceGrowth !=
            AquariumSurfaceGrowth.UNKNOWN
        require(
            hasSurfaceObservation ==
                (aquarium.automationProfile.latestObservationEpochDay != null)
        ) { "Backup surface observation value and date must be stored together." }
        val hasShrimp = aquarium.livestock.any { item ->
            item.category == AquariumLivestockCategory.SHRIMP
        }
        require(
            if (hasShrimp) {
                aquarium.automationProfile.shelterAvailability !=
                    AquariumShelterAvailability.NOT_REQUIRED
            } else {
                aquarium.automationProfile.shelterAvailability ==
                    AquariumShelterAvailability.NOT_REQUIRED &&
                    aquarium.automationProfile.latestSurfaceGrowth !=
                    AquariumSurfaceGrowth.TARGET_BIOFILM
            }
        ) { "Backup shelter or target-biofilm state conflicts with livestock inventory." }
        aquarium.livestock.forEach { livestock ->
            require(livestock.quantity > 0) {
                "Backup livestock quantity is invalid."
            }
        }
    }

    private fun validateCareTasks(
        tasks: List<ArchiveCareTask>,
        tankIds: Set<Long>
    ) {
        val taskIds = tasks.map(ArchiveCareTask::id)
        require(taskIds.all { taskId -> taskId > 0L }) {
            "Backup contains an invalid care task id."
        }
        require(taskIds.distinct().size == taskIds.size) {
            "Backup contains duplicate care task ids."
        }
        tasks.forEach { task -> validateCareTask(task, tankIds) }
    }

    private fun validateCareTask(task: ArchiveCareTask, tankIds: Set<Long>) {
        require(task.tankId in tankIds) {
            "Backup care task references an unknown aquarium."
        }
        require(task.title.isNotBlank()) { "Backup care task title is blank." }
        require(task.dueAtMillis > 0L) { "Backup care task due time is invalid." }
        require(task.createdAtMillis > 0L && task.updatedAtMillis > 0L) {
            "Backup care task timestamps are invalid."
        }
        requireArchiveEnumValue<CareTaskType>(task.type, "type")
        requireArchiveEnumValue<CareTaskSource>(task.source, "source")
        requireArchiveEnumValue<CareTaskStatus>(task.status, "status")
    }

    private fun validateAssignments(
        assignments: List<ArchiveDeviceAssignment>,
        tankIds: Set<Long>
    ) {
        val devices = mutableSetOf<String>()
        assignments.forEach { assignment ->
            require(assignment.tankId in tankIds) {
                "Backup device assignment references an unknown aquarium."
            }
            require(
                assignment.deviceUid.isNotBlank() &&
                    assignment.deviceUid == assignment.deviceUid.trim()
            ) {
                "Backup device assignment device id is invalid."
            }
            require(assignment.assignedAtMillis > 0L) {
                "Backup device assignment time is invalid."
            }
            require(
                assignment.lightInstallation.contractRevision ==
                    TankLightInstallationProfile.CONTRACT_REVISION
            ) { "Backup light installation profile is unsupported." }
            require(assignment.lightRecommendations.size <= MAX_LIGHT_RECOMMENDATIONS) {
                "Backup contains too many light recommendation records."
            }
            val recommendationIds = assignment.lightRecommendations.map { snapshot ->
                require(snapshot.recommendationId.isNotBlank()) {
                    "Backup light recommendation identity is missing."
                }
                require(snapshot.profileFingerprint.matches(PROFILE_FINGERPRINT_PATTERN)) {
                    "Backup light recommendation fingerprint is invalid."
                }
                require(
                    snapshot.policyVersion == DeviceLightQuickSetupCalculator.POLICY_VERSION &&
                    snapshot.evidenceSourceIds.isNotEmpty() &&
                    snapshot.reasonCodes.isNotEmpty()
                ) { "Backup light recommendation provenance is missing." }
                require(snapshot.plantCatalogIds.isNotEmpty() &&
                    snapshot.plantCatalogIds == snapshot.plantCatalogIds.distinct().sorted() &&
                    snapshot.substrateProductIds ==
                    snapshot.substrateProductIds.distinct().sorted()
                ) { "Backup light recommendation catalog inputs are invalid." }
                require(snapshot.hasPlants && snapshot.plantedFreshwater &&
                    snapshot.fixtureLengthMm > 0 && snapshot.tankHeightCm > 0 &&
                    snapshot.waterDepthCm in 5..snapshot.tankHeightCm
                ) {
                    "Backup light recommendation geometry is invalid."
                }
                require(snapshot.programEndMinute in 0 until MINUTES_PER_DAY &&
                    snapshot.programStartMinute in 0 until MINUTES_PER_DAY &&
                    snapshot.programEndMinute - snapshot.programStartMinute ==
                    snapshot.photoperiodMinutes &&
                    snapshot.photoperiodMinutes in 1..MINUTES_PER_DAY &&
                    snapshot.recommendationEpochDay > 0L &&
                    snapshot.reevaluationEpochDay >= snapshot.recommendationEpochDay
                ) { "Backup light recommendation schedule is invalid." }
                val priorDoseParts = listOf(
                    snapshot.priorAppliedPhotoperiodMinutes,
                    snapshot.priorAppliedMaximumChannelPercent,
                    snapshot.priorAppliedEpochDay
                ).count { value -> value != null }
                require(priorDoseParts in setOf(0, 3)) {
                    "Backup light recommendation prior dose is incomplete."
                }
                val applied = snapshot.outcome == TankLightRecommendationOutcome.APPLIED
                val authorityParts = listOf(
                    snapshot.firmwarePlanId,
                    snapshot.firmwarePlanRevision,
                    snapshot.firmwareStorageGeneration
                ).count { value -> value != null }
                require(applied == (snapshot.appliedAtMillis != null) &&
                    authorityParts in setOf(0, 3) &&
                    applied == (authorityParts == 3)
                ) { "Backup light recommendation outcome authority is invalid." }
                if (applied) {
                    require(snapshot.firmwarePlanId?.matches(FIRMWARE_PLAN_ID_PATTERN) == true &&
                        snapshot.firmwarePlanRevision?.let { revision -> revision > 0L } == true &&
                        snapshot.firmwareStorageGeneration?.let { generation ->
                            generation > 0L
                        } == true
                    ) { "Backup light recommendation firmware authority is invalid." }
                }
                snapshot.recommendationId
            }
            require(recommendationIds.distinct().size == recommendationIds.size) {
                "Backup contains duplicate light recommendation identities."
            }
            require(devices.add(assignment.deviceUid)) {
                "Backup assigns one device to more than one aquarium."
            }
        }
    }

    private fun validateMedia(
        aquariums: List<ArchiveAquarium>,
        mediaByEntryName: Map<String, File>
    ) {
        val referenced = aquariums.mapNotNull { aquarium ->
            aquarium.photo?.also { reference ->
                validateMediaReference(reference, aquarium.id, mediaByEntryName)
            }?.entryName
        }.toSet()
        require(referenced.size == aquariums.count { aquarium -> aquarium.photo != null }) {
            "Backup reuses a media entry for multiple aquariums."
        }
        require(mediaByEntryName.keys == referenced) {
            "Backup contains unreferenced or missing media entries."
        }
    }
}

private const val MINUTES_PER_DAY = 1_440
private const val MAX_LIGHT_RECOMMENDATIONS = 1_000
private const val MATERIAL_CATEGORY_CO2 = AquariumMaterialCategory.CO2
private val PROFILE_FINGERPRINT_PATTERN = Regex("^[0-9a-f]{16}$")
private val FIRMWARE_PLAN_ID_PATTERN = Regex("^lp-[0-9a-f]{8}$")

private fun validateEnvelope(manifest: UserDataBackupManifest) {
    require(manifest.format == USER_DATA_BACKUP_FORMAT) {
        "Unsupported backup format."
    }
    require(manifest.schemaVersion == USER_DATA_BACKUP_SCHEMA_VERSION) {
        "Unsupported backup schema version."
    }
    require(manifest.createdAtMillis > 0L) {
        "Backup creation time is invalid."
    }
    require(manifest.sourceAppVersion.isNotBlank()) {
        "Backup source application version is missing."
    }
    require(manifest.aquariums.size <= UserDataBackupLimits.MAX_AQUARIUMS) {
        "Backup contains too many aquariums."
    }
    require(manifest.careTasks.size <= UserDataBackupLimits.MAX_CARE_TASKS) {
        "Backup contains too many care tasks."
    }
    require(manifest.deviceAssignments.size <= UserDataBackupLimits.MAX_DEVICE_ASSIGNMENTS) {
        "Backup contains too many device assignments."
    }
}

private fun requireSafeArchiveEntryName(entryName: String) {
    require(entryName.isNotBlank())
    require(!entryName.startsWith('/'))
    require('\\' !in entryName)
    require(entryName.split('/').none { segment -> segment == ".." || segment.isBlank() })
}

private fun requireValidArchiveMediaEntryName(entryName: String) {
    requireSafeArchiveEntryName(entryName)
    require(UserDataBackupLimits.mediaEntryPattern.matches(entryName)) {
        "Backup media entry name is invalid."
    }
}

private fun validateMediaReference(
    reference: ArchiveMediaReference,
    tankId: Long,
    mediaByEntryName: Map<String, File>
) {
    requireValidArchiveMediaEntryName(reference.entryName)
    require(reference.entryName == "${UserDataBackupLimits.MEDIA_PREFIX}$tankId.jpg") {
        "Backup aquarium photo entry does not match its aquarium."
    }
    require(reference.byteSize in 1..UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES) {
        "Backup aquarium photo size is invalid."
    }
    require(UserDataBackupLimits.sha256Pattern.matches(reference.sha256)) {
        "Backup aquarium photo digest is invalid."
    }
    val file = requireNotNull(mediaByEntryName[reference.entryName]) {
        "Backup aquarium photo is missing."
    }
    require(file.isFile && file.length() == reference.byteSize.toLong()) {
        "Backup aquarium photo size does not match its manifest."
    }
    require(sha256(file).equals(reference.sha256, ignoreCase = true)) {
        "Backup aquarium photo integrity check failed."
    }
}

private fun validateArchiveItemIds(ids: List<Long>) {
    require(ids.size <= UserDataBackupLimits.MAX_ITEMS_PER_AQUARIUM) {
        "Backup aquarium contains too many inventory items."
    }
    require(ids.all { id -> id > 0L }) {
        "Backup aquarium contains an invalid inventory id."
    }
    require(ids.distinct().size == ids.size) {
        "Backup aquarium contains duplicate inventory ids."
    }
}

private inline fun <reified T : Enum<T>> requireArchiveEnumValue(value: String, field: String) {
    runCatching { enumValueOf<T>(value) }.getOrElse { error ->
        throw IllegalArgumentException("Backup care task $field is invalid.", error)
    }
}

internal fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .toHexString()
}

internal fun sha256(file: File): String {
    require(file.isFile) { "SHA-256 source file is unavailable." }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(UserDataBackupLimits.BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte ->
        (byte.toInt() and UNSIGNED_BYTE_MASK)
            .toString(HEX_RADIX)
            .padStart(2, '0')
    }
}

private const val UNSIGNED_BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
