package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightCalibrationCatalog
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightLifecycleStage
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanConfidence
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.data.store.CommercialStoreSchema

internal class TankDeviceAssignmentsValidationException(
    message: String
) : IllegalStateException(message)

internal sealed interface TankDeviceStoreAssignDecision {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision

    data class AlreadyAssigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision

    data class Conflict(
        val existingAssignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision
}

internal data class TankDeviceStoreAssignMutation(
    val store: TankDeviceAssignmentsStore,
    val decision: TankDeviceStoreAssignDecision
)

internal data class TankDeviceStoreRepairMutation(
    val store: TankDeviceAssignmentsStore,
    val removedAssignments: List<TankDeviceAssignment>
)

internal object TankDeviceAssignmentRules {

    fun validate(
        store: TankDeviceAssignmentsStore
    ) {
        if (store.schemaVersion != CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION) {
            throw TankDeviceAssignmentsValidationException(
                "Unsupported tank assignment schema version ${store.schemaVersion}."
            )
        }
        val deviceOwnerKeys = mutableSetOf<String>()

        store.getAssignmentsList().forEachIndexed { index, assignment ->
            val ownerUid = assignment.ownerUid
            val deviceUid = assignment.deviceUid

            if (ownerUid.isBlank() || ownerUid != ownerUid.trim()) {
                invalid(index, "owner_uid must be non-blank and trimmed")
            }

            if (assignment.tankId <= 0L) {
                invalid(index, "tank_id must be positive")
            }

            if (deviceUid.isBlank() || deviceUid != deviceUid.trim()) {
                invalid(index, "device_uid must be non-blank and trimmed")
            }

            if (assignment.assignedAtMillis <= 0L) {
                invalid(index, "assigned_at_millis must be positive")
            }
            validateLightAutomation(index, assignment)

            val ownerDeviceKey = ownerDeviceKey(
                ownerUid = ownerUid,
                deviceUid = deviceUid
            )

            if (!deviceOwnerKeys.add(ownerDeviceKey)) {
                invalid(
                    index = index,
                    reason = "an owner can assign a device to only one tank"
                )
            }
        }
    }

    private fun validateLightAutomation(
        assignmentIndex: Int,
        assignment: StoredTankDeviceAssignment
    ) {
        if (!assignment.hasLightInstallation()) {
            invalid(assignmentIndex, "light_installation is required")
        }
        val installation = assignment.lightInstallation
        if (installation.contractRevision != TankLightInstallationProfile.CONTRACT_REVISION) {
            invalid(assignmentIndex, "unsupported light installation contract revision")
        }
        if (installation.hasFixtureHeightAboveWaterCm() &&
            installation.fixtureHeightAboveWaterCm !in 0..200
        ) {
            invalid(assignmentIndex, "fixture height must be between 0 and 200 cm")
        }
        if (installation.hasInstalledAtEpochDay() && installation.installedAtEpochDay <= 0L) {
            invalid(assignmentIndex, "installed date must be positive")
        }
        if (installation.hasLastFixtureChangeEpochDay() &&
            installation.lastFixtureChangeEpochDay <= 0L
        ) {
            invalid(assignmentIndex, "fixture change date must be positive")
        }
        if (installation.hasUpdatedAtMillis() && installation.updatedAtMillis <= 0L) {
            invalid(assignmentIndex, "installation update timestamp must be positive")
        }
        if (assignment.lightRecommendationsCount > MAX_RECOMMENDATION_HISTORY) {
            invalid(assignmentIndex, "recommendation history exceeds the commercial limit")
        }
        val recommendationIds = mutableSetOf<String>()
        assignment.lightRecommendationsList.forEach { snapshot ->
            val requiredText = listOf(
                snapshot.recommendationId,
                snapshot.profileFingerprint,
                snapshot.policyVersion,
                snapshot.productKey,
                snapshot.hardwareRevision,
                snapshot.confidence,
                snapshot.plantDemand,
                snapshot.plantCoverage,
                snapshot.co2Readiness,
                snapshot.substrateSemantic,
                snapshot.daylightExposure,
                snapshot.surfaceGrowth,
                snapshot.shelterAvailability,
                snapshot.outcome
            )
            if (requiredText.any { value -> value.isBlank() || value != value.trim() }) {
                invalid(assignmentIndex, "recommendation contains missing or non-canonical text")
            }
            if (!recommendationIds.add(snapshot.recommendationId)) {
                invalid(assignmentIndex, "recommendation ids must be unique")
            }
            if (snapshot.evidenceSourceIdsCount == 0 ||
                snapshot.evidenceSourceIdsList.any(String::isBlank)
            ) {
                invalid(assignmentIndex, "recommendation evidence sources are required")
            }
            if (snapshot.evidenceSourceIdsList !=
                snapshot.evidenceSourceIdsList.distinct().sorted()
            ) {
                invalid(assignmentIndex, "recommendation evidence sources must be unique and sorted")
            }
            validateSnapshotCatalogIds(assignmentIndex, snapshot)
            validateEvidenceProvenance(assignmentIndex, snapshot)
            validateDecisionCodes(assignmentIndex, snapshot)
            if (!snapshot.recommendationId.matches(RECOMMENDATION_ID_PATTERN)) {
                invalid(assignmentIndex, "recommendation id is not a commercial smart-light id")
            }
            if (!snapshot.profileFingerprint.matches(PROFILE_FINGERPRINT_PATTERN)) {
                invalid(assignmentIndex, "recommendation input fingerprint is invalid")
            }
            val recommendationDigest = snapshot.recommendationId.removePrefix("slr-")
            if (!recommendationDigest.startsWith(snapshot.profileFingerprint)) {
                invalid(assignmentIndex, "recommendation id and input fingerprint disagree")
            }
            if (snapshot.policyVersion != DeviceLightQuickSetupCalculator.POLICY_VERSION) {
                invalid(assignmentIndex, "recommendation is not a commercial V1 policy")
            }
            validateRecommendationEnums(assignmentIndex, snapshot)
            if (!snapshot.hasPlants || !snapshot.plantedFreshwater ||
                snapshot.fixtureLengthMm <= 0 || snapshot.tankHeightCm <= 0 ||
                snapshot.waterDepthCm !in 5..snapshot.tankHeightCm ||
                snapshot.fixtureHeightAboveWaterCm !in 0..200
            ) {
                invalid(assignmentIndex, "recommendation geometry is invalid")
            }
            if (snapshot.programEndMinute !in 0 until MINUTES_PER_DAY ||
                snapshot.programStartMinute !in 0 until MINUTES_PER_DAY ||
                snapshot.photoperiodMinutes !in REVIEWED_PHOTOPERIOD_MINUTES ||
                snapshot.programEndMinute - snapshot.programStartMinute !=
                snapshot.photoperiodMinutes ||
                snapshot.rampDurationMinutes !in 0..(snapshot.photoperiodMinutes / 2) ||
                snapshot.initialStartPercent != MANAGED_PLAN_INITIAL_START_PERCENT ||
                snapshot.redPercent !in 0..100 ||
                snapshot.greenPercent !in 0..100 ||
                snapshot.bluePercent !in 0..100 ||
                (snapshot.hasWhitePercent() && snapshot.whitePercent !in 0..100) ||
                snapshot.maximumChannelPercent !in 0..100 ||
                snapshot.recommendationEpochDay !in
                MIN_DEVICE_EPOCH_DAY..MAX_DEVICE_EPOCH_DAY ||
                snapshot.reevaluationEpochDay < snapshot.recommendationEpochDay ||
                snapshot.createdAtMillis <= 0L
            ) {
                invalid(assignmentIndex, "recommendation schedule or output is invalid")
            }
            validateRecommendationOutput(assignmentIndex, snapshot)
            validateDecisionInputs(assignmentIndex, snapshot)
            validateRecommendationProgression(assignmentIndex, snapshot)
            validateCalibrationAuthority(assignmentIndex, snapshot)
            validateDaylightWindow(assignmentIndex, snapshot)
            if (snapshot.hasAppliedAtMillis() && snapshot.appliedAtMillis < snapshot.createdAtMillis) {
                invalid(assignmentIndex, "recommendation applied timestamp precedes creation")
            }
            if ((snapshot.outcome == TankLightRecommendationOutcome.APPLIED.name) !=
                snapshot.hasAppliedAtMillis()
            ) {
                invalid(assignmentIndex, "recommendation outcome and applied timestamp disagree")
            }
            validateAppliedAuthority(assignmentIndex, snapshot)
        }
    }

    private fun validateDecisionCodes(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val reasons = snapshot.reasonCodesList
        val warnings = snapshot.warningCodesList
        val validReasons = DeviceLightPlanReason.entries.names()
        val validWarnings = DeviceLightPlanWarning.entries.names()
        if (reasons.isEmpty() || reasons != reasons.distinct().sorted() ||
            reasons.any { reason -> reason !in validReasons }
        ) {
            invalid(assignmentIndex, "recommendation reason codes are invalid")
        }
        if (warnings != warnings.distinct().sorted() ||
            warnings.any { warning -> warning !in validWarnings }
        ) {
            invalid(assignmentIndex, "recommendation warning codes are invalid")
        }
    }

    private fun validateSnapshotCatalogIds(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val plantIds = snapshot.plantCatalogIdsList
        val substrateIds = snapshot.substrateProductIdsList
        if (plantIds.isEmpty() || plantIds.any { value -> value.isBlank() || value != value.trim() } ||
            plantIds != plantIds.distinct().sorted()
        ) {
            invalid(assignmentIndex, "recommendation plant catalog ids are invalid")
        }
        if (substrateIds.any { value -> value.isBlank() || value != value.trim() } ||
            substrateIds != substrateIds.distinct().sorted()
        ) {
            invalid(assignmentIndex, "recommendation substrate product ids are invalid")
        }
        val substrateRequired = snapshot.substrateSemantic !=
            AquariumSubstrateSemantic.NOT_APPLICABLE.name
        if (substrateRequired != substrateIds.isNotEmpty()) {
            invalid(assignmentIndex, "recommendation substrate inputs disagree")
        }
        val expectedSubstrateSemantic = AquariumSubstrateSemantics.aggregate(substrateIds).name
        if (snapshot.substrateSemantic != expectedSubstrateSemantic) {
            invalid(assignmentIndex, "recommendation substrate catalog authority is invalid")
        }

        val plantRecords = plantIds.mapNotNull(AquariumPlantLightCatalog::record)
        val reviewedDemandFloor = plantRecords.maxByOrNull { record ->
            plantDemandRank(record.lightDemand.name)
        }?.lightDemand?.name
        if (reviewedDemandFloor != null) {
            val allPlantsReviewed = plantRecords.size == plantIds.size
            val violatesCatalogAuthority = if (allPlantsReviewed) {
                snapshot.plantDemand != reviewedDemandFloor
            } else {
                plantDemandRank(snapshot.plantDemand) < plantDemandRank(reviewedDemandFloor)
            }
            if (violatesCatalogAuthority) {
                invalid(assignmentIndex, "recommendation plant catalog authority is invalid")
            }
        }
    }

    private fun validateEvidenceProvenance(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val expected = buildSet {
            add("tropica_growing_in")
            add("tropica_quick_guide")
            if (snapshot.confidence ==
                DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED.name
            ) {
                add("chihiros_light_intensity_guidance")
            }
            val plantEvidence = snapshot.plantCatalogIdsList.mapNotNull { catalogId ->
                AquariumPlantLightCatalog.record(catalogId)?.evidenceSourceId
            }
            if (plantEvidence.isNotEmpty()) add("tropica_plant_database")
            if (snapshot.surfaceGrowth == AquariumSurfaceGrowth.STABLE_ALGAE.name ||
                snapshot.surfaceGrowth == AquariumSurfaceGrowth.WORSENING_ALGAE.name
            ) {
                add("tropica_algae_control")
            }
            addAll(plantEvidence)
            addAll(
                AquariumSubstrateSemantics.evidenceSourceIds(
                    snapshot.substrateProductIdsList
                )
            )
            if (snapshot.co2ComponentPresent) {
                add("colombo_co2_profi_manual")
                add("bioscape_co2_generator_manual")
                add("kitaya_2003_co2_light_photosynthesis")
            }
        }.sorted()
        if (snapshot.evidenceSourceIdsList != expected) {
            invalid(assignmentIndex, "recommendation evidence provenance is invalid")
        }
    }

    private fun validateDecisionInputs(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val optionalEpochDays = listOf(
            snapshot.tankSetupEpochDay.takeIf { snapshot.hasTankSetupEpochDay() },
            snapshot.lastLightingResetEpochDay.takeIf {
                snapshot.hasLastLightingResetEpochDay()
            },
            snapshot.priorAppliedEpochDay.takeIf { snapshot.hasPriorAppliedEpochDay() }
        ).filterNotNull()
        if (optionalEpochDays.any { day -> day !in MIN_DEVICE_EPOCH_DAY..MAX_DEVICE_EPOCH_DAY }) {
            invalid(assignmentIndex, "recommendation decision date is invalid")
        }
        val priorParts = listOf(
            snapshot.hasPriorAppliedPhotoperiodMinutes(),
            snapshot.hasPriorAppliedMaximumChannelPercent(),
            snapshot.hasPriorAppliedEpochDay()
        )
        if (priorParts.any { present -> present != priorParts.first() }) {
            invalid(assignmentIndex, "recommendation prior dose is incomplete")
        }
        if (priorParts.first() &&
            (snapshot.priorAppliedPhotoperiodMinutes !in REVIEWED_PHOTOPERIOD_MINUTES ||
                snapshot.priorAppliedMaximumChannelPercent !in 0..100 ||
                snapshot.priorAppliedEpochDay > snapshot.recommendationEpochDay)
        ) {
            invalid(assignmentIndex, "recommendation prior dose is invalid")
        }
        if ((snapshot.hasProfileUpdatedAtMillis() && snapshot.profileUpdatedAtMillis <= 0L) ||
            (snapshot.hasInstallationUpdatedAtMillis() &&
                snapshot.installationUpdatedAtMillis <= 0L)
        ) {
            invalid(assignmentIndex, "recommendation source timestamp is invalid")
        }
        val co2Valid = if (snapshot.co2ComponentPresent) {
            snapshot.co2Readiness != AquariumCo2Readiness.NOT_INSTALLED.name
        } else {
            snapshot.co2Readiness == AquariumCo2Readiness.NOT_INSTALLED.name
        }
        val shelterValid = if (snapshot.hasShrimp) {
            snapshot.shelterAvailability != AquariumShelterAvailability.NOT_REQUIRED.name
        } else {
            snapshot.shelterAvailability == AquariumShelterAvailability.NOT_REQUIRED.name
        }
        if (!co2Valid || !shelterValid) {
            invalid(assignmentIndex, "recommendation biological inputs disagree")
        }
    }

    private fun validateRecommendationOutput(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val values = buildList {
            add(snapshot.redPercent)
            add(snapshot.greenPercent)
            add(snapshot.bluePercent)
            if (snapshot.hasWhitePercent()) add(snapshot.whitePercent)
        }
        val shapeValid = when (snapshot.productKey) {
            WRGB_PRODUCT_KEY -> snapshot.hasWhitePercent()
            RGB_PRODUCT_KEY -> !snapshot.hasWhitePercent()
            else -> false
        }
        val policyCap = recommendationOutputCap(snapshot)
        val effectiveCap = if (snapshot.hasPriorAppliedMaximumChannelPercent()) {
            minOf(policyCap, snapshot.priorAppliedMaximumChannelPercent)
        } else {
            policyCap
        }
        if (!shapeValid || values.maxOrNull() != snapshot.maximumChannelPercent ||
            snapshot.maximumChannelPercent > effectiveCap
        ) {
            invalid(assignmentIndex, "recommendation channel snapshot is invalid")
        }
    }

    private fun recommendationOutputCap(snapshot: StoredLightRecommendationSnapshot): Int {
        var cap = if (snapshot.confidence == DeviceLightPlanConfidence.CALIBRATED.name) {
            MAX_CALIBRATED_PERCENT
        } else {
            MAX_UNCALIBRATED_PERCENT
        }
        if (snapshot.confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED.name &&
            snapshot.photoperiodMinutes == DeviceLightLifecycleStage.STARTUP.durationMinutes
        ) {
            cap = minOf(cap, MAX_UNCALIBRATED_STARTUP_PERCENT)
        }
        if (snapshot.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON.name) {
            cap = minOf(cap, 40)
        }
        if (snapshot.plantCoverage == AquariumPlantCoverage.SPARSE.name) cap = minOf(cap, 40)
        if (snapshot.daylightExposure == AquariumDaylightExposure.DIRECT.name) {
            cap = minOf(cap, 35)
        }
        cap = when (snapshot.surfaceGrowth) {
            AquariumSurfaceGrowth.STABLE_ALGAE.name -> minOf(cap, 35)
            AquariumSurfaceGrowth.WORSENING_ALGAE.name -> minOf(cap, 30)
            else -> cap
        }
        cap = when (snapshot.shelterAvailability) {
            AquariumShelterAvailability.LIMITED.name -> minOf(cap, 35)
            AquariumShelterAvailability.NONE.name -> minOf(cap, 30)
            else -> cap
        }
        if (snapshot.waterDepthCm + snapshot.fixtureHeightAboveWaterCm <= 25) {
            cap = minOf(cap, 35)
        }
        return cap
    }

    private fun validateRecommendationProgression(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        if (!snapshot.hasPriorAppliedPhotoperiodMinutes()) {
            if (snapshot.photoperiodMinutes != DeviceLightLifecycleStage.STARTUP.durationMinutes) {
                invalid(assignmentIndex, "first recommendation must use the startup photoperiod")
            }
            return
        }
        val prior = snapshot.priorAppliedPhotoperiodMinutes
        val next = when (prior) {
            DeviceLightLifecycleStage.STARTUP.durationMinutes ->
                DeviceLightLifecycleStage.ACCLIMATION.durationMinutes
            DeviceLightLifecycleStage.ACCLIMATION.durationMinutes ->
                DeviceLightLifecycleStage.ESTABLISHED.durationMinutes
            else -> DeviceLightLifecycleStage.ESTABLISHED.durationMinutes
        }
        val allowed = snapshot.photoperiodMinutes == DeviceLightLifecycleStage.STARTUP.durationMinutes ||
            snapshot.photoperiodMinutes == prior ||
            snapshot.photoperiodMinutes == next
        if (!allowed) {
            invalid(assignmentIndex, "recommendation skips a reviewed photoperiod step")
        }
        if (snapshot.photoperiodMinutes <= prior) return

        val reviewReady = snapshot.recommendationEpochDay - snapshot.priorAppliedEpochDay >=
            MIN_REVIEW_INTERVAL_DAYS
        val resetDay = snapshot.lastLightingResetEpochDay.takeIf {
            snapshot.hasLastLightingResetEpochDay() && it <= snapshot.recommendationEpochDay
        }
        val resetReady = resetDay?.let { day ->
            snapshot.recommendationEpochDay - day + 1L > STARTUP_WINDOW_DAYS
        } == true
        val guarded = snapshot.plantCoverage == AquariumPlantCoverage.SPARSE.name ||
            snapshot.daylightExposure == AquariumDaylightExposure.DIRECT.name ||
            snapshot.surfaceGrowth == AquariumSurfaceGrowth.STABLE_ALGAE.name ||
            snapshot.surfaceGrowth == AquariumSurfaceGrowth.WORSENING_ALGAE.name ||
            (snapshot.co2ComponentPresent &&
                snapshot.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON.name) ||
            (snapshot.plantDemand == DeviceLightPlantDemand.HIGH.name &&
                snapshot.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON.name) ||
            (snapshot.hasShrimp &&
                snapshot.shelterAvailability != AquariumShelterAvailability.ADEQUATE.name)
        if (!reviewReady || !resetReady || guarded) {
            invalid(assignmentIndex, "recommendation progression bypasses a commercial guard")
        }
    }

    private fun validateAppliedAuthority(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val authorityFieldsPresent = listOf(
            snapshot.hasFirmwarePlanId(),
            snapshot.hasFirmwarePlanRevision(),
            snapshot.hasFirmwareStorageGeneration()
        )
        val applied = snapshot.outcome == TankLightRecommendationOutcome.APPLIED.name
        if (authorityFieldsPresent.any { present -> present != applied }) {
            invalid(assignmentIndex, "recommendation firmware authority is incomplete")
        }
        if (applied &&
            (!snapshot.firmwarePlanId.matches(FIRMWARE_PLAN_ID_PATTERN) ||
                snapshot.firmwarePlanRevision <= 0L ||
                snapshot.firmwareStorageGeneration <= 0L)
        ) {
            invalid(assignmentIndex, "recommendation firmware authority is invalid")
        }
    }

    private fun validateRecommendationEnums(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val valid = snapshot.confidence in DeviceLightPlanConfidence.entries.names() &&
            snapshot.plantDemand in DeviceLightPlantDemand.entries.names(excludeUnknown = true) &&
            snapshot.plantCoverage in AquariumPlantCoverage.entries.names(excludeUnknown = true) &&
            snapshot.co2Readiness in AquariumCo2Readiness.entries.names(excludeUnknown = true) &&
            snapshot.substrateSemantic in AquariumSubstrateSemantic.entries.names() &&
            snapshot.daylightExposure in
            AquariumDaylightExposure.entries.names(excludeUnknown = true) &&
            snapshot.surfaceGrowth in AquariumSurfaceGrowth.entries.names(excludeUnknown = true) &&
            snapshot.shelterAvailability in
            AquariumShelterAvailability.entries.names(excludeUnknown = true) &&
            snapshot.outcome in TankLightRecommendationOutcome.entries.names()
        if (!valid) invalid(assignmentIndex, "recommendation contains an unsupported enum value")
    }

    private fun validateCalibrationAuthority(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val calibrated = snapshot.confidence == DeviceLightPlanConfidence.CALIBRATED.name
        if (calibrated &&
            (snapshot.calibrationProfileId.isBlank() || snapshot.calibrationRevision <= 0)
        ) {
            invalid(assignmentIndex, "calibrated recommendation requires a calibration authority")
        }
        if (calibrated) {
            val approved = DeviceLightCalibrationCatalog.find(
                productKey = snapshot.productKey,
                hardwareRevision = snapshot.hardwareRevision,
                fixtureLengthMm = snapshot.fixtureLengthMm
            )
            if (approved == null || approved.profileId != snapshot.calibrationProfileId ||
                approved.revision != snapshot.calibrationRevision
            ) {
                invalid(assignmentIndex, "calibration authority is not in the approved catalog")
            }
        }
        if (!calibrated &&
            (snapshot.calibrationProfileId.isNotEmpty() || snapshot.calibrationRevision != 0 ||
                snapshot.maximumChannelPercent > MAX_UNCALIBRATED_PERCENT)
        ) {
            invalid(
                assignmentIndex,
                "uncalibrated recommendation exceeds its conservative output authority"
            )
        }
    }

    private fun validateDaylightWindow(
        assignmentIndex: Int,
        snapshot: StoredLightRecommendationSnapshot
    ) {
        val direct = snapshot.daylightExposure == AquariumDaylightExposure.DIRECT.name
        if (direct) {
            if (!snapshot.hasDaylightStartMinute() || !snapshot.hasDaylightEndMinute() ||
                snapshot.daylightStartMinute !in 0 until MINUTES_PER_DAY ||
                snapshot.daylightEndMinute !in 1 until MINUTES_PER_DAY ||
                snapshot.daylightStartMinute >= snapshot.daylightEndMinute
            ) {
                invalid(assignmentIndex, "direct daylight requires a valid same-day window")
            }
        } else if (snapshot.hasDaylightStartMinute() || snapshot.hasDaylightEndMinute()) {
            invalid(assignmentIndex, "non-direct daylight cannot carry a daylight window")
        }
    }

    private fun <E : Enum<E>> List<E>.names(excludeUnknown: Boolean = false): Set<String> =
        asSequence()
            .map { value -> value.name }
            .filterNot { name -> excludeUnknown && name == "UNKNOWN" }
            .toSet()

    private fun plantDemandRank(value: String): Int = when (value) {
        DeviceLightPlantDemand.LOW.name -> 1
        DeviceLightPlantDemand.MEDIUM.name -> 2
        DeviceLightPlantDemand.HIGH.name -> 3
        else -> 0
    }

    fun assign(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long,
        deviceUid: String,
        assignedAtMillis: Long
    ): TankDeviceStoreAssignMutation {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }
        require(assignedAtMillis > 0L) {
            "assignedAtMillis must be positive"
        }

        validate(store)

        val existing = store.getAssignmentsList().firstOrNull { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.deviceUid == normalizedDeviceUid
        }

        if (existing != null) {
            val existingDomain = existing.toDomain()
            val decision = if (existing.tankId == tankId) {
                TankDeviceStoreAssignDecision.AlreadyAssigned(existingDomain)
            } else {
                TankDeviceStoreAssignDecision.Conflict(existingDomain)
            }

            return TankDeviceStoreAssignMutation(
                store = store,
                decision = decision
            )
        }

        val assignment = TankDeviceAssignment(
            ownerUid = normalizedOwnerUid,
            tankId = tankId,
            deviceUid = com.aqua.aqualight.data.devices.model.DeviceUid(normalizedDeviceUid),
            assignedAtMillis = assignedAtMillis
        )

        val updatedStore = store.toBuilder()
            .addAssignments(assignment.toStored())
            .build()

        validate(updatedStore)

        return TankDeviceStoreAssignMutation(
            store = updatedStore,
            decision = TankDeviceStoreAssignDecision.Assigned(assignment)
        )
    }

    fun removeFromTank(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long,
        deviceUid: String
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }

        return removeMatching(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.tankId == tankId &&
                assignment.deviceUid == normalizedDeviceUid
        }
    }

    fun removeDevice(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        deviceUid: String
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        return removeMatching(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.deviceUid == normalizedDeviceUid
        }
    }

    fun removeTank(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long
    ): Pair<TankDeviceAssignmentsStore, Int> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }

        return removeMatchingCount(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.tankId == tankId
        }
    }

    fun clearOwner(
        store: TankDeviceAssignmentsStore,
        ownerUid: String
    ): Pair<TankDeviceAssignmentsStore, Int> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()

        return removeMatchingCount(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid
        }
    }

    fun repairOwner(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        validTankIds: Set<Long>,
        validDeviceUids: Set<String>
    ): TankDeviceStoreRepairMutation {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUids = validDeviceUids.map { deviceUid ->
            deviceUid.requireNormalizedDeviceUid()
        }.toSet()

        validate(store)

        val removedAssignments = mutableListOf<TankDeviceAssignment>()
        val keptAssignments = store.getAssignmentsList().filter { assignment ->
            val belongsToOwner = assignment.ownerUid == normalizedOwnerUid
            val isStale = belongsToOwner && (
                assignment.tankId !in validTankIds ||
                    assignment.deviceUid !in normalizedDeviceUids
                )

            if (isStale) {
                removedAssignments += assignment.toDomain()
            }

            !isStale
        }

        val updatedStore = if (removedAssignments.isEmpty()) {
            store
        } else {
            store.toBuilder()
                .clearAssignments()
                .addAllAssignments(keptAssignments)
                .build()
        }

        validate(updatedStore)

        return TankDeviceStoreRepairMutation(
            store = updatedStore,
            removedAssignments = removedAssignments.toList()
        )
    }

    private fun removeMatching(
        store: TankDeviceAssignmentsStore,
        predicate: (StoredTankDeviceAssignment) -> Boolean
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val (updatedStore, removedCount) = removeMatchingCount(
            store = store,
            predicate = predicate
        )

        return updatedStore to (removedCount > 0)
    }

    private fun removeMatchingCount(
        store: TankDeviceAssignmentsStore,
        predicate: (StoredTankDeviceAssignment) -> Boolean
    ): Pair<TankDeviceAssignmentsStore, Int> {
        validate(store)

        var removedCount = 0
        val keptAssignments = store.getAssignmentsList().filter { assignment ->
            val remove = predicate(assignment)
            if (remove) {
                removedCount += 1
            }
            !remove
        }

        if (removedCount == 0) {
            return store to 0
        }

        val updatedStore = store.toBuilder()
            .clearAssignments()
            .addAllAssignments(keptAssignments)
            .build()

        validate(updatedStore)

        return updatedStore to removedCount
    }

    private fun ownerDeviceKey(
        ownerUid: String,
        deviceUid: String
    ): String {
        return "$ownerUid\u0000$deviceUid"
    }

    private fun String.requireNormalizedOwnerUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return normalized
    }

    private fun String.requireNormalizedDeviceUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "deviceUid must not be blank"
        }
        return normalized
    }

    private fun invalid(
        index: Int,
        reason: String
    ): Nothing {
        throw TankDeviceAssignmentsValidationException(
            "Invalid tank assignment at index $index: $reason."
        )
    }

    private const val MAX_RECOMMENDATION_HISTORY = 1_000
    private const val MINUTES_PER_DAY = 1_440
    private const val MAX_CALIBRATED_PERCENT = 70
    private const val MAX_UNCALIBRATED_PERCENT = 50
    private const val MAX_UNCALIBRATED_STARTUP_PERCENT = 30
    private const val MIN_REVIEW_INTERVAL_DAYS = 14L
    private const val STARTUP_WINDOW_DAYS = 21L
    private const val MANAGED_PLAN_INITIAL_START_PERCENT = 100
    private const val WRGB_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
    private const val RGB_PRODUCT_KEY = "LIGHT_RGB_PRO_SLIM"
    private const val MIN_DEVICE_EPOCH_DAY = 10_957L
    private const val MAX_DEVICE_EPOCH_DAY = 47_481L
    private val REVIEWED_PHOTOPERIOD_MINUTES =
        DeviceLightLifecycleStage.entries.map { stage -> stage.durationMinutes }.toSet()
    private val RECOMMENDATION_ID_PATTERN = Regex("^slr-[0-9a-f]{24}$")
    private val PROFILE_FINGERPRINT_PATTERN = Regex("^[0-9a-f]{16}$")
    private val FIRMWARE_PLAN_ID_PATTERN = Regex("^lp-[0-9a-f]{8}$")
}
