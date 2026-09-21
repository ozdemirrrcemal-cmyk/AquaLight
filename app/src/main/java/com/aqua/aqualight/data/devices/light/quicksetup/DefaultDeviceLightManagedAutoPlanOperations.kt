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
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
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

    override fun observe(deviceUid: String): Flow<DeviceLightManagedPlanSnapshot?> =
        when (val access = access(deviceUid)) {
            is ManagedPlanAccess.Ready -> access.runtime.stateRevision.map {
                access.runtime.currentManagedAutoPlan(
                    access.uid,
                    DeviceLightManagedPlanReadAuthority.PRESENTATION
                )?.let(DeviceLightManagedPlanMapper::toApplicationSnapshot)
            }
            ManagedPlanAccess.InvalidDeviceUid,
            ManagedPlanAccess.RuntimeUnavailable -> flowOf(null)
        }

    override fun current(deviceUid: String): DeviceLightManagedPlanSnapshot? =
        when (val access = access(deviceUid)) {
            is ManagedPlanAccess.Ready -> access.runtime.currentManagedAutoPlan(
                access.uid,
                DeviceLightManagedPlanReadAuthority.AUTHORITATIVE
            )?.let(DeviceLightManagedPlanMapper::toApplicationSnapshot)
            ManagedPlanAccess.InvalidDeviceUid,
            ManagedPlanAccess.RuntimeUnavailable -> null
        }

    override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult =
        when (val access = access(deviceUid)) {
            ManagedPlanAccess.InvalidDeviceUid -> DeviceLightManagedPlanReadResult.Failed(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
            ManagedPlanAccess.RuntimeUnavailable -> DeviceLightManagedPlanReadResult.Failed(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
            is ManagedPlanAccess.Ready -> mapReadOutcome(
                access.runtime.requestManagedAutoPlan(access.uid)
            )
        }

    override suspend fun apply(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        existingPlanId: String?,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightManagedPlanApplyResult =
        when (val access = access(deviceUid)) {
            ManagedPlanAccess.InvalidDeviceUid -> failedApply(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
            ManagedPlanAccess.RuntimeUnavailable -> failedApply(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
            is ManagedPlanAccess.Ready -> applyReady(
                access = access,
                expectedRevision = expectedRevision,
                expectedStorageGeneration = expectedStorageGeneration,
                existingPlanId = existingPlanId,
                recommendation = recommendation
            )
        }

    override suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        planId: String
    ): DeviceLightManagedPlanApplyResult =
        when (val access = access(deviceUid)) {
            ManagedPlanAccess.InvalidDeviceUid -> failedApply(
                DeviceLightQuickSetupBlockReason.INVALID_DEVICE_UID
            )
            ManagedPlanAccess.RuntimeUnavailable -> failedApply(
                DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE
            )
            is ManagedPlanAccess.Ready -> deleteReady(
                deviceUid = deviceUid,
                access = access,
                expectedRevision = expectedRevision,
                expectedStorageGeneration = expectedStorageGeneration,
                planId = planId
            )
        }

    private suspend fun applyReady(
        access: ManagedPlanAccess.Ready,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        existingPlanId: String?,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightManagedPlanApplyResult {
        val product = access.runtime.currentStatus(access.uid)?.product
        val phases = product?.let { exactProduct ->
            recommendation.phases.mapNotNull { phase ->
                DeviceLightManagedPlanMapper.toRuntimePhase(phase, exactProduct)
            }
        }
        return when {
            product == null -> failedApply(DeviceLightQuickSetupBlockReason.CONNECTION_UNAVAILABLE)
            phases == null || phases.size != recommendation.phases.size ->
                failedApply(DeviceLightQuickSetupBlockReason.INVALID_INPUT)
            else -> mapApplyOutcome(
                access.uid.value,
                access.runtime.applyManagedAutoPlan(
                    access.uid,
                    DeviceLightManagedAutoPlanApplyPayload(
                        expectedRevision = expectedRevision,
                        expectedStorageGeneration = expectedStorageGeneration,
                        planId = existingPlanId,
                        initialStartPercent = recommendation.initialStartPercent,
                        phases = phases
                    )
                )
            )
        }
    }

    private suspend fun deleteReady(
        deviceUid: String,
        access: ManagedPlanAccess.Ready,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        planId: String
    ): DeviceLightManagedPlanApplyResult {
        val outcome = access.runtime.deleteManagedAutoPlan(
            access.uid,
            DeviceLightManagedAutoPlanDeletePayload(
                expectedRevision = expectedRevision,
                expectedStorageGeneration = expectedStorageGeneration,
                planId = planId
            )
        )
        return when (outcome) {
            is DeviceRuntimeCommandOutcome.Success -> mapDeleteReadback(
                access.runtime.requestManagedAutoPlan(access.uid)
            )
            else -> mapFailureOrStale(deviceUid, outcome)
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
    ): DeviceLightManagedPlanApplyResult =
        if (DeviceLightManagedPlanFailureMapper.isStale(outcome)) {
            DeviceLightManagedPlanApplyResult.Stale(current(deviceUid))
        } else {
            failedApply(DeviceLightManagedPlanFailureMapper.toBlockReason(outcome))
        }

    private fun access(deviceUid: String): ManagedPlanAccess {
        val uid = DeviceLightManagedPlanMapper.toUidOrNull(deviceUid)
        val runtime = devicesRepository.runtimeModules()?.light
        return when {
            uid == null -> ManagedPlanAccess.InvalidDeviceUid
            runtime == null -> ManagedPlanAccess.RuntimeUnavailable
            else -> ManagedPlanAccess.Ready(uid, runtime)
        }
    }

}

private fun mapReadOutcome(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan>
): DeviceLightManagedPlanReadResult = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanReadResult.Available(
        DeviceLightManagedPlanMapper.toApplicationSnapshot(outcome.value)
    )
    else -> DeviceLightManagedPlanReadResult.Failed(
        DeviceLightManagedPlanFailureMapper.toBlockReason(outcome)
    )
}

private fun mapDeleteReadback(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan>
): DeviceLightManagedPlanApplyResult = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanApplyResult.Applied(
        DeviceLightManagedPlanMapper.toApplicationSnapshot(outcome.value)
    )
    else -> failedApply(DeviceLightManagedPlanFailureMapper.toBlockReason(outcome))
}

private fun failedApply(
    reason: DeviceLightQuickSetupBlockReason
) = DeviceLightManagedPlanApplyResult.Failed(reason)

private sealed interface ManagedPlanAccess {
    data object InvalidDeviceUid : ManagedPlanAccess
    data object RuntimeUnavailable : ManagedPlanAccess
    data class Ready(
        val uid: DeviceUid,
        val runtime: DeviceLightRuntimeRepository
    ) : ManagedPlanAccess
}

private object DeviceLightManagedPlanMapper {
    fun toUidOrNull(value: String): DeviceUid? =
        value.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

    fun toRuntimePhase(
        phase: DeviceLightQuickSetupPhase,
        product: DeviceLightProduct
    ): DeviceLightManagedPlanPhase? = toRuntimeScene(phase, product)?.let { scene ->
        DeviceLightManagedPlanPhase(
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
        return if (phase.channelScenePercent.keys != expectedKeys) {
            null
        } else {
            DeviceLightScene(
                product = product,
                percents = product.sceneFields.associateWith { field ->
                    checkNotNull(phase.channelScenePercent[logicalKeyForPercentField(field)])
                }
            )
        }
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
        val firmware = outcome as? DeviceRuntimeCommandOutcome.FirmwareError
        val reason = firmware?.let { error ->
            runCatching { error.lightV1Data().reason }.getOrNull()
        }
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
