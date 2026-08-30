package com.aqua.aqualight.data.care

import com.aqua.aqualight.application.care.CareTaskInputLimits
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation

/** Authoritative invariant rules for the commercial care-task store. */
object CareTaskStoreRules {

    const val MIN_REPEAT_INTERVAL_DAYS =
        CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS
    const val MAX_REPEAT_INTERVAL_DAYS =
        CareTaskInputLimits.MAX_REPEAT_INTERVAL_DAYS
    const val MIN_MISSED_REMINDER_DAYS =
        CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS
    const val MAX_MISSED_REMINDER_DAYS =
        CareTaskInputLimits.MAX_MISSED_REMINDER_DAYS
    const val MIN_WATER_CHANGE_PERCENT = 1
    const val MAX_WATER_CHANGE_PERCENT = 100

    const val MAX_TITLE_CHARS = 120
    const val MAX_DESCRIPTION_CHARS = 2_000
    const val MAX_NOTE_CHARS = 2_000
    const val MAX_GENERATED_RULE_KEY_CHARS = 160

    private const val MIN_DATE_MILLIS = 946_684_800_000L // 2000-01-01 UTC
    private const val MAX_DATE_MILLIS = 4_102_444_800_000L // 2100-01-01 UTC

    private val taskTypes = CareTaskType.entries.mapTo(mutableSetOf()) { it.name }
    private val taskSources = CareTaskSource.entries.mapTo(mutableSetOf()) { it.name }
    private val taskStatuses = CareTaskStatus.entries.mapTo(mutableSetOf()) { it.name }

    fun defaultStore(): CareTasksStore = CareTasksStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.CARE_TASKS_VERSION)
        .build()

    fun validateStore(store: CareTasksStore): CareTasksStore {
        CommercialStoreSchema.requireCurrent(
            storeName = "CareTasksStore",
            actualVersion = store.schemaVersion,
            expectedVersion = CommercialStoreSchema.CARE_TASKS_VERSION
        )

        val ownerScopedIds = mutableSetOf<Pair<String, Long>>()

        store.tasksList.forEach { task ->
            validateStoredTask(task)

            val ownerKey = canonicalOwnerUid(task.ownerUid)
            if (!ownerScopedIds.add(ownerKey to task.id)) {
                violation("Duplicate care-task id ${task.id} for owner $ownerKey.")
            }
        }

        return store
    }

    fun validateTask(
        task: CareTask,
        expectedOwnerUid: String? = null
    ): CareTask {
        requirePositive("task.id", task.id)
        val ownerUid = canonicalOwnerUid(task.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        requirePositive("task.tankId", task.tankId)
        requireCanonicalRequiredText("task.title", task.title, MAX_TITLE_CHARS)
        requireTextLength("task.description", task.description, MAX_DESCRIPTION_CHARS)
        requireDate("task.dueAtMillis", task.dueAtMillis)
        requireOptionalDate("task.completedAtMillis", task.completedAtMillis ?: 0L)
        requireDate("task.createdAtMillis", task.createdAtMillis)
        requireDate("task.updatedAtMillis", task.updatedAtMillis)

        if (task.updatedAtMillis < task.createdAtMillis) {
            violation("task.updatedAtMillis must not precede task.createdAtMillis.")
        }

        validateStateRelationships(
            type = task.type,
            source = task.source,
            status = task.status,
            completedAtMillis = task.completedAtMillis ?: 0L,
            updatedAtMillis = task.updatedAtMillis,
            repeatEnabled = task.repeatEnabled,
            repeatIntervalDays = task.repeatIntervalDays,
            reminderEnabled = task.reminderEnabled,
            missedReminderEnabled = task.missedReminderEnabled,
            missedReminderDays = task.missedReminderDays,
            waterChangePercent = task.waterChangePercent ?: 0,
            generatedRuleKey = task.generatedRuleKey
        )

        requireTextLength("task.note", task.note, MAX_NOTE_CHARS)
        return task
    }

    fun validateStoredTask(
        task: StoredCareTask,
        expectedOwnerUid: String? = null
    ): StoredCareTask {
        requirePositive("task.id", task.id)
        val ownerUid = canonicalOwnerUid(task.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        requirePositive("task.tankId", task.tankId)
        requireCanonicalRequiredText("task.title", task.title, MAX_TITLE_CHARS)
        requireTextLength("task.description", task.description, MAX_DESCRIPTION_CHARS)
        requireEnum("task.type", task.type, taskTypes)
        requireEnum("task.source", task.source, taskSources)
        requireEnum("task.status", task.status, taskStatuses)
        requireDate("task.dueAtMillis", task.dueAtMillis)
        requireOptionalDate("task.completedAtMillis", task.completedAtMillis)
        requireDate("task.createdAtMillis", task.createdAtMillis)
        requireDate("task.updatedAtMillis", task.updatedAtMillis)

        if (task.updatedAtMillis < task.createdAtMillis) {
            violation("task.updatedAtMillis must not precede task.createdAtMillis.")
        }

        validateStateRelationships(
            type = CareTaskType.valueOf(task.type),
            source = CareTaskSource.valueOf(task.source),
            status = CareTaskStatus.valueOf(task.status),
            completedAtMillis = task.completedAtMillis,
            updatedAtMillis = task.updatedAtMillis,
            repeatEnabled = task.repeatEnabled,
            repeatIntervalDays = task.repeatIntervalDays,
            reminderEnabled = task.reminderEnabled,
            missedReminderEnabled = task.missedReminderEnabled,
            missedReminderDays = task.missedReminderDays,
            waterChangePercent = task.waterChangePercent,
            generatedRuleKey = task.generatedRuleKey
        )

        requireTextLength("task.note", task.note, MAX_NOTE_CHARS)
        return task
    }

    fun requireValidTankId(tankId: Long) {
        requirePositive("tankId", tankId)
    }

    fun nextUniqueId(
        currentTasks: List<StoredCareTask>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val maxExistingId = currentTasks.maxOfOrNull { it.id } ?: 0L
        val next = maxOf(nowMillis, maxExistingId + 1L)
        requirePositive("generated task id", next)
        return next
    }

    private fun validateStateRelationships(
        type: CareTaskType,
        source: CareTaskSource,
        status: CareTaskStatus,
        completedAtMillis: Long,
        updatedAtMillis: Long,
        repeatEnabled: Boolean,
        repeatIntervalDays: Int,
        reminderEnabled: Boolean,
        missedReminderEnabled: Boolean,
        missedReminderDays: Int,
        waterChangePercent: Int,
        generatedRuleKey: String
    ) {
        if (repeatIntervalDays !in MIN_REPEAT_INTERVAL_DAYS..MAX_REPEAT_INTERVAL_DAYS) {
            violation(
                "repeatIntervalDays must be between $MIN_REPEAT_INTERVAL_DAYS and " +
                    "$MAX_REPEAT_INTERVAL_DAYS."
            )
        }
        if (!repeatEnabled && repeatIntervalDays != MIN_REPEAT_INTERVAL_DAYS) {
            violation("Disabled repeat must use the canonical interval value 1.")
        }

        if (missedReminderDays !in MIN_MISSED_REMINDER_DAYS..MAX_MISSED_REMINDER_DAYS) {
            violation(
                "missedReminderDays must be between $MIN_MISSED_REMINDER_DAYS and " +
                    "$MAX_MISSED_REMINDER_DAYS."
            )
        }
        if (!reminderEnabled && missedReminderEnabled) {
            violation("Missed reminders require the primary reminder to be enabled.")
        }
        if (!missedReminderEnabled && missedReminderDays != MIN_MISSED_REMINDER_DAYS) {
            violation("Disabled missed reminders must use the canonical day value 1.")
        }

        when (status) {
            CareTaskStatus.PENDING -> {
                if (completedAtMillis != 0L) {
                    violation("Pending tasks must not have a completion timestamp.")
                }
            }

            CareTaskStatus.COMPLETED -> {
                requireDate("task.completedAtMillis", completedAtMillis)
                if (completedAtMillis > updatedAtMillis) {
                    violation(
                        "task.completedAtMillis must not be later than task.updatedAtMillis."
                    )
                }
            }
        }

        when (type) {
            CareTaskType.WATER_CHANGE -> {
                if (waterChangePercent !in MIN_WATER_CHANGE_PERCENT..MAX_WATER_CHANGE_PERCENT) {
                    violation(
                        "Water-change percent must be between $MIN_WATER_CHANGE_PERCENT and " +
                            "$MAX_WATER_CHANGE_PERCENT."
                    )
                }
            }

            else -> {
                if (waterChangePercent != 0) {
                    violation("Only WATER_CHANGE tasks may persist a water-change percent.")
                }
            }
        }

        when (source) {
            CareTaskSource.MANUAL -> {
                if (generatedRuleKey.isNotBlank()) {
                    violation("Manual tasks must not persist a generated rule key.")
                }
            }

            CareTaskSource.AUTOMATIC -> {
                requireCanonicalRequiredText(
                    "task.generatedRuleKey",
                    generatedRuleKey,
                    MAX_GENERATED_RULE_KEY_CHARS
                )
            }
        }
    }

    private fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("ownerUid must be non-blank and canonical.")
        }
        if (canonical.length > 128) {
            violation("ownerUid exceeds 128 characters.")
        }
        return canonical
    }

    private fun requireExpectedOwner(actualOwnerUid: String, expectedOwnerUid: String?) {
        val expected = expectedOwnerUid?.trim().orEmpty()
        if (expected.isNotEmpty() && actualOwnerUid != expected) {
            violation("Record owner does not match the active owner.")
        }
    }

    private fun requirePositive(field: String, value: Long) {
        if (value <= 0L) {
            violation("$field must be positive.")
        }
    }

    private fun requireEnum(field: String, value: String, allowed: Set<String>) {
        if (value !in allowed) {
            violation("$field contains an unsupported enum value.")
        }
    }

    private fun requireCanonicalRequiredText(
        field: String,
        value: String,
        maxChars: Int
    ) {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("$field must be non-blank and canonical.")
        }
        requireTextLength(field, canonical, maxChars)
    }

    private fun requireTextLength(field: String, value: String, maxChars: Int) {
        if (value.length > maxChars) {
            violation("$field exceeds $maxChars characters.")
        }
    }

    private fun requireOptionalDate(field: String, value: Long) {
        if (value != 0L) {
            requireDate(field, value)
        }
    }

    private fun requireDate(field: String, value: Long) {
        if (value !in MIN_DATE_MILLIS..MAX_DATE_MILLIS) {
            violation("$field is outside the supported commercial date range.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}
