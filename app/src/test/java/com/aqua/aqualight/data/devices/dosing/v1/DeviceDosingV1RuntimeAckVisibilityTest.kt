package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.DeviceDosingCalibrationStep
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.DeviceDosingCalibrationUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.DosingCalibrationProgressState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.reduceDosingCalibrationSnapshot
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1RuntimeAckVisibilityTest {

    @Test
    fun `calibration start runtime ack stays hidden until coherent readback`() = runTest {
        val releaseReadback = CompletableDeferred<Unit>()
        val gateway = ScriptedGateway().apply {
            enqueueInitialRefresh()
            enqueueCalibrationStartAck()
            enqueueRunningReadback(releaseReadback)
        }
        val adapter = DeviceDosingV1StateAdapter(DeviceDosingV1Repository(gateway))
        val initial = adapter.calibrationOperations.refresh(DEVICE_UID.value, SLOT_ID)
            as DeviceDosingCalibrationResult.Success
        val observed = observeCalibration(adapter)

        val startResult = async {
            adapter.calibrationOperations.start(DEVICE_UID.value, SLOT_ID)
        }
        runCurrent()
        assertRuntimeAckHidden(startResult, gateway, initial.snapshot, observed)

        releaseReadback.complete(Unit)
        runCurrent()

        assertTrue(startResult.await() is DeviceDosingCalibrationResult.Success)
        assertAuthoritativeRunning(observed)
    }

    private fun TestScope.observeCalibration(
        adapter: DeviceDosingV1StateAdapter
    ): MutableList<DeviceDosingCalibrationSnapshot> {
        val observed = mutableListOf<DeviceDosingCalibrationSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.calibrationOperations.observe(DEVICE_UID.value, SLOT_ID)
                .filterNotNull()
                .toList(observed)
        }
        runCurrent()
        return observed
    }

    private fun assertRuntimeAckHidden(
        startResult: Deferred<DeviceDosingCalibrationResult>,
        gateway: ScriptedGateway,
        initial: DeviceDosingCalibrationSnapshot,
        observed: List<DeviceDosingCalibrationSnapshot>
    ) {
        assertFalse(startResult.isCompleted)
        assertEquals(EXPECTED_ACTIONS_BEFORE_READBACK, gateway.actions)
        assertEquals(listOf(initial), observed)
        assertFalse(observed.any { snapshot -> snapshot.isRunning() })
        assertEquals(
            listOf(DeviceDosingCalibrationStep.CALIBRATION_RUN),
            observed.map { snapshot -> snapshot.presentationStep() }
        )
        assertFalse(
            observed.any { snapshot ->
                snapshot.presentationStep() == DeviceDosingCalibrationStep.MEASUREMENT
            }
        )
    }

    private fun assertAuthoritativeRunning(observed: List<DeviceDosingCalibrationSnapshot>) {
        assertEquals(2, observed.size)
        val authoritativeRunning = observed.last()
        assertEquals(DeviceDosingCalibrationSessionPhase.RUNNING, authoritativeRunning.sessionPhase)
        assertEquals(RUNNING_READBACK_UPTIME_MS, authoritativeRunning.deviceUptimeMs)
        assertEquals(CALIBRATION_STARTED_AT_UPTIME_MS, authoritativeRunning.startedAtUptimeMs)

        val presentation = reduceDosingCalibrationSnapshot(
            snapshot = authoritativeRunning,
            current = DeviceDosingCalibrationUiState(
                progress = DosingCalibrationProgressState(
                    isLoading = false,
                    isBusy = true,
                    step = DeviceDosingCalibrationStep.CALIBRATION_RUN
                )
            ),
            hasLocalProgress = true
        )
        assertEquals(DeviceDosingCalibrationStep.CALIBRATION_RUN, presentation.state.step)
        assertEquals(RUNNING_REMAINING_MS, presentation.state.remainingMs)
        assertTrue(presentation.state.isBusy)
        assertFalse(
            observed.any { snapshot ->
                snapshot.presentationStep() == DeviceDosingCalibrationStep.MEASUREMENT
            }
        )
    }

    private class ScriptedGateway : DeviceRuntimeCommandGateway {
        private data class Response(
            val action: String,
            val outcome: DeviceRuntimeCommandOutcome<*>,
            val release: CompletableDeferred<Unit>? = null
        )

        private val responses = ArrayDeque<Response>()
        val actions = mutableListOf<String>()

        fun enqueueInitialRefresh() {
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
            )
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus()
            )
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        fun enqueueCalibrationStartAck() {
            enqueueSuccess(
                DeviceDosingV1Contract.Action.CALIBRATION_START,
                DeviceDosingV1MutationParser.parseCalibrationStart(
                    DeviceDosingV1TestFixtures.calibrationStart()
                )
            )
        }

        fun enqueueRunningReadback(release: CompletableDeferred<Unit>) {
            val runningDetail = DeviceDosingV1TestFixtures.calibrationStart()
                .getJSONObject("channel")
                .also { detail ->
                    detail.getJSONObject("activeRun").put("remainingMs", RUNNING_REMAINING_MS)
                }
            val global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
                    .put("uptimeMs", RUNNING_READBACK_UPTIME_MS)
            )
            val channel = DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus(runningDetail)
                    .put("uptimeMs", RUNNING_READBACK_UPTIME_MS)
            )
            val progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
                    .put("uptimeMs", RUNNING_READBACK_UPTIME_MS)
            )
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, global, release)
            enqueueSuccess(DeviceDosingV1Contract.Action.STATUS_GET, channel)
            enqueueSuccess(DeviceDosingV1Contract.Action.PROGRESS_GET, progress)
        }

        private fun <T> enqueueSuccess(
            action: String,
            value: T,
            release: CompletableDeferred<Unit>? = null
        ) {
            responses.addLast(Response(action, success(action, value), release))
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            assertEquals(DEVICE_UID, deviceUid)
            val response = responses.removeFirst()
            assertEquals(response.action, command.action)
            actions += command.action
            response.release?.await()
            return response.outcome as DeviceRuntimeCommandOutcome<T>
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-RUNTIME-ACK-VISIBILITY")
        const val SLOT_ID = "dosing:channel1"
        const val CALIBRATION_STARTED_AT_UPTIME_MS = 123_500L
        const val RUNNING_READBACK_UPTIME_MS = 123_600L
        const val RUNNING_REMAINING_MS = 4_900L
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val EXPECTED_ACTIONS_BEFORE_READBACK = listOf(
            DeviceDosingV1Contract.Action.STATUS_GET,
            DeviceDosingV1Contract.Action.STATUS_GET,
            DeviceDosingV1Contract.Action.PROGRESS_GET,
            DeviceDosingV1Contract.Action.CALIBRATION_START,
            DeviceDosingV1Contract.Action.STATUS_GET
        )

        fun <T> success(action: String, value: T): DeviceRuntimeCommandOutcome.Success<T> =
            DeviceRuntimeCommandOutcome.Success(
                deviceUid = DEVICE_UID,
                module = DeviceDosingV1Contract.MODULE,
                action = action,
                messageId = "response-$action",
                generation = GENERATION,
                statusCode = 200,
                value = value
            )
    }
}

private fun DeviceDosingCalibrationSnapshot.isRunning(): Boolean =
    sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING

private fun DeviceDosingCalibrationSnapshot.presentationStep(): DeviceDosingCalibrationStep =
    reduceDosingCalibrationSnapshot(
        snapshot = this,
        current = DeviceDosingCalibrationUiState(
            progress = DosingCalibrationProgressState(
                isLoading = false,
                isBusy = true,
                step = DeviceDosingCalibrationStep.CALIBRATION_RUN
            )
        ),
        hasLocalProgress = true
    ).state.step
