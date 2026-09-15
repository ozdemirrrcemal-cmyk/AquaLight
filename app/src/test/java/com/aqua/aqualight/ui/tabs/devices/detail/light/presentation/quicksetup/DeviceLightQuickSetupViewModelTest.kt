package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyFailure
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupAquariumEnvironment
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupCalibrationCatalog
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupChannel
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecisionEngine
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupInput
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupLifecycleClassifier
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupMaintenanceObservations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupOperations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupReadResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `missing semantic data opens editor and never enables apply`() {
        val missing = missingSnapshot()
        val viewModel = DeviceLightQuickSetupViewModel(
            operations = FakeSmartSetupOperations(SmartSetupReadResult.Available(missing)),
            savedStateHandle = SavedStateHandle()
        )

        viewModel.bind(DEVICE_UID)

        assertTrue(viewModel.uiState.value.editMode)
        assertTrue(viewModel.uiState.value.decision is SmartSetupDecision.MissingData)
        assertFalse(viewModel.uiState.value.canSave)
        assertFalse(viewModel.uiState.value.canApply)
    }

    @Test
    fun `complete editor commits setup date and profile through one save call`() {
        val operations = FakeSmartSetupOperations(
            readResult = SmartSetupReadResult.Available(missingSnapshot()),
            saveResult = SmartSetupProfileSaveResult.Saved(readySnapshot())
        )
        val viewModel = DeviceLightQuickSetupViewModel(operations, SavedStateHandle())
        viewModel.bind(DEVICE_UID)

        viewModel.selectAquariumAge(SETUP_DAY)
        viewModel.selectPlantDensity(PlantDensity.MEDIUM)
        viewModel.selectPlantLightDemand(PlantLightDemand.HIGH)
        viewModel.selectCo2Status(Co2Status.ACTIVE)
        viewModel.selectActiveSoil(true)
        viewModel.selectWaterDepth(WATER_DEPTH_CM)
        viewModel.selectMountHeight(MOUNT_HEIGHT_CM)
        viewModel.selectViewingWindow(VIEW_START, VIEW_END)
        viewModel.selectAlgaeObservation(AquariumObservationSeverity.NONE)
        viewModel.selectPlantStressObservation(AquariumObservationSeverity.MILD)
        viewModel.recordObservationsToday()

        assertTrue(viewModel.uiState.value.canSave)
        viewModel.saveProfile()

        assertEquals(EVALUATION_DAY - SETUP_DAY + 1L, operations.savedSetupDate)
        assertEquals(
            AquariumLightingProfile(
                plantDensity = PlantDensity.MEDIUM,
                highestPlantLightDemand = PlantLightDemand.HIGH,
                co2Status = Co2Status.ACTIVE,
                isActiveSoil = true,
                waterDepthCm = WATER_DEPTH_CM,
                fixtureMountHeightCm = MOUNT_HEIGHT_CM,
                preferredViewingStartMinuteOfDay = VIEW_START,
                preferredViewingEndMinuteOfDay = VIEW_END,
                algaeObservation = AquariumObservationSeverity.NONE,
                plantStressObservation = AquariumObservationSeverity.MILD,
                observationDateEpochDay = EVALUATION_DAY
            ),
            operations.savedProfile
        )
        assertFalse(viewModel.uiState.value.editMode)
        assertFalse(viewModel.uiState.value.draftDirty)
        assertTrue(viewModel.uiState.value.decision is SmartSetupDecision.Ready)
    }

    @Test
    fun `apply sends exact preview fingerprint once and confirmed result locks action`() {
        val snapshot = readySnapshot()
        val fingerprint = (snapshot.decision as SmartSetupDecision.Ready)
            .recommendation.profileFingerprint
        val operations = FakeSmartSetupOperations(
            readResult = SmartSetupReadResult.Available(snapshot),
            applyResult = SmartSetupApplyResult.Applied(
                planId = "lp-00000001",
                revision = 1L,
                storageGeneration = 9L,
                profileFingerprint = fingerprint
            )
        )
        val viewModel = DeviceLightQuickSetupViewModel(operations, SavedStateHandle())
        viewModel.bind(DEVICE_UID)

        viewModel.applyPlan()
        viewModel.applyPlan()

        assertEquals(1, operations.applyCount)
        assertEquals(fingerprint, operations.appliedFingerprint)
        assertTrue(viewModel.uiState.value.planInstalled)
        assertFalse(viewModel.uiState.value.canApply)
    }

    @Test
    fun `stale authority rereads once and never retries apply`() {
        val operations = FakeSmartSetupOperations(
            readResult = SmartSetupReadResult.Available(readySnapshot()),
            applyResult = SmartSetupApplyResult.Failed(SmartSetupApplyFailure.STALE_AUTHORITY)
        )
        val viewModel = DeviceLightQuickSetupViewModel(operations, SavedStateHandle())
        viewModel.bind(DEVICE_UID)

        viewModel.applyPlan()

        assertEquals(1, operations.applyCount)
        assertEquals(2, operations.readCount)
        assertFalse(viewModel.uiState.value.operationInProgress)
        assertNull(viewModel.uiState.value.appliedResult)
    }

    @Test
    fun `unsaved semantic draft survives view model recreation`() {
        val handle = SavedStateHandle()
        val operations = FakeSmartSetupOperations(
            SmartSetupReadResult.Available(readySnapshot())
        )
        DeviceLightQuickSetupViewModel(operations, handle).apply {
            bind(DEVICE_UID)
            startEditing()
            selectPlantDensity(PlantDensity.HIGH)
        }

        val recreated = DeviceLightQuickSetupViewModel(operations, handle)
        recreated.bind(DEVICE_UID)

        assertTrue(recreated.uiState.value.editMode)
        assertTrue(recreated.uiState.value.draftDirty)
        assertEquals(PlantDensity.HIGH, recreated.uiState.value.editor.plantDensity)
        assertFalse(recreated.uiState.value.canApply)
    }

    @Test
    fun `unexpected read failure exits loading and fails closed`() {
        val operations = FakeSmartSetupOperations(
            readResult = SmartSetupReadResult.Available(readySnapshot()),
            throwOnRead = true
        )
        val viewModel = DeviceLightQuickSetupViewModel(operations, SavedStateHandle())

        viewModel.bind(DEVICE_UID)

        assertFalse(viewModel.uiState.value.initialLoading)
        assertEquals(
            DeviceLightQuickSetupLoadFailure.UNAVAILABLE,
            viewModel.uiState.value.loadFailure
        )
        assertFalse(viewModel.uiState.value.canApply)
    }

    private class FakeSmartSetupOperations(
        var readResult: SmartSetupReadResult,
        var saveResult: SmartSetupProfileSaveResult =
            SmartSetupProfileSaveResult.Saved(readySnapshot()),
        var applyResult: SmartSetupApplyResult =
            SmartSetupApplyResult.Failed(SmartSetupApplyFailure.REJECTED),
        private val throwOnRead: Boolean = false
    ) : SmartSetupOperations {
        var readCount = 0
        var applyCount = 0
        var savedSetupDate: Long? = null
        var savedProfile: AquariumLightingProfile? = null
        var appliedFingerprint: String? = null

        override suspend fun read(deviceUid: String): SmartSetupReadResult {
            readCount += 1
            if (throwOnRead) error("read failed")
            return readResult
        }

        override suspend fun saveProfile(
            deviceUid: String,
            setupDateEpochDay: Long,
            profile: AquariumLightingProfile
        ): SmartSetupProfileSaveResult {
            savedSetupDate = setupDateEpochDay
            savedProfile = profile
            return saveResult
        }

        override suspend fun apply(
            deviceUid: String,
            expectedProfileFingerprint: String
        ): SmartSetupApplyResult {
            applyCount += 1
            appliedFingerprint = expectedProfileFingerprint
            return applyResult
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        const val DEVICE_UID = "smart-light-01"
        const val TANK_ID = 42L
        const val EVALUATION_DAY = 21_000L
        const val SETUP_DAY = 21
        const val WATER_DEPTH_CM = 35
        const val MOUNT_HEIGHT_CM = 10
        const val VIEW_START = 10 * 60
        const val VIEW_END = 20 * 60

        fun readySnapshot(): SmartSetupSnapshot = snapshot(completeInput())

        fun missingSnapshot(): SmartSetupSnapshot = snapshot(
            completeInput().copy(
                setupDateEpochDay = null,
                setupDay = null,
                lifecycleStage = null,
                plantDensity = null,
                highestPlantLightDemand = null,
                co2Status = null,
                isActiveSoil = null,
                waterDepthCm = null,
                fixtureMountHeightCm = null,
                preferredViewingStartMinuteOfDay = null,
                preferredViewingEndMinuteOfDay = null,
                algaeObservation = null,
                plantStressObservation = null,
                observationDateEpochDay = null
            )
        )

        fun snapshot(input: SmartSetupInput): SmartSetupSnapshot = SmartSetupSnapshot(
            deviceUid = DEVICE_UID,
            tankId = TANK_ID,
            tankName = "Display tank",
            input = input,
            decision = SmartSetupDecisionEngine.decide(input),
            installedPlanId = null,
            installedPlanRevision = 0L,
            installedPlanFingerprintMatches = false
        )

        fun completeInput(): SmartSetupInput {
            val setupDate = EVALUATION_DAY - SETUP_DAY + 1L
            val lifecycle = checkNotNull(
                SmartSetupLifecycleClassifier.classify(setupDate, EVALUATION_DAY)
            )
            return SmartSetupInput(
                tankId = TANK_ID,
                tankName = "Display tank",
                aquariumEnvironment = SmartSetupAquariumEnvironment.FRESHWATER,
                evaluationEpochDay = EVALUATION_DAY,
                setupDateEpochDay = setupDate,
                setupDay = lifecycle.setupDay,
                lifecycleStage = lifecycle.stage,
                isPlanted = true,
                plantDensity = PlantDensity.MEDIUM,
                highestPlantLightDemand = PlantLightDemand.HIGH,
                co2Status = Co2Status.ACTIVE,
                isActiveSoil = true,
                waterDepthCm = WATER_DEPTH_CM,
                fixtureMountHeightCm = MOUNT_HEIGHT_CM,
                deviceProductKey = SmartSetupCalibrationCatalog.WRGB_PRO_ELITE_PRODUCT,
                reportedCalibrationRevision = SmartSetupCalibrationCatalog.wrgbProElite.revision,
                reportedChannelSceneKeys = SmartSetupChannel.entries.map { channel ->
                    channel.sceneKey
                },
                calibrationProfile = SmartSetupCalibrationCatalog.wrgbProElite,
                preferredViewingStartMinuteOfDay = VIEW_START,
                preferredViewingEndMinuteOfDay = VIEW_END,
                algaeObservation = AquariumObservationSeverity.NONE,
                plantStressObservation = AquariumObservationSeverity.MILD,
                observationDateEpochDay = EVALUATION_DAY,
                maintenance = SmartSetupMaintenanceObservations()
            )
        }
    }
}
