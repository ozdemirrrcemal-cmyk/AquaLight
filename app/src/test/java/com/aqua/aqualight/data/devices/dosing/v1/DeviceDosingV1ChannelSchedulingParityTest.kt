package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ChannelSchedulingParityTest {

    @Test
    fun `current firmware channel status fixture parses and remains fail closed`() {
        val canonical = resourceJson(CHANNEL_STATUS_FIXTURE)
        val parsed = DeviceDosingV1StatusParser.parseChannel(canonical)
        val effective = parsed.scheduling.effectiveScheduledDose
        val drifted = JSONObject(canonical.toString()).put("unexpectedTopLevelField", true)

        assertEquals("channel1", parsed.channel.channelKey.value)
        assertTrue(effective.available)
        assertEquals(0.08, effective.minimumMilliliters!!, 0.0)
        assertEquals(2_880.0, effective.maximumMilliliters!!, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1StatusParser.parseChannel(drifted)
        }
    }

    @Test
    fun `authoritative snapshot uses channel calibrated limits not global placeholder`() {
        val documents = documents(revision = 7L, calibrated = true)
        val mapped = DeviceDosingV1SnapshotMapper.map(documents)

        assertFalse(documents.global.scheduling.effectiveScheduledDose.available)
        assertTrue(documents.channelStatus.scheduling.effectiveScheduledDose.available)
        assertEquals(
            80L..2_880_000L,
            mapped.channel.scheduling.effectiveScheduledDoseMicroliters
        )
    }

    @Test
    fun `uncalibrated channel keeps effective scheduled limits unavailable`() {
        val mapped = DeviceDosingV1SnapshotMapper.map(
            documents(revision = 7L, calibrated = false)
        )

        assertNull(mapped.channel.scheduling.effectiveScheduledDoseMicroliters)
    }

    @Test
    fun `central state owner accepts reconnect scheduling and rejects stale connection`() {
        val owner = DeviceDosingV1StateOwner()
        val channelKey = DeviceDosingV1ChannelKey.from("channel1")
        val calibrated = documents(revision = 7L, calibrated = true)
        val initialToken = owner.beginRequest(DEVICE_UID, channelKey)

        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                token = initialToken,
                connectionGeneration = GENERATION_ONE,
                global = calibrated.global,
                channelStatus = calibrated.channelStatus,
                progressStatus = calibrated.progressStatus
            )
        )
        assertEquals(
            80L..2_880_000L,
            owner.reads.currentChannel(DEVICE_UID, channelKey)
                ?.scheduling?.effectiveScheduledDoseMicroliters
        )

        val uncalibratedReconnect = documents(revision = 3L, calibrated = false)
        val reconnectToken = owner.beginRequest(DEVICE_UID, channelKey)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                token = reconnectToken,
                connectionGeneration = GENERATION_TWO,
                global = uncalibratedReconnect.global,
                channelStatus = uncalibratedReconnect.channelStatus,
                progressStatus = uncalibratedReconnect.progressStatus
            )
        )
        assertNull(
            owner.reads.currentChannel(DEVICE_UID, channelKey)
                ?.scheduling?.effectiveScheduledDoseMicroliters
        )

        val staleOldConnection = documents(revision = 8L, calibrated = true)
        val staleToken = owner.beginRequest(DEVICE_UID, channelKey)
        assertEquals(
            DeviceDosingV1CommitDisposition.STALE_CONNECTION,
            owner.commitRefresh(
                token = staleToken,
                connectionGeneration = GENERATION_ONE,
                global = staleOldConnection.global,
                channelStatus = staleOldConnection.channelStatus,
                progressStatus = staleOldConnection.progressStatus
            )
        )
        assertNull(
            owner.reads.currentChannel(DEVICE_UID, channelKey)
                ?.scheduling?.effectiveScheduledDoseMicroliters
        )
    }

    private fun documents(
        revision: Long,
        calibrated: Boolean
    ): DeviceDosingV1SnapshotDocuments {
        val globalJson = DeviceDosingV1TestFixtures.globalStatus()
        globalJson.getJSONObject("scheduling").setEffectiveUnavailable()
        val global = DeviceDosingV1StatusParser.parseGlobal(globalJson).let { status ->
            status.copy(
                channels = status.channels.map { channel ->
                    if (channel.channelKey.value == "channel1") {
                        channel.copy(revision = revision)
                    } else {
                        channel
                    }
                }
            )
        }

        val channelJson = resourceJson(CHANNEL_STATUS_FIXTURE)
        channelJson.getJSONObject("channel").put("revision", revision)
        if (!calibrated) {
            channelJson.getJSONObject("scheduling").setEffectiveUnavailable()
            channelJson.getJSONObject("channel")
                .put("runtimeEnabled", false)
                .put("runtimeReason", "missingCalibration")
                .getJSONObject("calibration")
                .put("confirmed", false)
                .put("doseMsPerMl", 0)
                .put("lastCalibratedAt", 0)
        }
        val channel = DeviceDosingV1StatusParser.parseChannel(channelJson)
        val progress = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        ).copy(revision = revision)

        return DeviceDosingV1SnapshotDocuments(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            global = global,
            channelStatus = channel,
            progressStatus = progress,
            lowLevelAlertEnabled = false
        )
    }

    private fun JSONObject.setEffectiveUnavailable() {
        getJSONObject("effectiveScheduledDose")
            .put("available", false)
            .put("minDoseMl", JSONObject.NULL)
            .put("maxDoseMl", JSONObject.NULL)
    }

    private fun resourceJson(name: String): JSONObject =
        JSONObject(
            requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
                "Missing fixture resource: $name"
            }.use { input -> input.readBytes().toString(Charsets.UTF_8) }
        )

    private companion object {
        const val CHANNEL_STATUS_FIXTURE = "aql_dosing_channel_status_v1.json"
        const val SLOT_ID = "dosing:channel1"
        val DEVICE_UID = DeviceUid("AQL-DOSING-SCHEDULING-PARITY")
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)
    }
}
