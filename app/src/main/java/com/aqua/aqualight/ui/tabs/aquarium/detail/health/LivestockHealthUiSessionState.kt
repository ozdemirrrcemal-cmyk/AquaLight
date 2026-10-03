package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle

internal data class ActiveLivestockFollowupUi(
    val livestockId: Long,
    val symptomKey: String,
    val affectedCount: Int,
    val totalCount: Int,
    val startedAtMillis: Long,
    val lastCheckAtMillis: Long
)

internal data class ClosedLivestockFollowupUi(
    val livestockId: Long,
    val symptomKey: String,
    val affectedCount: Int,
    val totalCount: Int,
    val startedAtMillis: Long,
    val closedAtMillis: Long,
    val closeReason: String,
    val checkCount: Int
)

internal object LivestockHealthUiSessionState {
    const val KEY_HAS_OBSERVATION = "livestock_health_ui_has_observation"
    const val KEY_LIVESTOCK_ID = "livestock_health_ui_livestock_id"
    const val KEY_SYMPTOM_KEY = "livestock_health_ui_symptom_key"
    const val KEY_AFFECTED_COUNT = "livestock_health_ui_affected_count"
    const val KEY_STARTED_AT = "livestock_health_ui_started_at"
    const val KEY_LAST_CHECK_AT = "livestock_health_ui_last_check_at"
    const val KEY_ACTIVE_REVISION = "livestock_health_active_revision"
    const val KEY_CLOSED_REVISION = "livestock_health_closed_revision"

    const val CLOSE_REASON_RECOVERED = "recovered"
    const val CLOSE_REASON_MANUAL = "manual"

    private const val KEY_ACTIVE_LIVESTOCK_IDS = "livestock_health_active_ids"
    private const val KEY_ACTIVE_SYMPTOMS = "livestock_health_active_symptoms"
    private const val KEY_ACTIVE_AFFECTED = "livestock_health_active_affected"
    private const val KEY_ACTIVE_TOTALS = "livestock_health_active_totals"
    private const val KEY_ACTIVE_STARTED = "livestock_health_active_started"
    private const val KEY_ACTIVE_LAST_CHECK = "livestock_health_active_last_check"

    private const val KEY_CLOSED_LIVESTOCK_IDS = "livestock_health_closed_ids"
    private const val KEY_CLOSED_SYMPTOMS = "livestock_health_closed_symptoms"
    private const val KEY_CLOSED_AFFECTED = "livestock_health_closed_affected"
    private const val KEY_CLOSED_TOTALS = "livestock_health_closed_totals"
    private const val KEY_CLOSED_STARTED = "livestock_health_closed_started"
    private const val KEY_CLOSED_AT = "livestock_health_closed_at"
    private const val KEY_CLOSED_REASONS = "livestock_health_closed_reasons"
    private const val KEY_CLOSED_CHECK_COUNTS = "livestock_health_closed_check_counts"

    fun activeFollowups(handle: SavedStateHandle): List<ActiveLivestockFollowupUi> {
        val ids = handle.get<LongArray>(KEY_ACTIVE_LIVESTOCK_IDS) ?: longArrayOf()
        val symptoms = handle.get<ArrayList<String>>(KEY_ACTIVE_SYMPTOMS).orEmpty()
        val affected = handle.get<IntArray>(KEY_ACTIVE_AFFECTED) ?: intArrayOf()
        val totals = handle.get<IntArray>(KEY_ACTIVE_TOTALS) ?: intArrayOf()
        val started = handle.get<LongArray>(KEY_ACTIVE_STARTED) ?: longArrayOf()
        val lastCheck = handle.get<LongArray>(KEY_ACTIVE_LAST_CHECK) ?: longArrayOf()

        val size = listOf(
            ids.size,
            symptoms.size,
            affected.size,
            totals.size,
            started.size,
            lastCheck.size
        ).minOrNull() ?: 0

        return List(size) { index ->
            ActiveLivestockFollowupUi(
                livestockId = ids[index],
                symptomKey = symptoms[index],
                affectedCount = affected[index],
                totalCount = totals[index],
                startedAtMillis = started[index],
                lastCheckAtMillis = lastCheck[index]
            )
        }
    }

    fun upsertActiveFollowup(
        handle: SavedStateHandle,
        entry: ActiveLivestockFollowupUi
    ) {
        val current = activeFollowups(handle).toMutableList()
        val index = current.indexOfFirst {
            it.livestockId == entry.livestockId && it.symptomKey == entry.symptomKey
        }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(0, entry)
        }
        writeActive(handle, current.take(MAX_UI_ACTIVE))
    }

    fun updateActiveFollowup(
        handle: SavedStateHandle,
        livestockId: Long,
        symptomKey: String,
        affectedCount: Int,
        lastCheckAtMillis: Long
    ) {
        val current = activeFollowups(handle).toMutableList()
        val index = current.indexOfFirst {
            it.livestockId == livestockId && it.symptomKey == symptomKey
        }
        if (index < 0) return
        current[index] = current[index].copy(
            affectedCount = affectedCount,
            lastCheckAtMillis = lastCheckAtMillis
        )
        writeActive(handle, current)
    }

    fun removeActiveFollowup(
        handle: SavedStateHandle,
        livestockId: Long,
        symptomKey: String
    ): ActiveLivestockFollowupUi? {
        val current = activeFollowups(handle).toMutableList()
        val index = current.indexOfFirst {
            it.livestockId == livestockId && it.symptomKey == symptomKey
        }
        if (index < 0) return null
        val removed = current.removeAt(index)
        writeActive(handle, current)
        return removed
    }

    fun closedFollowups(handle: SavedStateHandle): List<ClosedLivestockFollowupUi> {
        val ids = handle.get<LongArray>(KEY_CLOSED_LIVESTOCK_IDS) ?: longArrayOf()
        val symptoms = handle.get<ArrayList<String>>(KEY_CLOSED_SYMPTOMS).orEmpty()
        val affected = handle.get<IntArray>(KEY_CLOSED_AFFECTED) ?: intArrayOf()
        val totals = handle.get<IntArray>(KEY_CLOSED_TOTALS) ?: intArrayOf()
        val started = handle.get<LongArray>(KEY_CLOSED_STARTED) ?: longArrayOf()
        val closed = handle.get<LongArray>(KEY_CLOSED_AT) ?: longArrayOf()
        val reasons = handle.get<ArrayList<String>>(KEY_CLOSED_REASONS).orEmpty()
        val checks = handle.get<IntArray>(KEY_CLOSED_CHECK_COUNTS) ?: intArrayOf()

        val size = listOf(
            ids.size,
            symptoms.size,
            affected.size,
            totals.size,
            started.size,
            closed.size,
            reasons.size,
            checks.size
        ).minOrNull() ?: 0

        return List(size) { index ->
            ClosedLivestockFollowupUi(
                livestockId = ids[index],
                symptomKey = symptoms[index],
                affectedCount = affected[index],
                totalCount = totals[index],
                startedAtMillis = started[index],
                closedAtMillis = closed[index],
                closeReason = reasons[index],
                checkCount = checks[index]
            )
        }
    }

    fun addClosedFollowup(
        handle: SavedStateHandle,
        entry: ClosedLivestockFollowupUi
    ) {
        val current = closedFollowups(handle).toMutableList()
        current.add(0, entry)
        val trimmed = current.take(MAX_UI_HISTORY)

        handle[KEY_CLOSED_LIVESTOCK_IDS] = trimmed.map { it.livestockId }.toLongArray()
        handle[KEY_CLOSED_SYMPTOMS] = ArrayList(trimmed.map { it.symptomKey })
        handle[KEY_CLOSED_AFFECTED] = trimmed.map { it.affectedCount }.toIntArray()
        handle[KEY_CLOSED_TOTALS] = trimmed.map { it.totalCount }.toIntArray()
        handle[KEY_CLOSED_STARTED] = trimmed.map { it.startedAtMillis }.toLongArray()
        handle[KEY_CLOSED_AT] = trimmed.map { it.closedAtMillis }.toLongArray()
        handle[KEY_CLOSED_REASONS] = ArrayList(trimmed.map { it.closeReason })
        handle[KEY_CLOSED_CHECK_COUNTS] = trimmed.map { it.checkCount }.toIntArray()
        bump(handle, KEY_CLOSED_REVISION)
    }

    private fun writeActive(
        handle: SavedStateHandle,
        entries: List<ActiveLivestockFollowupUi>
    ) {
        handle[KEY_ACTIVE_LIVESTOCK_IDS] = entries.map { it.livestockId }.toLongArray()
        handle[KEY_ACTIVE_SYMPTOMS] = ArrayList(entries.map { it.symptomKey })
        handle[KEY_ACTIVE_AFFECTED] = entries.map { it.affectedCount }.toIntArray()
        handle[KEY_ACTIVE_TOTALS] = entries.map { it.totalCount }.toIntArray()
        handle[KEY_ACTIVE_STARTED] = entries.map { it.startedAtMillis }.toLongArray()
        handle[KEY_ACTIVE_LAST_CHECK] = entries.map { it.lastCheckAtMillis }.toLongArray()
        handle[KEY_HAS_OBSERVATION] = entries.isNotEmpty()
        bump(handle, KEY_ACTIVE_REVISION)
    }

    private fun bump(handle: SavedStateHandle, key: String) {
        val current = handle.get<Int>(key) ?: 0
        handle[key] = current + 1
    }

    private const val MAX_UI_ACTIVE = 20
    private const val MAX_UI_HISTORY = 20
}
