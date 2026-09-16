package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanAuthority
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanFailure
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanMutationResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPersistenceResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecordedOutcome
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
    fun `new assessment rechecks only dynamic CO2 and surface conditions`() {
        val viewModel = fixture().viewModel.apply { bind(DEVICE_UID) }

        val loaded = viewModel.uiState.value
        assertTrue(loaded.contentEnabled)
        assertEquals(DeviceLightQuickSetupMode.CREATE, loaded.mode)
        assertEquals(2, loaded.missingInputCount)
        assertFalse(loaded.hasSetupQuestions)
        assertFalse(loaded.requestsPlantDemand)
        assertFalse(loaded.requestsPlantCoverage)
        assertTrue(loaded.requestsCo2Readiness)
        assertFalse(loaded.requestsDaylight)
        assertTrue(loaded.requestsSurfaceObservation)
        assertFalse(loaded.requestsShelter)
        assertNull(loaded.co2Readiness)
        assertNull(loaded.surfaceGrowth)
        assertNull(loaded.plan)

        viewModel.selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
        viewModel.selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)

        val assessed = viewModel.uiState.value
        assertNotNull(assessed.plan)
        assertTrue(assessed.canApply)
        assertEquals(20 * 60, assessed.programEndMinute)
    }

    @Test
    fun `tank without plants is blocked without asking irrelevant assessment questions`() {
        val state = DeviceLightQuickSetupUiState(
            tank = TANK.copy(
                hasPlants = false,
                plantedFreshwater = false,
                plantDemand = DeviceLightPlantDemand.UNKNOWN,
                plantCoverage = AquariumPlantCoverage.UNKNOWN
            ),
            todayEpochDay = TODAY,
            contentEnabled = true
        )

        assertTrue(state.profileBlocked)
        assertTrue(state.hasBlockingWarning)
        assertFalse(state.canApply)
    }

    @Test
    fun `unreviewed plant answer cannot reduce an exact catalog demand`() {
        val mixedTank = TANK.copy(
            plantDemand = DeviceLightPlantDemand.UNKNOWN,
            reviewedPlantDemandFloor = DeviceLightPlantDemand.MEDIUM,
            plantCatalogIds = setOf(
                "plant:micranthemum_tweediei_monte_carlo",
                "plant:unreviewed"
            )
        )
        val viewModel = fixture(tank = mixedTank).viewModel.apply { bind(DEVICE_UID) }

        assertTrue(viewModel.uiState.value.requestsPlantDemand)

        viewModel.selectPlantDemand(DeviceLightPlantDemand.LOW)

        assertEquals(DeviceLightPlantDemand.MEDIUM, viewModel.uiState.value.plantDemand)
    }

    @Test
    fun `recommendation is persisted before firmware and outcome is recorded after apply`() {
        val events = mutableListOf<String>()
        val fixture = fixture(events = events)
        fixture.viewModel.apply {
            bind(DEVICE_UID)
            selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
            selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)
            apply()
        }

        assertEquals(listOf("prepare", "firmware", "outcome:true"), events)
        assertEquals(DeviceLightQuickSetupMode.ACTIVE, fixture.viewModel.uiState.value.mode)
        assertFalse(fixture.viewModel.uiState.value.canApply)
        assertEquals(1, fixture.plans.applyCount)
    }

    @Test
    fun `persistence failure prevents firmware mutation`() {
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            prepareResult = DeviceLightQuickSetupPersistenceResult.Failed()
        )
        fixture.viewModel.apply {
            bind(DEVICE_UID)
            selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
            selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)
            apply()
        }

        assertEquals(listOf("prepare"), events)
        assertEquals(0, fixture.plans.applyCount)
        assertFalse(fixture.viewModel.uiState.value.applying)
    }

    @Test
    fun `applied outcome audit is retried before reporting an audit error`() {
        val events = mutableListOf<String>()
        val fixture = fixture(events = events, outcomeFailuresBeforeSuccess = 2)
        fixture.viewModel.apply {
            bind(DEVICE_UID)
            selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
            selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)
            apply()
        }

        assertEquals(
            listOf("prepare", "firmware", "outcome:true", "outcome:true", "outcome:true"),
            events
        )
        assertEquals(DeviceLightQuickSetupMode.ACTIVE, fixture.viewModel.uiState.value.mode)
    }

    @Test
    fun `installed program edit rechecks dynamic conditions and exposes setup values`() {
        val draft = expectedPlan().toManagedPlanDraft()
        val fixture = fixture(
            reads = listOf(snapshot(REFRESHED_AUTHORITY, draft))
        )
        fixture.viewModel.bind(DEVICE_UID)

        assertEquals(DeviceLightQuickSetupMode.ACTIVE, fixture.viewModel.uiState.value.mode)
        assertNull(fixture.viewModel.uiState.value.plan)
        assertFalse(fixture.viewModel.uiState.value.canApply)

        fixture.viewModel.setInstalledPlanEditing(true)

        val editing = fixture.viewModel.uiState.value
        assertEquals(DeviceLightQuickSetupMode.EDIT, editing.mode)
        assertTrue(editing.hasSetupQuestions)
        assertTrue(editing.requestsSurfaceObservation)
        assertTrue(editing.requestsCo2Readiness)
        assertTrue(editing.requestsDaylight)
        assertTrue(editing.requestsWaterDepth)
        assertTrue(editing.requestsFixtureHeight)
        assertTrue(editing.requestsProgramEnd)
        assertEquals(2, editing.missingInputCount)
        assertNull(editing.co2Readiness)
        assertNull(editing.surfaceGrowth)
        assertFalse(editing.canApply)
    }

    @Test
    fun `stale authority is reread once and never blindly retried`() {
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            reads = listOf(snapshot(FIRST_AUTHORITY), snapshot(REFRESHED_AUTHORITY)),
            applyFailure = DeviceLightManagedPlanFailure.STALE_AUTHORITY
        )
        fixture.viewModel.apply {
            bind(DEVICE_UID)
            selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
            selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)
            apply()
        }

        val state = fixture.viewModel.uiState.value
        assertEquals(2, fixture.plans.readCount)
        assertEquals(1, fixture.plans.applyCount)
        assertEquals(FIRST_AUTHORITY, fixture.plans.appliedAuthority)
        assertEquals(REFRESHED_AUTHORITY, state.managedPlanSnapshot?.authority)
        assertEquals(listOf("prepare", "firmware", "outcome:false"), events)
        assertFalse(state.applying)
    }

    @Test
    fun `transport uncertainty is recorded as indeterminate and never retried blindly`() {
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            applyFailure = DeviceLightManagedPlanFailure.UNAVAILABLE
        )
        fixture.viewModel.apply {
            bind(DEVICE_UID)
            selectCo2Readiness(AquariumCo2Readiness.READY_AT_LIGHT_ON)
            selectSurfaceGrowth(AquariumSurfaceGrowth.NONE)
            apply()
        }

        assertEquals(listOf("prepare", "firmware", "outcome:indeterminate"), events)
        assertEquals(1, fixture.plans.applyCount)
        assertFalse(fixture.viewModel.uiState.value.applying)
    }

    private fun fixture(
        events: MutableList<String> = mutableListOf(),
        tank: DeviceLightQuickSetupTank = TANK,
        reads: List<DeviceLightManagedPlanSnapshot> = listOf(snapshot(FIRST_AUTHORITY)),
        prepareResult: DeviceLightQuickSetupPersistenceResult =
            DeviceLightQuickSetupPersistenceResult.Prepared(AUDIT_ID),
        outcomeFailuresBeforeSuccess: Int = 0,
        applyFailure: DeviceLightManagedPlanFailure? = null
    ): Fixture {
        val tankOperations = FakeTankOperations(
            events,
            tank,
            prepareResult,
            outcomeFailuresBeforeSuccess
        )
        val plans = FakeManagedPlanOperations(events, reads, applyFailure)
        return Fixture(
            viewModel = DeviceLightQuickSetupViewModel(
                tankOperations = tankOperations,
                managedPlanOperations = plans
            ),
            plans = plans
        )
    }

    private data class Fixture(
        val viewModel: DeviceLightQuickSetupViewModel,
        val plans: FakeManagedPlanOperations
    )

    private class FakeTankOperations(
        private val events: MutableList<String>,
        private val tank: DeviceLightQuickSetupTank,
        private val prepareResult: DeviceLightQuickSetupPersistenceResult,
        private var outcomeFailuresBeforeSuccess: Int
    ) : DeviceLightQuickSetupTankOperations {
        override suspend fun readForDevice(
            deviceUid: String
        ): DeviceLightQuickSetupTankReadResult =
            DeviceLightQuickSetupTankReadResult.Available(tank)

        override suspend fun prepareRecommendation(
            deviceUid: String,
            input: DeviceLightQuickSetupInput,
            plan: DeviceLightQuickSetupPlan
        ): DeviceLightQuickSetupPersistenceResult {
            events += "prepare"
            return prepareResult
        }

        override suspend fun recordRecommendationOutcome(
            deviceUid: String,
            auditId: String,
            outcome: DeviceLightQuickSetupRecordedOutcome
        ): DeviceLightQuickSetupPersistenceResult {
            assertEquals(AUDIT_ID, auditId)
            events += when (outcome) {
                is DeviceLightQuickSetupRecordedOutcome.Applied -> "outcome:true"
                DeviceLightQuickSetupRecordedOutcome.Failed -> "outcome:false"
                DeviceLightQuickSetupRecordedOutcome.Indeterminate ->
                    "outcome:indeterminate"
            }
            if (outcomeFailuresBeforeSuccess > 0) {
                outcomeFailuresBeforeSuccess -= 1
                return DeviceLightQuickSetupPersistenceResult.Failed()
            }
            return DeviceLightQuickSetupPersistenceResult.Saved
        }
    }

    private class FakeManagedPlanOperations(
        private val events: MutableList<String>,
        private val reads: List<DeviceLightManagedPlanSnapshot>,
        private val applyFailure: DeviceLightManagedPlanFailure?
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
            events += "firmware"
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
        const val AUDIT_ID = "slr-0123456789abcdef01234567"
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
            deviceLocalEpochDay = TODAY,
            setupDateEpochDay = TODAY - 10,
            tankHeightCm = 45,
            hasPlants = true,
            plantDemand = DeviceLightPlantDemand.HIGH,
            reviewedPlantDemandFloor = DeviceLightPlantDemand.HIGH,
            plantCatalogIds = setOf("plant:hemianthus_callitrichoides_cuba"),
            plantEvidenceSourceIds = setOf("tropica_plant_4478"),
            plantCoverage = AquariumPlantCoverage.DENSE,
            co2ComponentPresent = true,
            co2Readiness = AquariumCo2Readiness.READY_AT_LIGHT_ON,
            substrateSemantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
            substrateProductIds = setOf("substrate_chihiros_aquasoil_9l"),
            substrateEvidenceSourceIds = setOf("chihiros_aqua_soil_launch"),
            daylightExposure = AquariumDaylightExposure.INDIRECT,
            daylightStartMinute = null,
            daylightEndMinute = null,
            preferredLightEndMinute = 20 * 60,
            surfaceGrowth = AquariumSurfaceGrowth.NONE,
            latestObservationEpochDay = TODAY - 20,
            hasShrimp = false,
            shelterAvailability = AquariumShelterAvailability.NOT_REQUIRED,
            waterDepthCm = 38,
            fixtureHeightAboveWaterCm = 12,
            profileUpdatedAtMillis = null,
            installationUpdatedAtMillis = null,
            plantedFreshwater = true,
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productDisplayName = "WRGB Pro Elite",
            hardwareRevision = "rev-a",
            fixtureLengthMm = 900,
            calibrationProfile = null,
            lastLightingResetEpochDay = TODAY - 10,
            lastAppliedPhotoperiodMinutes = null,
            lastAppliedMaximumChannelPercent = null,
            lastAppliedEpochDay = null,
            nextReevaluationEpochDay = null
        )

        fun expectedPlan() =
            DeviceLightQuickSetupCalculator.calculate(
                    tank = TANK,
                    input = DeviceLightQuickSetupInput(
                        plantDemand = TANK.plantDemand,
                        plantCoverage = TANK.plantCoverage,
                        waterDepthCm = requireNotNull(TANK.waterDepthCm),
                        fixtureHeightAboveWaterCm =
                            requireNotNull(TANK.fixtureHeightAboveWaterCm),
                        co2Readiness = TANK.co2Readiness,
                        substrateSemantic = TANK.substrateSemantic,
                        daylightExposure = TANK.daylightExposure,
                        daylightStartMinute = null,
                        daylightEndMinute = null,
                        surfaceGrowth = AquariumSurfaceGrowth.NONE,
                        shelterAvailability = AquariumShelterAvailability.NOT_REQUIRED,
                        programEndMinute = requireNotNull(TANK.preferredLightEndMinute)
                    ),
                    todayEpochDay = TODAY
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
            initialStartPercent = draft?.initialStartPercent ?: 100,
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
