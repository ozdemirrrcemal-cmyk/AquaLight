package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanAuthority
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureLightManagedPlanOperationsTest {

    @Test
    fun fixtureManagedPlanAppliesEnforcesAuthorityAndDeletesInProcess() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixtureUid = fixtures.snapshots
            .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
            .deviceUid
            .value
        val operations = DebugFixtureLightManagedPlanOperations(
            delegate = FailingManagedPlanOperations,
            fixtures = fixtures,
            todayEpochDay = { TODAY_EPOCH_DAY }
        )
        val initial = operations.read(fixtureUid).availableSnapshot()
        val draft = validDraft(initial.channels)

        val appliedResult = operations.apply(fixtureUid, initial.authority, draft)
        assertTrue(appliedResult is DeviceLightManagedPlanMutationResult.Applied)
        val applied = (appliedResult as DeviceLightManagedPlanMutationResult.Applied).snapshot
        assertTrue(applied.installed)
        assertEquals("lp-00000001", applied.authority.installedPlanId)
        assertEquals(initial.authority.revision + 1L, applied.authority.revision)
        assertEquals(
            initial.authority.storageGeneration + 1L,
            applied.authority.storageGeneration
        )
        assertEquals(DeviceLightManagedPlanRuntimeState.ACTIVE, applied.runtimeState)
        assertEquals(0, applied.activePhaseIndex)
        assertEquals(draft.phases, applied.phases)

        val stale = operations.apply(fixtureUid, initial.authority, draft)
        assertEquals(
            DeviceLightManagedPlanFailure.STALE_AUTHORITY,
            (stale as DeviceLightManagedPlanMutationResult.Failed).failure
        )

        assertEquals(
            DeviceLightManagedPlanMutationResult.Deleted,
            operations.delete(fixtureUid, applied.authority)
        )
        val deleted = operations.read(fixtureUid).availableSnapshot()
        assertFalse(deleted.installed)
        assertEquals(null, deleted.authority.installedPlanId)
        assertEquals(DeviceLightManagedPlanRuntimeState.NOT_INSTALLED, deleted.runtimeState)
        assertTrue(deleted.phases.isEmpty())
    }

    private fun validDraft(
        channels: List<DeviceLightAutomaticChannel>
    ): DeviceLightManagedPlanDraft {
        val scene = DeviceLightAutomaticScene(channels.associateWith { CHANNEL_PERCENT })
        return DeviceLightManagedPlanDraft(
            initialStartPercent = INITIAL_START_PERCENT,
            phases = listOf(
                DeviceLightManagedPlanPhaseDraft(
                    validFromEpochDay = FIRST_PHASE_START,
                    validUntilEpochDayExclusive = SECOND_PHASE_START,
                    transitionDays = TRANSITION_DAYS,
                    weekdaysMask = EVERY_DAY_MASK,
                    startTimeMs = START_TIME_MS,
                    endTimeMs = END_TIME_MS,
                    rampDurationMs = RAMP_DURATION_MS,
                    scene = scene
                ),
                DeviceLightManagedPlanPhaseDraft(
                    validFromEpochDay = SECOND_PHASE_START,
                    validUntilEpochDayExclusive = null,
                    transitionDays = TRANSITION_DAYS,
                    weekdaysMask = EVERY_DAY_MASK,
                    startTimeMs = START_TIME_MS,
                    endTimeMs = END_TIME_MS,
                    rampDurationMs = RAMP_DURATION_MS,
                    scene = scene
                )
            )
        )
    }

    private companion object {
        const val TODAY_EPOCH_DAY = 20_000L
        const val FIRST_PHASE_START = 19_990L
        const val SECOND_PHASE_START = 20_011L
        const val TRANSITION_DAYS = 7
        const val EVERY_DAY_MASK = 127
        const val START_TIME_MS = 57_600_000L
        const val END_TIME_MS = 79_200_000L
        const val RAMP_DURATION_MS = 3_600_000L
        const val INITIAL_START_PERCENT = 60
        const val CHANNEL_PERCENT = 50
    }
}

private object FailingManagedPlanOperations : DeviceLightManagedPlanOperations {
    override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult = fail(deviceUid)

    override suspend fun apply(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority,
        draft: DeviceLightManagedPlanDraft
    ): DeviceLightManagedPlanMutationResult = fail(deviceUid)

    override suspend fun delete(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority
    ): DeviceLightManagedPlanMutationResult = fail(deviceUid)

    private fun fail(deviceUid: String): Nothing =
        error("Fixture managed plan must not call production delegate: $deviceUid")
}

private fun DeviceLightManagedPlanReadResult.availableSnapshot() =
    (this as DeviceLightManagedPlanReadResult.Available).snapshot
