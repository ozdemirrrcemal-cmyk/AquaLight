@file:Suppress("LongParameterList")

package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightCalibrationCatalog
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPersistenceResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecordedOutcome
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankLightRecommendationOutcome
import com.aqua.aqualight.data.aquarium.devices.TankLightRecommendationSnapshot
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.smartcare.SmartCareTankClassifier
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DefaultDeviceLightQuickSetupTankOperations(
    private val ownerUid: String,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val tankStore: AquariumTankDataStoreManager,
    private val devicesRepository: DevicesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : DeviceLightQuickSetupTankOperations {

    override suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
            ?: return failure(DeviceLightQuickSetupTankFailure.INVALID_DEVICE)
        return safely { readKnownDevice(uid) }
    }

    override suspend fun prepareRecommendation(
        deviceUid: String,
        input: DeviceLightQuickSetupInput,
        plan: DeviceLightQuickSetupPlan
    ): DeviceLightQuickSetupPersistenceResult {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
            ?: return DeviceLightQuickSetupPersistenceResult.Failed()
        var preparedAuditId: String? = null
        return try {
            val context = when (val result = readKnownDevice(uid)) {
                is DeviceLightQuickSetupTankReadResult.Available -> result.tank
                is DeviceLightQuickSetupTankReadResult.Failed ->
                    return DeviceLightQuickSetupPersistenceResult.Failed()
            }
            val assignment = requireNotNull(assignmentRepository.assignmentForDevice(uid))
            val tanks = tankStore.tanksSnapshotForOwner(ownerUid)
            val tank = requireNotNull(tanks.singleOrNull { item -> item.id == assignment.tankId })
            require(assignment.tankId == context.tankId)
            require(
                assignment.lightInstallation.fixtureHeightAboveWaterCm ==
                    context.fixtureHeightAboveWaterCm &&
                    assignment.lightInstallation.updatedAtMillis ==
                    context.installationUpdatedAtMillis
            ) { "Light installation changed before recommendation persistence." }
            require(tank.matchesAutomationContext(context)) {
                "Tank automation inputs changed before recommendation persistence."
            }
            val now = nowMillis()
            val today = context.deviceLocalEpochDay
            require(input.substrateSemantic == context.substrateSemantic) {
                "Substrate semantics changed after recommendation calculation."
            }
            require(DeviceLightQuickSetupCalculator.calculate(context, input, today) == plan) {
                "Recommendation inputs or verified device identity changed before persistence."
            }

            val storedCo2 = if (context.co2ComponentPresent) {
                input.co2Readiness
            } else {
                AquariumCo2Readiness.NOT_INSTALLED
            }
            val plantDemandSelection = AquariumPlantLightCatalog.resolveSelection(
                catalogIds = tank.plants.map { plant -> plant.catalogId },
                unreviewedDemand = AquariumPlantLightDemand.UNKNOWN
            )
            val storedPlantDemandOverride = if (plantDemandSelection.requiresUserInput) {
                input.plantDemand.toAquariumDemand()
            } else {
                AquariumPlantLightDemand.UNKNOWN
            }
            assignmentRepository.mutateLightAutomation(uid) { current ->
                check(current.tankId == tank.id) {
                    "Tank assignment changed during recommendation preparation."
                }
                check(
                    current.lightInstallation == assignment.lightInstallation &&
                        current.lightRecommendations == assignment.lightRecommendations
                ) { "Lighting authority changed during recommendation preparation." }
                val fixtureIdentityChanged = current.lightRecommendations
                    .lastAppliedRecommendation()
                    ?.matchesFixtureIdentity(
                        productKey = context.productKey,
                        hardwareRevision = context.hardwareRevision,
                        fixtureLengthMm = context.fixtureLengthMm
                    ) == false
                val previousHeight = current.lightInstallation.fixtureHeightAboveWaterCm
                val previousWaterDepth = context.waterDepthCm ?: context.lastAppliedWaterDepthCm
                val waterDepthChanged = previousWaterDepth?.let { previousDepth ->
                    previousDepth != input.waterDepthCm
                } == true
                val installation = current.lightInstallation.copy(
                    fixtureHeightAboveWaterCm = input.fixtureHeightAboveWaterCm,
                    installedAtEpochDay = current.lightInstallation.installedAtEpochDay
                        ?: today,
                    lastFixtureChangeEpochDay = if (fixtureIdentityChanged || waterDepthChanged ||
                        (previousHeight != null &&
                            previousHeight != input.fixtureHeightAboveWaterCm)
                    ) {
                        today
                    } else {
                        current.lightInstallation.lastFixtureChangeEpochDay ?: today
                    },
                    updatedAtMillis = now
                )
                val auditId = nextAuditId(
                    plan = plan,
                    existing = current.lightRecommendations,
                    createdAtMillis = now
                )
                preparedAuditId = auditId
                val snapshot = recommendationSnapshot(
                    tank = context,
                    input = input,
                    plan = plan,
                    auditId = auditId,
                    createdAtMillis = now
                )
                current.copy(
                    lightInstallation = installation,
                    lightRecommendations = current.lightRecommendations + snapshot
                )
            }
            tankStore.updateTankAutomationProfileIfUnchanged(
                tankId = tank.id,
                expectedTank = tank,
                profile = tank.automationProfile.copy(
                    waterDepthCm = input.waterDepthCm,
                    plantDemandOverride = storedPlantDemandOverride,
                    plantCoverage = input.plantCoverage,
                    co2Readiness = storedCo2,
                    daylightExposure = input.daylightExposure,
                    daylightStartMinute = input.daylightStartMinute,
                    daylightEndMinute = input.daylightEndMinute,
                    preferredLightEndMinute = input.programEndMinute,
                    latestSurfaceGrowth = input.surfaceGrowth,
                    latestObservationEpochDay = today,
                    shelterAvailability = input.shelterAvailability,
                    lastMajorPlantingEpochDay = if (
                        context.plantCoverage == AquariumPlantCoverage.UNKNOWN
                    ) {
                        today
                    } else {
                        tank.automationProfile.lastMajorPlantingEpochDay
                    },
                    updatedAtMillis = now
                )
            )
            val completedAuditId = requireNotNull(preparedAuditId)
            preparedAuditId = null
            DeviceLightQuickSetupPersistenceResult.Prepared(completedAuditId)
        } catch (error: CancellationException) {
            preparedAuditId?.let { auditId ->
                withContext(NonCancellable) {
                    markPreparedRecommendationFailed(uid, auditId)
                }
            }
            throw error
        } catch (error: Throwable) {
            preparedAuditId?.let { auditId ->
                markPreparedRecommendationFailed(uid, auditId)
            }
            DeviceLightQuickSetupPersistenceResult.Failed(error)
        }
    }

    override suspend fun recordRecommendationOutcome(
        deviceUid: String,
        auditId: String,
        outcome: DeviceLightQuickSetupRecordedOutcome
    ): DeviceLightQuickSetupPersistenceResult {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
            ?: return DeviceLightQuickSetupPersistenceResult.Failed()
        return try {
            val appliedPlan = (outcome as? DeviceLightQuickSetupRecordedOutcome.Applied)?.plan
            val targetOutcome = when (outcome) {
                is DeviceLightQuickSetupRecordedOutcome.Applied ->
                    TankLightRecommendationOutcome.APPLIED
                DeviceLightQuickSetupRecordedOutcome.Failed ->
                    TankLightRecommendationOutcome.FAILED
                DeviceLightQuickSetupRecordedOutcome.Indeterminate ->
                    TankLightRecommendationOutcome.INDETERMINATE
            }
            var found = false
            assignmentRepository.mutateLightAutomation(uid) { current ->
                val updated = current.lightRecommendations.map { snapshot ->
                    if (snapshot.recommendationId == auditId) {
                        found = true
                        check(
                            snapshot.outcome == TankLightRecommendationOutcome.PREPARED ||
                                snapshot.outcome == targetOutcome
                        ) { "A terminal recommendation outcome cannot be rewritten." }
                        appliedPlan?.requireMatches(snapshot)
                        if (snapshot.outcome == targetOutcome &&
                            ((appliedPlan != null) == (snapshot.appliedAtMillis != null))
                        ) {
                            appliedPlan?.let { verified ->
                                check(
                                    snapshot.firmwarePlanId ==
                                        verified.authority.installedPlanId
                                )
                                check(
                                    snapshot.firmwarePlanRevision ==
                                        verified.authority.revision
                                )
                                check(
                                    snapshot.firmwareStorageGeneration ==
                                        verified.authority.storageGeneration
                                )
                            }
                            snapshot
                        } else {
                            snapshot.copy(
                                outcome = targetOutcome,
                                appliedAtMillis = appliedPlan?.let {
                                    maxOf(nowMillis(), snapshot.createdAtMillis)
                                },
                                firmwarePlanId = appliedPlan?.authority?.installedPlanId,
                                firmwarePlanRevision = appliedPlan?.authority?.revision,
                                firmwareStorageGeneration =
                                    appliedPlan?.authority?.storageGeneration
                            )
                        }
                    } else {
                        snapshot
                    }
                }
                check(found) { "Prepared lighting recommendation was not found." }
                current.copy(lightRecommendations = updated)
            }
            DeviceLightQuickSetupPersistenceResult.Saved
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DeviceLightQuickSetupPersistenceResult.Failed(error)
        }
    }

    private suspend fun readKnownDevice(
        uid: DeviceUid
    ): DeviceLightQuickSetupTankReadResult {
        val device = devicesRepository.currentDevice(uid)
            ?: return failure(DeviceLightQuickSetupTankFailure.DEVICE_NOT_FOUND)
        if (!device.hasVerifiedCommercialIdentity()) {
            return failure(DeviceLightQuickSetupTankFailure.DEVICE_IDENTITY_UNVERIFIED)
        }
        val lightStatus = readVerifiedLightStatus(uid, device)
            ?: return failure(DeviceLightQuickSetupTankFailure.DEVICE_RUNTIME_UNVERIFIED)
        if (lightStatus.deviceLocalEpochDayOrNull() == null) {
            return failure(DeviceLightQuickSetupTankFailure.DEVICE_TIME_UNVERIFIED)
        }
        val assignment = assignmentRepository.assignmentForDevice(uid)
            ?: return failure(DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED)
        val tank = tankStore.tanksSnapshotForOwner(ownerUid)
            .singleOrNull { item -> item.id == assignment.tankId }
            ?: return failure(DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND)
        return DeviceLightQuickSetupTankReadResult.Available(
            mapDeviceLightQuickSetupTank(
                tank = tank,
                assignment = assignment,
                device = device,
                lightStatus = lightStatus
            )
        )
    }

    private suspend fun readVerifiedLightStatus(
        uid: DeviceUid,
        device: DeviceSnapshot
    ): DeviceLightStatus? {
        val runtime = devicesRepository.runtimeModules()?.light ?: return null
        val status = when (val outcome = runtime.requestStatus(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.value
            else -> null
        }
        return status?.takeIf { value ->
            value.product.wireValue == device.product.productKey &&
                value.electricalDesign.available &&
                value.electricalDesign.fixtureLengthMm?.let { it > 0 } == true
        }
    }

    private suspend fun safely(
        block: suspend () -> DeviceLightQuickSetupTankReadResult
    ): DeviceLightQuickSetupTankReadResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        failure(DeviceLightQuickSetupTankFailure.UNAVAILABLE)
    }

    private suspend fun markPreparedRecommendationFailed(
        uid: DeviceUid,
        auditId: String
    ) {
        repeat(PREPARED_CLEANUP_ATTEMPTS) {
            val saved = runCatching {
                assignmentRepository.mutateLightAutomation(uid) { current ->
                    current.copy(
                        lightRecommendations = current.lightRecommendations.map { snapshot ->
                            if (snapshot.recommendationId == auditId &&
                                snapshot.outcome == TankLightRecommendationOutcome.PREPARED
                            ) {
                                snapshot.copy(outcome = TankLightRecommendationOutcome.FAILED)
                            } else {
                                snapshot
                            }
                        }
                    )
                }
            }.isSuccess
            if (saved) return
        }
    }
}

internal fun mapDeviceLightQuickSetupTank(
    tank: SavedAquariumTank,
    assignment: TankDeviceAssignment,
    device: DeviceSnapshot,
    lightStatus: DeviceLightStatus
): DeviceLightQuickSetupTank = mapDeviceLightQuickSetupTank(
    tank = tank,
    assignment = assignment,
    device = device,
    fixtureLengthMm = requireNotNull(lightStatus.electricalDesign.fixtureLengthMm),
    deviceLocalEpochDay = requireNotNull(lightStatus.deviceLocalEpochDayOrNull()),
    installedPlanId = lightStatus.auto.planId.takeIf { lightStatus.auto.planInstalled },
    installedPlanRevision = lightStatus.auto.planRevision
)

internal fun mapDeviceLightQuickSetupTank(
    tank: SavedAquariumTank,
    assignment: TankDeviceAssignment,
    device: DeviceSnapshot,
    fixtureLengthMm: Int,
    deviceLocalEpochDay: Long,
    installedPlanId: String?,
    installedPlanRevision: Long
): DeviceLightQuickSetupTank {
    require(fixtureLengthMm > 0)
    require(deviceLocalEpochDay in MIN_DEVICE_EPOCH_DAY..MAX_DEVICE_EPOCH_DAY)
    val characteristics = SmartCareTankClassifier.classify(tank)
    val productKey = device.product.productKey
    val hardwareRevision = device.product.hardwareRevision
    val profile = tank.automationProfile
    val plantDemandSelection = AquariumPlantLightCatalog.resolveSelection(
        catalogIds = tank.plants.map { plant -> plant.catalogId },
        unreviewedDemand = profile.plantDemandOverride
    )
    val co2Readiness = if (characteristics.hasCo2) {
        profile.co2Readiness.takeUnless { it == AquariumCo2Readiness.NOT_INSTALLED }
            ?: AquariumCo2Readiness.UNKNOWN
    } else {
        AquariumCo2Readiness.NOT_INSTALLED
    }
    val shelter = if (characteristics.hasShrimp) {
        profile.shelterAvailability.takeUnless {
            it == AquariumShelterAvailability.NOT_REQUIRED
        } ?: AquariumShelterAvailability.UNKNOWN
    } else {
        AquariumShelterAvailability.NOT_REQUIRED
    }
    val latestAppliedRecommendation = assignment.lightRecommendations.lastAppliedRecommendation()
    val fixtureIdentityChanged = latestAppliedRecommendation?.matchesFixtureIdentity(
        productKey = productKey,
        hardwareRevision = hardwareRevision,
        fixtureLengthMm = fixtureLengthMm
    ) == false
    val lastApplied = assignment.lightRecommendations
        .asSequence()
        .filter { recommendation ->
            recommendation.outcome == TankLightRecommendationOutcome.APPLIED &&
                recommendation.appliedAtMillis != null &&
                recommendation.productKey == productKey &&
                recommendation.hardwareRevision == hardwareRevision &&
                recommendation.fixtureLengthMm == fixtureLengthMm &&
                recommendation.firmwarePlanId == installedPlanId &&
                recommendation.firmwarePlanRevision == installedPlanRevision
        }
        .maxByOrNull { recommendation -> requireNotNull(recommendation.appliedAtMillis) }
    return DeviceLightQuickSetupTank(
        tankId = tank.id,
        tankName = tank.name,
        deviceLocalEpochDay = deviceLocalEpochDay,
        setupDateEpochDay = tank.setupDateEpochDay,
        tankHeightCm = tank.heightCm,
        hasPlants = characteristics.hasPlants,
        plantDemand = plantDemandSelection.effectiveDemand.toDeviceDemand(),
        reviewedPlantDemandFloor =
            plantDemandSelection.reviewedDemandFloor.toDeviceDemand(),
        plantCatalogIds = tank.plants.mapTo(sortedSetOf()) { plant -> plant.catalogId },
        plantEvidenceSourceIds = AquariumPlantLightCatalog.evidenceSourceIds(
            tank.plants.map { plant -> plant.catalogId }
        ),
        plantCoverage = profile.plantCoverage,
        co2ComponentPresent = characteristics.hasCo2,
        co2Readiness = co2Readiness,
        substrateSemantic = tank.materials.substrateSemantic(),
        substrateProductIds = tank.materials
            .filter { material ->
                material.substrateSemantic != AquariumSubstrateSemantic.NOT_APPLICABLE
            }
            .mapTo(sortedSetOf()) { material -> material.productId },
        substrateEvidenceSourceIds = AquariumSubstrateSemantics.evidenceSourceIds(
            tank.materials.map { material -> material.productId }
        ),
        daylightExposure = profile.daylightExposure,
        daylightStartMinute = profile.daylightStartMinute,
        daylightEndMinute = profile.daylightEndMinute,
        preferredLightEndMinute = profile.preferredLightEndMinute,
        surfaceGrowth = profile.latestSurfaceGrowth,
        latestObservationEpochDay = profile.latestObservationEpochDay,
        hasShrimp = characteristics.hasShrimp,
        shelterAvailability = shelter,
        waterDepthCm = profile.waterDepthCm,
        fixtureHeightAboveWaterCm = assignment.lightInstallation.fixtureHeightAboveWaterCm,
        profileUpdatedAtMillis = profile.updatedAtMillis,
        installationUpdatedAtMillis = assignment.lightInstallation.updatedAtMillis,
        plantedFreshwater = characteristics.isFreshwater && characteristics.hasPlants,
        productKey = productKey,
        productDisplayName = device.product.displayName.ifBlank { device.title },
        hardwareRevision = hardwareRevision,
        fixtureLengthMm = fixtureLengthMm,
        calibrationProfile = DeviceLightCalibrationCatalog.find(
            productKey = productKey,
            hardwareRevision = hardwareRevision,
            fixtureLengthMm = fixtureLengthMm
        ),
        lastLightingResetEpochDay = listOfNotNull(
            tank.setupDateEpochDay,
            profile.lastMajorPlantingEpochDay,
            assignment.lightInstallation.installedAtEpochDay,
            assignment.lightInstallation.lastFixtureChangeEpochDay,
            deviceLocalEpochDay.takeIf { fixtureIdentityChanged }
        ).maxOrNull(),
        lastAppliedPhotoperiodMinutes = lastApplied?.photoperiodMinutes,
        lastAppliedMaximumChannelPercent = lastApplied?.maximumChannelPercent,
        lastAppliedEpochDay = lastApplied?.recommendationEpochDay,
        nextReevaluationEpochDay = lastApplied?.reevaluationEpochDay,
        lastAppliedWaterDepthCm = lastApplied?.waterDepthCm
    )
}

private fun List<TankLightRecommendationSnapshot>.lastAppliedRecommendation(): TankLightRecommendationSnapshot? =
    asSequence()
        .filter { recommendation ->
            recommendation.outcome == TankLightRecommendationOutcome.APPLIED &&
                recommendation.appliedAtMillis != null
        }
        .maxByOrNull { recommendation -> requireNotNull(recommendation.appliedAtMillis) }

private fun TankLightRecommendationSnapshot.matchesFixtureIdentity(
    productKey: String,
    hardwareRevision: String,
    fixtureLengthMm: Int
): Boolean =
    this.productKey == productKey &&
        this.hardwareRevision == hardwareRevision &&
        this.fixtureLengthMm == fixtureLengthMm

private fun DeviceSnapshot.hasVerifiedCommercialIdentity(): Boolean =
    hasValidatedRuntimeMetadata &&
        product.productKey.isNotBlank() &&
        product.hardwareRevision.isNotBlank()

private fun DeviceLightPlantDemand.toAquariumDemand(): AquariumPlantLightDemand = when (this) {
    DeviceLightPlantDemand.UNKNOWN -> AquariumPlantLightDemand.UNKNOWN
    DeviceLightPlantDemand.LOW -> AquariumPlantLightDemand.LOW
    DeviceLightPlantDemand.MEDIUM -> AquariumPlantLightDemand.MEDIUM
    DeviceLightPlantDemand.HIGH -> AquariumPlantLightDemand.HIGH
}

private fun AquariumPlantLightDemand.toDeviceDemand(): DeviceLightPlantDemand = when (this) {
    AquariumPlantLightDemand.UNKNOWN -> DeviceLightPlantDemand.UNKNOWN
    AquariumPlantLightDemand.LOW -> DeviceLightPlantDemand.LOW
    AquariumPlantLightDemand.MEDIUM -> DeviceLightPlantDemand.MEDIUM
    AquariumPlantLightDemand.HIGH -> DeviceLightPlantDemand.HIGH
}

private fun List<SavedAquariumMaterial>.substrateSemantic(): AquariumSubstrateSemantic =
    AquariumSubstrateSemantics.aggregate(
        asSequence()
            .filter { material ->
                material.substrateSemantic != AquariumSubstrateSemantic.NOT_APPLICABLE
            }
            .map(SavedAquariumMaterial::productId)
            .asIterable()
    )

private fun SavedAquariumTank.matchesAutomationContext(
    context: DeviceLightQuickSetupTank
): Boolean {
    val characteristics = SmartCareTankClassifier.classify(this)
    val profile = automationProfile
    val plantDemandSelection = AquariumPlantLightCatalog.resolveSelection(
        catalogIds = plants.map { plant -> plant.catalogId },
        unreviewedDemand = profile.plantDemandOverride
    )
    val normalizedCo2Readiness = if (characteristics.hasCo2) {
        profile.co2Readiness.takeUnless { it == AquariumCo2Readiness.NOT_INSTALLED }
            ?: AquariumCo2Readiness.UNKNOWN
    } else {
        AquariumCo2Readiness.NOT_INSTALLED
    }
    val normalizedShelter = if (characteristics.hasShrimp) {
        profile.shelterAvailability.takeUnless {
            it == AquariumShelterAvailability.NOT_REQUIRED
        } ?: AquariumShelterAvailability.UNKNOWN
    } else {
        AquariumShelterAvailability.NOT_REQUIRED
    }
    val substrateIds = materials
        .filter { material ->
            material.substrateSemantic != AquariumSubstrateSemantic.NOT_APPLICABLE
        }
        .mapTo(sortedSetOf()) { material -> material.productId }
    return id == context.tankId &&
        setupDateEpochDay == context.setupDateEpochDay &&
        heightCm == context.tankHeightCm &&
        characteristics.hasPlants == context.hasPlants &&
        plantDemandSelection.effectiveDemand.toDeviceDemand() == context.plantDemand &&
        plantDemandSelection.reviewedDemandFloor.toDeviceDemand() ==
        context.reviewedPlantDemandFloor &&
        plants.mapTo(sortedSetOf()) { plant -> plant.catalogId } == context.plantCatalogIds &&
        AquariumPlantLightCatalog.evidenceSourceIds(plants.map { plant -> plant.catalogId }) ==
        context.plantEvidenceSourceIds &&
        profile.plantCoverage == context.plantCoverage &&
        characteristics.hasCo2 == context.co2ComponentPresent &&
        normalizedCo2Readiness == context.co2Readiness &&
        materials.substrateSemantic() == context.substrateSemantic &&
        substrateIds == context.substrateProductIds &&
        AquariumSubstrateSemantics.evidenceSourceIds(materials.map { material -> material.productId }) ==
        context.substrateEvidenceSourceIds &&
        profile.daylightExposure == context.daylightExposure &&
        profile.daylightStartMinute == context.daylightStartMinute &&
        profile.daylightEndMinute == context.daylightEndMinute &&
        profile.preferredLightEndMinute == context.preferredLightEndMinute &&
        profile.latestSurfaceGrowth == context.surfaceGrowth &&
        profile.latestObservationEpochDay == context.latestObservationEpochDay &&
        characteristics.hasShrimp == context.hasShrimp &&
        normalizedShelter == context.shelterAvailability &&
        profile.waterDepthCm == context.waterDepthCm &&
        profile.updatedAtMillis == context.profileUpdatedAtMillis &&
        (characteristics.isFreshwater && characteristics.hasPlants) == context.plantedFreshwater
}

private fun recommendationSnapshot(
    tank: DeviceLightQuickSetupTank,
    input: DeviceLightQuickSetupInput,
    plan: DeviceLightQuickSetupPlan,
    auditId: String,
    createdAtMillis: Long
): TankLightRecommendationSnapshot {
    val draft = plan.currentPhase.draft
    val channels = draft.scene.channels
    return TankLightRecommendationSnapshot(
        recommendationId = auditId,
        profileFingerprint = plan.profileFingerprint,
        policyVersion = plan.policyVersion,
        evidenceSourceIds = plan.evidenceSourceIds.sorted(),
        reasonCodes = plan.reasons.map { reason -> reason.name }.sorted(),
        warningCodes = plan.warnings.map { warning -> warning.name }.sorted(),
        plantCatalogIds = tank.plantCatalogIds.sorted(),
        substrateProductIds = tank.substrateProductIds.sorted(),
        productKey = tank.productKey,
        hardwareRevision = tank.hardwareRevision,
        fixtureLengthMm = tank.fixtureLengthMm,
        calibrationProfileId = plan.calibrationProfileId,
        calibrationRevision = plan.calibrationRevision,
        confidence = plan.confidence.name,
        tankSetupEpochDay = tank.setupDateEpochDay,
        tankHeightCm = tank.tankHeightCm,
        hasPlants = tank.hasPlants,
        plantedFreshwater = tank.plantedFreshwater,
        co2ComponentPresent = tank.co2ComponentPresent,
        hasShrimp = tank.hasShrimp,
        waterDepthCm = input.waterDepthCm,
        fixtureHeightAboveWaterCm = input.fixtureHeightAboveWaterCm,
        plantDemand = input.plantDemand.name,
        plantCoverage = input.plantCoverage.name,
        co2Readiness = input.co2Readiness.name,
        substrateSemantic = input.substrateSemantic.name,
        daylightExposure = input.daylightExposure.name,
        daylightStartMinute = input.daylightStartMinute,
        daylightEndMinute = input.daylightEndMinute,
        surfaceGrowth = input.surfaceGrowth.name,
        shelterAvailability = input.shelterAvailability.name,
        programEndMinute = input.programEndMinute,
        programStartMinute = (draft.startTimeMs / MINUTE_MS).toInt(),
        rampDurationMinutes = (draft.rampDurationMs / MINUTE_MS).toInt(),
        photoperiodMinutes = plan.currentPhase.lifecycleStage.durationMinutes,
        initialStartPercent = plan.initialStartPercent,
        redPercent = channels.getValue(DeviceLightAutomaticChannel.RED),
        greenPercent = channels.getValue(DeviceLightAutomaticChannel.GREEN),
        bluePercent = channels.getValue(DeviceLightAutomaticChannel.BLUE),
        whitePercent = channels[DeviceLightAutomaticChannel.WHITE],
        maximumChannelPercent = plan.maximumChannelPercent,
        recommendationEpochDay = plan.currentPhase.draft.validFromEpochDay,
        reevaluationEpochDay = plan.reevaluationEpochDay,
        lastLightingResetEpochDay = tank.lastLightingResetEpochDay,
        priorAppliedPhotoperiodMinutes = tank.lastAppliedPhotoperiodMinutes,
        priorAppliedMaximumChannelPercent = tank.lastAppliedMaximumChannelPercent,
        priorAppliedEpochDay = tank.lastAppliedEpochDay,
        profileUpdatedAtMillis = tank.profileUpdatedAtMillis,
        installationUpdatedAtMillis = tank.installationUpdatedAtMillis,
        createdAtMillis = createdAtMillis,
        outcome = TankLightRecommendationOutcome.PREPARED
    )
}

private fun nextAuditId(
    plan: DeviceLightQuickSetupPlan,
    existing: List<TankLightRecommendationSnapshot>,
    createdAtMillis: Long
): String {
    if (existing.none { snapshot -> snapshot.recommendationId == plan.recommendationId }) {
        return plan.recommendationId
    }
    var nonce = 1
    while (true) {
        val suffix = sha256Hex("${plan.recommendationId}|$createdAtMillis|$nonce").take(8)
        val candidate = "slr-${plan.profileFingerprint}$suffix"
        if (existing.none { snapshot -> snapshot.recommendationId == candidate }) {
            return candidate
        }
        nonce += 1
    }
}

private fun DeviceLightManagedPlanSnapshot.requireMatches(
    prepared: TankLightRecommendationSnapshot
) {
    check(installed && authority.installedPlanId != null) {
        "Firmware did not return an installed managed plan."
    }
    check(initialStartPercent == prepared.initialStartPercent)
    val phase = phases.single()
    check(phase.validFromEpochDay == prepared.recommendationEpochDay)
    check(phase.validUntilEpochDayExclusive == null)
    check(phase.transitionDays == 0)
    check(phase.weekdaysMask == EVERY_DAY_MASK)
    check(phase.startTimeMs == prepared.programStartMinute * MINUTE_MS)
    check(phase.endTimeMs == prepared.programEndMinute * MINUTE_MS)
    check(phase.rampDurationMs == prepared.rampDurationMinutes * MINUTE_MS)
    check(phase.scene.channels.getValue(DeviceLightAutomaticChannel.RED) == prepared.redPercent)
    check(
        phase.scene.channels.getValue(DeviceLightAutomaticChannel.GREEN) ==
            prepared.greenPercent
    )
    check(
        phase.scene.channels.getValue(DeviceLightAutomaticChannel.BLUE) ==
            prepared.bluePercent
    )
    check(phase.scene.channels[DeviceLightAutomaticChannel.WHITE] == prepared.whitePercent)
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun DeviceLightStatus.deviceLocalEpochDayOrNull(): Long? {
    if (!scheduler.ready) return null
    val date = scheduler.localDate ?: return null
    return runCatching { LocalDate.parse(date).toEpochDay() }
        .getOrNull()
        ?.takeIf { epochDay -> epochDay in MIN_DEVICE_EPOCH_DAY..MAX_DEVICE_EPOCH_DAY }
}

private fun failure(failure: DeviceLightQuickSetupTankFailure) =
    DeviceLightQuickSetupTankReadResult.Failed(failure)

private const val MIN_DEVICE_EPOCH_DAY = 10_957L
private const val MAX_DEVICE_EPOCH_DAY = 47_481L
private const val MINUTE_MS = 60_000L
private const val EVERY_DAY_MASK = 127
private const val PREPARED_CLEANUP_ATTEMPTS = 3
