package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingMutationReconciliation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMutationOrigin
import com.aqua.aqualight.application.devices.dosing.applyProgramAgainstOrigin

internal data class DeviceDosingPlanSaveRequest(
    val deviceUid: String,
    val slotId: String,
    val program: DeviceDosingProgram,
    val baseRevision: Long,
    val baseProgram: DeviceDosingProgram?
)

/** Sends the plan intent through the central replay-safe assignment transaction. */
internal suspend fun DeviceDosingChannelOperations.reconcilePlanSave(
    request: DeviceDosingPlanSaveRequest
): DeviceDosingMutationReconciliation = DeviceDosingMutationReconciliation(
    result = applyProgramAgainstOrigin(
        deviceUid = request.deviceUid,
        slotId = request.slotId,
        program = request.program,
        origin = DeviceDosingProgramMutationOrigin(
            revision = request.baseRevision,
            baseProgram = request.baseProgram
        )
    )
)

private data class DosingPlanMutationDomain(
    val program: DeviceDosingProgram?
)

internal fun DeviceDosingProgram?.hasSamePlanMutationDomain(
    other: DeviceDosingProgram?
): Boolean = toPlanMutationDomain() == other.toPlanMutationDomain()

private fun DeviceDosingProgram?.toPlanMutationDomain(): DosingPlanMutationDomain =
    DosingPlanMutationDomain(
        program = this?.copy(missedDoseRecoveryEnabled = false)
    )
