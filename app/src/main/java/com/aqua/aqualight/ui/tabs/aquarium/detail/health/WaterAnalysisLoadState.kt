package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisFailure
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface WaterAnalysisLoadState<out T> {
    data object Loading : WaterAnalysisLoadState<Nothing>
    data class Content<T>(val value: T) : WaterAnalysisLoadState<T>
    data class Error(val failure: WaterAnalysisFailure) : WaterAnalysisLoadState<Nothing>
}

internal fun <T> Flow<T>.asWaterLoadState(): Flow<WaterAnalysisLoadState<T>> =
    map<T, WaterAnalysisLoadState<T>> { WaterAnalysisLoadState.Content(it) }
        .onStart { emit(WaterAnalysisLoadState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(WaterAnalysisLoadState.Error(
                (error as? WaterAnalysisUnavailableException)?.failure ?: WaterAnalysisFailure.STORE_UNAVAILABLE
            ))
        }
