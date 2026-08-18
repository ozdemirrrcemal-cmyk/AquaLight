package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1LifecycleRetentionTest {

    @Test
    fun `lifecycle boundary retains presentation while revoking current authority`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        val adapter = DeviceDosingV1StateAdapter(
            repository = DeviceDosingV1Repository(NoopGateway),
            stateOwner = owner
        )
        seed(owner, revision = 7L, generation = GENERATION_ONE)

        adapter.consume(DeviceRuntimeLifecycleEvent.Unavailable(DEVICE_UID))

        assertNull(adapter.currentChannel(DEVICE_UID.value, SLOT_ID))
        assertNull(adapter.currentCalibration(DEVICE_UID.value, SLOT_ID))
        assertNull(owner.reads.authoritativeRevision(DEVICE_UID, CHANNEL_KEY))
        assertEquals(7L, owner.reads.observeChannel(DEVICE_UID, CHANNEL_KEY).first()?.revision)
        assertEquals(
            "Macro",
            owner.reads.observeCalibration(DEVICE_UID, CHANNEL_KEY).first()?.channelTitle
        )
        assertEquals(
            listOf(7L),
            owner.reads.observeAll(DEVICE_UID).first().map { snapshot -> snapshot.revision }
        )
    }

    @Test
    fun `lifecycle boundary rejects requests started by the previous runtime session`() = runTest {
        val owner = DeviceDosingV1StateOwner()
        seed(owner, revision = 7L, generation = GENERATION_ONE)
        val previousSessionRequest = owner.beginRequest(DEVICE_UID, CHANNEL_KEY)

        owner.invalidateForRuntimeLifecycle(DEVICE_UID)
        val revisionEight = fixtureState(revision = 8L)

        assertEquals(
            DeviceDosingV1CommitDisposition.STALE_REQUEST,
            owner.commitRefresh(
                previousSessionRequest,
                GENERATION_ONE,
                revisionEight.global,
                revisionEight.channel,
                revisionEight.progress
            )
        )
        assertNull(owner.reads.currentChannel(DEVICE_UID, CHANNEL_KEY))
        assertEquals(7L, owner.reads.observeChannel(DEVICE_UID, CHANNEL_KEY).first()?.revision)

        val freshRequest = owner.beginRequest(DEVICE_UID, CHANNEL_KEY)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                freshRequest,
                GENERATION_ONE,
                revisionEight.global,
                revisionEight.channel,
                revisionEight.progress
            )
        )
        assertEquals(8L, owner.reads.currentChannel(DEVICE_UID, CHANNEL_KEY)?.revision)
    }

    @Test
    fun `new connection generation may restart revision after lifecycle invalidation`() {
        val owner = DeviceDosingV1StateOwner()
        seed(owner, revision = 7L, generation = GENERATION_ONE)

        owner.invalidateForRuntimeLifecycle(DEVICE_UID)
        val reconnectRequest = owner.beginRequest(DEVICE_UID, CHANNEL_KEY)
        val revisionThree = fixtureState(revision = 3L)

        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                reconnectRequest,
                GENERATION_TWO,
                revisionThree.global,
                revisionThree.channel,
                revisionThree.progress
            )
        )
        assertEquals(3L, owner.reads.currentChannel(DEVICE_UID, CHANNEL_KEY)?.revision)
    }

    private fun seed(
        owner: DeviceDosingV1StateOwner,
        revision: Long,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        val request = owner.beginRequest(DEVICE_UID, CHANNEL_KEY)
        val state = fixtureState(revision)
        assertEquals(
            DeviceDosingV1CommitDisposition.APPLIED,
            owner.commitRefresh(
                request,
                generation,
                state.global,
                state.channel,
                state.progress
            )
        )
        assertTrue(owner.reads.currentChannel(DEVICE_UID, CHANNEL_KEY) != null)
    }

    private fun fixtureState(revision: Long): FixtureState {
        val global = DeviceDosingV1StatusParser.parseGlobal(
            DeviceDosingV1TestFixtures.globalStatus()
        ).let { status ->
            status.copy(
                channels = status.channels.map { channel ->
                    if (channel.channelKey == CHANNEL_KEY) {
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
        return FixtureState(global, channel, progress)
    }

    private object NoopGateway : DeviceRuntimeCommandGateway {
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> = error("Lifecycle retention test must not execute I/O.")
    }

    private data class FixtureState(
        val global: DeviceDosingV1GlobalStatus,
        val channel: DeviceDosingV1ChannelStatus,
        val progress: DeviceDosingV1ProgressStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-LIFECYCLE-TEST")
        const val SLOT_ID = "dosing:channel1"
        val CHANNEL_KEY = DeviceDosingV1ChannelKey.from("channel1")
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)
    }
}
