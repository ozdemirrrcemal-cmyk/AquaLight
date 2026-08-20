package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingV1PresentationContinuityTest {

    @Test
    fun `lifecycle invalidation keeps presentation while authority fails closed`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        val channelKey = DeviceDosingV1ChannelKey.from("channel1")
        val revisionSeven = documents(revision = 7L)
        val initial = owner.beginRequest(DEVICE_UID, channelKey)

        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                initial,
                GENERATION_ONE,
                revisionSeven.global,
                revisionSeven.channel,
                revisionSeven.progress
            )
        )
        val provenPresentation = owner.reads.observeAll(DEVICE_UID).first()
        assertEquals(7L, provenPresentation.single().revision)

        owner.invalidateDevice(DEVICE_UID)

        assertNull(owner.reads.currentChannel(DEVICE_UID, channelKey))
        assertNull(owner.reads.authoritativeRevision(DEVICE_UID, channelKey))
        assertEquals(provenPresentation, owner.reads.observeAll(DEVICE_UID).first())

        val revisionThree = documents(revision = 3L)
        val reconnected = owner.beginRequest(DEVICE_UID, channelKey)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                reconnected,
                GENERATION_TWO,
                revisionThree.global,
                revisionThree.channel,
                revisionThree.progress
            )
        )
        assertEquals(3L, owner.reads.currentChannel(DEVICE_UID, channelKey)?.revision)
        assertEquals(3L, owner.reads.observeAll(DEVICE_UID).first().single().revision)
    }

    private fun documents(revision: Long): Documents {
        val global = DeviceDosingV1StatusParser.parseGlobal(
            DeviceDosingV1TestFixtures.globalStatus()
        ).let { status ->
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
        val channel = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).let { status ->
            status.copy(channel = status.channel.copy(revision = revision))
        }
        val progress = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        ).copy(revision = revision)
        return Documents(global, channel, progress)
    }

    private data class Documents(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-PRESENTATION-CONTINUITY")
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)
    }
}
