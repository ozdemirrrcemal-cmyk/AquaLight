package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import java.util.UUID

/** Each fragment owns this instance and its immutable Safe Args route. */
class WaterAnalysisViewModel(
    private val operations: WaterAnalysisOperations,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val tankId: Long = checkNotNull(savedStateHandle["tankId"])
    private val analysisId: Long? = savedStateHandle["analysisId"]
    private val history by lazy { WaterAnalysisHistoryController(operations, savedStateHandle, tankId) }
    internal val mutations = WaterAnalysisMutationController(operations, viewModelScope)
    internal val requestId: String = savedStateHandle.get<String>("water_request_id")
        ?: UUID.randomUUID().toString().also { savedStateHandle["water_request_id"] = it }
    internal var draft: Bundle?
        get() = savedStateHandle["water_draft"]
        set(value) { savedStateHandle["water_draft"] = value }

    init { require(tankId > 0 && (analysisId == null || analysisId > 0)) }

    fun historyStateForTank(tankId: Long) = history.state.also {
        require(tankId == this.tankId)
    }

    fun showHistoryPage(cursor: WaterHistoryCursor, newer: Boolean) = history.show(cursor, newer)

    fun latestAnalysisState(tankId: Long): LiveData<WaterAnalysisLoadState<WaterAnalysisSnapshot?>> {
        require(tankId == this.tankId)
        return operations.latestAnalysis(tankId).asWaterLoadState().asLiveData()
    }

    fun analysisState(tankId: Long, analysisId: Long): LiveData<WaterAnalysisLoadState<WaterAnalysisSnapshot?>> {
        require(tankId == this.tankId && analysisId == this.analysisId)
        return operations.analysis(tankId, analysisId).asWaterLoadState().asLiveData()
    }

    internal fun saveAnalysis(input: WaterAnalysisInput) {
        require(input.tankId == tankId && analysisId == null)
        mutations.save(input)
    }

    internal fun deleteAnalysis(analysisId: Long) {
        require(analysisId == this.analysisId)
        mutations.delete(tankId, analysisId)
    }
}
