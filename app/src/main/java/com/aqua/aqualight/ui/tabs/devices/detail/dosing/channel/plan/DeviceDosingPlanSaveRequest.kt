package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingMutationReconciliation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRevisionedIntent
import com.aqua.aqualight.application.devices.dosing.applyProgramAgainstBaseRevision
import com.aqua.aqualight.application.devices.dosing.applyRevisionedIntentWithReconciliation

internal data class DeviceDosingPlanSaveRequest(
    val deviceUid: String,
    val slotId: String,
    val state: DeviceDosingPlanEditorState,
    val program: DeviceDosingProgram,
    val baseRevision: Long
)

/** Reconciles only the plan-owned program domain; the detail switch remains independent. */
internal suspend fun DeviceDosingChannelOperations.reconcilePlanSave(
    request: DeviceDosingPlanSaveRequest
): DeviceDosingMutationReconciliation {
    val applyAtRevision: suspend (Long) -> DeviceDosingChannelOperationResult = { revision ->
        applyProgramAgainstBaseRevision(
            deviceUid = request.deviceUid,
            slotId = request.slotId,
            program = request.program,
            baseRevision = revision
        )
    }
    return if (request.state.baseProgramKnown) {
        applyRevisionedIntentWithReconciliation(
            intent = DeviceDosingRevisionedIntent(
                deviceUid = request.deviceUid,
                slotId = request.slotId,
                baseRevision = request.baseRevision,
                baseDomain = request.state.baseProgram.toPlanMutationDomain(),
                desiredDomain = request.program.toPlanMutationDomain()
            ),
            domainFrom = { snapshot -> snapshot.program.toPlanMutationDomain() },
            applyAtRevision = applyAtRevision
        )
    } else {
        DeviceDosingMutationReconciliation(applyAtRevision(request.baseRevision))
    }
}

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
