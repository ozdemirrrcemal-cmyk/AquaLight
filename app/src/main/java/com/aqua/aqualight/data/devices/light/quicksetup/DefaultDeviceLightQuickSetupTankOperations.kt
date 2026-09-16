package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.smartcare.SmartCareTankClassifier
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CancellationException

internal class DefaultDeviceLightQuickSetupTankOperations(
    private val ownerUid: String,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val tankStore: AquariumTankDataStoreManager,
    private val devicesRepository: DevicesRepository
) : DeviceLightQuickSetupTankOperations {

    override suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
        return if (uid == null) {
            failure(DeviceLightQuickSetupTankFailure.INVALID_DEVICE)
        } else {
            try {
                readKnownDevice(uid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failure(DeviceLightQuickSetupTankFailure.UNAVAILABLE)
            }
        }
    }

    private suspend fun readKnownDevice(uid: DeviceUid): DeviceLightQuickSetupTankReadResult {
        val device = devicesRepository.currentDevice(uid)
        return if (device == null) {
            failure(DeviceLightQuickSetupTankFailure.DEVICE_NOT_FOUND)
        } else {
            readAssignedTank(uid, device)
        }
    }

    private suspend fun readAssignedTank(
        uid: DeviceUid,
        device: DeviceSnapshot
    ): DeviceLightQuickSetupTankReadResult {
        val assignment = assignmentRepository.assignmentForDevice(uid)
        return if (assignment == null) {
            failure(DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED)
        } else {
            val tank = tankStore.tanksSnapshotForOwner(ownerUid)
                .singleOrNull { it.id == assignment.tankId }
            if (tank == null) {
                failure(DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND)
            } else {
                val characteristics = SmartCareTankClassifier.classify(tank)
                DeviceLightQuickSetupTankReadResult.Available(
                    DeviceLightQuickSetupTank(
                        tankId = tank.id,
                        tankName = tank.name,
                        setupDateEpochDay = tank.setupDateEpochDay,
                        widthCm = tank.widthCm,
                        lengthCm = tank.lengthCm,
                        heightCm = tank.heightCm,
                        plantCount = tank.plants.size,
                        inferredPlantDemand = inferPlantDemand(tank.plants),
                        inferredPlantDensity = inferPlantDensity(tank.plants.size),
                        inferredCo2Installed = characteristics.hasCo2,
                        inferredActiveSoil = characteristics.hasActiveSoil,
                        plantedFreshwater = characteristics.isFreshwater &&
                            characteristics.hasPlants,
                        productKey = device.product.productKey,
                        productDisplayName = device.product.displayName.ifBlank { device.title }
                    )
                )
            }
        }
    }
}

private fun inferPlantDemand(plants: List<SavedAquariumPlant>): DeviceLightPlantDemand {
    val signals = plants.map { plant -> "${plant.category} ${plant.plantName}".normalized() }
    return when {
        plants.isEmpty() -> DeviceLightPlantDemand.MEDIUM
        signals.any { signal -> HIGH_DEMAND_SIGNALS.any(signal::contains) } ->
            DeviceLightPlantDemand.HIGH
        signals.all { signal -> LOW_DEMAND_SIGNALS.any(signal::contains) } ->
            DeviceLightPlantDemand.LOW
        else -> DeviceLightPlantDemand.MEDIUM
    }
}

private fun inferPlantDensity(plantCount: Int): DeviceLightPlantDensity = when {
    plantCount <= SPARSE_PLANT_COUNT_MAX -> DeviceLightPlantDensity.SPARSE
    plantCount >= DENSE_PLANT_COUNT_MIN -> DeviceLightPlantDensity.DENSE
    else -> DeviceLightPlantDensity.MEDIUM
}

private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)

private fun failure(failure: DeviceLightQuickSetupTankFailure) =
    DeviceLightQuickSetupTankReadResult.Failed(failure)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private const val SPARSE_PLANT_COUNT_MAX = 2
private const val DENSE_PLANT_COUNT_MIN = 8
private val HIGH_DEMAND_SIGNALS = listOf(
    "ground cover", "foreground", "rare", "rotala macrandra", "hemianthus",
    "glossostigma", "utricularia", "eriocaulon", "tonina", "pantanal"
)
private val LOW_DEMAND_SIGNALS = listOf(
    "epiphyte", "moss", "floating", "anubias", "microsorum", "bucephalandra",
    "bolbitis", "cryptocoryne"
)
