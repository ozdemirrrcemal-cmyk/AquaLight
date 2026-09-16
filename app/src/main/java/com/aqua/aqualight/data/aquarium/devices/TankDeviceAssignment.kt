package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.model.DeviceUid

data class TankDeviceAssignment(
    val ownerUid: String,
    val tankId: Long,
    val deviceUid: DeviceUid,
    val assignedAtMillis: Long,
    val lightInstallation: TankLightInstallationProfile = TankLightInstallationProfile(),
    val lightRecommendations: List<TankLightRecommendationSnapshot> = emptyList()
)

internal fun StoredTankDeviceAssignment.toDomain(): TankDeviceAssignment {
    return TankDeviceAssignment(
        ownerUid = ownerUid,
        tankId = tankId,
        deviceUid = DeviceUid(deviceUid),
        assignedAtMillis = assignedAtMillis,
        lightInstallation = lightInstallation.toDomain(),
        lightRecommendations = lightRecommendationsList.map { it.toDomain() }
    )
}

internal fun TankDeviceAssignment.toStored(): StoredTankDeviceAssignment {
    return StoredTankDeviceAssignment.newBuilder()
        .setOwnerUid(ownerUid)
        .setTankId(tankId)
        .setDeviceUid(deviceUid.value)
        .setAssignedAtMillis(assignedAtMillis)
        .setLightInstallation(lightInstallation.toStored())
        .addAllLightRecommendations(lightRecommendations.map { it.toStored() })
        .build()
}

private fun StoredLightInstallationProfile.toDomain(): TankLightInstallationProfile =
    TankLightInstallationProfile(
        contractRevision = contractRevision,
        fixtureHeightAboveWaterCm = fixtureHeightAboveWaterCm.takeIf {
            hasFixtureHeightAboveWaterCm()
        },
        installedAtEpochDay = installedAtEpochDay.takeIf { hasInstalledAtEpochDay() },
        lastFixtureChangeEpochDay = lastFixtureChangeEpochDay.takeIf {
            hasLastFixtureChangeEpochDay()
        },
        updatedAtMillis = updatedAtMillis.takeIf { hasUpdatedAtMillis() }
    )

private fun TankLightInstallationProfile.toStored(): StoredLightInstallationProfile {
    val builder = StoredLightInstallationProfile.newBuilder()
        .setContractRevision(contractRevision)
    fixtureHeightAboveWaterCm?.let(builder::setFixtureHeightAboveWaterCm)
    installedAtEpochDay?.let(builder::setInstalledAtEpochDay)
    lastFixtureChangeEpochDay?.let(builder::setLastFixtureChangeEpochDay)
    updatedAtMillis?.let(builder::setUpdatedAtMillis)
    return builder.build()
}

private fun StoredLightRecommendationSnapshot.toDomain(): TankLightRecommendationSnapshot =
    TankLightRecommendationSnapshot(
        recommendationId = recommendationId,
        profileFingerprint = profileFingerprint,
        policyVersion = policyVersion,
        evidenceSourceIds = evidenceSourceIdsList,
        reasonCodes = reasonCodesList,
        warningCodes = warningCodesList,
        plantCatalogIds = plantCatalogIdsList,
        substrateProductIds = substrateProductIdsList,
        productKey = productKey,
        hardwareRevision = hardwareRevision,
        fixtureLengthMm = fixtureLengthMm,
        calibrationProfileId = calibrationProfileId,
        calibrationRevision = calibrationRevision,
        confidence = confidence,
        tankSetupEpochDay = tankSetupEpochDay.takeIf { hasTankSetupEpochDay() },
        tankHeightCm = tankHeightCm,
        hasPlants = hasPlants,
        plantedFreshwater = plantedFreshwater,
        co2ComponentPresent = co2ComponentPresent,
        hasShrimp = hasShrimp,
        waterDepthCm = waterDepthCm,
        fixtureHeightAboveWaterCm = fixtureHeightAboveWaterCm,
        plantDemand = plantDemand,
        plantCoverage = plantCoverage,
        co2Readiness = co2Readiness,
        substrateSemantic = substrateSemantic,
        daylightExposure = daylightExposure,
        daylightStartMinute = daylightStartMinute.takeIf { hasDaylightStartMinute() },
        daylightEndMinute = daylightEndMinute.takeIf { hasDaylightEndMinute() },
        surfaceGrowth = surfaceGrowth,
        shelterAvailability = shelterAvailability,
        programEndMinute = programEndMinute,
        programStartMinute = programStartMinute,
        rampDurationMinutes = rampDurationMinutes,
        photoperiodMinutes = photoperiodMinutes,
        initialStartPercent = initialStartPercent,
        redPercent = redPercent,
        greenPercent = greenPercent,
        bluePercent = bluePercent,
        whitePercent = whitePercent.takeIf { hasWhitePercent() },
        maximumChannelPercent = maximumChannelPercent,
        recommendationEpochDay = recommendationEpochDay,
        reevaluationEpochDay = reevaluationEpochDay,
        lastLightingResetEpochDay = lastLightingResetEpochDay.takeIf {
            hasLastLightingResetEpochDay()
        },
        priorAppliedPhotoperiodMinutes = priorAppliedPhotoperiodMinutes.takeIf {
            hasPriorAppliedPhotoperiodMinutes()
        },
        priorAppliedMaximumChannelPercent = priorAppliedMaximumChannelPercent.takeIf {
            hasPriorAppliedMaximumChannelPercent()
        },
        priorAppliedEpochDay = priorAppliedEpochDay.takeIf { hasPriorAppliedEpochDay() },
        profileUpdatedAtMillis = profileUpdatedAtMillis.takeIf { hasProfileUpdatedAtMillis() },
        installationUpdatedAtMillis = installationUpdatedAtMillis.takeIf {
            hasInstallationUpdatedAtMillis()
        },
        createdAtMillis = createdAtMillis,
        appliedAtMillis = appliedAtMillis.takeIf { hasAppliedAtMillis() },
        firmwarePlanId = firmwarePlanId.takeIf { hasFirmwarePlanId() },
        firmwarePlanRevision = firmwarePlanRevision.takeIf {
            hasFirmwarePlanRevision()
        },
        firmwareStorageGeneration = firmwareStorageGeneration.takeIf {
            hasFirmwareStorageGeneration()
        },
        outcome = TankLightRecommendationOutcome.valueOf(outcome)
    )

private fun TankLightRecommendationSnapshot.toStored(): StoredLightRecommendationSnapshot {
    val builder = StoredLightRecommendationSnapshot.newBuilder()
        .setRecommendationId(recommendationId)
        .setProfileFingerprint(profileFingerprint)
        .setPolicyVersion(policyVersion)
        .addAllEvidenceSourceIds(evidenceSourceIds)
        .addAllReasonCodes(reasonCodes)
        .addAllWarningCodes(warningCodes)
        .addAllPlantCatalogIds(plantCatalogIds)
        .addAllSubstrateProductIds(substrateProductIds)
        .setProductKey(productKey)
        .setHardwareRevision(hardwareRevision)
        .setFixtureLengthMm(fixtureLengthMm)
        .setCalibrationProfileId(calibrationProfileId)
        .setCalibrationRevision(calibrationRevision)
        .setConfidence(confidence)
        .setTankHeightCm(tankHeightCm)
        .setHasPlants(hasPlants)
        .setPlantedFreshwater(plantedFreshwater)
        .setCo2ComponentPresent(co2ComponentPresent)
        .setHasShrimp(hasShrimp)
        .setWaterDepthCm(waterDepthCm)
        .setFixtureHeightAboveWaterCm(fixtureHeightAboveWaterCm)
        .setPlantDemand(plantDemand)
        .setPlantCoverage(plantCoverage)
        .setCo2Readiness(co2Readiness)
        .setSubstrateSemantic(substrateSemantic)
        .setDaylightExposure(daylightExposure)
        .setSurfaceGrowth(surfaceGrowth)
        .setShelterAvailability(shelterAvailability)
        .setProgramEndMinute(programEndMinute)
        .setProgramStartMinute(programStartMinute)
        .setRampDurationMinutes(rampDurationMinutes)
        .setPhotoperiodMinutes(photoperiodMinutes)
        .setInitialStartPercent(initialStartPercent)
        .setRedPercent(redPercent)
        .setGreenPercent(greenPercent)
        .setBluePercent(bluePercent)
        .setMaximumChannelPercent(maximumChannelPercent)
        .setRecommendationEpochDay(recommendationEpochDay)
        .setReevaluationEpochDay(reevaluationEpochDay)
        .setCreatedAtMillis(createdAtMillis)
        .setOutcome(outcome.name)
    daylightStartMinute?.let(builder::setDaylightStartMinute)
    daylightEndMinute?.let(builder::setDaylightEndMinute)
    tankSetupEpochDay?.let(builder::setTankSetupEpochDay)
    lastLightingResetEpochDay?.let(builder::setLastLightingResetEpochDay)
    priorAppliedPhotoperiodMinutes?.let(builder::setPriorAppliedPhotoperiodMinutes)
    priorAppliedMaximumChannelPercent?.let(builder::setPriorAppliedMaximumChannelPercent)
    priorAppliedEpochDay?.let(builder::setPriorAppliedEpochDay)
    profileUpdatedAtMillis?.let(builder::setProfileUpdatedAtMillis)
    installationUpdatedAtMillis?.let(builder::setInstallationUpdatedAtMillis)
    whitePercent?.let(builder::setWhitePercent)
    appliedAtMillis?.let(builder::setAppliedAtMillis)
    firmwarePlanId?.let(builder::setFirmwarePlanId)
    firmwarePlanRevision?.let(builder::setFirmwarePlanRevision)
    firmwareStorageGeneration?.let(builder::setFirmwareStorageGeneration)
    return builder.build()
}
