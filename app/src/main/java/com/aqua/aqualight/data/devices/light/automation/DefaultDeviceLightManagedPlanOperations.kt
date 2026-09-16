package com.aqua.aqualight.data.devices.light.automation

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanAuthority
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanDeletePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanPhase
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManagedPlanRuntimeState as RuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.applyManagedPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.deleteManagedPlan
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data
import com.aqua.aqualight.data.devices.runtime.modules.light.requestManagedPlan
import java.util.concurrent.CancellationException

internal class DefaultDeviceLightManagedPlanOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightManagedPlanOperations {

    override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = uid?.let { runtime?.currentStatus(it) }
        return when {
            uid == null -> readFailure(DeviceLightManagedPlanFailure.INVALID_DATA)
            runtime == null -> readFailure(DeviceLightManagedPlanFailure.UNAVAILABLE)
            status == null -> readFailure(DeviceLightManagedPlanFailure.NOT_CONNECTED)
            else -> try {
                when (val outcome = runtime.requestManagedPlan(uid)) {
                    is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanReadResult.Available(
                        outcome.value.toApplicationSnapshot(uid, status, productName(uid, status))
                    )
                    else -> readFailure(outcome.toManagedPlanFailure())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                readFailure(DeviceLightManagedPlanFailure.INVALID_DATA)
            }
        }
    }

    override suspend fun apply(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority,
        draft: DeviceLightManagedPlanDraft
    ): DeviceLightManagedPlanMutationResult = withRuntime(deviceUid) { uid, runtime, status ->
        val payload = DeviceLightManagedPlanApplyPayload(
            expectedRevision = authority.revision,
            expectedStorageGeneration = authority.storageGeneration,
            planId = authority.installedPlanId,
            initialStartPercent = draft.initialStartPercent,
            phases = draft.phases.map { phase -> phase.toRuntimePhase(status) }
        )
        when (val outcome = runtime.applyManagedPlan(uid, payload)) {
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanMutationResult.Applied(
                outcome.value.toApplicationSnapshot(uid, status, productName(uid, status))
            )
            else -> DeviceLightManagedPlanMutationResult.Failed(outcome.toManagedPlanFailure())
        }
    }

    override suspend fun delete(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority
    ): DeviceLightManagedPlanMutationResult {
        val planId = authority.installedPlanId
            ?: return DeviceLightManagedPlanMutationResult.Failed(
                DeviceLightManagedPlanFailure.NOT_FOUND
            )
        return withRuntime(deviceUid) { uid, runtime, _ ->
            when (
                val outcome = runtime.deleteManagedPlan(
                    uid,
                    DeviceLightManagedPlanDeletePayload(
                        expectedRevision = authority.revision,
                        expectedStorageGeneration = authority.storageGeneration,
                        planId = planId
                    )
                )
            ) {
                is DeviceRuntimeCommandOutcome.Success -> DeviceLightManagedPlanMutationResult.Deleted
                else -> DeviceLightManagedPlanMutationResult.Failed(outcome.toManagedPlanFailure())
            }
        }
    }

    private suspend fun withRuntime(
        deviceUid: String,
        command: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository,
            DeviceLightStatus
        ) -> DeviceLightManagedPlanMutationResult
    ): DeviceLightManagedPlanMutationResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = uid?.let { runtime?.currentStatus(it) }
        return when {
            uid == null -> mutationFailure(DeviceLightManagedPlanFailure.INVALID_DATA)
            runtime == null -> mutationFailure(DeviceLightManagedPlanFailure.UNAVAILABLE)
            status == null -> mutationFailure(DeviceLightManagedPlanFailure.NOT_CONNECTED)
            else -> try {
                command(uid, runtime, status)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                mutationFailure(DeviceLightManagedPlanFailure.INVALID_DATA)
            } catch (_: Exception) {
                mutationFailure(DeviceLightManagedPlanFailure.UNAVAILABLE)
            }
        }
    }

    private fun productName(uid: DeviceUid, status: DeviceLightStatus): String =
        devicesRepository.currentDevice(uid)?.product?.displayName
            ?.takeIf(String::isNotBlank)
            ?: status.product.wireValue
}

private fun DeviceLightManagedPlanPhaseDraft.toRuntimePhase(
    status: DeviceLightStatus
): DeviceLightManagedPlanPhase = DeviceLightManagedPlanPhase(
    validFromEpochDay = validFromEpochDay,
    validUntilEpochDayExclusive = validUntilEpochDayExclusive,
    transitionDays = transitionDays,
    weekdaysMask = weekdaysMask,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    rampDurationMs = rampDurationMs,
    scene = scene.toRuntimeScene(status)
)

private fun DeviceLightAutomaticScene.toRuntimeScene(status: DeviceLightStatus): DeviceLightScene =
    DeviceLightScene(
        product = status.product,
        percents = status.product.sceneFields.associateWith { sceneField ->
            channels.getValue(sceneField.toAutomaticChannel())
        }
    )

private fun DeviceLightManagedPlan.toApplicationSnapshot(
    uid: DeviceUid,
    status: DeviceLightStatus,
    productDisplayName: String
): DeviceLightManagedPlanSnapshot {
    val channels = status.product.sceneFields.map(String::toAutomaticChannel)
    return DeviceLightManagedPlanSnapshot(
        deviceUid = uid.value,
        productDisplayName = productDisplayName,
        channels = channels,
        authority = DeviceLightManagedPlanAuthority(
            storageGeneration = storageGeneration,
            revision = revision,
            installedPlanId = planId
        ),
        installed = installed,
        initialStartPercent = initialStartPercent,
        phases = phases.map { phase ->
            DeviceLightManagedPlanPhaseDraft(
                validFromEpochDay = phase.validFromEpochDay,
                validUntilEpochDayExclusive = phase.validUntilEpochDayExclusive,
                transitionDays = phase.transitionDays,
                weekdaysMask = phase.weekdaysMask,
                startTimeMs = phase.startTimeMs,
                endTimeMs = phase.endTimeMs,
                rampDurationMs = phase.rampDurationMs,
                scene = DeviceLightAutomaticScene(
                    channels.associateWith { channel ->
                        phase.scene.percents.getValue(channel.sceneKey)
                    }
                )
            )
        },
        runtimeState = runtime.state.toApplicationState(),
        activePhaseIndex = runtime.activePhaseIndex
    )
}

private fun RuntimeState.toApplicationState(): DeviceLightManagedPlanRuntimeState = when (this) {
    RuntimeState.NOT_INSTALLED -> DeviceLightManagedPlanRuntimeState.NOT_INSTALLED
    RuntimeState.NOT_SELECTED -> DeviceLightManagedPlanRuntimeState.NOT_SELECTED
    RuntimeState.RTC_BLOCKED -> DeviceLightManagedPlanRuntimeState.RTC_BLOCKED
    RuntimeState.BEFORE_PLAN -> DeviceLightManagedPlanRuntimeState.BEFORE_PLAN
    RuntimeState.ACTIVE -> DeviceLightManagedPlanRuntimeState.ACTIVE
}

private fun String.toAutomaticChannel(): DeviceLightAutomaticChannel =
    requireNotNull(DeviceLightAutomaticChannel.entries.singleOrNull { it.sceneKey == this })

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private fun readFailure(failure: DeviceLightManagedPlanFailure) =
    DeviceLightManagedPlanReadResult.Failed(failure)

private fun mutationFailure(failure: DeviceLightManagedPlanFailure) =
    DeviceLightManagedPlanMutationResult.Failed(failure)

private fun DeviceRuntimeCommandOutcome<*>.toManagedPlanFailure(): DeviceLightManagedPlanFailure =
    when (this) {
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightManagedPlanFailure.NOT_CONNECTED
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightManagedPlanFailure.UNSUPPORTED
        is DeviceRuntimeCommandOutcome.FirmwareError -> {
            when (runCatching { lightV1Data().reason }.getOrNull()) {
                DeviceLightErrorReason.STALE_REVISION,
                DeviceLightErrorReason.STALE_STORAGE_GENERATION ->
                    DeviceLightManagedPlanFailure.STALE_AUTHORITY
                DeviceLightErrorReason.AUTO_PLAN_NOT_FOUND -> DeviceLightManagedPlanFailure.NOT_FOUND
                DeviceLightErrorReason.AUTO_PLAN_SELECTED -> DeviceLightManagedPlanFailure.SELECTED
                else -> DeviceLightManagedPlanFailure.REJECTED
            }
        }
        is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightManagedPlanFailure.INVALID_DATA
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightManagedPlanFailure.UNAVAILABLE
        is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
    }
