package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot

class WaterAnalysisViewModel(
    private val operations: WaterAnalysisOperations
) : ViewModel() {

    fun analysesForTank(tankId: Long): LiveData<List<WaterAnalysisSnapshot>> =
        operations.analysesForTank(tankId).asLiveData()

    fun analysis(analysisId: Long): LiveData<WaterAnalysisSnapshot?> =
        operations.analysis(analysisId).asLiveData()

    suspend fun saveAnalysis(input: WaterAnalysisInput): Long =
        operations.saveAnalysis(input)

    suspend fun deleteAnalysis(analysisId: Long) =
        operations.deleteAnalysis(analysisId)
}
