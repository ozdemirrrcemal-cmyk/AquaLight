from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"Expected exactly one match in {path}, found {count}: {old[:80]!r}"
        )
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


limits_path = Path(
    "app/src/main/java/com/aqua/aqualight/application/care/CareTaskInputLimits.kt"
)
limits_path.parent.mkdir(parents=True, exist_ok=True)
limits_content = '''package com.aqua.aqualight.application.care

/** Single commercial product contract for user-entered care-task schedules. */
object CareTaskInputLimits {

    const val MIN_REPEAT_INTERVAL_DAYS = 1
    const val MAX_REPEAT_INTERVAL_DAYS = 365

    const val MIN_MISSED_REMINDER_DAYS = 1
    const val MAX_MISSED_REMINDER_DAYS = 30

    fun isValidRepeatIntervalDays(value: Int): Boolean =
        value in MIN_REPEAT_INTERVAL_DAYS..MAX_REPEAT_INTERVAL_DAYS

    fun isValidMissedReminderDays(value: Int): Boolean =
        value in MIN_MISSED_REMINDER_DAYS..MAX_MISSED_REMINDER_DAYS

    fun parseRepeatIntervalDays(rawValue: String): Int? =
        rawValue.trim().toIntOrNull()?.takeIf(::isValidRepeatIntervalDays)

    fun parseMissedReminderDays(rawValue: String): Int? =
        rawValue.trim().toIntOrNull()?.takeIf(::isValidMissedReminderDays)
}
'''
limits_path.write_text(limits_content, encoding="utf-8")

rules = "app/src/main/java/com/aqua/aqualight/data/care/CareTaskStoreRules.kt"
replace_once(
    rules,
    "package com.aqua.aqualight.data.care\n\nimport com.aqua.aqualight.data.care.model.CareTask",
    "package com.aqua.aqualight.data.care\n\n"
    "import com.aqua.aqualight.application.care.CareTaskInputLimits\n"
    "import com.aqua.aqualight.data.care.model.CareTask",
)
replace_once(
    rules,
    '''    const val MIN_REPEAT_INTERVAL_DAYS = 1
    const val MAX_REPEAT_INTERVAL_DAYS = 3_650
    const val MIN_MISSED_REMINDER_DAYS = 1
    const val MAX_MISSED_REMINDER_DAYS = 365''',
    '''    const val MIN_REPEAT_INTERVAL_DAYS =
        CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS
    const val MAX_REPEAT_INTERVAL_DAYS =
        CareTaskInputLimits.MAX_REPEAT_INTERVAL_DAYS
    const val MIN_MISSED_REMINDER_DAYS =
        CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS
    const val MAX_MISSED_REMINDER_DAYS =
        CareTaskInputLimits.MAX_MISSED_REMINDER_DAYS''',
)

fragment = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/"
    "AddCareTaskFragment.kt"
)
replace_once(
    fragment,
    "import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot\n",
    "import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot\n"
    "import com.aqua.aqualight.application.care.CareTaskInputLimits\n",
)
replace_once(
    fragment,
    "            task.repeatIntervalDays.coerceAtLeast(1).toString()",
    "            task.repeatIntervalDays.toString()",
)
replace_once(
    fragment,
    "            task.missedReminderDays.coerceAtLeast(1).toString()",
    "            task.missedReminderDays.toString()",
)
replace_once(
    fragment,
    '''    private fun saveTask() {
        if (ensureNotificationPermissionBeforeSave()) {
            saveTaskInternal()
        }
    }''',
    '''    private fun saveTask() {
        if (readScheduleValues() == null) {
            return
        }
        if (ensureNotificationPermissionBeforeSave()) {
            saveTaskInternal()
        }
    }''',
)
replace_once(
    fragment,
    '''        val repeatDays = binding.etRepeatDays.text.toString()
            .toIntOrNull()
            ?.coerceAtLeast(1)
            ?: getString(R.string.maintenance_default_repeat_days).toInt()
        val missedDays = binding.etMissedReminderDays.text.toString()
            .toIntOrNull()
            ?.coerceAtLeast(1)
            ?: getString(
                R.string.maintenance_default_missed_reminder_days
            ).toInt()''',
    '''        val scheduleValues = readScheduleValues() ?: return
        val repeatDays = scheduleValues.repeatIntervalDays
        val missedDays = scheduleValues.missedReminderDays''',
)
replace_once(
    fragment,
    '''    private fun closeForm() {
        findNavController().navigateUp()
    }''',
    '''    private fun readScheduleValues(): CareTaskScheduleValues? {
        val repeatIntervalDays = if (binding.switchRepeat.isChecked) {
            CareTaskInputLimits.parseRepeatIntervalDays(
                binding.etRepeatDays.text.toString()
            ) ?: run {
                showSnackBar(
                    getString(
                        R.string.maintenance_validation_repeat_days_range,
                        CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS,
                        CareTaskInputLimits.MAX_REPEAT_INTERVAL_DAYS
                    ),
                    BaseActivity.SnackType.WARNING
                )
                return null
            }
        } else {
            CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS
        }

        val missedReminderEnabled =
            binding.switchReminder.isChecked && binding.switchMissedReminder.isChecked
        val missedReminderDays = if (missedReminderEnabled) {
            CareTaskInputLimits.parseMissedReminderDays(
                binding.etMissedReminderDays.text.toString()
            ) ?: run {
                showSnackBar(
                    getString(
                        R.string.maintenance_validation_missed_reminder_days_range,
                        CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS,
                        CareTaskInputLimits.MAX_MISSED_REMINDER_DAYS
                    ),
                    BaseActivity.SnackType.WARNING
                )
                return null
            }
        } else {
            CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS
        }

        return CareTaskScheduleValues(
            repeatIntervalDays = repeatIntervalDays,
            missedReminderDays = missedReminderDays
        )
    }

    private fun closeForm() {
        findNavController().navigateUp()
    }''',
)
replace_once(
    fragment,
    '''    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }''',
    '''    private data class CareTaskScheduleValues(
        val repeatIntervalDays: Int,
        val missedReminderDays: Int
    )

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }''',
)

strings = "app/src/main/res/values/maintenance_strings.xml"
replace_once(
    strings,
    '    <string name="maintenance_validation_custom_title_short">Custom task title must be at least 2 characters.</string>',
    '''    <string name="maintenance_validation_custom_title_short">Custom task title must be at least 2 characters.</string>
    <string name="maintenance_validation_repeat_days_range">Repeat interval must be between %1$d and %2$d days.</string>
    <string name="maintenance_validation_missed_reminder_days_range">Missed reminder duration must be between %1$d and %2$d days.</string>''',
)

test_path = Path(
    "app/src/test/java/com/aqua/aqualight/data/care/"
    "CareTaskCommercialLimitContractTest.kt"
)
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text(
    '''package com.aqua.aqualight.data.care

import com.aqua.aqualight.application.care.CareTaskInputLimits
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CareTaskCommercialLimitContractTest {

    @Test
    fun applicationParserRejectsBlankZeroAndAboveCommercialBounds() {
        assertEquals(1, CareTaskInputLimits.parseRepeatIntervalDays("1"))
        assertEquals(365, CareTaskInputLimits.parseRepeatIntervalDays("365"))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays(""))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays("0"))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays("366"))

        assertEquals(1, CareTaskInputLimits.parseMissedReminderDays("1"))
        assertEquals(30, CareTaskInputLimits.parseMissedReminderDays("30"))
        assertNull(CareTaskInputLimits.parseMissedReminderDays(""))
        assertNull(CareTaskInputLimits.parseMissedReminderDays("0"))
        assertNull(CareTaskInputLimits.parseMissedReminderDays("31"))
    }

    @Test
    fun storeAndApplicationUseTheSameCommercialLimits() {
        assertEquals(
            CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS,
            CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MAX_REPEAT_INTERVAL_DAYS,
            CareTaskStoreRules.MAX_REPEAT_INTERVAL_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS,
            CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MAX_MISSED_REMINDER_DAYS,
            CareTaskStoreRules.MAX_MISSED_REMINDER_DAYS
        )
    }

    @Test
    fun storeAcceptsBoundaryValuesAndRejectsValuesAboveThem() {
        CareTaskStoreRules.validateStoredTask(
            validTask(repeatIntervalDays = 365, missedReminderDays = 30)
        )

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(
                validTask(repeatIntervalDays = 366, missedReminderDays = 30)
            )
        }
        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(
                validTask(repeatIntervalDays = 365, missedReminderDays = 31)
            )
        }
    }

    private fun validTask(
        repeatIntervalDays: Int,
        missedReminderDays: Int
    ): StoredCareTask = StoredCareTask.newBuilder()
        .setId(901L)
        .setOwnerUid("owner-a")
        .setTankId(77L)
        .setTitle("Commercial limit test")
        .setDescription("")
        .setType(CareTaskType.CUSTOM.name)
        .setSource(CareTaskSource.MANUAL.name)
        .setStatus(CareTaskStatus.PENDING.name)
        .setDueAtMillis(1_767_312_000_000L)
        .setCompletedAtMillis(0L)
        .setRepeatEnabled(true)
        .setRepeatIntervalDays(repeatIntervalDays)
        .setReminderEnabled(true)
        .setMissedReminderEnabled(true)
        .setMissedReminderDays(missedReminderDays)
        .setWaterChangePercent(0)
        .setNote("")
        .setGeneratedRuleKey("")
        .setCreatedAtMillis(1_767_225_600_000L)
        .setUpdatedAtMillis(1_767_225_600_000L)
        .build()
}
''',
    encoding="utf-8",
)

contract = "docs/stage5-data-integrity-contract.md"
replace_once(
    contract,
    "## Delivery rule",
    '''## Care schedule product limits

- Repeat interval: `1..365` days.
- Missed-reminder duration: `1..30` days.
- Blank, zero, malformed, or out-of-range values are rejected; they are never silently coerced.
- UI input parsing and persistent-store validation use the same application contract.

## Delivery rule''',
)
