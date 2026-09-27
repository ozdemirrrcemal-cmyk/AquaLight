package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

/** Saves one cursor, never a record list or an unbounded stack of visited pages. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WaterAnalysisHistoryController(
    operations: WaterAnalysisOperations,
    private val savedState: SavedStateHandle,
    private val tankId: Long
) {
    val state = savedState.getStateFlow<Bundle?>(PAGE_KEY, null).flatMapLatest { page ->
        val cursor = page?.let {
            WaterHistoryCursor(tankId, it.getLong("observed"), it.getLong("created"), it.getLong("analysis"))
        }
        operations.historyPage(tankId, cursor, page?.getBoolean("newer") == true).asWaterLoadState()
    }.asLiveData()

    fun show(cursor: WaterHistoryCursor, newer: Boolean) {
        require(cursor.tankId == tankId)
        savedState[PAGE_KEY] = Bundle().apply {
            putLong("observed", cursor.observedAtMillis)
            putLong("created", cursor.createdAtMillis)
            putLong("analysis", cursor.analysisId)
            putBoolean("newer", newer)
        }
    }

    private companion object { const val PAGE_KEY = "water_history_page" }
}
