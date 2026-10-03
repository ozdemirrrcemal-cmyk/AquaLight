package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle

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

    const val CLOSE_REASON_RECOVERED = "recovered"
    const val CLOSE_REASON_MANUAL = "manual"

    private const val KEY_CLOSED_LIVESTOCK_IDS = "livestock_health_closed_ids"
    private const val KEY_CLOSED_SYMPTOMS = "livestock_health_closed_symptoms"
    private const val KEY_CLOSED_AFFECTED = "livestock_health_closed_affected"
    private const val KEY_CLOSED_TOTALS = "livestock_health_closed_totals"
    private const val KEY_CLOSED_STARTED = "livestock_health_closed_started"
    private const val KEY_CLOSED_AT = "livestock_health_closed_at"
    private const val KEY_CLOSED_REASONS = "livestock_health_closed_reasons"
    private const val KEY_CLOSED_CHECK_COUNTS = "livestock_health_closed_check_counts"

    fun closedFollowups(handle: SavedStateHandle): List<ClosedLivestockFollowupUi> {
        val ids = handle.get<LongArray>(KEY_CLOSED_LIVESTOCK_IDS).orEmpty()
        val symptoms = handle.get<ArrayList<String>>(KEY_CLOSED_SYMPTOMS).orEmpty()
        val affected = handle.get<IntArray>(KEY_CLOSED_AFFECTED).orEmpty()
        val totals = handle.get<IntArray>(KEY_CLOSED_TOTALS).orEmpty()
        val started = handle.get<LongArray>(KEY_CLOSED_STARTED).orEmpty()
        val closed = handle.get<LongArray>(KEY_CLOSED_AT).orEmpty()
        val reasons = handle.get<ArrayList<String>>(KEY_CLOSED_REASONS).orEmpty()
        val checks = handle.get<IntArray>(KEY_CLOSED_CHECK_COUNTS).orEmpty()

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
    }

    private const val MAX_UI_HISTORY = 20
}
