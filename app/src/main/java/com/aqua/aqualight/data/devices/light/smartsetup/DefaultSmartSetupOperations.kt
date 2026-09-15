package com.aqua.aqualight.data.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.devices.light.smartsetup.SmartLightPhaseDraft
import com.aqua.aqualight.application.devices.light.smartsetup.SmartLightPlanDraft
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupAquariumEnvironment
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupCalibrationCatalog
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecisionEngine
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupInput
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupLifecycleClassifier
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupMaintenanceObservations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupOperations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupReadResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.toApplicationSnapshot
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAuthorityRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanPhase
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.applyManagedPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data
import com.aqua.aqualight.data.devices.runtime.modules.light.refreshAuthoritySet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first

/** Production adapter. It never derives semantic facts from user-entered text. */
internal class DefaultSmartSetupOperations(
    private val ownerUid: String,
    private val devicesRepository: DevicesRepository,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val tankStore: AquariumTankDataStoreManager,
    private val careTaskStore: CareTaskDataStoreManager,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SmartSetupOperations {

    override suspend fun read(deviceUid: String): SmartSetupReadResult = try {
        when (val resolution = resolve(deviceUid)) {
            is Resolution.Ready -> SmartSetupReadResult.Available(resolution.snapshot)
            is Resolution.Failed -> resolution.result
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SmartSetupReadResult.InvalidFirmwareData
    }

    override suspend fun saveProfile(
        deviceUid: String,
        setupDateEpochDay: Long,
        profile: AquariumLightingProfile
    ): SmartSetupProfileSaveResult = try {
        val uid = deviceUid.toUidOrNull()
            ?: return SmartSetupProfileSaveResult.Failed(
                SmartSetupProfileSaveFailure.UNAVAILABLE
            )
        val assignment = assignmentRepository.assignmentForDevice(uid)
            ?: return SmartSetupProfileSaveResult.Failed(
                SmartSetupProfileSaveFailure.DEVICE_NOT_ASSIGNED
            )
        val tankExists = tankStore.tanksSnapshotForOwner(ownerUid)
            .any { tank -> tank.id == assignment.tankId }
        if (!tankExists) {
            return SmartSetupProfileSaveResult.Failed(
                SmartSetupProfileSaveFailure.AQUARIUM_NOT_FOUND
            )
        }
        tankStore.updateTankSmartSetupProfile(
            tankId = assignment.tankId,
            setupDateEpochDay = setupDateEpochDay,
            profile = profile
        )
        when (val refreshed = read(uid.value)) {
            is SmartSetupReadResult.Available -> SmartSetupProfileSaveResult.Saved(
                refreshed.snapshot
            )
            SmartSetupReadResult.AquariumNotFound -> SmartSetupProfileSaveResult.Failed(
                SmartSetupProfileSaveFailure.AQUARIUM_NOT_FOUND
            )
            SmartSetupReadResult.DeviceNotAssigned -> SmartSetupProfileSaveResult.Failed(
                SmartSetupProfileSaveFailure.DEVICE_NOT_ASSIGNED
            )
            else -> SmartSetupProfileSaveResult.Failed(SmartSetupProfileSaveFailure.UNAVAILABLE)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IllegalArgumentException) {
        SmartSetupProfileSaveResult.Failed(SmartSetupProfileSaveFailure.INVALID_PROFILE)
    } catch (_: Exception) {
        SmartSetupProfileSaveResult.Failed(SmartSetupProfileSaveFailure.UNAVAILABLE)
    }

    override suspend fun apply(
        deviceUid: String,
        expectedProfileFingerprint: String
    ): SmartSetupApplyResult = try {
        val resolution = when (val fresh = resolve(deviceUid)) {
            is Resolution.Ready -> fresh
            is Resolution.Failed -> return SmartSetupApplyResult.Failed(
                fresh.result.toApplyFailure()
            )
        }
        val ready = resolution.snapshot.decision as? SmartSetupDecision.Ready
            ?: return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.NOT_READY)
        val recommendation = ready.recommendation
        if (recommendation.profileFingerprint != expectedProfileFingerprint) {
            return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.PREVIEW_CHANGED)
        }
        val payload = DeviceLightManagedPlanApplyPayload(
            expectedRevision = resolution.plan.revision,
            expectedStorageGeneration = resolution.status.storageGeneration,
            planId = resolution.plan.planId,
            initialStartPercent = recommendation.plan.initialStartPercent,
            phases = recommendation.plan.toRuntimePhases(resolution.status.product)
        )
        when (
            val applied = resolution.runtime.applyManagedPlan(resolution.deviceUid, payload)
        ) {
            is DeviceRuntimeCommandOutcome.Success -> confirmApply(
                resolution = resolution,
                recommendationPlan = recommendation.plan,
                profileFingerprint = recommendation.profileFingerprint,
                commandDocument = applied.value
            )
            is DeviceRuntimeCommandOutcome.FirmwareError -> SmartSetupApplyResult.Failed(
                applied.toSmartSetupFailure()
            )
            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated -> SmartSetupApplyResult.Failed(
                SmartSetupApplyFailure.NOT_CONNECTED
            )
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> SmartSetupApplyResult.Failed(
                SmartSetupApplyFailure.UNSUPPORTED
            )
            is DeviceRuntimeCommandOutcome.ProtocolError -> SmartSetupApplyResult.Failed(
                SmartSetupApplyFailure.INVALID_FIRMWARE_DATA
            )
            is DeviceRuntimeCommandOutcome.Cancelled,
            is DeviceRuntimeCommandOutcome.SendFailed,
            is DeviceRuntimeCommandOutcome.Timeout -> SmartSetupApplyResult.Failed(
                SmartSetupApplyFailure.UNAVAILABLE
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SmartSetupApplyResult.Failed(SmartSetupApplyFailure.INVALID_FIRMWARE_DATA)
    }

    private suspend fun confirmApply(
        resolution: Resolution.Ready,
        recommendationPlan: SmartLightPlanDraft,
        profileFingerprint: String,
        commandDocument: DeviceLightManagedPlanDocument
    ): SmartSetupApplyResult {
        val refreshed = resolution.runtime.refreshAuthoritySet(resolution.deviceUid)
        if (refreshed !is DeviceLightAuthorityRefreshResult.Refreshed) {
            return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.COMMIT_UNCONFIRMED)
        }
        val status = refreshed.status
        val plan = refreshed.plan
        val commandCanonical = commandDocument.copy(event = null)
        val confirmed = listOf(
            commandCanonical == plan.copy(event = null),
            plan.installed,
            plan.planId != null,
            plan.matches(recommendationPlan, status.product),
            status.mode == DeviceLightMode.AUTO,
            status.auto.planInstalled,
            status.auto.planId == plan.planId,
            status.auto.planRevision == plan.revision,
            status.storageGeneration == plan.storageGeneration
        ).all { valid -> valid }
        if (!confirmed) {
            return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.COMMIT_UNCONFIRMED)
        }
        return SmartSetupApplyResult.Applied(
            planId = requireNotNull(plan.planId),
            revision = plan.revision,
            storageGeneration = plan.storageGeneration,
            profileFingerprint = profileFingerprint
        )
    }

    private suspend fun resolve(deviceUid: String): Resolution {
        val uid = deviceUid.toUidOrNull()
            ?: return Resolution.Failed(SmartSetupReadResult.InvalidDevice)
        val assignment = assignmentRepository.assignmentForDevice(uid)
            ?: return Resolution.Failed(SmartSetupReadResult.DeviceNotAssigned)
        val tank = tankStore.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { candidate -> candidate.id == assignment.tankId }
            ?.toApplicationSnapshot()
            ?: return Resolution.Failed(SmartSetupReadResult.AquariumNotFound)
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return Resolution.Failed(SmartSetupReadResult.Unavailable)
        val authority = when (val result = runtime.refreshAuthoritySet(uid)) {
            is DeviceLightAuthorityRefreshResult.Refreshed -> result
            is DeviceLightAuthorityRefreshResult.Failed -> {
                return Resolution.Failed(result.outcome.toReadFailure())
            }
        }
        val timeRuntime = devicesRepository.runtimeModules()?.time
            ?: return Resolution.Failed(SmartSetupReadResult.Unavailable)
        val time = when (val outcome = timeRuntime.requestStatus(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> {
                if (outcome.generation != authority.generation) {
                    return Resolution.Failed(SmartSetupReadResult.InvalidFirmwareData)
                }
                outcome.value
            }
            else -> return Resolution.Failed(outcome.toReadFailure())
        }
        val zone = runCatching { ZoneId.of(time.timezoneId) }.getOrNull()
            ?: return Resolution.Failed(SmartSetupReadResult.InvalidFirmwareData)
        val tasks = careTaskStore.tasksForTankFlow(tank.id).first()
        val input = tank.toSmartSetupInput(authority.status, tasks, zone, nowMillis())
        val decision = SmartSetupDecisionEngine.decide(input)
        val snapshot = SmartSetupSnapshot(
            deviceUid = uid.value,
            tankId = tank.id,
            tankName = tank.name,
            input = input,
            decision = decision,
            installedPlanId = authority.plan.planId,
            installedPlanRevision = authority.plan.revision,
            installedPlanFingerprintMatches =
                (decision as? SmartSetupDecision.Ready)?.recommendation?.plan?.let { draft ->
                    authority.plan.matches(draft, authority.status.product)
                } == true
        )
        return Resolution.Ready(
            deviceUid = uid,
            runtime = runtime,
            status = authority.status,
            plan = authority.plan,
            snapshot = snapshot
        )
    }

    private sealed interface Resolution {
        data class Ready(
            val deviceUid: DeviceUid,
            val runtime: DeviceLightRuntimeRepository,
            val status: DeviceLightStatus,
            val plan: DeviceLightManagedPlanDocument,
            val snapshot: SmartSetupSnapshot
        ) : Resolution

        data class Failed(val result: SmartSetupReadResult) : Resolution
    }
}

private fun AquariumTankSnapshot.toSmartSetupInput(
    status: DeviceLightStatus,
    tasks: List<CareTask>,
    zone: ZoneId,
    nowMillis: Long
): SmartSetupInput {
    val evaluationDay = status.scheduler.localDate?.let { date ->
        LocalDate.parse(date).toEpochDay()
    }
    val lifecycle = if (setupDateEpochDay != null && evaluationDay != null) {
        SmartSetupLifecycleClassifier.classify(setupDateEpochDay, evaluationDay)
    } else {
        null
    }
    val calibrationRevision = status.reportedCalibrationRevision()
    val calibration = SmartSetupCalibrationCatalog.reviewedProfileFor(status.product.wireValue)
        ?.takeIf { profile ->
            calibrationRevision == profile.revision &&
                status.product.sceneFields == profile.channels.map { channel -> channel.sceneKey }
        }
    return SmartSetupInput(
        tankId = id,
        tankName = name,
        aquariumEnvironment = tankType.toSmartSetupEnvironment(),
        evaluationEpochDay = evaluationDay,
        setupDateEpochDay = setupDateEpochDay,
        setupDay = lifecycle?.setupDay,
        lifecycleStage = lifecycle?.stage,
        isPlanted = tankType == AquariumTankTaxonomy.TYPE_PLANTED || plants.isNotEmpty(),
        plantDensity = lightingProfile.plantDensity,
        highestPlantLightDemand = lightingProfile.highestPlantLightDemand,
        co2Status = lightingProfile.co2Status,
        isActiveSoil = lightingProfile.isActiveSoil,
        waterDepthCm = lightingProfile.waterDepthCm,
        fixtureMountHeightCm = lightingProfile.fixtureMountHeightCm,
        deviceProductKey = status.product.wireValue,
        reportedCalibrationRevision = calibrationRevision,
        reportedChannelSceneKeys = status.product.sceneFields,
        calibrationProfile = calibration,
        preferredViewingStartMinuteOfDay =
            lightingProfile.preferredViewingStartMinuteOfDay,
        preferredViewingEndMinuteOfDay = lightingProfile.preferredViewingEndMinuteOfDay,
        algaeObservation = lightingProfile.algaeObservation,
        plantStressObservation = lightingProfile.plantStressObservation,
        observationDateEpochDay = lightingProfile.observationDateEpochDay,
        maintenance = tasks.toMaintenanceObservations(zone, nowMillis)
    )
}

private fun DeviceLightStatus.reportedCalibrationRevision(): Int? {
    if (product != DeviceLightProduct.WRGB_PRO_ELITE) return null
    val revisions = listOf(
        electricalDesign.contractRevision,
        power.modelRevision,
        color.modelRevision
    )
    if (revisions.any { revision -> revision == null }) return null
    val exact = revisions.filterNotNull().distinct()
    return exact.singleOrNull() ?: INVALID_CALIBRATION_REVISION
}

private fun String.toSmartSetupEnvironment(): SmartSetupAquariumEnvironment = when (this) {
    AquariumTankTaxonomy.TYPE_FISH,
    AquariumTankTaxonomy.TYPE_SHRIMP,
    AquariumTankTaxonomy.TYPE_PLANTED -> SmartSetupAquariumEnvironment.FRESHWATER
    AquariumTankTaxonomy.TYPE_MARINE,
    AquariumTankTaxonomy.TYPE_SOFTIES,
    AquariumTankTaxonomy.TYPE_MIXED_REEF,
    AquariumTankTaxonomy.TYPE_SPS,
    AquariumTankTaxonomy.TYPE_CORAL -> SmartSetupAquariumEnvironment.MARINE
    AquariumTankTaxonomy.TYPE_OTHER -> SmartSetupAquariumEnvironment.UNKNOWN
    else -> SmartSetupAquariumEnvironment.UNKNOWN
}

private fun List<CareTask>.toMaintenanceObservations(
    zone: ZoneId,
    nowMillis: Long
): SmartSetupMaintenanceObservations {
    val completed = filter { task ->
        task.status == CareTaskStatus.COMPLETED &&
            task.completedAtMillis != null &&
            task.type in RELEVANT_CARE_TASKS
    }
    fun latest(type: CareTaskType): Long? = completed
        .asSequence()
        .filter { task -> task.type == type }
        .mapNotNull(CareTask::completedAtMillis)
        .maxOrNull()
        ?.let { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay() }
    val overdue = count { task ->
        task.type in RELEVANT_CARE_TASKS &&
            task.status == CareTaskStatus.PENDING &&
            task.dueAtMillis <= nowMillis
    }
    return SmartSetupMaintenanceObservations(
        latestWaterChangeEpochDay = latest(CareTaskType.WATER_CHANGE),
        latestAlgaeCleaningEpochDay = latest(CareTaskType.ALGAE_CLEANING),
        latestPlantHealthCheckEpochDay = latest(CareTaskType.PLANT_HEALTH_CHECK),
        latestCo2CheckEpochDay = latest(CareTaskType.CO2_CHECK),
        overdueRelevantTaskCount = overdue
    )
}

private fun SmartLightPlanDraft.toRuntimePhases(
    product: DeviceLightProduct
): List<DeviceLightManagedPlanPhase> = phases.map { phase -> phase.toRuntime(product) }

private fun SmartLightPhaseDraft.toRuntime(product: DeviceLightProduct) =
    DeviceLightManagedPlanPhase(
        validFromEpochDay = validFromEpochDay,
        validUntilEpochDayExclusive = validUntilEpochDayExclusive,
        transitionDays = transitionDays,
        weekdaysMask = weekdaysMask,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        rampDurationMs = rampDurationMs,
        scene = com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene(
            product = product,
            percents = product.sceneFields.associateWith { sceneKey ->
                val channel = scene.channels.keys.single { value -> value.sceneKey == sceneKey }
                scene.channels.getValue(channel)
            }
        )
    )

private fun DeviceLightManagedPlanDocument.matches(
    draft: SmartLightPlanDraft,
    product: DeviceLightProduct
): Boolean = installed &&
    initialStartPercent == draft.initialStartPercent &&
    phases == draft.toRuntimePhases(product)

private fun DeviceRuntimeCommandOutcome<*>.toReadFailure(): SmartSetupReadResult = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> SmartSetupReadResult.NotConnected
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> SmartSetupReadResult.InvalidFirmwareData
    is DeviceRuntimeCommandOutcome.ProtocolError -> SmartSetupReadResult.InvalidFirmwareData
    is DeviceRuntimeCommandOutcome.FirmwareError -> SmartSetupReadResult.InvalidFirmwareData
    is DeviceRuntimeCommandOutcome.Cancelled,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout -> SmartSetupReadResult.Unavailable
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}

private fun SmartSetupReadResult.toApplyFailure(): SmartSetupApplyFailure = when (this) {
    SmartSetupReadResult.NotConnected -> SmartSetupApplyFailure.NOT_CONNECTED
    SmartSetupReadResult.InvalidFirmwareData,
    SmartSetupReadResult.InvalidDevice -> SmartSetupApplyFailure.INVALID_FIRMWARE_DATA
    SmartSetupReadResult.DeviceNotAssigned,
    SmartSetupReadResult.AquariumNotFound,
    SmartSetupReadResult.Unavailable -> SmartSetupApplyFailure.UNAVAILABLE
    is SmartSetupReadResult.Available -> error("An available read has no apply failure.")
}

private fun DeviceRuntimeCommandOutcome.FirmwareError.toSmartSetupFailure(): SmartSetupApplyFailure {
    val reason = runCatching { lightV1Data().reason }.getOrNull()
        ?: return SmartSetupApplyFailure.INVALID_FIRMWARE_DATA
    return when (reason) {
        DeviceLightErrorReason.STALE_REVISION,
        DeviceLightErrorReason.STALE_STORAGE_GENERATION -> SmartSetupApplyFailure.STALE_AUTHORITY
        DeviceLightErrorReason.OUTPUT_TRANSACTION_FAILED,
        DeviceLightErrorReason.STORAGE_COMMIT_FAILED -> SmartSetupApplyFailure.COMMIT_UNCONFIRMED
        else -> SmartSetupApplyFailure.REJECTED
    }
}

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private val RELEVANT_CARE_TASKS = setOf(
    CareTaskType.WATER_CHANGE,
    CareTaskType.ALGAE_CLEANING,
    CareTaskType.PLANT_HEALTH_CHECK,
    CareTaskType.CO2_CHECK,
    CareTaskType.LIGHT_CHECK
)

private const val INVALID_CALIBRATION_REVISION = -1
