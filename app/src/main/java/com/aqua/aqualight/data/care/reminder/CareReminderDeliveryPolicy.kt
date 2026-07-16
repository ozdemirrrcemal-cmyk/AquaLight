package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus

/** Final delivery-time policy for an already scheduled care reminder. */
internal object CareReminderDeliveryPolicy {

    fun shouldDeliver(
        task: CareTask,
        tank: SavedAquariumTank?
    ): Boolean {
        return task.status == CareTaskStatus.PENDING &&
            task.reminderEnabled &&
            tank?.careRemindersEnabled == true
    }
}
