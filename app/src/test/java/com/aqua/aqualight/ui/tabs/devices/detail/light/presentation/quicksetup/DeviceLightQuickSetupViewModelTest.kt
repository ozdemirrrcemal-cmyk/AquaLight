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
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightQuickSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `tank profile automatically produces an applicable program`() {
        val plans = FakeManagedPlanOperations()
        val viewModel = viewModel(plans).apply { bind(DEVICE_UID) }

        val loaded = viewModel.uiState.value
        assertTrue(loaded.contentEnabled)
        assertEquals(TANK, loaded.tank)
        assertNotNull(loaded.plan)
        assertEquals(
            QUICK_SETUP_AUTOMATIC_END_MINUTE * 60_000L,
            loaded.plan?.currentPhase?.draft?.endTimeMs
        )
        assertEquals(expectedPlan().profileFingerprint, loaded.plan?.profileFingerprint)
        assertTrue(loaded.canApply)

        viewModel.toggleDetails()
        assertTrue(viewModel.uiState.value.detailsExpanded)
    }

    @Test
    fun `stale apply authority is reread and forces deliberate recalculation`() {
        val plans = FakeManagedPlanOperations(
            applyResult = DeviceLightManagedPlanMutationResult.Failed(
                DeviceLightManagedPlanFailure.STALE_AUTHORITY
            )
        )
        val viewModel = viewModel(plans).apply {
            bind(DEVICE_UID)
        }

        viewModel.apply()

        val state = viewModel.uiState.value
        assertEquals(2, plans.readCount)
        assertEquals(FIRST_AUTHORITY, plans.appliedAuthority)
        assertEquals(REFRESHED_AUTHORITY, state.managedPlanSnapshot?.authority)
        assertNotNull(state.plan)
        assertTrue(state.contentEnabled)
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
            programEndMinute = QUICK_SETUP_AUTOMATIC_END_MINUTE
        ),
        todayEpochDay = TODAY
    )

    private class FakeManagedPlanOperations(
        private val applyResult: DeviceLightManagedPlanMutationResult =
            DeviceLightManagedPlanMutationResult.Applied(snapshot(FIRST_AUTHORITY))
    ) : DeviceLightManagedPlanOperations {
        var readCount = 0
        var appliedAuthority: DeviceLightManagedPlanAuthority? = null

        override suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult {
            readCount += 1
            val authority = if (readCount == 1) FIRST_AUTHORITY else REFRESHED_AUTHORITY
            return DeviceLightManagedPlanReadResult.Available(snapshot(authority))
        }

        override suspend fun apply(
            deviceUid: String,
            authority: DeviceLightManagedPlanAuthority,
            draft: DeviceLightManagedPlanDraft
        ): DeviceLightManagedPlanMutationResult {
            appliedAuthority = authority
            return applyResult
        }

        override suspend fun delete(
            deviceUid: String,
            authority: DeviceLightManagedPlanAuthority
        ): DeviceLightManagedPlanMutationResult = error("Delete is not part of quick setup.")
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

        fun snapshot(authority: DeviceLightManagedPlanAuthority) = DeviceLightManagedPlanSnapshot(
            deviceUid = DEVICE_UID,
            productDisplayName = "WRGB Pro Elite",
            channels = DeviceLightAutomaticChannel.entries,
            authority = authority,
            installed = authority.installedPlanId != null,
            initialStartPercent = 60,
            phases = emptyList(),
            runtimeState = if (authority.installedPlanId == null) {
                DeviceLightManagedPlanRuntimeState.NOT_INSTALLED
            } else {
                DeviceLightManagedPlanRuntimeState.NOT_SELECTED
            },
            activePhaseIndex = null
        )
    }
}
