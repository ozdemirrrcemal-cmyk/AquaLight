package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLightCustomLibraryDraftMapperTest {

    @Test
    fun `custom library entry maps all editor values`() {
        val entry = customEntry(DeviceLightLibraryChannel.entries)

        val draft = entry.toCustomEditorDraft(
            editorChannels = DeviceLightCustomChannelId.entries,
            maxPoints = MAX_POINT_CAPACITY
        )

        requireNotNull(draft)
        assertEquals(WEEKDAYS_MASK, draft.weekdaysMask)
        assertEquals(listOf(START_TIME_MS, END_TIME_MS), draft.points.map { it.timeMs })
        assertEquals(RED_PERCENT, draft.points.last().channels[DeviceLightCustomChannelId.RED])
        assertEquals(WHITE_PERCENT, draft.points.last().channels[DeviceLightCustomChannelId.WHITE])
    }

    @Test
    fun `incompatible product channels are rejected`() {
        val entry = customEntry(DeviceLightLibraryChannel.entries)

        val draft = entry.toCustomEditorDraft(
            editorChannels = DeviceLightCustomChannelId.entries.dropLast(1),
            maxPoints = MAX_POINT_CAPACITY
        )

        assertNull(draft)
    }

    private fun customEntry(
        channels: List<DeviceLightLibraryChannel>
    ): DeviceLightLibraryEntry {
        val off = channels.associateWith { 0 }
        val on = channels.associateWith { channel ->
            when (channel) {
                DeviceLightLibraryChannel.RED -> RED_PERCENT
                DeviceLightLibraryChannel.GREEN -> GREEN_PERCENT
                DeviceLightLibraryChannel.BLUE -> BLUE_PERCENT
                DeviceLightLibraryChannel.WHITE -> WHITE_PERCENT
            }
        }
        return DeviceLightLibraryEntry(
            id = "custom-entry",
            name = "Saved curve",
            productKey = "LIGHT_WRGB_PRO_ELITE",
            channels = channels,
            payload = DeviceLightLibraryPayload.Custom(
                weekdaysMask = WEEKDAYS_MASK,
                points = listOf(
                    DeviceLightLibraryCustomPoint(
                        timeMs = START_TIME_MS,
                        scene = DeviceLightLibraryScene(off)
                    ),
                    DeviceLightLibraryCustomPoint(
                        timeMs = END_TIME_MS,
                        scene = DeviceLightLibraryScene(on)
                    )
                )
            ),
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }

    private companion object {
        const val WEEKDAYS_MASK = 31
        const val START_TIME_MS = 28_800_000L
        const val END_TIME_MS = 64_800_000L
        const val RED_PERCENT = 24
        const val GREEN_PERCENT = 52
        const val BLUE_PERCENT = 60
        const val WHITE_PERCENT = 48
    }
}
