package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityRejection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleMode
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleUpdate
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirNotificationAvailability
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DosingDraftViewModelBoundaryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `plan owns restored draft and saves fixture mutation`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingPlanViewModel(operations)
        viewModel.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            restoredDraft = DosingPlanDraft(distributedDailyDoseMicroliters = 2_000L)
        )
        viewModel.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            restoredDraft = DosingPlanDraft(distributedDailyDoseMicroliters = 9_000L)
        )

        viewModel.setDailyDoseMicroliters(3_000L)
        viewModel.applyScheduleUpdate(DosingPlanScheduleUpdate.Hourly(3_600_000L))
        viewModel.save()

        val draft = viewModel.currentEditorState().draft
        assertEquals(3_000L, draft.distributedDailyDoseMicroliters)
        assertEquals(3_600_000L, draft.hourlyStartTimeMs)
        assertEquals(DosingPlanScheduleMode.HOURLY, draft.selectedScheduleMode)
        val savedSchedule = operations.lastProgram?.schedule as? DeviceDosingProgramSchedule.Hourly24
        assertNotNull(savedSchedule)
        assertEquals(3_000L, savedSchedule?.dailyDoseMicroliters)
        assertTrue(viewModel.currentEditorState().canSave)
    }

    @Test
    fun `disabled valid plan remains saveable`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingPlanViewModel(operations)
        viewModel.bind("device-1", "dosing:channel2", restoredDraft = null)

        viewModel.setScheduleEnabled(false)

        assertTrue(viewModel.currentEditorState().canSave)
        viewModel.save()
        assertFalse(operations.lastProgram?.enabled ?: true)
    }

    @Test
    fun `reservoir validates draft before accepting state`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingReservoirViewModel(operations)
        viewModel.bind("device-1", "dosing:channel2")

        assertFalse(viewModel.currentEditorState().canSave)
        assertFalse(viewModel.currentDraft().lowLevelAlertEnabled)
        assertEquals(450_000L, viewModel.currentDraft().reservoirCapacityMicroliters)

        viewModel.setCapacityInput("-1", Locale.US)
        assertEquals(450_000L, viewModel.currentDraft().reservoirCapacityMicroliters)
        assertEquals(
            DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED,
            viewModel.currentEditorState().capacityRejection
        )

        viewModel.setCapacityInput("800,125", Locale.forLanguageTag("tr-TR"))
        assertEquals(800_125L, viewModel.currentDraft().reservoirCapacityMicroliters)
        assertEquals(null, viewModel.currentEditorState().capacityRejection)
        assertTrue(viewModel.currentDraft().trackingEnabled)
        assertTrue(viewModel.currentEditorState().canSave)
    }

    @Test
    fun `reservoir discards unsaved capacity on viewmodel recreation`() = runTest(dispatcher) {
        val original = DeviceDosingReservoirViewModel(FakeDeviceDosingChannelOperations())
        original.bind("device-1", "dosing:channel2")
        original.setCapacityInput("123,456", Locale.forLanguageTag("tr-TR"))
        assertEquals(123_456L, original.currentDraft().reservoirCapacityMicroliters)

        val recreated = DeviceDosingReservoirViewModel(FakeDeviceDosingChannelOperations())
        recreated.bind("device-1", "dosing:channel2")

        assertEquals(450_000L, recreated.currentDraft().reservoirCapacityMicroliters)
        assertFalse(recreated.currentEditorState().canSave)
    }

    @Test
    fun `reservoir keeps user alert intent while Android delivery is blocked`() =
        runTest(dispatcher) {
            val viewModel = DeviceDosingReservoirViewModel(FakeDeviceDosingChannelOperations())
            viewModel.bind("device-1", "dosing:channel2")

            viewModel.setLowLevelAlertEnabled(true)
            viewModel.setNotificationAvailability(
                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
            )

            assertTrue(viewModel.currentDraft().lowLevelAlertEnabled)
            assertEquals(
                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED,
                viewModel.currentEditorState().notificationAvailability
            )

            viewModel.setLowLevelAlertEnabled(false)

            assertFalse(viewModel.currentDraft().lowLevelAlertEnabled)
            assertEquals(
                DeviceDosingReservoirNotificationAvailability.AVAILABLE,
                viewModel.currentEditorState().notificationAvailability
            )
        }

    @Test
    fun `detail owns route validity from authoritative snapshot and delegates setting mutation`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations(initialSnapshot = null)
            val invalid = DeviceDosingChannelDetailViewModel(operations)
            invalid.bind(
                deviceUidText = "device-1",
                slotIdText = "dosing:channel2"
            )
            assertFalse(invalid.currentDraft().authoritativeStateAvailable)
            assertFalse(invalid.currentDraft().routeValid)

            val validOperations = FakeDeviceDosingChannelOperations()
            val valid = DeviceDosingChannelDetailViewModel(validOperations)
            valid.bind(
                deviceUidText = "device-1",
                slotIdText = "dosing:channel2"
            )
            assertTrue(valid.currentDraft().authoritativeStateAvailable)
            assertTrue(valid.currentDraft().routeValid)
            assertEquals("Channel 2", valid.currentDraft().channelTitle)

            validOperations.snapshot.value = requireNotNull(validOperations.snapshot.value).copy(
                channelTitle = "Trace Elements"
            )
            assertEquals("Trace Elements", valid.currentDraft().channelTitle)

            valid.setMissedDoseRecoveryEnabled(true)
            assertTrue(valid.currentDraft().routeValid)
            assertTrue(valid.currentDraft().missedDoseRecoveryEnabled)
            assertEquals(true, validOperations.lastMissedDoseRecoveryEnabled)

            valid.startManualDose("2.500")
            assertEquals(2_500L, validOperations.lastManualDoseMicroliters)
            assertTrue(valid.currentDraft().manualDoseActive)

            valid.stopManualDose()
            assertFalse(valid.currentDraft().manualDoseActive)

            valid.resetChannel()
            assertFalse(valid.currentDraft().routeValid)
        }
}
