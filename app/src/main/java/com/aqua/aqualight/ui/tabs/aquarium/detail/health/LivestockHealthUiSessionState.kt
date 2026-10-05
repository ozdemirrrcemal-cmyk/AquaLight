package com.aqua.aqualight.ui.tabs.aquarium.detail.health

internal data class ActiveLivestockFollowupUi(
    val observationId: Long,
    val livestockId: Long,
    val symptomKey: String,
    val otherObservation: String,
    val affectedCount: Int,
    val totalCount: Int,
    val startedAtMillis: Long,
    val lastCheckAtMillis: Long
)

internal data class ClosedLivestockFollowupUi(
    val observationId: Long,
    val livestockId: Long,
    val symptomKey: String,
    val otherObservation: String,
    val affectedCount: Int,
    val totalCount: Int,
    val startedAtMillis: Long,
    val closedAtMillis: Long,
    val closeReason: String,
    val checkCount: Int
)

internal object LivestockHealthUiSessionState {
    const val CLOSE_REASON_RECOVERED = "recovered"
    const val CLOSE_REASON_MANUAL = "manual"
}
