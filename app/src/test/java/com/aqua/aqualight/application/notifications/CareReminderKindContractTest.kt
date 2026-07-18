package com.aqua.aqualight.application.notifications

import com.aqua.aqualight.data.care.model.CareTaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class CareReminderKindContractTest {

    @Test
    fun notificationKindsCoverEveryPersistedCareTaskTypeExactly() {
        assertEquals(
            CareTaskType.entries.map(CareTaskType::name).toSet(),
            CareReminderKind.entries.map(CareReminderKind::name).toSet()
        )
    }
}
