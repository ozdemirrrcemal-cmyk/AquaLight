package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.devices.light.smartsetup.SmartLightPlanDraft
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupCalibrationCatalog
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecisionEngine
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupOperations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupReadResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupSnapshot
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.devices.light.smartsetup.SmartSetupDeviceFacts
import com.aqua.aqualight.data.devices.light.smartsetup.toSmartSetupInput
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException

/** Complete in-process Smart Setup authority for installable-debug Light fixtures only. */
@Suppress(
    "ComplexCondition",
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooGenericExceptionCaught"
)
internal class DebugFixtureSmartSetupOperations(
    private val delegate: SmartSetupOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val fixtureAssignments: DebugFixtureTankAssignments,
    private val tankSnapshot: suspend (Long) -> AquariumTankSnapshot?,
    private val careTasks: suspend (Long) -> List<CareTask>,
    private val persistProfile: suspend (Long, Long, AquariumLightingProfile) -> Unit,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SmartSetupOperations {

    private val planLock = Any()
    private val appliedPlans = mutableMapOf<String, DebugAppliedPlan>()

    override suspend fun read(deviceUid: String): SmartSetupReadResult {
        val normalizedDeviceUid = deviceUid.trim()
        val fixture = fixtures.snapshot(normalizedDeviceUid)
            ?: return delegate.read(normalizedDeviceUid)
        return try {
            readFixture(normalizedDeviceUid, fixture)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SmartSetupReadResult.Unavailable
        }
    }

    override suspend fun saveProfile(
        deviceUid: String,
        setupDateEpochDay: Long,
        profile: AquariumLightingProfile
    ): SmartSetupProfileSaveResult {
        val normalizedDeviceUid = deviceUid.trim()
        val fixture = fixtures.snapshot(normalizedDeviceUid)
            ?: return delegate.saveProfile(normalizedDeviceUid, setupDateEpochDay, profile)
        if (fixture.product.family != DeviceFamily.LIGHT) {
            return SmartSetupProfileSaveResult.Failed(SmartSetupProfileSaveFailure.UNAVAILABLE)
        }
        return try {
            val tankId = fixtureAssignments.tankIdFor(normalizedDeviceUid)
                ?: return SmartSetupProfileSaveResult.Failed(
                    SmartSetupProfileSaveFailure.DEVICE_NOT_ASSIGNED
                )
            if (tankSnapshot(tankId) == null) {
                return SmartSetupProfileSaveResult.Failed(
                    SmartSetupProfileSaveFailure.AQUARIUM_NOT_FOUND
                )
            }
            persistProfile(tankId, setupDateEpochDay, profile)
            when (val refreshed = readFixture(normalizedDeviceUid, fixture)) {
                is SmartSetupReadResult.Available -> SmartSetupProfileSaveResult.Saved(
                    refreshed.snapshot
                )
                SmartSetupReadResult.DeviceNotAssigned -> SmartSetupProfileSaveResult.Failed(
                    SmartSetupProfileSaveFailure.DEVICE_NOT_ASSIGNED
                )
                SmartSetupReadResult.AquariumNotFound -> SmartSetupProfileSaveResult.Failed(
                    SmartSetupProfileSaveFailure.AQUARIUM_NOT_FOUND
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
    }

    override suspend fun apply(
        deviceUid: String,
        expectedProfileFingerprint: String
    ): SmartSetupApplyResult {
        val normalizedDeviceUid = deviceUid.trim()
        val fixture = fixtures.snapshot(normalizedDeviceUid)
            ?: return delegate.apply(normalizedDeviceUid, expectedProfileFingerprint)
        if (fixture.product.family != DeviceFamily.LIGHT) {
            return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.UNSUPPORTED)
        }
        return try {
            val read = readFixture(normalizedDeviceUid, fixture)
            val snapshot = (read as? SmartSetupReadResult.Available)?.snapshot
                ?: return SmartSetupApplyResult.Failed(read.toApplyFailure())
            val recommendation = (snapshot.decision as? SmartSetupDecision.Ready)?.recommendation
                ?: return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.NOT_READY)
            if (recommendation.profileFingerprint != expectedProfileFingerprint) {
                return SmartSetupApplyResult.Failed(SmartSetupApplyFailure.PREVIEW_CHANGED)
            }
            val applied = synchronized(planLock) {
                val current = appliedPlans[normalizedDeviceUid]
                DebugAppliedPlan(
                    planId = current?.planId
                        ?: "debug-smart-${recommendation.profileFingerprint.take(20)}",
                    revision = Math.addExact(current?.revision ?: 0L, 1L),
                    storageGeneration = Math.addExact(current?.storageGeneration ?: 0L, 1L),
                    profileFingerprint = recommendation.profileFingerprint,
                    plan = recommendation.plan
                ).also { plan -> appliedPlans[normalizedDeviceUid] = plan }
            }
            SmartSetupApplyResult.Applied(
                planId = applied.planId,
                revision = applied.revision,
                storageGeneration = applied.storageGeneration,
                profileFingerprint = applied.profileFingerprint
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SmartSetupApplyResult.Failed(SmartSetupApplyFailure.UNAVAILABLE)
        }
    }

    private suspend fun readFixture(
        deviceUid: String,
        fixture: DeviceSnapshot
    ): SmartSetupReadResult {
        if (fixture.product.family != DeviceFamily.LIGHT) {
            return SmartSetupReadResult.InvalidDevice
        }
        val tankId = fixtureAssignments.tankIdFor(deviceUid)
            ?: return SmartSetupReadResult.DeviceNotAssigned
        val tank = tankSnapshot(tankId) ?: return SmartSetupReadResult.AquariumNotFound
        val product = DeviceLightProduct.entries.singleOrNull { candidate ->
            candidate.wireValue == fixture.product.productKey
        } ?: return SmartSetupReadResult.InvalidDevice
        val calibration = SmartSetupCalibrationCatalog.reviewedProfileFor(product.wireValue)
        val now = nowMillis()
        val zone = zoneId()
        val input = tank.toSmartSetupInput(
            deviceFacts = SmartSetupDeviceFacts(
                evaluationEpochDay = Instant.ofEpochMilli(now)
                    .atZone(zone)
                    .toLocalDate()
                    .toEpochDay(),
                productKey = product.wireValue,
                calibrationRevision = calibration?.revision,
                channelSceneKeys = product.sceneFields,
                calibrationProfile = calibration
            ),
            tasks = careTasks(tankId),
            zone = zone,
            nowMillis = now
        )
        val decision = SmartSetupDecisionEngine.decide(input)
        val installedPlan = synchronized(planLock) { appliedPlans[deviceUid] }
        val recommendation = (decision as? SmartSetupDecision.Ready)?.recommendation
        return SmartSetupReadResult.Available(
            SmartSetupSnapshot(
                deviceUid = deviceUid,
                tankId = tank.id,
                tankName = tank.name,
                input = input,
                decision = decision,
                installedPlanId = installedPlan?.planId,
                installedPlanRevision = installedPlan?.revision ?: 0L,
                installedPlanFingerprintMatches = installedPlan != null &&
                    recommendation != null &&
                    installedPlan.profileFingerprint == recommendation.profileFingerprint &&
                    installedPlan.plan == recommendation.plan
            )
        )
    }
}

private data class DebugAppliedPlan(
    val planId: String,
    val revision: Long,
    val storageGeneration: Long,
    val profileFingerprint: String,
    val plan: SmartLightPlanDraft
)

private fun SmartSetupReadResult.toApplyFailure(): SmartSetupApplyFailure = when (this) {
    SmartSetupReadResult.NotConnected -> SmartSetupApplyFailure.NOT_CONNECTED
    SmartSetupReadResult.InvalidDevice,
    SmartSetupReadResult.InvalidFirmwareData -> SmartSetupApplyFailure.INVALID_FIRMWARE_DATA
    SmartSetupReadResult.DeviceNotAssigned,
    SmartSetupReadResult.AquariumNotFound,
    SmartSetupReadResult.Unavailable -> SmartSetupApplyFailure.UNAVAILABLE
    is SmartSetupReadResult.Available -> error("An available read has no failure.")
}
