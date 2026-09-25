package com.aqua.aqualight.application.aquarium

/** Stable observation vocabulary. Display text is resolved in the UI layer. */
enum class LivestockHealthSymptom(val code: String) {
    SURFACE_FREQUENCY_CHANGE("surface_frequency_change"),
    APPETITE_CHANGE("appetite_change"),
    SWIMMING_CHANGE("swimming_change"),
    SKIN_OR_SPOTS("skin_or_spots"),
    FIN_CHANGE("fin_change"),
    ACTIVITY_CHANGE("activity_change"),
    COLOR_CHANGE("color_change"),
    MOLTING_CHANGE("molting_change"),
    SHELL_CHANGE("shell_change"),
    POLYP_RETRACTION("polyp_retraction"),
    OTHER("other");

    companion object {
        fun fromCode(code: String): LivestockHealthSymptom =
            entries.first { it.code == code }
    }
}

enum class LivestockHealthTrend(val code: String) {
    INCREASING("increasing"),
    SAME("same"),
    DECREASING("decreasing"),
    RESOLVED("resolved");

    companion object {
        fun fromCode(code: String): LivestockHealthTrend =
            entries.first { it.code == code }
    }
}

enum class BaselineChange(val code: String) {
    YES("yes"), UNSURE("unsure");

    companion object {
        fun fromCode(code: String): BaselineChange =
            entries.first { it.code == code }
    }
}

data class LivestockHealthCheck(
    val id: Long,
    val observedAtMillis: Long,
    val affectedCount: Int,
    val trend: LivestockHealthTrend,
    val note: String,
    val photoUri: String? = null
)

data class LivestockHealthObservation(
    val id: Long,
    val livestockId: Long,
    val livestockName: String,
    val livestockCategory: String,
    val catalogEntryId: String,
    val affectedCount: Int,
    val observedAtMillis: Long,
    val startedAtMillis: Long,
    val symptoms: List<LivestockHealthSymptom>,
    val trend: LivestockHealthTrend,
    val note: String,
    val baselineChange: BaselineChange?,
    val closedAtMillis: Long?,
    val outcome: LivestockHealthTrend?,
    val checks: List<LivestockHealthCheck>,
    val photoUri: String? = null
) {
    val isActive: Boolean get() = closedAtMillis == null
    val latestTrend: LivestockHealthTrend get() = checks.lastOrNull()?.trend ?: trend
    val latestAffectedCount: Int get() = checks.lastOrNull()?.affectedCount ?: affectedCount
}
