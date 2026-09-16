package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanAuthority
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAlgaeLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLight
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightQuickSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `new program requires both observed conditions before calculation`() {
        val plans = FakeManagedPlanOperations()
        val viewModel = viewModel(plans).apply { bind(DEVICE_UID) }

        val loaded = viewModel.uiState.value
        assertTrue(loaded.contentEnabled)
        assertEquals(DeviceLightQuickSetupMode.CREATE, loaded.mode)
        assertEquals(TANK, loaded.tank)
        assertNull(loaded.plan)
        assertFalse(loaded.canApply)

        viewModel.selectCondition(
            DeviceLightQuickSetupConditionSelection.AmbientLight(
                DeviceLightAmbientLight.INDIRECT
            )
        )
        assertNull(viewModel.uiState.value.plan)

        viewModel.selectCondition(
            DeviceLightQuickSetupConditionSelection.AlgaeLevel(DeviceLightAlgaeLevel.MILD)
        )
        val assessed = viewModel.uiState.value
        assertNotNull(assessed.plan)
        assertEquals(
            expectedPlan().profileFingerprint,
            assessed.plan?.profileFingerprint
        )
        assertTrue(assessed.canApply)
    }

    @Test
    fun `successful creation transitions to active and cannot create repeatedly`() {
        val plans = FakeManagedPlanOperations()
        val viewModel = viewModel(plans).apply {
            bind(DEVICE_UID)
            selectCondition(
                DeviceLightQuickSetupConditionSelection.AmbientLight(
                    DeviceLightAmbientLight.INDIRECT
                )
            )
            selectCondition(
                DeviceLightQuickSetupConditionSelection.AlgaeLevel(DeviceLightAlgaeLevel.MILD)
            )
        }

        viewModel.apply()

        val active = viewModel.uiState.value
        assertEquals(DeviceLightQuickSetupMode.ACTIVE, active.mode)
        assertTrue(active.hasInstalledPlan)
        assertFalse(active.canApply)
        assertEquals(1, plans.applyCount)

        viewModel.apply()
        assertEquals(1, plans.applyCount)
    }

    @Test
    fun `installed program opens in active mode without a create action`() {
        val draft = expectedPlan().toManagedPlanDraft()
        val plans = FakeManagedPlanOperations(
            reads = listOf(snapshot(REFRESHED_AUTHORITY, draft))
        )
        val viewModel = viewModel(plans).apply { bind(DEVICE_UID) }

        val state = viewModel.uiState.value
        assertEquals(DeviceLightQuickSetupMode.ACTIVE, state.mode)
        assertTrue(state.hasInstalledPlan)
        assertNull(state.plan)
        assertFalse(state.canApply)

        viewModel.setInstalledPlanEditing(true)
        assertEquals(DeviceLightQuickSetupMode.EDIT, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.canApply)

        viewModel.setInstalledPlanEditing(false)
        assertEquals(DeviceLightQuickSetupMode.ACTIVE, viewModel.uiState.value.mode)
    }

    @Test
    fun `stale apply authority is reread without blindly retrying`() {
        val plans = FakeManagedPlanOperations(
            reads = listOf(
                snapshot(FIRST_AUTHORITY),
                snapshot(REFRESHED_AUTHORITY)
            ),
            applyFailure = DeviceLightManagedPlanFailure.STALE_AUTHORITY
        )
        val viewModel = viewModel(plans).apply {
            bind(DEVICE_UID)
            selectCondition(
                DeviceLightQuickSetupConditionSelection.AmbientLight(
                    DeviceLightAmbientLight.INDIRECT
                )
            )
            selectCondition(
                DeviceLightQuickSetupConditionSelection.AlgaeLevel(DeviceLightAlgaeLevel.MILD)
            )
        }

        viewModel.apply()

        val state = viewModel.uiState.value
        assertEquals(2, plans.readCount)
        assertEquals(1, plans.applyCount)
        assertEquals(FIRST_AUTHORITY, plans.appliedAuthority)
        assertEquals(REFRESHED_AUTHORITY, state.managedPlanSnapshot?.authority)
        assertNotNull(state.plan)
        assertEquals(DeviceLightQuickSetupMode.EDIT, state.mode)
        assertTrue(state.canApply)
        assertFalse(state.applying)
    }

    private fun viewModel(plans: FakeManagedPlanOperations) = DeviceLightQuickSetupViewModel(
        tankOperations = object : DeviceLightQuickSetupTankOperations {
            override suspend fun readForDevice(
                deviceUid: String
            ): DeviceLightQuickSetupTankReadResult =
                DeviceLightQuickSetupTankReadResult.Available(TANK)
        },
        managedPlanOperations = plans,
        todayEpochDay = { TODAY }
    )

    private fun expectedPlan() = DeviceLightQuickSetupCalculator.calculate(
        tank = TANK,
        input = DeviceLightQuickSetupInput(
            plantDemand = TANK.inferredPlantDemand,
            plantDensity = TANK.inferredPlantDensity,
            aquariumHeightCm = TANK.heightCm,
            co2Installed = TANK.inferredCo2Installed,
            activeSoil = TANK.inferredActiveSoil,
            ambientLight = DeviceLightAmbientLight.INDIRECT,
            algaeLevel = DeviceLightAlgaeLevel.MILD,
            programEndMinute = QUICK_SETUP_AUTOMATIC_END_MINUTE
        ),
        todayEpochDay = TODAY
    )

    private class FakeManagedPlanOperations(
        private val reads: List<DeviceLightManagedPlanSnapshot> =
            listOf(snapshot(FIRST_AUTHORITY)),
        private val applyFailure: DeviceLightManagedPlanFailure? = null
    ) : DeviceLightManagedPlanOperations {
        var readCount = 0
        var applyCount = 0
        var appliedAuthority: DeviceLightManagedPlanAuthority? = null

        override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult {
            val snapshot = reads[readCount.coerceAtMost(reads.lastIndex)]
            readCount += 1
            return DeviceLightManagedPlanReadResult.Available(snapshot)
        }

        override suspend fun apply(
            deviceUid: String,
            authority: DeviceLightManagedPlanAuthority,
            draft: DeviceLightManagedPlanDraft
        ): DeviceLightManagedPlanMutationResult {
            applyCount += 1
            appliedAuthority = authority
            return applyFailure?.let { failure ->
                DeviceLightManagedPlanMutationResult.Failed(failure)
            }
                ?: DeviceLightManagedPlanMutationResult.Applied(
                    snapshot(REFRESHED_AUTHORITY, draft)
                )
        }

        override suspend fun delete(
            deviceUid: String,
            authority: DeviceLightManagedPlanAuthority
        ): DeviceLightManagedPlanMutationResult = error("Delete is not part of smart lighting.")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        const val DEVICE_UID = "quick-light"
        const val TODAY = 20_000L

        val FIRST_AUTHORITY = DeviceLightManagedPlanAuthority(
            storageGeneration = 4,
            revision = 8,
            installedPlanId = null
        )
        val REFRESHED_AUTHORITY = DeviceLightManagedPlanAuthority(
            storageGeneration = 5,
            revision = 9,
            installedPlanId = "lp-00000002"
        )
        val TANK = DeviceLightQuickSetupTank(
            tankId = 7,
            tankName = "Living room",
            setupDateEpochDay = TODAY - 10,
            widthCm = 90,
            lengthCm = 45,
            heightCm = 45,
            plantCount = 12,
            inferredPlantDemand = DeviceLightPlantDemand.HIGH,
            inferredPlantDensity = DeviceLightPlantDensity.DENSE,
            inferredCo2Installed = true,
            inferredActiveSoil = true,
            plantedFreshwater = true,
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productDisplayName = "WRGB Pro Elite"
        )

        fun snapshot(
            authority: DeviceLightManagedPlanAuthority,
            draft: DeviceLightManagedPlanDraft? = null
        ) = DeviceLightManagedPlanSnapshot(
            deviceUid = DEVICE_UID,
            productDisplayName = "WRGB Pro Elite",
            channels = DeviceLightAutomaticChannel.entries,
            authority = authority,
            installed = authority.installedPlanId != null,
            initialStartPercent = draft?.initialStartPercent ?: 60,
            phases = draft?.phases.orEmpty(),
            runtimeState = if (authority.installedPlanId == null) {
                DeviceLightManagedPlanRuntimeState.NOT_INSTALLED
            } else {
                DeviceLightManagedPlanRuntimeState.ACTIVE
            },
            activePhaseIndex = if (draft?.phases.isNullOrEmpty()) null else 0
        )
    }
}
