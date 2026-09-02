package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CalibrationProjectionStabilityTest {

    @Test
    fun `calibration mutation ack keeps previous coherent presentation`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        val initial = fixtureState()
        commit(owner, initial)
        val before = requireNotNull(owner.reads.observeCalibration(DEVICE_UID, CHANNEL_KEY).first())
        val start = DeviceDosingV1MutationParser.parseCalibrationStart(
            runningCalibrationStartMutation()
        )

        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.recordMutation(
                token = owner.beginRequest(DEVICE_UID, CHANNEL_KEY),
                connectionGeneration = CONNECTION_GENERATION,
                channel = start.channel
            )
        )

        assertNull(owner.reads.currentCalibration(DEVICE_UID, CHANNEL_KEY))
        assertEquals(before, owner.reads.observeCalibration(DEVICE_UID, CHANNEL_KEY).first())
        assertEquals(DeviceDosingCalibrationSessionPhase.IDLE, before.sessionPhase)
    }

    @Test
    fun `terminal calibration confirmation ack projects committed idle state safely`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        commit(owner, pendingVerificationState())
        val pending = requireNotNull(
            owner.reads.currentCalibration(DEVICE_UID, CHANNEL_KEY)
        )
        assertEquals(DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION, pending.sessionPhase)
        assertTrue(pending.verificationDoseComplete)
        assertFalse(pending.calibrated)

        val confirm = DeviceDosingV1MutationParser.parseCalibrationConfirm(
            DeviceDosingV1TestFixtures.calibrationConfirm().also { mutation ->
                mutation.getJSONObject("channel")
                    .put("displayName", "Trace Elements")
                    .put("effectiveName", "Trace Elements")
            }
        )

        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.recordMutation(
                token = owner.beginRequest(DEVICE_UID, CHANNEL_KEY),
                connectionGeneration = CONNECTION_GENERATION,
                channel = confirm.channel
            )
        )

        // A mutation ACK never becomes authoritative state by itself, but the owner may retain a
        // committed presentation projection while coherent readback proceeds in the background.
        assertNull(owner.reads.currentCalibration(DEVICE_UID, CHANNEL_KEY))
        val continuation = requireNotNull(
            owner.reads.committedMutationContinuation(DEVICE_UID, CHANNEL_KEY)
        )
        val committed = continuation.calibration
        assertEquals(DeviceDosingCalibrationSessionPhase.IDLE, committed.sessionPhase)
        assertTrue(committed.calibrated)
        assertEquals("Trace Elements", committed.channelTitle)
        assertTrue(committed.lastCalibratedAt > 0L)
        assertEquals(0L, committed.pendingDoseMsPerMl)
        assertFalse(committed.verificationDoseStarted)
        assertFalse(committed.verificationDoseComplete)
        assertFalse(committed.manualActive)
        assertEquals(ENVELOPE_UPTIME_MS, committed.deviceUptimeMs)
        assertTrue(continuation.channel.controls.programEditable)
    }

    @Test
    fun `authoritative refresh still publishes coherent running calibration timing`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        commit(owner, fixtureState())

        val running = fixtureState(running = true)
        commit(owner, running)

        val calibration = requireNotNull(
            owner.reads.currentCalibration(DEVICE_UID, CHANNEL_KEY)
        )
        assertEquals(DeviceDosingCalibrationSessionPhase.RUNNING, calibration.sessionPhase)
        assertEquals(ENVELOPE_UPTIME_MS, calibration.deviceUptimeMs)
        assertEquals(AUTHORITATIVE_RUN_STARTED_AT_MS, calibration.startedAtUptimeMs)
        assertTrue(calibration.deviceUptimeMs >= calibration.startedAtUptimeMs)
    }

    @Test
    fun `expired run remains active until firmware proves the physical output is off`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        val global = DeviceDosingV1StatusParser.parseGlobal(
            DeviceDosingV1TestFixtures.globalStatus().also { status ->
                status.getJSONArray("channels").getJSONObject(0).put("active", true)
            }
        )
        val channel = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus().also { status ->
                status.getJSONObject("channel").apply {
                    markCalibrationRunning(AUTHORITATIVE_RUN_STARTED_AT_MS)
                    getJSONObject("activeRun")
                        .put("active", true)
                        .put("source", "calibration")
                        .put("targetAmountMl", 0.0)
                        .put("remainingMs", 0)
                    getJSONObject("lastRuntimeEvent")
                        .put("valid", true)
                        .put("kind", "fault")
                        .put("reason", "hardwareStopFailed")
                        .put("source", "calibration")
                }
            }
        )
        commit(
            owner,
            FixtureState(
                global = global,
                channel = channel,
                progress = DeviceDosingV1StatusParser.parseProgress(
                    DeviceDosingV1TestFixtures.progressStatus()
                )
            )
        )

        val calibration = requireNotNull(
            owner.reads.currentCalibration(DEVICE_UID, CHANNEL_KEY)
        )
        assertTrue(calibration.manualActive)
    }

    private fun commit(
        owner: DeviceDosingV1StateOwner,
        state: FixtureState
    ) {
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                token = owner.beginRequest(DEVICE_UID, CHANNEL_KEY),
                connectionGeneration = CONNECTION_GENERATION,
                global = state.global,
                channelStatus = state.channel,
                progressStatus = state.progress
            )
        )
    }

    private fun fixtureState(running: Boolean = false): FixtureState {
        val global = DeviceDosingV1StatusParser.parseGlobal(
            DeviceDosingV1TestFixtures.globalStatus()
        )
        val channelJson = DeviceDosingV1TestFixtures.channelStatus()
        if (running) {
            channelJson.getJSONObject("channel")
                .markCalibrationRunning(AUTHORITATIVE_RUN_STARTED_AT_MS)
        }
        return FixtureState(
            global = global,
            channel = DeviceDosingV1StatusParser.parseChannel(channelJson),
            progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            )
        )
    }

    private fun pendingVerificationState(): FixtureState {
        val channel = DeviceDosingV1TestFixtures.channelStatus().also { status ->
            status.getJSONObject("channel").getJSONObject("calibration")
                .put("confirmed", false)
                .put("doseMsPerMl", 0.0)
                .put("lastCalibratedAt", 0)
                .put("state", "pendingVerification")
                .put("durationMs", CALIBRATION_DURATION_MS)
                .put("measuredMl", 4.8)
                .put("pendingDoseMsPerMl", 625)
                .put("verificationDoseStarted", true)
                .put("verificationDoseComplete", true)
        }
        return FixtureState(
            global = DeviceDosingV1StatusParser.parseGlobal(
                DeviceDosingV1TestFixtures.globalStatus()
            ),
            channel = DeviceDosingV1StatusParser.parseChannel(channel),
            progress = DeviceDosingV1StatusParser.parseProgress(
                DeviceDosingV1TestFixtures.progressStatus()
            )
        )
    }

    private fun runningCalibrationStartMutation(): JSONObject =
        DeviceDosingV1TestFixtures.calibrationStart().also { mutation ->
            mutation.getJSONObject("channel")
                .markCalibrationRunning(FUTURE_RUN_STARTED_AT_MS)
        }

    private fun JSONObject.markCalibrationRunning(startedAtMs: Long): JSONObject = apply {
        getJSONObject("calibration")
            .put("state", "running")
            .put("durationMs", CALIBRATION_DURATION_MS)
        put(
            "lastRuntimeEvent",
            JSONObject()
                .put("valid", true)
                .put("sequence", RUN_EVENT_SEQUENCE)
                .put("occurredAtMs", startedAtMs)
                .put("kind", "runStarted")
                .put("reason", "none")
                .put("source", "calibration")
        )
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-CALIBRATION-PROJECTION")
        val CHANNEL_KEY = DeviceDosingV1ChannelKey.from("channel1")
        val CONNECTION_GENERATION =
            com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration(1L)
        const val ENVELOPE_UPTIME_MS = 123_456L
        const val AUTHORITATIVE_RUN_STARTED_AT_MS = 122_000L
        const val FUTURE_RUN_STARTED_AT_MS = 123_500L
        const val CALIBRATION_DURATION_MS = 3_000L
        const val RUN_EVENT_SEQUENCE = 12L
    }
}
