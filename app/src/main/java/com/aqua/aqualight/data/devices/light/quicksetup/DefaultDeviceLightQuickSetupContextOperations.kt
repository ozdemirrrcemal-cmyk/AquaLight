package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumMaterialCategoryKeys
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContext
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlant
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

internal class DefaultDeviceLightQuickSetupContextOperations(
    private val ownerUid: String,
    private val tankStore: AquariumTankDataStoreManager,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val devicesRepository: DevicesRepository
) : DeviceLightQuickSetupContextOperations {

    @Suppress("ReturnCount")
    override suspend fun readContext(deviceUid: String): DeviceLightQuickSetupContextResult {
        val normalizedUid = deviceUid.trim()
        if (normalizedUid.isBlank()) return blocked(DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID)
        val uid = DeviceUid(normalizedUid)
        val device = devicesRepository.currentDevice(uid)
            ?: return blocked(DeviceLightQuickSetupBlockReason.DEVICE_NOT_REGISTERED)
        if (!device.hasValidatedRuntimeMetadata) {
            return blocked(DeviceLightQuickSetupBlockReason.DEVICE_METADATA_NOT_READY)
        }
        val root = device.toDeviceRootSnapshot()
        if (root.catalogState != DeviceRootCatalogState.VALID || root.family != OwnerDeviceFamily.LIGHT) {
            return blocked(DeviceLightQuickSetupBlockReason.UNSUPPORTED_PRODUCT)
        }
        if (QUICK_SETUP_FEATURE !in root.supportedFeatures) {
            return blocked(DeviceLightQuickSetupBlockReason.UNSUPPORTED_PRODUCT)
        }
        return readAssignedContext(normalizedUid, uid, root)
    }

    private suspend fun readAssignedContext(
        normalizedUid: String,
        uid: DeviceUid,
        root: DeviceRootSnapshot
    ): DeviceLightQuickSetupContextResult {
        val assignment = assignmentRepository.assignmentForDevice(uid)
            ?: return blocked(DeviceLightQuickSetupBlockReason.DEVICE_NOT_ASSIGNED)
        val tank = tankStore.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { candidate -> candidate.id == assignment.tankId }
            ?: return blocked(DeviceLightQuickSetupBlockReason.AQUARIUM_NOT_FOUND)

        val assignedLights = assignmentRepository.assignedDevicesForTank(tank.id)
            .first()
            .count { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
        if (assignedLights != 1) {
            return blocked(DeviceLightQuickSetupBlockReason.MULTIPLE_LIGHT_FIXTURES_UNSUPPORTED)
        }

        val setupEpochDay = tank.setupDateEpochDay
            ?: return blocked(DeviceLightQuickSetupBlockReason.MISSING_SETUP_DATE)
        if (tank.plants.isEmpty()) return blocked(DeviceLightQuickSetupBlockReason.NO_PLANTS)
        if (tank.plants.any { plant -> plant.catalogId.isBlank() }) {
            return blocked(DeviceLightQuickSetupBlockReason.MISSING_PLANT_CATALOG_ID)
        }
        if (tank.plants.any { plant -> AquariumPlantLightCatalog.record(plant.catalogId) == null }) {
            return blocked(DeviceLightQuickSetupBlockReason.UNKNOWN_PLANT_CATALOG_ID)
        }

        return DeviceLightQuickSetupContextResult.Available(
            DeviceLightQuickSetupContext(
                deviceUid = normalizedUid,
                productKey = root.productKey,
                productDisplayName = root.productDisplayName.ifBlank { root.title },
                channelKeys = root.channelSlots.lightChannels.map { slot -> slot.wireKey.value },
                tankId = tank.id,
                aquariumName = tank.name,
                setupDateEpochDay = setupEpochDay,
                tankWidthCm = tank.widthCm,
                tankLengthCm = tank.lengthCm,
                tankHeightCm = tank.heightCm,
                tankType = tank.tankType,
                tankStyle = tank.tankStyle,
                plants = tank.plants.map { plant ->
                    DeviceLightQuickSetupPlant(plant.catalogId, plant.plantName)
                },
                substrateSemantic = resolveSubstrateSemantic(tank),
                co2Present = tank.materials.any { material ->
                    material.categoryKey == AquariumMaterialCategoryKeys.CO2
                },
                profileFingerprint = fingerprint(tank, root.productKey, normalizedUid)
            )
        )
    }

    private fun resolveSubstrateSemantic(tank: SavedAquariumTank): AquariumSubstrateSemantic =
        tank.materials
            .asSequence()
            .filter { material ->
                material.categoryKey == AquariumMaterialCategoryKeys.SUBSTRATE ||
                    material.categoryKey == AquariumMaterialCategoryKeys.GRAVEL
            }
            .map { material ->
                AquariumSubstrateMetadataCatalog.resolveSemantic(
                    material.productId,
                    material.categoryKey
                )
            }
            .maxByOrNull(::substrateRank)
            ?: AquariumSubstrateSemantic.UNKNOWN

    private fun fingerprint(
        tank: SavedAquariumTank,
        productKey: String,
        deviceUid: String
    ): String {
        val canonical = buildString {
            append(deviceUid).append('|').append(productKey).append('|')
            append(tank.id).append('|').append(tank.setupDateEpochDay).append('|')
            append(tank.widthCm).append('x').append(tank.lengthCm).append('x').append(tank.heightCm)
            append('|').append(tank.tankType).append('|').append(tank.tankStyle)
            tank.plants.map { plant -> plant.catalogId }.sorted().forEach { append("|p:").append(it) }
            tank.materials
                .filter { material ->
                    material.categoryKey == AquariumMaterialCategoryKeys.CO2 ||
                        material.categoryKey == AquariumMaterialCategoryKeys.SUBSTRATE ||
                        material.categoryKey == AquariumMaterialCategoryKeys.GRAVEL
                }
                .map { material -> material.categoryKey + ":" + material.productId }
                .sorted()
                .forEach { append("|m:").append(it) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    @Suppress("MagicNumber")
    private fun substrateRank(semantic: AquariumSubstrateSemantic): Int = when (semantic) {
        AquariumSubstrateSemantic.ACTIVE_SOIL -> 5
        AquariumSubstrateSemantic.NUTRIENT_BASE -> 4
        AquariumSubstrateSemantic.INERT -> 3
        AquariumSubstrateSemantic.ADDITIVE -> 2
        AquariumSubstrateSemantic.UNKNOWN -> 1
        AquariumSubstrateSemantic.NOT_APPLICABLE -> 0
    }

    private fun blocked(
        reason: DeviceLightQuickSetupBlockReason
    ) = DeviceLightQuickSetupContextResult.Blocked(reason)

    private companion object {
        const val QUICK_SETUP_FEATURE = "LIGHT_QUICK_SETUP"
    }
}
