package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedAutoPlanOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanApplyResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPhase
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendation
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedAutoPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedAutoPlanApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedAutoPlanDeletePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanPhase
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.applyManagedAutoPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.currentManagedAutoPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.deleteManagedAutoPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data
import com.aqua.aqualight.data.devices.runtime.modules.light.requestManagedAutoPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceLightManagedAutoPlanOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightManagedAutoPlanOperations {

    @Suppress("ReturnCount")
    override fun observe(deviceUid: String): Flow<DeviceLightManagedPlanSnapshot?> {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid) ?: return flowOf(null)
        val runtime = devicesRepository.runtimeModules()?.light ?: return flowOf(null)
        return runtime.stateRevision.map {
            runtime.currentManagedAutoPlan(
                uid,
                DeviceLightManagedPlanReadAuthority.PRESENTATION
            )?.let(DeviceLightManagedPlanMapper::toApplicationSnapshot)
        }
    }

    override fun current(deviceUid: String): DeviceLightManagedPlanSnapshot? {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid) ?: return null
        val runtime = devicesRepository.runtimeModules()?.light ?: return null
        return runtime.currentManagedAutoPlan(
            uid,
            DeviceLightManagedPlanReadAuthority.AUTHORITATIVE
        )?.let(DeviceLightManagedPlanMapper::toApplicationSnapshot)
    }

    override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid)
            ?: return DeviceLightManagedPlanReadResult.Failed(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return DeviceLightManagedPlanReadResult.Failed(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
        return when (val outcome = runtime.requestManagedAutoPlan(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanReadResult.Available(
                DeviceLightManagedPlanMapper.toApplicationSnapshot(outcome.value)
            )
            else -> DeviceLightManagedPlanReadResult.Failed(
                DeviceLightManagedPlanFailureMapper.toBlockReason(outcome)
            )
        }
    }

    override suspend fun apply(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        existingPlanId: String?,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightManagedPlanApplyResult {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid)
            ?: return DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
        val product = runtime.currentStatus(uid)?.product
            ?: return DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
        val phases = recommendation.phases.map { phase ->
            DeviceLightManagedPlanMapper.toRuntimePhase(phase, product)
                ?: return DeviceLightManagedPlanApplyResult.Failed(
                    DeviceLightQuickSetupBlockReason.INVALID_INPUT
                )
        }
        val outcome = runtime.applyManagedAutoPlan(
            uid,
            DeviceLightManagedAutoPlanApplyPayload(
                expectedRevision = expectedRevision,
                expectedStorageGeneration = expectedStorageGeneration,
                planId = existingPlanId,
                initialStartPercent = recommendation.initialStartPercent,
                phases = phases
            )
        )
        return mapApplyOutcome(deviceUid, outcome)
    }

    override suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        planId: String
    ): DeviceLightManagedPlanApplyResult {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid)
            ?: return DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
        val outcome = runtime.deleteManagedAutoPlan(
            uid,
            DeviceLightManagedAutoPlanDeletePayload(
                expectedRevision = expectedRevision,
                expectedStorageGeneration = expectedStorageGeneration,
                planId = planId
            )
        )
        if (outcome !is DeviceRuntimeCommandOutcome.Success) {
            return mapFailureOrStale(deviceUid, outcome)
        }
        return when (val readback = runtime.requestManagedAutoPlan(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanApplyResult.Applied(
                DeviceLightManagedPlanMapper.toApplicationSnapshot(readback.value)
            )
            else -> DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightManagedPlanFailureMapper.toBlockReason(readback)
            )
        }
    }

    private fun mapApplyOutcome(
        deviceUid: String,
        outcome: DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan>
    ): DeviceLightManagedPlanApplyResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanApplyResult.Applied(
            DeviceLightManagedPlanMapper.toApplicationSnapshot(outcome.value)
        )
        else -> mapFailureOrStale(deviceUid, outcome)
    }

    private fun mapFailureOrStale(
        deviceUid: String,
        outcome: DeviceRuntimeCommandOutcome<*>
    ): DeviceLightManagedPlanApplyResult {
        return if (DeviceLightManagedPlanFailureMapper.isStale(outcome)) {
            DeviceLightManagedPlanApplyResult.Stale(current(deviceUid))
        } else {
            DeviceLightManagedPlanApplyResult.Failed(
                DeviceLightManagedPlanFailureMapper.toBlockReason(outcome)
            )
        }
    }
}

private object DeviceLightManagedPlanMapper {
    fun toUidOrNull(value: String): DeviceUid? =
        value.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

    fun toRuntimePhase(
        phase: DeviceLightQuickSetupPhase,
        product: DeviceLightProduct
    ): DeviceLightManagedPlanPhase? {
        val scene = toRuntimeScene(phase, product) ?: return null
        return DeviceLightManagedPlanPhase(
            validFromEpochDay = phase.validFromEpochDay,
            validUntilEpochDayExclusive = phase.validUntilEpochDayExclusive,
            transitionDays = phase.transitionDays,
            weekdaysMask = phase.weekdaysMask,
            startTimeMs = phase.startMinuteOfDay * MILLIS_PER_MINUTE,
            endTimeMs = phase.endMinuteOfDay * MILLIS_PER_MINUTE,
            rampDurationMs = phase.rampMinutes * MILLIS_PER_MINUTE,
            scene = scene
        )
    }

    fun toApplicationSnapshot(plan: DeviceLightManagedAutoPlan) = DeviceLightManagedPlanSnapshot(
        storageGeneration = plan.storageGeneration,
        revision = plan.revision,
        installed = plan.installed,
        planId = plan.planId,
        initialStartPercent = plan.initialStartPercent,
        runtimeState = DeviceLightManagedPlanRuntimeState.valueOf(plan.runtime.state.name),
        activePhaseIndex = plan.runtime.activePhaseIndex,
        transitionPermille = plan.runtime.transitionPermille,
        nextTransitionEpochDay = plan.runtime.nextTransitionEpochDay
    )

    private fun toRuntimeScene(
        phase: DeviceLightQuickSetupPhase,
        product: DeviceLightProduct
    ): DeviceLightScene? {
        val expectedKeys = product.sceneFields.map(::logicalKeyForPercentField).toSet()
        if (phase.channelScenePercent.keys != expectedKeys) return null
        return DeviceLightScene(
            product = product,
            percents = product.sceneFields.associateWith { field ->
                phase.channelScenePercent[logicalKeyForPercentField(field)] ?: return null
            }
        )
    }

    private fun logicalKeyForPercentField(field: String): String = when (field) {
        "redPercent" -> "red"
        "greenPercent" -> "green"
        "bluePercent" -> "blue"
        "whitePercent" -> "white"
        else -> error("Unsupported Light scene field: $field")
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}

private object DeviceLightManagedPlanFailureMapper {
    fun isStale(outcome: DeviceRuntimeCommandOutcome<*>): Boolean {
        val firmware = outcome as? DeviceRuntimeCommandOutcome.FirmwareError ?: return false
        val reason = runCatching { firmware.lightV1Data().reason }.getOrNull()
        return reason == DeviceLightErrorReason.Known.STALE_REVISION ||
            reason == DeviceLightErrorReason.Known.STALE_STORAGE_GENERATION
    }

    fun toBlockReason(outcome: DeviceRuntimeCommandOutcome<*>): DeviceLightQuickSetupBlockReason =
        when (outcome) {
            is DeviceRuntimeCommandOutcome.FirmwareError -> when (
                runCatching { outcome.lightV1Data().reason }.getOrNull()
            ) {
                DeviceLightErrorReason.Known.RTC_NOT_READY ->
                    DeviceLightQuickSetupBlockReason.RTC_NOT_READY
                DeviceLightErrorReason.Known.STALE_REVISION,
                DeviceLightErrorReason.Known.STALE_STORAGE_GENERATION ->
                    DeviceLightQuickSetupBlockReason.STALE_CONTEXT
                else -> DeviceLightQuickSetupBlockReason.DEVICE_WRITE_FAILED
            }
            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated,
            is DeviceRuntimeCommandOutcome.SendFailed,
            is DeviceRuntimeCommandOutcome.Timeout,
            is DeviceRuntimeCommandOutcome.Cancelled ->
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                DeviceLightQuickSetupBlockReason.UNSUPPORTED_PRODUCT
            is DeviceRuntimeCommandOutcome.ProtocolError ->
                DeviceLightQuickSetupBlockReason.MALFORMED_FIRMWARE_STATE
            is DeviceRuntimeCommandOutcome.Success -> error("Successful outcome has no failure.")
        }
}
